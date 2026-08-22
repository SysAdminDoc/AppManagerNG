# SPDX-License-Identifier: GPL-3.0-or-later

import importlib.util
import json
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "verify_dependency_floor.py"
SPEC = importlib.util.spec_from_file_location("verify_dependency_floor", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
gate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gate)

REPO_ROOT = SCRIPT.parents[1]
POLICY = REPO_ROOT / "docs" / "policy" / "minsdk-21-ceiling.json"


def _string_pool(strings: list[str]) -> bytes:
    body = bytearray()
    offsets = []
    for value in strings:
        offsets.append(len(body))
        encoded = value.encode("utf-8")
        body.extend((len(value), len(encoded)))
        body.extend(encoded)
        body.append(0)
    header_size = 28
    strings_start = header_size + len(strings) * 4
    chunk_size = strings_start + len(body)
    header = struct.pack(
        "<HHIIIIII",
        gate.RES_STRING_POOL_TYPE,
        header_size,
        chunk_size,
        len(strings),
        0,
        gate.UTF8_FLAG,
        strings_start,
        0,
    )
    return header + b"".join(struct.pack("<I", offset) for offset in offsets) + body


def _binary_manifest(min_sdk: int) -> bytes:
    strings = ["manifest", "uses-sdk", "minSdkVersion", str(min_sdk)]
    pool = _string_pool(strings)
    start = struct.pack(
        "<HHIIIIIHHHHHH",
        gate.RES_XML_START_ELEMENT_TYPE,
        16,
        56,
        1,
        gate.NO_INDEX,
        gate.NO_INDEX,
        1,
        20,
        20,
        1,
        0,
        0,
        0,
    )
    attribute = struct.pack(
        "<IIIHBBI",
        gate.NO_INDEX,
        2,
        3,
        8,
        0,
        gate.TYPE_INT_DEC,
        min_sdk,
    )
    end = struct.pack(
        "<HHIIIII",
        gate.RES_XML_END_ELEMENT_TYPE,
        16,
        24,
        1,
        gate.NO_INDEX,
        gate.NO_INDEX,
        1,
    )
    children = pool + start + attribute + end
    return struct.pack("<HHI", gate.RES_XML_TYPE, 8, 8 + len(children)) + children


class DependencyFloorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _aar(self, name: str, manifest: bytes) -> Path:
        path = self.root / f"{name}.aar"
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("AndroidManifest.xml", manifest)
        return path

    def test_allowlist_is_machine_readable_and_matches_the_repo(self) -> None:
        policy = json.loads(POLICY.read_text(encoding="utf-8"))
        self.assertEqual(1, policy["schema_version"])
        self.assertEqual(21, policy["min_sdk"])
        errors, _ = gate.evaluate(REPO_ROOT, POLICY)
        self.assertEqual([], errors)

    def test_compiled_manifest_at_api_21_is_accepted(self) -> None:
        self.assertEqual(21, gate.parse_manifest_min_sdk(_binary_manifest(21)))
        candidate = self._aar("floor", _binary_manifest(21))
        errors, _ = gate.evaluate(REPO_ROOT, POLICY, [f"Material Components={candidate}"])
        self.assertEqual([], errors)

    def test_compiled_manifest_above_floor_names_dependency_and_required_sdk(self) -> None:
        candidate = self._aar("higher", _binary_manifest(23))
        errors, _ = gate.evaluate(REPO_ROOT, POLICY, [f"Material Components={candidate}"])
        self.assertTrue(any("Material Components" in error for error in errors))
        self.assertTrue(any("minSdk 23" in error for error in errors))

    def test_text_manifest_is_supported(self) -> None:
        manifest = (
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
            '<uses-sdk android:minSdkVersion="24"/></manifest>'
        ).encode("utf-8")
        candidate = self._aar("text", manifest)
        self.assertEqual(24, gate.read_aar_min_sdk(candidate))

    def test_malformed_candidate_is_a_failure(self) -> None:
        candidate = self.root / "broken.aar"
        candidate.write_bytes(b"not a zip")
        errors, _ = gate.evaluate(REPO_ROOT, POLICY, [f"Broken={candidate}"])
        self.assertTrue(any("Broken" in error and "cannot be read" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
