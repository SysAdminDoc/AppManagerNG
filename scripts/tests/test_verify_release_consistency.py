#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later

import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "verify-release-consistency.sh"


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
class VerifyReleaseConsistencyTest(unittest.TestCase):
    def run_function(self, command: str, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [BASH, "-c", f'source "$1"; {command}', "test", SCRIPT.as_posix(), *arguments],
            check=False,
            capture_output=True,
            text=True,
        )

    def test_prerelease_badge_decodes_shields_hyphen_escape(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            readme = Path(raw) / "README.md"
            readme.write_text(
                "https://img.shields.io/badge/version-1.2.3--rc4-blue.svg\n",
                encoding="utf-8",
            )

            result = self.run_function('extract_readme_badge_version "$2"', readme.as_posix())

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("1.2.3-rc4", result.stdout.strip())

    def test_prerelease_version_and_tag_share_the_supported_grammar(self) -> None:
        result = self.run_function(
            'is_supported_version_name "$2" && extract_release_tag_version "$3"',
            "1.2.3-beta7",
            "v1.2.3-beta7",
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("1.2.3-beta7", result.stdout.strip())

    def test_unsupported_tag_suffix_is_rejected(self) -> None:
        result = self.run_function('extract_release_tag_version "$2"', "v1.2.3-preview1")

        self.assertNotEqual(0, result.returncode)


if __name__ == "__main__":
    unittest.main()
