#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Verify release packets against the checked-in published-release receipt."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any, Callable


PACKETS = {
    "accrescent-listing.md": ("target_sdk", "signing_certificate_sha256"),
    "fdroid-listing.md": (),
    "izzyondroid-listing.md": (
        "artifact_name",
        "artifact_size",
        "artifact_sha256",
        "signing_certificate_sha256",
    ),
}


def _identity(value: str) -> str:
    return value


def _lower(value: str) -> str:
    return value.lower()


def _digits(value: str) -> str:
    return value.replace(",", "")


def _digest(value: str) -> str:
    return value.replace(":", "").lower()


FIELD_PATTERNS: dict[str, tuple[re.Pattern[str], Callable[[str], str]]] = {
    "tag": (re.compile(r"\bv\d+\.\d+\.\d+\b"), _identity),
    "commit": (
        re.compile(r"(?im)^\s*(?:-\s*)?(?:Commit|commit):\s*`?([0-9a-f]{40})"),
        _lower,
    ),
    "version_name": (
        re.compile(
            r"(?im)^\s*(?:-\s*)?(?:Version name|versionName|CurrentVersion):"
            r"\s*`?(\d+\.\d+\.\d+)"
        ),
        _identity,
    ),
    "version_code": (
        re.compile(
            r"(?im)^\s*(?:-\s*)?(?:Version code|versionCode|CurrentVersionCode):"
            r"\s*`?(\d+)"
        ),
        _identity,
    ),
    "package_name": (
        re.compile(r"(?im)^\s*(?:-\s*)?Package(?: name)?:\s*`?([A-Za-z0-9_.]+)"),
        _identity,
    ),
    "target_sdk": (
        re.compile(r"(?im)^\s*(?:-\s*)?Target SDK:\s*`?(\d+)"),
        _identity,
    ),
    "artifact_name": (
        re.compile(
            r"(?im)^\s*(?:-\s*)?(?:Preferred APK|Published APK):\s*"
            r"`?([^`\r\n]+?\.apk)`?\s*$"
        ),
        _identity,
    ),
    "artifact_size": (
        re.compile(
            r"(?im)^\s*(?:-\s*)?(?:APK size|Published APK size):\s*"
            r"`?([0-9][0-9,]*)\s+bytes"
        ),
        _digits,
    ),
    "artifact_sha256": (
        re.compile(
            r"(?im)^\s*(?:-\s*)?(?:SHA-256|APK SHA-256):\s*"
            r"(?:\r?\n\s*)?`?([0-9a-f]{64})"
        ),
        _lower,
    ),
    "signing_certificate_sha256": (
        re.compile(
            r"(?im)^\s*(?:-\s*)?Signing certificate SHA-256:\s*"
            r"(?:\r?\n\s*)?`?([0-9a-f:]{64,95})"
        ),
        _digest,
    ),
}


class ReceiptError(ValueError):
    pass


def _require(data: dict[str, Any], key: str, expected_type: type) -> Any:
    value = data.get(key)
    if expected_type is int and isinstance(value, bool):
        raise ReceiptError(f"{key} must be an integer")
    if not isinstance(value, expected_type):
        raise ReceiptError(f"{key} must be a {expected_type.__name__}")
    if expected_type is str and not value:
        raise ReceiptError(f"{key} must not be empty")
    return value


