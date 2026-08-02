# SPDX-License-Identifier: GPL-3.0-or-later

import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "run_dependency_cve_gate.py"
SPEC = importlib.util.spec_from_file_location("run_dependency_cve_gate", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
gate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gate)


class DependencyCveGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.report_dir = self.root / "build" / "reports"
        self.out_dir = self.root / "publish"

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _write_reports(self) -> None:
        self.report_dir.mkdir(parents=True, exist_ok=True)
        for name in gate.REPORT_NAMES.values():
            (self.report_dir / name).write_text(f"fixture:{name}\n", encoding="utf-8")

    def test_gate_passes_blocking_threshold_and_receipts_both_reports(self) -> None:
        def scanner(command, **kwargs):
            self._write_reports()
            return subprocess.CompletedProcess(command, 0)

        with mock.patch.object(gate.subprocess, "run", side_effect=scanner) as run:
            paths = gate.run_gate(["gradlew"], self.root, self.out_dir)

        command = run.call_args.args[0]
        self.assertIn("dependencyCheckAggregate", command)
        self.assertIn("-PdependencyCheckFailBuildOnCvss=9.0", command)
        receipt = json.loads((self.out_dir / gate.RECEIPT_NAME).read_text(encoding="utf-8"))
        self.assertEqual("9.0", receipt["blockingCvss"])
        self.assertTrue(receipt["passed"])
        self.assertEqual(0, receipt["scannerExitCode"])
        self.assertEqual({"HTML", "SARIF"}, {report["format"] for report in receipt["reports"]})
        self.assertEqual(3, len(paths))
        self.assertTrue(all(path.is_file() for path in paths))

    def test_scanner_failure_blocks_release_and_retains_diagnostic_reports(self) -> None:
        def scanner(command, **kwargs):
            self._write_reports()
            return subprocess.CompletedProcess(command, 1)

        with mock.patch.object(gate.subprocess, "run", side_effect=scanner):
            with self.assertRaisesRegex(gate.GateError, "reports were retained"):
                gate.run_gate(["gradlew"], self.root, self.out_dir)
        receipt = json.loads((self.out_dir / gate.RECEIPT_NAME).read_text(encoding="utf-8"))
        self.assertFalse(receipt["passed"])
        self.assertEqual(1, receipt["scannerExitCode"])
        self.assertTrue((self.out_dir / gate.REPORT_NAMES["HTML"]).is_file())
        self.assertTrue((self.out_dir / gate.REPORT_NAMES["SARIF"]).is_file())

    def test_missing_report_blocks_release(self) -> None:
        def scanner(command, **kwargs):
            self.report_dir.mkdir(parents=True, exist_ok=True)
            (self.report_dir / gate.REPORT_NAMES["HTML"]).write_text("html", encoding="utf-8")
            return subprocess.CompletedProcess(command, 0)

        with mock.patch.object(gate.subprocess, "run", side_effect=scanner):
            with self.assertRaisesRegex(gate.GateError, "produced no report"):
                gate.run_gate(["gradlew"], self.root, self.out_dir)


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
                gate.run_gate(["gradlew"], self.root, self.out_dir)
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


if __name__ == "__main__":
    unittest.main()
