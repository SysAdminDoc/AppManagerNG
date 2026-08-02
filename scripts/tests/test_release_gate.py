# SPDX-License-Identifier: GPL-3.0-or-later

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "release_gate.py"
SPEC = importlib.util.spec_from_file_location("release_gate", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
gate = importlib.util.module_from_spec(SPEC)
# @dataclass resolves annotations through sys.modules, so register before executing.
sys.modules["release_gate"] = gate
SPEC.loader.exec_module(gate)


def _issues_xml(entries):
    body = "".join(
        f'<issue id="{i}" message="{m}"><location file="{f}" line="{n}"/></issue>'
        for i, f, m, n in entries
    )
    return f'<?xml version="1.0" encoding="UTF-8"?><issues format="6">{body}</issues>'


REPO_ROOT = SCRIPT.parents[1]


class LintPathTest(unittest.TestCase):
    """The baseline stores app-module-relative paths; the report stores absolute ones.

    Every case below made the whole baseline look new before these paths were reconciled.
    """

    PREFIXES = ("libcore/compat", "libcore/io", "libcore/ui", "hiddenapi", "docs", "app")

    def canonical(self, raw: str) -> str:
        return gate._canonical_lint_path(raw, REPO_ROOT, self.PREFIXES)

    def test_an_absolute_report_path_and_a_relative_baseline_path_agree(self) -> None:
        absolute = str(REPO_ROOT / "app" / "src" / "main" / "A.java")
        self.assertEqual(self.canonical(absolute), self.canonical("src/main/A.java"))

    def test_a_finding_in_another_module_agrees_with_its_dot_dot_baseline_entry(self) -> None:
        absolute = str(REPO_ROOT / "libcore" / "ui" / "src" / "main" / "B.java")
        self.assertEqual(self.canonical(absolute), self.canonical("../libcore/ui/src/main/B.java"))

    def test_a_finding_outside_the_repository_agrees_across_both_forms(self) -> None:
        # Gradle-cache findings sit above the repo root, and the two sides differ by one level.
        absolute = str(REPO_ROOT.parent.parent / ".gradle" / "caches" / "x.jar")
        self.assertEqual(self.canonical(absolute), self.canonical("../../../.gradle/caches/x.jar"))

    def test_backslashes_do_not_make_a_separate_issue(self) -> None:
        self.assertEqual(self.canonical("src/main/A.java"), self.canonical("src\\main\\A.java"))

    def test_the_module_list_is_read_from_settings_gradle(self) -> None:
        prefixes = gate.module_prefixes(REPO_ROOT)
        self.assertIn("app", prefixes)
        self.assertIn("libcore/ui", prefixes)
        # Longest first, so libcore/ui is stripped before a hypothetical libcore.
        self.assertEqual(sorted(prefixes, key=len, reverse=True), list(prefixes))


class LintDiffTest(unittest.TestCase):
    def test_line_numbers_do_not_make_an_old_issue_look_new(self) -> None:
        baseline = gate.parse_lint_issues(_issues_xml([("UnusedIds", "res/a.xml", "unused", 10)]))
        current = gate.parse_lint_issues(_issues_xml([("UnusedIds", "res/a.xml", "unused", 4242)]))
        new, stale = gate.diff_lint(current, baseline)
        self.assertEqual([], new)
        self.assertEqual([], stale)

    def test_windows_and_posix_paths_are_the_same_issue(self) -> None:
        baseline = gate.parse_lint_issues(_issues_xml([("X", "src\\main\\A.java", "m", 1)]))
        current = gate.parse_lint_issues(_issues_xml([("X", "src/main/A.java", "m", 1)]))
        self.assertEqual(([], []), gate.diff_lint(current, baseline))

    def test_the_committed_baseline_matches_the_committed_lint_report(self) -> None:
        """The gate is only worth running if it converges on this repository's real data."""
        baseline_file = REPO_ROOT / "app" / "lint-baseline.xml"
        report_file = REPO_ROOT / "app" / "build" / "reports" / "lint-results-flossRelease.xml"
        if not baseline_file.is_file() or not report_file.is_file():
            self.skipTest("no lint report on disk; run the lint gate stage first")
        current = gate.parse_lint_issues(report_file.read_text(encoding="utf-8", errors="replace"), REPO_ROOT)
        baseline = gate.parse_lint_issues(baseline_file.read_text(encoding="utf-8"), REPO_ROOT)
        new, stale = gate.diff_lint(current, baseline)
        self.assertEqual([], new[:5], f"{len(new)} unbaselined issue(s)")
        self.assertEqual([], stale[:5], f"{len(stale)} stale baseline entry/entries")

    def test_an_unbaselined_issue_is_reported_as_new(self) -> None:
        baseline = gate.parse_lint_issues(_issues_xml([("Old", "a.java", "known", 1)]))
        current = gate.parse_lint_issues(
            _issues_xml([("Old", "a.java", "known", 1), ("New", "b.java", "fresh", 2)])
        )
        new, stale = gate.diff_lint(current, baseline)
        self.assertEqual(1, len(new))
        self.assertEqual("New", new[0].issue_id)
        self.assertEqual([], stale)

    def test_a_fourth_occurrence_of_a_thrice_baselined_issue_is_new(self) -> None:
        entry = ("Dup", "a.java", "same", 1)
        baseline = gate.parse_lint_issues(_issues_xml([entry] * 3))
        current = gate.parse_lint_issues(_issues_xml([entry] * 4))
        new, stale = gate.diff_lint(current, baseline)
        self.assertEqual(1, len(new))
        self.assertEqual([], stale)

    def test_a_fixed_issue_is_reported_as_stale(self) -> None:
        baseline = gate.parse_lint_issues(_issues_xml([("Gone", "a.java", "fixed", 1)]))
        new, stale = gate.diff_lint([], baseline)
        self.assertEqual([], new)
        self.assertEqual(1, len(stale))
        self.assertEqual("Gone", stale[0].issue_id)


class LintPruneTest(unittest.TestCase):
    def test_pruning_removes_only_the_entries_no_longer_reported(self) -> None:
        baseline_xml = _issues_xml(
            [("Keep", "a.java", "still here", 1), ("Gone", "b.java", "fixed", 2)]
        )
        current = gate.parse_lint_issues(_issues_xml([("Keep", "a.java", "still here", 99)]))
        pruned, removed = gate.prune_baseline_xml(baseline_xml, current)
        self.assertEqual(1, removed)
        remaining = gate.parse_lint_issues(pruned)
        self.assertEqual(["Keep"], [issue.issue_id for issue in remaining])

    def test_pruning_duplicates_keeps_the_surviving_count(self) -> None:
        entry = ("Dup", "a.java", "same", 1)
        baseline_xml = _issues_xml([entry] * 5)
        current = gate.parse_lint_issues(_issues_xml([entry] * 2))
        pruned, removed = gate.prune_baseline_xml(baseline_xml, current)
        self.assertEqual(3, removed)
        self.assertEqual(2, len(gate.parse_lint_issues(pruned)))

    def test_a_clean_baseline_is_left_byte_identical(self) -> None:
        baseline_xml = _issues_xml([("Keep", "a.java", "m", 1)])
        current = gate.parse_lint_issues(baseline_xml)
        pruned, removed = gate.prune_baseline_xml(baseline_xml, current)
        self.assertEqual(0, removed)
        self.assertEqual(baseline_xml, pruned)

    def test_the_real_baseline_parses(self) -> None:
        real = SCRIPT.parents[1] / "app" / "lint-baseline.xml"
        if not real.is_file():
            self.skipTest("lint baseline not present")
        issues = gate.parse_lint_issues(real.read_text(encoding="utf-8"))
        self.assertGreater(len(issues), 0)


class TranslationOutputTest(unittest.TestCase):
    GOOD = "Source strings: 100\n\n=== Coverage Report (bottom 5) ===\n  values-fr: 80 / 100 (80%)\n"

    def test_a_consistent_report_passes(self) -> None:
        self.assertEqual([], gate.validate_translation_output(self.GOOD))

    def test_an_empty_extraction_is_not_a_pass(self) -> None:
        problems = gate.validate_translation_output("Source strings: 0\n  values-fr: 0 / 0 (0%)\n")
        self.assertTrue(any("no source strings" in p for p in problems))

    def test_a_missing_count_is_a_failure_of_the_check_itself(self) -> None:
        self.assertTrue(gate.validate_translation_output("PASSED: nothing to see\n"))

    def test_a_denominator_that_disagrees_with_the_source_count_is_rejected(self) -> None:
        problems = gate.validate_translation_output(
            "Source strings: 100\n  values-fr: 80 / 90 (88%)\n"
        )
        self.assertTrue(any("denominator" in p for p in problems))

    def test_more_translations_than_source_strings_is_rejected(self) -> None:
        problems = gate.validate_translation_output(
            "Source strings: 100\n  values-fr: 120 / 100 (120%)\n"
        )
        self.assertTrue(any("exceeds" in p for p in problems))
        self.assertTrue(any("out of range" in p for p in problems))

    def test_a_report_with_no_locales_is_rejected(self) -> None:
        problems = gate.validate_translation_output("Source strings: 100\n")
        self.assertTrue(any("no locale coverage rows" in p for p in problems))

    def test_machine_readable_summary_is_parsed(self) -> None:
        summary = gate.parse_translation_summary(
            'TRANSLATION_SUMMARY={"localeCount":2,"newMissing":0,"sourceStrings":100,"staleStrings":0}'
        )
        self.assertEqual(100, summary["sourceStrings"])

    def test_machine_readable_summary_rejects_new_gaps(self) -> None:
        with self.assertRaises(gate.GateError):
            gate.parse_translation_summary(
                'TRANSLATION_SUMMARY={"localeCount":2,"newMissing":1,"sourceStrings":100,"staleStrings":0}'
            )


BADGING = (
    "package: name='io.github.sysadmindoc.AppManagerNG' versionCode='14' versionName='0.6.6' "
    "compileSdkVersion='37'\n"
    "sdkVersion:'21'\n"
    "targetSdkVersion:'36'\n"
)


class ArtifactIdentityTest(unittest.TestCase):
    EXPECTED = {
        "packageName": "io.github.sysadmindoc.AppManagerNG",
        "versionName": "0.6.6",
        "versionCode": 14,
        "minSdk": 21,
        "targetSdk": 36,
    }

    def test_a_matching_apk_passes(self) -> None:
        self.assertEqual([], gate.check_artifact_identity(gate.parse_badging(BADGING), self.EXPECTED))

    def test_a_stale_version_is_caught(self) -> None:
        actual = gate.parse_badging(BADGING.replace("versionCode='14'", "versionCode='13'"))
        problems = gate.check_artifact_identity(actual, self.EXPECTED)
        self.assertTrue(any("versionCode" in p for p in problems))

    def test_the_wrong_package_is_caught(self) -> None:
        actual = gate.parse_badging(BADGING.replace("sysadmindoc", "muntashirakon"))
        self.assertTrue(any("package name" in p for p in gate.check_artifact_identity(actual, self.EXPECTED)))

    def test_a_lowered_target_sdk_is_caught(self) -> None:
        actual = gate.parse_badging(BADGING.replace("targetSdkVersion:'36'", "targetSdkVersion:'33'"))
        self.assertTrue(any("targetSdk" in p for p in gate.check_artifact_identity(actual, self.EXPECTED)))

    def test_a_missing_sdk_declaration_is_caught_rather_than_ignored(self) -> None:
        actual = gate.parse_badging(BADGING.replace("targetSdkVersion:'36'\n", ""))
        self.assertIsNone(actual["targetSdk"])
        self.assertTrue(any("does not declare" in p for p in gate.check_artifact_identity(actual, self.EXPECTED)))

    def test_unreadable_badging_is_an_error_not_an_empty_identity(self) -> None:
        with self.assertRaises(gate.GateError):
            gate.parse_badging("ERROR: dump failed\n")

    def test_the_signer_digest_is_read_back(self) -> None:
        digest = "A" * 64
        output = f"Signer #1 certificate SHA-256 digest: {digest}\n"
        self.assertEqual(digest.lower(), gate.parse_signing_certificate(output))

    def test_an_unsigned_apk_reports_no_digest(self) -> None:
        self.assertIsNone(gate.parse_signing_certificate("DOES NOT VERIFY\n"))


class ReceiptTest(unittest.TestCase):
    def test_the_receipt_binds_the_commit_artifacts_and_signer(self) -> None:
        results = [
            gate.StageResult("source", True, "HEAD abc", {"head": "abc123", "clean": True}),
            gate.StageResult("reproducible", True, "1 artifact", {
                "artifacts": [{"name": "app.apk", "size": 10, "sha256": "f" * 64}]
            }),
            gate.StageResult("artifact", True, "ok", {
                "apks": [{"name": "app.apk"}],
                "signingCertificateSha256": "d" * 64,
            }),
        ]
        identity = {"packageName": "p", "versionName": "1.0", "versionCode": 1}
        with tempfile.TemporaryDirectory() as tmp:
            receipt = gate.build_receipt(Path(tmp), results, identity, "v1.0", "2026-01-01T00:00:00Z", "gradlew")
        self.assertEqual(gate.RECEIPT_SCHEMA_VERSION, receipt["schemaVersion"])
        self.assertEqual("abc123", receipt["source"]["head"])
        self.assertEqual("v1.0", receipt["source"]["tag"])
        self.assertEqual("d" * 64, receipt["signingCertificateSha256"])
        self.assertEqual("f" * 64, receipt["artifacts"][0]["sha256"])
        self.assertEqual(identity, receipt["identity"])
        self.assertEqual(
            ["source", "reproducible", "artifact"],
            [stage["name"] for stage in receipt["stages"]],
        )

    def test_the_receipt_records_which_stages_actually_ran(self) -> None:
        results = [gate.StageResult("source", True, "HEAD abc", {"head": "abc", "clean": True})]
        with tempfile.TemporaryDirectory() as tmp:
            receipt = gate.build_receipt(Path(tmp), results, {}, None, None, "gradlew")
        self.assertEqual(["source"], [stage["name"] for stage in receipt["stages"]])
        self.assertEqual([], receipt["artifacts"])
        self.assertIsNone(receipt["signingCertificateSha256"])

    def test_the_receipt_keeps_stage_data_for_audit_counts(self) -> None:
        results = [gate.StageResult("translation", True, "100 source strings", {
            "sourceStrings": 100,
            "localeCount": 2,
            "newMissing": 0,
            "staleStrings": 0,
        })]
        with tempfile.TemporaryDirectory() as tmp:
            receipt = gate.build_receipt(Path(tmp), results, {}, None, None, "gradlew")
        self.assertEqual(100, receipt["stages"][0]["data"]["sourceStrings"])


class SourceStageTest(unittest.TestCase):
    def _repo(self, tmp: Path) -> Path:
        import subprocess

        subprocess.run(["git", "init", "-q"], cwd=tmp, check=True)
        subprocess.run(["git", "config", "user.email", "t@example.com"], cwd=tmp, check=True)
        subprocess.run(["git", "config", "user.name", "t"], cwd=tmp, check=True)
        (tmp / "a.txt").write_text("one\n", encoding="utf-8")
        subprocess.run(["git", "add", "."], cwd=tmp, check=True)
        subprocess.run(["git", "commit", "-qm", "init"], cwd=tmp, check=True)
        return tmp

    def test_a_clean_tree_passes_and_records_head(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            repo = self._repo(Path(raw))
            result = gate.stage_source(repo, None, False)
            self.assertTrue(result.passed)
            self.assertTrue(result.data["clean"])
            self.assertEqual(40, len(result.data["head"]))

    def test_a_dirty_tree_blocks_the_release(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            repo = self._repo(Path(raw))
            (repo / "a.txt").write_text("two\n", encoding="utf-8")
            with self.assertRaises(gate.GateError) as caught:
                gate.stage_source(repo, None, False)
            self.assertIn("not clean", str(caught.exception))

    def test_a_tag_that_is_not_head_blocks_the_release(self) -> None:
        import subprocess

        with tempfile.TemporaryDirectory() as raw:
            repo = self._repo(Path(raw))
            subprocess.run(["git", "tag", "v1.0"], cwd=repo, check=True)
            (repo / "a.txt").write_text("two\n", encoding="utf-8")
            subprocess.run(["git", "commit", "-qam", "second"], cwd=repo, check=True)
            with self.assertRaises(gate.GateError) as caught:
                gate.stage_source(repo, "v1.0", False)
            self.assertIn("not the checked-out HEAD", str(caught.exception))

    def test_a_tag_on_head_is_accepted(self) -> None:
        import subprocess

        with tempfile.TemporaryDirectory() as raw:
            repo = self._repo(Path(raw))
            subprocess.run(["git", "tag", "v1.0"], cwd=repo, check=True)
            result = gate.stage_source(repo, "v1.0", False)
            self.assertEqual("v1.0", result.data["tag"])


if __name__ == "__main__":
    unittest.main()