def load_receipt(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ReceiptError(f"cannot read {path}: {exc}") from exc
    if not isinstance(data, dict):
        raise ReceiptError("receipt root must be an object")
    if _require(data, "schemaVersion", int) != 1:
        raise ReceiptError("schemaVersion must be 1")

    version_name = _require(data, "versionName", str)
    tag = _require(data, "tag", str)
    if tag != f"v{version_name}":
        raise ReceiptError(f"tag {tag!r} does not match versionName {version_name!r}")
    _require(data, "versionCode", int)
    package_name = _require(data, "packageName", str)
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", package_name):
        raise ReceiptError(f"packageName {package_name!r} is invalid")
    _require(data, "targetSdk", int)
    repository = _require(data, "repository", str).rstrip("/")
    release_url = _require(data, "releaseUrl", str)
    if release_url != f"{repository}/releases/tag/{tag}":
        raise ReceiptError("releaseUrl does not match repository and tag")
    published_at = _require(data, "publishedAt", str)
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z", published_at):
        raise ReceiptError("publishedAt must be an absolute UTC timestamp")

    for key in ("tagObjectSha", "tagCommit", "signingCertificateSha256"):
        digest = _require(data, key, str)
        if not re.fullmatch(r"[0-9a-f]{40}" if key != "signingCertificateSha256" else r"[0-9a-f]{64}", digest):
            raise ReceiptError(f"{key} is not a lowercase hexadecimal digest")

    artifact = _require(data, "selectedArtifact", dict)
    artifact_name = _require(artifact, "name", str)
    if not artifact_name.endswith(".apk"):
        raise ReceiptError("selectedArtifact.name must identify an APK")
    size = _require(artifact, "size", int)
    if size <= 0:
        raise ReceiptError("selectedArtifact.size must be positive")
    artifact_sha = _require(artifact, "sha256", str)
    if not re.fullmatch(r"[0-9a-f]{64}", artifact_sha):
        raise ReceiptError("selectedArtifact.sha256 is not a lowercase SHA-256 digest")
    artifact_url = _require(artifact, "url", str)
    if artifact_url != f"{repository}/releases/download/{tag}/{artifact_name}":
        raise ReceiptError("selectedArtifact.url does not match repository, tag, and name")
    return data


def expected_fields(receipt: dict[str, Any]) -> dict[str, str]:
    artifact = receipt["selectedArtifact"]
    return {
        "tag": receipt["tag"],
        "commit": receipt["tagCommit"],
        "version_name": receipt["versionName"],
        "version_code": str(receipt["versionCode"]),
        "package_name": receipt["packageName"],
        "target_sdk": str(receipt["targetSdk"]),
        "artifact_name": artifact["name"],
        "artifact_size": str(artifact["size"]),
        "artifact_sha256": artifact["sha256"],
        "signing_certificate_sha256": receipt["signingCertificateSha256"],
    }


def verify_packet(path: Path, fields: dict[str, str], extra_fields: tuple[str, ...]) -> list[str]:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        return [f"{path}: cannot read packet: {exc}"]

    errors: list[str] = []
    for field in ("tag", "commit", "version_name", "version_code", "package_name", *extra_fields):
        pattern, normalize = FIELD_PATTERNS[field]
        values = [normalize(value) for value in pattern.findall(text)]
        expected = fields[field]
        if not values:
            errors.append(f"{path}: missing {field.replace('_', ' ')}")
            continue
        stale = sorted(set(value for value in values if value != expected))
        if stale:
            errors.append(
                f"{path}: stale {field.replace('_', ' ')} value(s) {', '.join(stale)}; expected {expected}"
            )
    return errors


def verify_build_gradle(path: Path, receipt: dict[str, Any]) -> list[str]:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        return [f"{path}: cannot read build metadata: {exc}"]
    errors: list[str] = []
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    code_match = re.search(r"versionCode\s*=\s*(\d+)", text)
    package_match = re.search(r"applicationId\s*=\s*['\"]([^'\"]+)['\"]", text)
    if not name_match or name_match.group(1) != receipt["versionName"]:
        actual = name_match.group(1) if name_match else "missing"
        errors.append(f"{path}: versionName {actual} != receipt {receipt['versionName']}")
    if not code_match or int(code_match.group(1)) != receipt["versionCode"]:
        actual = code_match.group(1) if code_match else "missing"
        errors.append(f"{path}: versionCode {actual} != receipt {receipt['versionCode']}")
    if not package_match or package_match.group(1) != receipt["packageName"]:
        actual = package_match.group(1) if package_match else "missing"
        errors.append(f"{path}: applicationId {actual} != receipt {receipt['packageName']}")
    return errors


def verify_versions_gradle(path: Path, receipt: dict[str, Any]) -> list[str]:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        return [f"{path}: cannot read SDK metadata: {exc}"]
    match = re.search(r"(?m)^\s*target_sdk\s*=\s*(\d+)", text)
    if not match or int(match.group(1)) != receipt["targetSdk"]:
        actual = match.group(1) if match else "missing"
        return [f"{path}: target_sdk {actual} != receipt {receipt['targetSdk']}"]
    return []


def verify_git_tag(repo_root: Path, receipt: dict[str, Any]) -> list[str]:
    result = subprocess.run(
        ["git", "-C", str(repo_root), "rev-list", "-n", "1", receipt["tag"]],
        check=False,
        capture_output=True,
        text=True,
    )
    actual = result.stdout.strip().lower()
    if result.returncode != 0:
        detail = result.stderr.strip() or "tag lookup failed"
        return [f"git tag {receipt['tag']}: {detail}"]
    if actual != receipt["tagCommit"]:
        return [f"git tag {receipt['tag']} resolves to {actual}; expected {receipt['tagCommit']}"]
    return []


def parse_args(argv: list[str]) -> argparse.Namespace:
    repo_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--receipt",
        type=Path,
        default=repo_root / "docs" / "distribution" / "release-receipt.json",
    )
    parser.add_argument(
        "--distribution-dir",
        type=Path,
        default=repo_root / "docs" / "distribution",
    )
    parser.add_argument("--build-gradle", type=Path, default=repo_root / "app" / "build.gradle")
    parser.add_argument("--versions-gradle", type=Path, default=repo_root / "versions.gradle")
    parser.add_argument("--repo-root", type=Path, default=repo_root)
    parser.add_argument("--skip-git", action="store_true", help=argparse.SUPPRESS)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    try:
        receipt = load_receipt(args.receipt)
    except ReceiptError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    fields = expected_fields(receipt)
    errors = verify_build_gradle(args.build_gradle, receipt)
    errors.extend(verify_versions_gradle(args.versions_gradle, receipt))
    if not args.skip_git:
        errors.extend(verify_git_tag(args.repo_root, receipt))
    for packet, extras in PACKETS.items():
        errors.extend(verify_packet(args.distribution_dir / packet, fields, extras))

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    artifact = receipt["selectedArtifact"]
    print(
        "OK: release packets match receipt "
        f"{receipt['tag']} code {receipt['versionCode']} commit {receipt['tagCommit']} "
        f"artifact {artifact['name']} ({artifact['size']} bytes, sha256:{artifact['sha256']})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
