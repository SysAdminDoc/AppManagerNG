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

    def test_every_compared_mapping_is_listed_in_the_compared_receipt(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            out = Path(raw) / "out"
            for label in ("first", "second"):
                (out / label / "mapping").mkdir(parents=True)
                (out / label / "mapping" / "flossRelease.txt").write_text("a\n", encoding="utf-8", newline="\n")
            (out / "publish").mkdir()
            result = self.run_function('verify_and_publish_mappings', out)
            compared = (out / "compared.sha256").read_text(encoding="utf-8").splitlines()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(1, len(compared))
        self.assertTrue(compared[0].endswith("  mapping/flossRelease.txt"), compared)

    def run_function(self, function: str, out: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [BASH, "-c",
             'source "$1"; OUT_DIR="$2"; FIRST_DIR="$2/first"; SECOND_DIR="$2/second"; '
             f'PUBLISH_DIR="$2/publish"; ASSET_LIST="$2/release-assets.txt"; {function}',
             "test", SCRIPT.as_posix(), out.as_posix()],
            check=False,
            capture_output=True,
            text=True,
        )

    def pairs(self, published: list[str]) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as raw:
            out = Path(raw) / "out"
            (out / "publish").mkdir(parents=True)
            for name in published:
                (out / "publish" / name).write_text("x", encoding="utf-8")
            return self.run_function("verify_published_mapping_pairs", out)

    def test_every_published_apk_has_its_mapping(self) -> None:
        ok = self.pairs(["AppManagerNG-reproducible-floss-release.apk",
                         "AppManagerNG-reproducible-floss-release-mapping.txt"])
        self.assertEqual(0, ok.returncode, ok.stderr)

        missing = self.pairs(["AppManagerNG-reproducible-floss-release.apk",
                              "AppManagerNG-reproducible-full-release.apk",
                              "AppManagerNG-reproducible-floss-release-mapping.txt"])
        self.assertNotEqual(0, missing.returncode)
        self.assertIn("AppManagerNG-reproducible-full-release.apk ships no "
                      "AppManagerNG-reproducible-full-release-mapping.txt", missing.stderr)

    def test_a_mapping_without_its_apk_fails(self) -> None:
        result = self.pairs(["AppManagerNG-reproducible-floss-release.apk",
                             "AppManagerNG-reproducible-floss-release-mapping.txt",
                             "AppManagerNG-reproducible-full-release-mapping.txt"])

        self.assertNotEqual(0, result.returncode)
        self.assertIn("full-release-mapping.txt does not belong to any published APK", result.stderr)

    def test_no_published_apk_fails(self) -> None:
        result = self.pairs([])

        self.assertNotEqual(0, result.returncode)
        self.assertIn("No APK was published", result.stderr)

    def metadata(self, first: dict[str, str], second: dict[str, str]):
        with tempfile.TemporaryDirectory() as raw:
            out = Path(raw) / "out"
            for label, files in (("first", first), ("second", second)):
                (out / label / "metadata").mkdir(parents=True)
                for name, content in files.items():
                    (out / label / "metadata" / name).write_text(content, encoding="utf-8", newline="\n")
            result = self.run_function("verify_output_metadata", out)
            receipt = out / "compared.sha256"
            compared = receipt.read_text(encoding="utf-8").splitlines() if receipt.exists() else []
            return result, compared

    def test_identical_output_metadata_is_recorded_as_compared(self) -> None:
        files = {"floss-release-output-metadata.json": '{"versionCode":32}\n'}

        result, compared = self.metadata(files, dict(files))

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(compared and compared[0].endswith("  metadata/floss-release-output-metadata.json"))

    def test_changed_or_one_sided_output_metadata_fails(self) -> None:
        changed, _ = self.metadata({"floss-release-output-metadata.json": '{"versionCode":32}\n'},
                                   {"floss-release-output-metadata.json": '{"versionCode":33}\n'})
        self.assertNotEqual(0, changed.returncode)
        self.assertIn("floss-release-output-metadata.json is not reproducible", changed.stderr)

        first_only, _ = self.metadata({"full-release-output-metadata.json": "{}\n"}, {})
        self.assertNotEqual(0, first_only.returncode)
        self.assertIn("by the first build but not the second", first_only.stderr)

        second_only, _ = self.metadata({}, {"full-release-output-metadata.json": "{}\n"})
        self.assertNotEqual(0, second_only.returncode)
        self.assertIn("by the second build but not the first", second_only.stderr)

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
