#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Check the API-21 dependency allowlist and optional resolved AAR manifests.

The default invocation is deliberately local. It reads versions.gradle and the checked-in
allowlist only. Maintainers can add one or more ``--candidate NAME=path/to/library.aar`` values
to inspect resolved AAR manifests before changing a pinned dependency.
"""

from __future__ import annotations

import argparse
import json
import re
import struct
import sys
import zipfile
from pathlib import Path
from typing import Any, Iterable, Sequence
from xml.etree import ElementTree


ANDROID_NS = "http://schemas.android.com/apk/res/android"
ANDROID_MIN_SDK = "minSdkVersion"
POLICY_SCHEMA_VERSION = 1
MAX_MANIFEST_BYTES = 8 * 1024 * 1024
NO_INDEX = 0xFFFFFFFF

RES_XML_TYPE = 0x0003
RES_STRING_POOL_TYPE = 0x0001
RES_XML_START_ELEMENT_TYPE = 0x0102
RES_XML_END_ELEMENT_TYPE = 0x0103
UTF8_FLAG = 0x00000100
TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11


class VerificationError(ValueError):
    """A policy or manifest could not be checked safely."""


def _u16(data: bytes, offset: int) -> int:
    if offset < 0 or offset + 2 > len(data):
        raise VerificationError("truncated binary XML")
    return struct.unpack_from("<H", data, offset)[0]


def _u32(data: bytes, offset: int) -> int:
    if offset < 0 or offset + 4 > len(data):
        raise VerificationError("truncated binary XML")
    return struct.unpack_from("<I", data, offset)[0]


def _decode_length8(data: bytes, offset: int) -> tuple[int, int]:
    if offset < 0 or offset >= len(data):
        raise VerificationError("truncated UTF-8 string length")
    first = data[offset]
    if first & 0x80:
        if offset + 1 >= len(data):
            raise VerificationError("truncated UTF-8 string length")
        return ((first & 0x7F) << 8) | data[offset + 1], 2
    return first, 1


def _decode_length16(data: bytes, offset: int) -> tuple[int, int]:
    first = _u16(data, offset)
    if first & 0x8000:
        return ((first & 0x7FFF) << 16) | _u16(data, offset + 2), 4
    return first, 2


def _read_string(data: bytes, string_data: int, string_offset: int, utf8: bool) -> str:
    start = string_data + string_offset
    if start < 0 or start >= len(data):
        raise VerificationError("string pool offset is outside the manifest")
    if utf8:
        _, length_size = _decode_length8(data, start)
        byte_length, byte_size = _decode_length8(data, start + length_size)
        begin = start + length_size + byte_size
        end = begin + byte_length
        if end > len(data):
            raise VerificationError("truncated UTF-8 string pool entry")
        return data[begin:end].decode("utf-8", errors="strict")
    code_units, length_size = _decode_length16(data, start)
    begin = start + length_size
    end = begin + code_units * 2
    if end > len(data):
        raise VerificationError("truncated UTF-16 string pool entry")
    return data[begin:end].decode("utf-16-le", errors="strict")


def _parse_string_pool(data: bytes, offset: int) -> list[str]:
    if _u16(data, offset) != RES_STRING_POOL_TYPE:
        raise VerificationError("manifest string pool is missing")
    header_size = _u16(data, offset + 2)
    chunk_size = _u32(data, offset + 4)
    if header_size < 28 or chunk_size < header_size or offset + chunk_size > len(data):
        raise VerificationError("invalid manifest string pool")
    string_count = _u32(data, offset + 8)
    style_count = _u32(data, offset + 12)
    flags = _u32(data, offset + 16)
    strings_start = _u32(data, offset + 20)
    if string_count > 100_000 or style_count > 100_000:
        raise VerificationError("manifest string pool is unreasonably large")
    offsets_start = offset + header_size
    offsets_end = offsets_start + string_count * 4
    if offsets_end > offset + chunk_size:
        raise VerificationError("invalid manifest string offsets")
    string_data = offset + strings_start
    if string_data > offset + chunk_size:
        raise VerificationError("invalid manifest string data offset")
    utf8 = bool(flags & UTF8_FLAG)
    return [
        _read_string(data, string_data, _u32(data, offsets_start + index * 4), utf8)
        for index in range(string_count)
    ]


def _string(strings: list[str], index: int) -> str | None:
    if index == NO_INDEX or index < 0 or index >= len(strings):
        return None
    return strings[index]


def _parse_binary_manifest(data: bytes) -> int:
    if len(data) < 8 or _u16(data, 0) != RES_XML_TYPE:
        raise VerificationError("AndroidManifest.xml is neither text nor binary XML")
    root_header = _u16(data, 2)
    root_size = _u32(data, 4)
    if root_header < 8 or root_size < root_header or root_size > len(data):
        raise VerificationError("invalid binary XML container")

    strings: list[str] | None = None
    offset = root_header
    end = root_size
    uses_sdk_depth = 0
    while offset + 8 <= end:
        chunk_type = _u16(data, offset)
        header_size = _u16(data, offset + 2)
        chunk_size = _u32(data, offset + 4)
        if header_size < 8 or chunk_size < header_size or offset + chunk_size > end:
            raise VerificationError("invalid binary XML chunk")
        if chunk_type == RES_STRING_POOL_TYPE:
            strings = _parse_string_pool(data, offset)
        elif chunk_type == RES_XML_START_ELEMENT_TYPE:
            if strings is None:
                raise VerificationError("binary XML element appears before its string pool")
            if header_size < 16:
                raise VerificationError("invalid binary XML start element")
            name = _string(strings, _u32(data, offset + 20))
            attribute_start = _u16(data, offset + 24)
            attribute_size = _u16(data, offset + 26)
            attribute_count = _u16(data, offset + 28)
            if attribute_size < 20:
                raise VerificationError("invalid binary XML attribute size")
            attributes = offset + 16 + attribute_start
            if attributes + attribute_count * attribute_size > offset + chunk_size:
                raise VerificationError("binary XML attributes exceed their chunk")
            if name == "uses-sdk":
                uses_sdk_depth += 1
                for index in range(attribute_count):
                    attribute = attributes + index * attribute_size
                    attr_name = _string(strings, _u32(data, attribute + 4))
                    if attr_name != ANDROID_MIN_SDK:
                        continue
                    raw_value = _string(strings, _u32(data, attribute + 8))
                    data_type = data[attribute + 15]
                    value = _u32(data, attribute + 16)
                    if data_type in (TYPE_INT_DEC, TYPE_INT_HEX):
                        return value
                    if data_type == TYPE_STRING and raw_value is not None:
                        return _parse_sdk_value(raw_value)
                    raise VerificationError("uses-sdk minSdkVersion has an unsupported value type")
        elif chunk_type == RES_XML_END_ELEMENT_TYPE and strings is not None:
            if header_size >= 24 and _string(strings, _u32(data, offset + 20)) == "uses-sdk":
                uses_sdk_depth = max(0, uses_sdk_depth - 1)
        offset += chunk_size
    if uses_sdk_depth:
        raise VerificationError("binary XML has an unterminated uses-sdk element")
    return 1


def _parse_sdk_value(raw: str) -> int:
    value = raw.strip()
    try:
        parsed = int(value, 0)
    except ValueError as exc:
        raise VerificationError(f"minSdkVersion '{raw}' is not an integer") from exc
    if parsed < 1 or parsed > 10_000:
        raise VerificationError(f"minSdkVersion '{raw}' is outside the supported range")
    return parsed


def _parse_text_manifest(data: bytes) -> int:
    try:
        root = ElementTree.fromstring(data.decode("utf-8-sig"))
    except (UnicodeDecodeError, ElementTree.ParseError) as exc:
        raise VerificationError("AndroidManifest.xml is not valid XML") from exc
    uses_sdk = next(
        (element for element in root if element.tag.rsplit("}", 1)[-1] == "uses-sdk"),
        None,
    )
    if uses_sdk is None:
        return 1
    raw = uses_sdk.attrib.get(f"{{{ANDROID_NS}}}{ANDROID_MIN_SDK}")
    if raw is None:
        raw = uses_sdk.attrib.get(ANDROID_MIN_SDK)
    return 1 if raw is None else _parse_sdk_value(raw)


def parse_manifest_min_sdk(data: bytes) -> int:
    """Return the manifest's required minSdk, treating an omitted uses-sdk as API 1."""

    if not data:
        raise VerificationError("AndroidManifest.xml is empty")
    if data.lstrip().startswith(b"<") or data.startswith(b"\xef\xbb\xbf"):
        return _parse_text_manifest(data)
    return _parse_binary_manifest(data)


