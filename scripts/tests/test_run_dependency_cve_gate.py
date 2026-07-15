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


if __name__ == "__main__":
    unittest.main()
