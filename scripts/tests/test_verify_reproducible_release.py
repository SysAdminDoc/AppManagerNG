#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later

import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "verify_reproducible_release.sh"
POWERSHELL_SCRIPT = SCRIPT.with_suffix(".ps1")


def find_bash() -> str | None:
    if sys.platform == "win32":
        git = shutil.which("git")
        if git:
            git_bash = Path(git).resolve().parent.parent / "bin" / "bash.exe"
            if git_bash.is_file():
                return str(git_bash)
    return shutil.which("bash")


BASH = find_bash()


@unittest.skipUnless(BASH, "bash is required")
class MappingVerificationTest(unittest.TestCase):
    """The R8 mapping comparison both entry points rely on."""

    def verify(self, first: dict[str, str], second: dict[str, str]):
        with tempfile.TemporaryDirectory() as raw:
            out = Path(raw) / "out"
            for label, mappings in (("first", first), ("second", second)):
                mapping_dir = out / label / "mapping"
                mapping_dir.mkdir(parents=True)
                for variant, content in mappings.items():
                    (mapping_dir / f"{variant}.txt").write_text(content, encoding="utf-8", newline="\n")
            (out / "publish").mkdir()
            result = subprocess.run(
                [BASH, "-c",
                 'source "$1"; OUT_DIR="$2"; FIRST_DIR="$2/first"; SECOND_DIR="$2/second"; '
                 'PUBLISH_DIR="$2/publish"; ASSET_LIST="$2/release-assets.txt"; verify_and_publish_mappings',
                 "test", SCRIPT.as_posix(), out.as_posix()],
                check=False,
                capture_output=True,
                text=True,
            )
            published = sorted(path.name for path in (out / "publish").iterdir())
            receipt = out / "sha256.txt"
            receipt_lines = receipt.read_text(encoding="utf-8").splitlines() if receipt.exists() else []
            return result, published, receipt_lines

    def test_identical_mappings_are_published_with_their_checksums(self) -> None:
        mappings = {"flossRelease": "a -> b\n", "fullRelease": "c -> d\n"}

        result, published, receipt_lines = self.verify(mappings, dict(mappings))

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([
            "AppManagerNG-reproducible-floss-release-mapping.txt",
            "AppManagerNG-reproducible-floss-release-mapping.txt.sha256",
            "AppManagerNG-reproducible-full-release-mapping.txt",
            "AppManagerNG-reproducible-full-release-mapping.txt.sha256",
        ], published)
        self.assertEqual(2, len(receipt_lines))
        self.assertTrue(all(re.fullmatch(r"[0-9a-f]{64}  AppManagerNG-reproducible-\S+-mapping\.txt", line)
                            for line in receipt_lines), receipt_lines)

    def test_a_mismatched_mapping_fails(self) -> None:
        result, _, _ = self.verify({"flossRelease": "a -> b\n", "fullRelease": "c -> d\n"},
                                   {"flossRelease": "a -> b\n", "fullRelease": "c -> e\n"})

        self.assertNotEqual(0, result.returncode)
        self.assertIn("R8 mapping for fullRelease is not reproducible", result.stderr)

    def test_a_mapping_missing_from_the_second_build_fails(self) -> None:
        result, _, _ = self.verify({"flossRelease": "a\n", "fullRelease": "b\n"}, {"flossRelease": "a\n"})

        self.assertNotEqual(0, result.returncode)
        self.assertIn("fullRelease produced a mapping in the first build but not the second", result.stderr)

    def test_a_mapping_missing_from_the_first_build_fails(self) -> None:
        result, _, _ = self.verify({"flossRelease": "a\n"}, {"flossRelease": "a\n", "fullRelease": "b\n"})

        self.assertNotEqual(0, result.returncode)
        self.assertIn("fullRelease produced a mapping in the second build but not the first", result.stderr)

    def test_a_minified_variant_without_a_mapping_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            (root / "mapping" / "flossRelease").mkdir(parents=True)
            result = subprocess.run(
                [BASH, "-c", 'source "$1"; MAPPING_ROOT="$2/mapping"; copy_mappings "$2/first"',
                 "test", SCRIPT.as_posix(), root.as_posix()],
                check=False,
                capture_output=True,
                text=True,
            )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("Minified variant flossRelease produced no mapping.txt", result.stderr)


class PowerShellEntryPointTest(unittest.TestCase):
    """The Windows entry point must not grow a second, drifting copy of the comparison."""

    def test_it_runs_the_shell_verifier(self) -> None:
        source = POWERSHELL_SCRIPT.read_text(encoding="utf-8")

        self.assertIn('"scripts/verify_reproducible_release.sh"', source)
        for independent_step in ("Get-FileHash", "assembleRelease", "Compare-Object", "Copy-Item"):
            self.assertNotIn(independent_step, source,
                             f"{independent_step} belongs in the shell verifier, not the wrapper")

    def test_its_options_reach_the_variables_the_shell_verifier_reads(self) -> None:
        powershell = POWERSHELL_SCRIPT.read_text(encoding="utf-8")
        shell = SCRIPT.read_text(encoding="utf-8")

        for variable in ("GRADLE_CMD", "PYTHON_CMD", "REPRO_OUT_DIR"):
            self.assertIn(variable, powershell)
            self.assertIn("${" + variable, shell)
        # Evidence must live outside build/, which each clean build empties.
        self.assertIn('[string] $OutDir = "reproducible-release"', powershell)


if __name__ == "__main__":
    unittest.main()