def read_aar_min_sdk(path: Path) -> int:
    if not path.is_file():
        raise VerificationError("candidate AAR does not exist")
    try:
        with zipfile.ZipFile(path) as archive:
            try:
                info = archive.getinfo("AndroidManifest.xml")
            except KeyError as exc:
                raise VerificationError("candidate AAR has no AndroidManifest.xml") from exc
            if info.file_size > MAX_MANIFEST_BYTES:
                raise VerificationError("candidate AndroidManifest.xml is too large")
            manifest = archive.read(info)
    except (OSError, zipfile.BadZipFile, zipfile.LargeZipFile) as exc:
        raise VerificationError(f"candidate AAR cannot be read: {exc}") from exc
    return parse_manifest_min_sdk(manifest)


def _extract_assignment(text: str, variable: str) -> str | None:
    match = re.search(
        rf"(?m)^\s*{re.escape(variable)}\s*=\s*['\"]?([^'\"\s]+)",
        text,
    )
    return match.group(1) if match else None


def _load_policy(path: Path) -> dict[str, Any]:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise VerificationError(f"allowlist cannot be read: {exc}") from exc
    if not isinstance(raw, dict) or raw.get("schema_version") != POLICY_SCHEMA_VERSION:
        raise VerificationError(f"allowlist schema_version must be {POLICY_SCHEMA_VERSION}")
    if not isinstance(raw.get("min_sdk"), int) or isinstance(raw["min_sdk"], bool):
        raise VerificationError("allowlist min_sdk must be an integer")
    pins = raw.get("pins")
    if not isinstance(pins, list) or not pins:
        raise VerificationError("allowlist pins must be a non-empty array")
    variables: set[str] = set()
    for pin in pins:
        if not isinstance(pin, dict):
            raise VerificationError("each allowlist pin must be an object")
        required = ("variable", "dependency", "max_version", "first_incompatible_version")
        if any(not isinstance(pin.get(key), str) or not pin[key] for key in required):
            raise VerificationError("each allowlist pin needs variable, dependency, max_version, and first_incompatible_version")
        if pin["variable"] in variables:
            raise VerificationError(f"allowlist repeats {pin['variable']}")
        variables.add(pin["variable"])
    return raw


