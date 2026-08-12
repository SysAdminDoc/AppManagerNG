# SPDX-License-Identifier: GPL-3.0-or-later

import json
import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


VERIFIER = Path(__file__).resolve().parents[1] / "verify_release_metadata.py"
SPEC = importlib.util.spec_from_file_location("verify_release_metadata", VERIFIER)
assert SPEC is not None and SPEC.loader is not None
metadata = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(metadata)


class VerifyReleaseMetadataTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.distribution_dir = self.root / "distribution"
        self.distribution_dir.mkdir()
        self.receipt = {
            "schemaVersion": 1,
            "repository": "https://github.com/example/project",
            "releaseUrl": "https://github.com/example/project/releases/tag/v0.6.5",
            "publishedAt": "2026-07-10T06:42:49Z",
            "tag": "v0.6.5",
            "tagObjectSha": "b" * 40,
            "tagCommit": "f" * 40,
            "versionName": "0.6.5",
            "versionCode": 13,
            "packageName": "io.example.app",
            "targetSdk": 36,
            "selectedArtifact": {
                "name": "Example-0.6.5-floss-release.apk",
                "size": 18948513,
                "sha256": "9" * 64,
                "url": "https://github.com/example/project/releases/download/v0.6.5/Example-0.6.5-floss-release.apk",
            },
            "signingCertificateSha256": "2" * 64,
        }
        self.receipt_path = self.root / "receipt.json"
        self.receipt_path.write_text(json.dumps(self.receipt), encoding="utf-8")
        self.build_gradle = self.root / "build.gradle"
        self.build_gradle.write_text(
            "applicationId = 'io.example.app'\n"
            'versionName = "0.6.5"\n'
            "versionCode = 13\n",
            encoding="utf-8",
        )
        self.versions_gradle = self.root / "versions.gradle"
        self.versions_gradle.write_text("target_sdk = 36\n", encoding="utf-8")
        self._write_valid_packets()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _write_valid_packets(self) -> None:
        common = """\
- Tag: `v0.6.5`
- Commit: `ffffffffffffffffffffffffffffffffffffffff`
- Package name: `io.example.app`
- Version name: `0.6.5`
- Version code: `13`
"""
        (self.distribution_dir / "accrescent-listing.md").write_text(
            common
            + "- Target SDK: `36`\n- Signing certificate SHA-256:\n  `"
            + "22:" * 31
            + "22`\n",
            encoding="utf-8",
        )
        (self.distribution_dir / "fdroid-listing.md").write_text(
            common
            + """
Builds:
  - versionName: 0.6.5
    versionCode: 13
    commit: ffffffffffffffffffffffffffffffffffffffff
CurrentVersion: 0.6.5
CurrentVersionCode: 13
""",
            encoding="utf-8",
        )
        (self.distribution_dir / "izzyondroid-listing.md").write_text(
            common
            + """
- Preferred APK: `Example-0.6.5-floss-release.apk`
- APK size: 18,948,513 bytes.
- SHA-256: `9999999999999999999999999999999999999999999999999999999999999999`
- Signing certificate SHA-256:
  `22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22`
""",
            encoding="utf-8",
        )

    def _set_tree_version(self, version_name: str, version_code: int) -> None:
        text = self.build_gradle.read_text(encoding="utf-8")
        text = text.replace('versionName = "0.6.5"', f'versionName = "{version_name}"')
        text = text.replace("versionCode = 13", f"versionCode = {version_code}")
        self.build_gradle.write_text(text, encoding="utf-8")

    def _tag_head(self, tag: str) -> None:
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.email", "test@example.com"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.name", "Test"], cwd=self.root, check=True)
        subprocess.run(["git", "add", "-A"], cwd=self.root, check=True)
        subprocess.run(["git", "commit", "-qm", "fixture"], cwd=self.root, check=True)
        subprocess.run(["git", "tag", tag], cwd=self.root, check=True)

    def _run(self) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(VERIFIER),
                "--receipt",
                str(self.receipt_path),
                "--distribution-dir",
                str(self.distribution_dir),
                "--build-gradle",
                str(self.build_gradle),
                "--versions-gradle",
                str(self.versions_gradle),
                "--skip-git",
            ],
            check=False,
            capture_output=True,
            text=True,
        )

    def test_matching_packets_pass(self) -> None:
        result = self._run()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_stale_markdown_version_code_fails(self) -> None:
        path = self.distribution_dir / "accrescent-listing.md"
        path.write_text(
            path.read_text(encoding="utf-8").replace("Version code: `13`", "Version code: `7`"),
            encoding="utf-8",
        )
        result = self._run()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale version code", result.stderr)

    def test_stale_yaml_version_code_fails(self) -> None:
        path = self.distribution_dir / "fdroid-listing.md"
        path.write_text(path.read_text(encoding="utf-8").replace("versionCode: 13", "versionCode: 7"), encoding="utf-8")
        result = self._run()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale version code", result.stderr)

    def test_stale_artifact_hash_fails(self) -> None:
        path = self.distribution_dir / "izzyondroid-listing.md"
        path.write_text(path.read_text(encoding="utf-8").replace("9" * 64, "0" * 64), encoding="utf-8")
        result = self._run()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale artifact sha256", result.stderr)

    def test_untagged_tree_one_release_ahead_passes(self) -> None:
        self._set_tree_version("0.6.6", 14)
        result = self._run()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_untagged_tree_behind_receipt_fails(self) -> None:
        self._set_tree_version("0.6.4", 12)
        result = self._run()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("behind published receipt", result.stderr)

    def test_matching_version_on_exact_release_tag_passes(self) -> None:
        self._tag_head("v0.6.5")
        self.assertEqual(
            [],
            metadata.verify_build_gradle(self.build_gradle, self.receipt, self.root),
        )

    def test_exact_tag_must_match_tree_version(self) -> None:
        self._tag_head("v0.6.6")
        errors = metadata.verify_build_gradle(self.build_gradle, self.receipt, self.root)
        self.assertTrue(any("do not match versionName" in error for error in errors))

    def test_tagged_candidate_must_match_published_receipt(self) -> None:
        self._set_tree_version("0.6.6", 14)
        self._tag_head("v0.6.6")
        errors = metadata.verify_build_gradle(self.build_gradle, self.receipt, self.root)
        self.assertTrue(any("tagged versionName" in error for error in errors))
        self.assertTrue(any("tagged versionCode" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
