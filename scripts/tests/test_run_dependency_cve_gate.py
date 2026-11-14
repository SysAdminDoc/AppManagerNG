# SPDX-License-Identifier: GPL-3.0-or-later

import importlib.util
import json
import subprocess
import tempfile
import unittest
import zipfile
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "run_dependency_cve_gate.py"
SPEC = importlib.util.spec_from_file_location("run_dependency_cve_gate", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
gate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gate)
# The DEX and jar builders live with the reachability tests
FIXTURES_SPEC = importlib.util.spec_from_file_location(
    "test_dependency_cve_reachability", Path(__file__).resolve().parent / "test_dependency_cve_reachability.py")
assert FIXTURES_SPEC is not None and FIXTURES_SPEC.loader is not None
fixtures = importlib.util.module_from_spec(FIXTURES_SPEC)
FIXTURES_SPEC.loader.exec_module(fixtures)

NOW = datetime(2026, 9, 26, 12, 0, 0, tzinfo=timezone.utc)
GUAVA = "pkg:maven/com.google.guava/guava@32.1.3-android"
OUTPUT_NAMES = {*gate.REPORT_NAMES.values(), gate.REACHABILITY_JSON_NAME, gate.REACHABILITY_TEXT_NAME,
                gate.RECEIPT_NAME}


class DependencyCveGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.report_dir = self.root / "build" / "reports"
        self.out_dir = self.root / "publish"
        self.data_dir = self.root / "dependency-check-data"
        # The scan tests below are about the scan, so they start from a database refreshed an
        # hour ago. The feed tests further down replace this record.
        self._write_feed(refreshed_at=NOW - timedelta(hours=1))

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _run_gate(self, env=None, apks=()):
        return gate.run_gate(["gradlew"], self.root, self.out_dir, data_dir=self.data_dir,
                             env={} if env is None else env, clock=lambda: NOW, apks=apks)

    def _write_feed(self, refreshed_at=None, database=True, record=None) -> None:
        self.data_dir.mkdir(parents=True, exist_ok=True)
        database_path = self.data_dir / gate.DATABASE_NAME
        if database:
            database_path.write_bytes(b"h2")
        record_path = self.data_dir / gate.FEED_RECEIPT_NAME
        record_path.unlink(missing_ok=True)
        if record is None and refreshed_at is not None:
            stat = database_path.stat() if database_path.exists() else None
            record = json.dumps({"schemaVersion": 1,
                                 "refreshedAt": refreshed_at.strftime("%Y-%m-%dT%H:%M:%SZ"),
                                 "gradleTask": "dependencyCheckUpdate", "result": "succeeded",
                                 "databaseBytes": stat.st_size if stat else 0,
                                 "databaseModifiedNs": stat.st_mtime_ns if stat else 0})
        if record is not None:
            record_path.write_text(record, encoding="utf-8")

    def _write_reports(self, dependencies=(), directory=None) -> None:
        # The JSON report is parsed, so it has to be a real one; the others are only copied.
        directory = directory or self.report_dir
        directory.mkdir(parents=True, exist_ok=True)
        for report_format, name in gate.REPORT_NAMES.items():
            content = (json.dumps({"dependencies": list(dependencies)}) if report_format == "JSON"
                       else f"fixture:{name}\n")
            (directory / name).write_text(content, encoding="utf-8")

    def _write_nested_reports(self) -> None:
        self._write_reports(directory=self.report_dir / "dependency-check")

    def _scanner_reporting(self, dependencies, exit_code=0):
        def scanner(command, **kwargs):
            self._write_reports(dependencies)
            return subprocess.CompletedProcess(command, exit_code)
        return scanner

    def _write_config(self, name, content) -> None:
        path = self.root / "config" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def _write_apk(self, name, descriptors=("Lio/github/App;",), mapping=True) -> Path:
        apk = self.root / name
        with zipfile.ZipFile(apk, "w") as archive:
            archive.writestr("classes.dex", fixtures.dex_with_types(list(descriptors)))
        if mapping:
            gate.mapping_for(apk).write_text("io.github.App -> a:\n", encoding="utf-8")
        return apk

    def test_gate_passes_blocking_threshold_and_receipts_both_reports(self) -> None:
        def scanner(command, **kwargs):
            self._write_reports()
            return subprocess.CompletedProcess(command, 0)

        with mock.patch.object(gate.subprocess, "run", side_effect=scanner) as run:
            paths = self._run_gate()

        command = run.call_args.args[0]
        self.assertIn("dependencyCheckAggregate", command)
        self.assertIn("-PdependencyCheckFailBuildOnCvss=9.0", command)
        receipt = json.loads((self.out_dir / gate.RECEIPT_NAME).read_text(encoding="utf-8"))
        self.assertEqual("9.0", receipt["blockingCvss"])
        self.assertTrue(receipt["passed"])
        self.assertEqual(0, receipt["scannerExitCode"])
        self.assertEqual(3, receipt["schemaVersion"])
        self.assertEqual({"HTML", "SARIF", "JSON"}, {report["format"] for report in receipt["reports"]})
        self.assertEqual(OUTPUT_NAMES, {path.name for path in paths})
        self.assertTrue(all(path.is_file() for path in paths))

    def test_scanner_failure_blocks_release_and_retains_diagnostic_reports(self) -> None:
        def scanner(command, **kwargs):
            self._write_reports()
            return subprocess.CompletedProcess(command, 1)

        with mock.patch.object(gate.subprocess, "run", side_effect=scanner):
            with self.assertRaisesRegex(gate.GateError, "reports were retained"):
                self._run_gate()
        receipt = json.loads((self.out_dir / gate.RECEIPT_NAME).read_text(encoding="utf-8"))
        self.assertFalse(receipt["passed"])
        self.assertEqual(1, receipt["scannerExitCode"])
        self.assertTrue((self.out_dir / gate.REPORT_NAMES["HTML"]).is_file())
        self.assertTrue((self.out_dir / gate.REPORT_NAMES["SARIF"]).is_file())

    def test_dependency_check_13_nested_reports_are_published(self) -> None:
        def scanner(command, **kwargs):
            self._write_nested_reports()
            return subprocess.CompletedProcess(command, 0)

        with mock.patch.object(gate.subprocess, "run", side_effect=scanner):
            paths = self._run_gate()

        self.assertEqual(OUTPUT_NAMES, {path.name for path in paths})
        self.assertTrue((self.out_dir / gate.REPORT_NAMES["HTML"]).is_file())
        self.assertTrue((self.out_dir / gate.REPORT_NAMES["SARIF"]).is_file())

    def test_missing_report_blocks_release(self) -> None:
        def scanner(command, **kwargs):
            self.report_dir.mkdir(parents=True, exist_ok=True)
            (self.report_dir / gate.REPORT_NAMES["HTML"]).write_text("html", encoding="utf-8")
            return subprocess.CompletedProcess(command, 0)

        with mock.patch.object(gate.subprocess, "run", side_effect=scanner):
            with self.assertRaisesRegex(gate.GateError, "produced no report"):
                self._run_gate()


    def test_a_dependency_verification_abort_blocks_the_release_without_a_receipt(self) -> None:
        """Gradle can fail before the scanner ever starts.

        A configuration whose artifacts have no entry in verification-metadata.xml aborts
        resolution, so no report is written and nothing is scanned. That must read as a gate
        failure with no receipt at all -- a receipt claiming ``passed`` on an unscanned build,
        or a silent skip, would let a release ship with no CVE evidence behind it.
        """

        def scanner(command, **kwargs):
            # Resolution aborted: the report directory stays empty.
            self.report_dir.mkdir(parents=True, exist_ok=True)
            return subprocess.CompletedProcess(command, 1)

        with mock.patch.object(gate.subprocess, "run", side_effect=scanner):
            with self.assertRaisesRegex(gate.GateError, "produced no report") as raised:
                self._run_gate()
        self.assertIn("scanner exit code 1", str(raised.exception))
        self.assertFalse((self.out_dir / gate.RECEIPT_NAME).exists(),
                         "a receipt must not exist for a build that was never scanned")

    def test_the_posix_wrapper_is_swapped_for_the_batch_one_on_windows(self) -> None:
        (self.root / "gradlew.bat").write_text("@echo off", encoding="utf-8")
        with mock.patch.object(gate.os, "name", "nt"):
            resolved = gate.resolve_gradle_command(["./gradlew"], self.root)
        self.assertTrue(resolved[0].endswith("gradlew.bat"),
                        f"expected the batch wrapper, got {resolved[0]}")

    def test_a_posix_host_keeps_the_wrapper_it_was_given(self) -> None:
        with mock.patch.object(gate.os, "name", "posix"):
            self.assertEqual(["./gradlew"], gate.resolve_gradle_command(["./gradlew"], self.root))

    def test_an_explicit_launcher_is_never_second_guessed(self) -> None:
        with mock.patch.object(gate.os, "name", "nt"):
            self.assertEqual(["/opt/gradle/bin/gradle", "-q"],
                             gate.resolve_gradle_command(["/opt/gradle/bin/gradle", "-q"], self.root))

    def test_an_empty_command_is_refused(self) -> None:
        with self.assertRaises(gate.GateError):
            gate.resolve_gradle_command([], self.root)

    def _passing_scanner(self, calls):
        def scanner(command, **kwargs):
            calls.append(command)
            if "dependencyCheckAggregate" in command:
                self._write_reports()
            return subprocess.CompletedProcess(command, 0)
        return scanner

    def test_a_fresh_local_database_passes_and_the_receipt_states_its_age(self) -> None:
        self._write_feed(refreshed_at=NOW - timedelta(hours=30))
        calls = []

        with mock.patch.object(gate.subprocess, "run", side_effect=self._passing_scanner(calls)):
            self._run_gate()

        self.assertEqual(1, len(calls), "a keyless run must not attempt a refresh")
        self.assertIn("-PdependencyCheckAutoUpdate=false", calls[0])
        receipt = json.loads((self.out_dir / gate.RECEIPT_NAME).read_text(encoding="utf-8"))
        self.assertEqual({
            "updateMode": "local",
            "refreshResult": "not-attempted",
            "databaseRefreshedAt": "2026-09-25T06:00:00Z",
            "ageHours": 30.0,
            "maxAgeHours": gate.MAX_FEED_AGE_HOURS,
            "decision": "fresh",
        }, receipt["vulnerabilityFeed"])

    def test_a_stale_database_blocks_before_the_scan_starts(self) -> None:
        self._write_feed(refreshed_at=NOW - timedelta(hours=gate.MAX_FEED_AGE_HOURS + 1))

        with mock.patch.object(gate.subprocess, "run") as run:
            with self.assertRaisesRegex(gate.GateError, "last refreshed 7.0 days ago"):
                self._run_gate()

        run.assert_not_called()
        self.assertFalse((self.out_dir / gate.RECEIPT_NAME).exists())

    def test_a_database_without_a_refresh_record_is_undated_and_blocks(self) -> None:
        self._write_feed(refreshed_at=None)

        with mock.patch.object(gate.subprocess, "run") as run:
            with self.assertRaisesRegex(gate.GateError, "no refresh record, so its age is unknown"):
                self._run_gate()

        run.assert_not_called()

    def test_a_missing_database_blocks(self) -> None:
        self._write_feed(refreshed_at=NOW, database=False)
        (self.data_dir / gate.DATABASE_NAME).unlink(missing_ok=True)

        with mock.patch.object(gate.subprocess, "run") as run:
            with self.assertRaisesRegex(gate.GateError, "no vulnerability database"):
                self._run_gate()

        run.assert_not_called()

    def test_an_unreadable_or_unsuccessful_record_blocks(self) -> None:
        for record in ("not json", json.dumps({"refreshedAt": "yesterday", "result": "succeeded"}),
                       json.dumps({"refreshedAt": "2026-09-26T11:00:00Z", "result": "failed"})):
            with self.subTest(record=record):
                self._write_feed(record=record)
                with mock.patch.object(gate.subprocess, "run") as run:
                    with self.assertRaisesRegex(gate.GateError, "is unreadable"):
                        self._run_gate()
                run.assert_not_called()

    def test_a_record_dated_in_the_future_blocks(self) -> None:
        self._write_feed(refreshed_at=NOW + timedelta(hours=2))

        with mock.patch.object(gate.subprocess, "run") as run:
            with self.assertRaisesRegex(gate.GateError, "check the system clock"):
                self._run_gate()

        run.assert_not_called()

    def test_a_keyed_run_refreshes_first_and_records_the_refresh(self) -> None:
        # Even a record that would be stale is replaced by the refresh.
        self._write_feed(refreshed_at=NOW - timedelta(days=30))
        calls = []

        with mock.patch.object(gate.subprocess, "run", side_effect=self._passing_scanner(calls)):
            self._run_gate(env={"NVD_API_KEY": "fixture"})

        self.assertIn("dependencyCheckUpdate", calls[0])
        self.assertIn("dependencyCheckAggregate", calls[1])
        record = json.loads((self.data_dir / gate.FEED_RECEIPT_NAME).read_text(encoding="utf-8"))
        self.assertEqual("2026-09-26T12:00:00Z", record["refreshedAt"])
        receipt = json.loads((self.out_dir / gate.RECEIPT_NAME).read_text(encoding="utf-8"))
        self.assertEqual("online-refresh", receipt["vulnerabilityFeed"]["updateMode"])
        self.assertEqual("succeeded", receipt["vulnerabilityFeed"]["refreshResult"])
        self.assertEqual(0.0, receipt["vulnerabilityFeed"]["ageHours"])

    def test_a_failed_refresh_blocks_and_records_nothing(self) -> None:
        self._write_feed(refreshed_at=NOW - timedelta(days=30))
        before = (self.data_dir / gate.FEED_RECEIPT_NAME).read_text(encoding="utf-8")
        calls = []

        def scanner(command, **kwargs):
            calls.append(command)
            return subprocess.CompletedProcess(command, 1)

        with mock.patch.object(gate.subprocess, "run", side_effect=scanner):
            with self.assertRaisesRegex(gate.GateError, "feed refresh failed"):
                self._run_gate(env={"NVD_API_KEY": "fixture"})

        self.assertEqual(1, len(calls), "the scan must not run after a failed refresh")
        self.assertEqual(before, (self.data_dir / gate.FEED_RECEIPT_NAME).read_text(encoding="utf-8"))
        self.assertFalse((self.out_dir / gate.RECEIPT_NAME).exists())

    def test_both_gradle_runs_are_pinned_to_the_checked_database_directory(self) -> None:
        calls = []

        with mock.patch.object(gate.subprocess, "run", side_effect=self._passing_scanner(calls)):
            self._run_gate(env={"NVD_API_KEY": "fixture"})

        expected = f"-PdependencyCheckDataDirectory={self.data_dir}"
        self.assertEqual(2, len(calls))
        for command in calls:
            self.assertIn(expected, command)

    def test_a_database_replaced_after_its_refresh_record_is_undated(self) -> None:
        # The record vouches for one database file; a copy put in its place later does not inherit it.
        (self.data_dir / gate.DATABASE_NAME).write_bytes(b"an older database restored from a backup")

        with mock.patch.object(gate.subprocess, "run") as run:
            with self.assertRaisesRegex(gate.GateError, "changed after its recorded refresh"):
                self._run_gate()

        run.assert_not_called()

    def test_a_keyed_refresh_records_the_database_it_vouches_for(self) -> None:
        calls = []

        with mock.patch.object(gate.subprocess, "run", side_effect=self._passing_scanner(calls)):
            self._run_gate(env={"NVD_API_KEY": "fixture"})

        record = json.loads((self.data_dir / gate.FEED_RECEIPT_NAME).read_text(encoding="utf-8"))
        stat = (self.data_dir / gate.DATABASE_NAME).stat()
        self.assertEqual(stat.st_size, record["databaseBytes"])
        self.assertEqual(stat.st_mtime_ns, record["databaseModifiedNs"])

    def test_a_suppression_nobody_could_review_blocks_before_the_scan(self) -> None:
        self._write_config("owasp-suppressions.xml",
                           f'<suppressions xmlns="{fixtures.NS}"><suppress><notes>No tag.</notes>'
                           '<packageUrl regex="true">^pkg:maven/a/b@1$</packageUrl><cve>CVE-2026-1</cve>'
                           '</suppress></suppressions>')

        with mock.patch.object(gate.subprocess, "run") as run:
            with self.assertRaisesRegex(gate.GateError, r"suppression 1 \(CVE-2026-1\) has no disposition"):
                self._run_gate()

        run.assert_not_called()
        self.assertFalse((self.out_dir / gate.RECEIPT_NAME).exists())

    def test_a_disposition_the_scan_no_longer_matches_blocks_after_it(self) -> None:
        self._write_config("dependency-cve-dispositions.json", json.dumps({"schemaVersion": 1, "dispositions": [
            {"ids": ["CVE-2026-11822"], "disposition": "cpe-name-collision", "reason": "No SQLite of its own.",
             "source": "https://example.org/advisory", "reviewed": "2026-11-14",
             "packageUrl": r"^pkg:maven/androidx\.sqlite/sqlite-android@2\.5\.1$"}]}))

        with mock.patch.object(gate.subprocess, "run", side_effect=self._scanner_reporting([])):
            with self.assertRaisesRegex(gate.GateError, "matches no CVE-2026-11822 finding in this scan"):
                self._run_gate()

        receipt = json.loads((self.out_dir / gate.RECEIPT_NAME).read_text(encoding="utf-8"))
        self.assertFalse(receipt["passed"])
        self.assertEqual(1, len(receipt["reachability"]["validationProblems"]))

    def test_a_critical_finding_the_scanner_let_through_still_blocks(self) -> None:
        shipped = fixtures.dependency(GUAVA, "guava.jar", ["app:flossReleaseRuntimeClasspath"],
                                      vulnerabilities=[fixtures.vulnerability("CVE-2026-1", 9.8)])

        with mock.patch.object(gate.subprocess, "run", side_effect=self._scanner_reporting([shipped])):
            with self.assertRaisesRegex(gate.GateError, "the scanner passed, but these findings"):
                self._run_gate()

        receipt = json.loads((self.out_dir / gate.RECEIPT_NAME).read_text(encoding="utf-8"))
        self.assertFalse(receipt["passed"])
        self.assertEqual(["CVE-2026-1 " + GUAVA], receipt["reachability"]["blocking"])
        text = (self.out_dir / gate.REACHABILITY_TEXT_NAME).read_text(encoding="utf-8")
        self.assertIn("FLOSS, packaged in the release APK (1, no APK checked)", text)

    def test_each_release_apk_and_its_mapping_go_in_the_receipt(self) -> None:
        floss = self._write_apk("AppManagerNG-reproducible-floss-release.apk")
        full = self._write_apk("AppManagerNG-reproducible-full-release.apk")

        with mock.patch.object(gate.subprocess, "run", side_effect=self._scanner_reporting([])):
            self._run_gate(apks=[floss, full])

        receipt = json.loads((self.out_dir / gate.RECEIPT_NAME).read_text(encoding="utf-8"))
        self.assertTrue(receipt["passed"])
        self.assertEqual([("floss", floss.name, "AppManagerNG-reproducible-floss-release-mapping.txt"),
                          ("full", full.name, "AppManagerNG-reproducible-full-release-mapping.txt")],
                         [(apk["flavor"], apk["apk"], apk["mapping"]) for apk in receipt["reachability"]["apks"]])
        self.assertTrue(receipt["reachability"]["flavors"]["floss"]["apkChecked"])
        self.assertTrue(receipt["reachability"]["flavors"]["full"]["apkChecked"])

    def test_an_apk_without_a_flavor_a_mapping_or_alone_in_its_flavor_blocks_before_the_scan(self) -> None:
        cases = {
            "doesn't say which flavor": [self._write_apk("app-release.apk")],
            "R8 mapping .* is unreadable": [self._write_apk("app-floss-release.apk", mapping=False)],
            "two floss release APKs": [self._write_apk("a-floss-release.apk"), self._write_apk("b-floss-release.apk")],
        }
        for message, apks in cases.items():
            with self.subTest(message=message):
                with mock.patch.object(gate.subprocess, "run") as run:
                    with self.assertRaisesRegex(gate.GateError, message):
                        self._run_gate(apks=apks)
                run.assert_not_called()

    def test_each_apk_takes_its_own_flag(self) -> None:
        args = gate.parse_args(["--out-dir", "out", "--apk", "a-floss-release.apk", "--apk", "b-full-release.apk"])

        self.assertEqual([Path("a-floss-release.apk"), Path("b-full-release.apk")], args.apk)

    def test_the_default_database_lives_under_gradle_user_home(self) -> None:
        self.assertEqual(Path("/g") / "dependency-check-data" / gate.DATA_FORMAT_DIRECTORY,
                         gate.default_data_directory({"GRADLE_USER_HOME": "/g"}))


if __name__ == "__main__":
    unittest.main()