def _candidate_spec(spec: str) -> tuple[str, Path]:
    if "=" in spec:
        label, raw_path = spec.split("=", 1)
        if not label or not raw_path:
            raise VerificationError("candidate must use NAME=PATH")
    else:
        raw_path = spec
        label = Path(raw_path).stem
    return label, Path(raw_path)


def evaluate(
    repo_root: Path,
    policy_path: Path,
    candidates: Iterable[str] = (),
) -> tuple[list[str], list[str]]:
    """Return ``(errors, messages)`` without invoking a network or external tool."""

    errors: list[str] = []
    messages: list[str] = []
    try:
        policy = _load_policy(policy_path)
    except VerificationError as exc:
        return [f"ERROR: {exc}"], messages

    versions_path = repo_root / "versions.gradle"
    try:
        versions = versions_path.read_text(encoding="utf-8")
    except OSError as exc:
        return [f"ERROR: versions.gradle cannot be read: {exc}"], messages

    policy_min_sdk = policy["min_sdk"]
    actual_min_sdk = _extract_assignment(versions, "min_sdk")
    if actual_min_sdk is None:
        errors.append("min_sdk is missing from versions.gradle")
    else:
        try:
            parsed_min_sdk = int(actual_min_sdk)
        except ValueError:
            parsed_min_sdk = -1
        if parsed_min_sdk != policy_min_sdk:
            errors.append(
                f"project min_sdk is {actual_min_sdk}, but the API-21 allowlist requires {policy_min_sdk}"
            )
        else:
            messages.append(f"OK: min_sdk = {parsed_min_sdk}")

    for pin in policy["pins"]:
        variable = pin["variable"]
        dependency = pin["dependency"]
        expected = pin["max_version"]
        actual = _extract_assignment(versions, variable)
        if actual is None:
            errors.append(f"{dependency} ({variable}) is missing from versions.gradle")
        elif actual != expected:
            errors.append(
                f"{dependency} ({variable}) is {actual}, but the API-21 ceiling is {expected}"
            )
        else:
            messages.append(f"OK: {dependency} pinned at {actual} (ceiling {expected})")

    dependency_check = _extract_assignment(versions, "dependency_check_version")
    if dependency_check is not None:
        messages.append(f"OK: dependency-check at {dependency_check}")

    for raw_candidate in candidates:
        try:
            label, path = _candidate_spec(raw_candidate)
            if not path.is_absolute():
                path = repo_root / path
            required_min_sdk = read_aar_min_sdk(path)
        except VerificationError as exc:
            errors.append(f"{raw_candidate}: {exc}")
            continue
        if required_min_sdk > policy_min_sdk:
            errors.append(
                f"{label} ({path}) requires minSdk {required_min_sdk}, "
                f"but project minSdk is {policy_min_sdk}"
            )
        else:
            messages.append(f"OK: {label} AAR requires minSdk {required_min_sdk}")
    return errors, messages


def main(argv: Sequence[str] | None = None) -> int:
    script_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=script_root)
    parser.add_argument(
        "--policy",
        type=Path,
        default=Path("docs/policy/minsdk-21-ceiling.json"),
        help="machine-readable API-21 allowlist, relative to --repo-root",
    )
    parser.add_argument(
        "--candidate",
        action="append",
        default=[],
        metavar="NAME=PATH",
        help="resolved AAR to inspect; repeat for multiple candidates",
    )
    args = parser.parse_args(list(argv) if argv is not None else None)
    repo_root = args.repo_root.resolve()
    policy_path = args.policy if args.policy.is_absolute() else repo_root / args.policy

    print("=== API-21 Dependency Ceiling Gate ===")
    print(f"Allowlist: {policy_path}")
    errors, messages = evaluate(repo_root, policy_path, args.candidate)
    for message in messages:
        print(message)
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    if errors:
        print("FAILED: API-21 dependency ceiling check failed", file=sys.stderr)
        return 1
    print("PASSED: API-21 dependency ceiling and candidate manifests are compatible")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
