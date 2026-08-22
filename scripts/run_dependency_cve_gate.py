#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Run the blocking local OWASP release gate and preserve its reports."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Sequence


BLOCKING_CVSS = "9.0"
REPORT_NAMES = {
    "HTML": "dependency-check-report.html",
    "SARIF": "dependency-check-report.sarif",
}
RECEIPT_NAME = "dependency-cve-receipt.json"


class GateError(RuntimeError):
    pass


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def resolve_gradle_command(gradle_command: Sequence[str], repo_root: Path) -> list[str]:
    """Picks a Gradle launcher this interpreter can actually exec.

    Callers hand us ``./gradlew`` because that is what works from a shell. Python's subprocess
    does not go through a shell, and on Windows the POSIX wrapper is a shell script rather than
    an executable image, so launching it fails with "not a valid Win32 application". The Windows
    batch wrapper is used instead when it exists.
    """
    if not gradle_command:
        raise GateError("Gradle command must not be empty")
    resolved = list(gradle_command)
    launcher_name = str(resolved[0]).replace("\\", "/").rsplit("/", 1)[-1]
    if os.name == "nt" and launcher_name in ("gradlew", "gradlew.sh"):
        batch = repo_root / "gradlew.bat"
        if batch.is_file():
            resolved[0] = str(batch)
    return resolved


def run_gate(
    gradle_command: Sequence[str],
    repo_root: Path,
    out_dir: Path,
    report_dir: Path | None = None,
) -> list[Path]:
    gradle_command = resolve_gradle_command(gradle_command, repo_root)
    report_dir = report_dir or repo_root / "build" / "reports"
    report_paths = [report_dir / name for name in REPORT_NAMES.values()]
    nested_report_paths = [report_dir / "dependency-check" / name for name in REPORT_NAMES.values()]
    for report in (*report_paths, *nested_report_paths):
        report.unlink(missing_ok=True)
    for name in (*REPORT_NAMES.values(), RECEIPT_NAME):
        (out_dir / name).unlink(missing_ok=True)

    command = [
        *gradle_command,
        "--no-daemon",
        "--stacktrace",
        "dependencyCheckAggregate",
        f"-PdependencyCheckFailBuildOnCvss={BLOCKING_CVSS}",
    ]
    try:
        result = subprocess.run(command, cwd=repo_root, check=False)
    except OSError as exc:
        raise GateError(f"could not start dependency CVE scanner: {exc}") from exc
    missing = [path for path in report_paths if not path.is_file()]
    if missing and all(path.is_file() for path in nested_report_paths):
        report_dir = report_dir / "dependency-check"
        report_paths = nested_report_paths
        missing = []
    if missing:
        detail = "dependency CVE gate produced no report: " + ", ".join(str(path) for path in missing)
        if result.returncode != 0:
            detail += f" (scanner exit code {result.returncode})"
        raise GateError(detail)

    out_dir.mkdir(parents=True, exist_ok=True)
    reports = []
    published_paths: list[Path] = []
    for report_format, name in REPORT_NAMES.items():
        destination = out_dir / name
        shutil.copy2(report_dir / name, destination)
        reports.append({
            "format": report_format,
            "name": name,
            "sha256": _sha256(destination),
        })
        published_paths.append(destination)

    receipt_path = out_dir / RECEIPT_NAME
    receipt_path.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "scanner": "OWASP Dependency-Check",
                "gradleTask": "dependencyCheckAggregate",
                "blockingCvss": BLOCKING_CVSS,
                "passed": result.returncode == 0,
                "scannerExitCode": result.returncode,
                "reports": reports,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    published_paths.append(receipt_path)
    if result.returncode != 0:
        raise GateError(
            "dependency CVE gate failed; reports were retained, but release is blocked "
            "until the scanner succeeds with no unsuppressed CVSS 9.0+ findings"
        )
    return published_paths


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    repo_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gradle-cmd", default="./gradlew")
    parser.add_argument("--repo-root", type=Path, default=repo_root)
    parser.add_argument("--out-dir", type=Path, required=True)
    parser.add_argument("--report-dir", type=Path)
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    repo_root = args.repo_root.resolve()
    out_dir = args.out_dir if args.out_dir.is_absolute() else repo_root / args.out_dir
    report_dir = args.report_dir
    if report_dir is not None and not report_dir.is_absolute():
        report_dir = repo_root / report_dir
    try:
        paths = run_gate([args.gradle_cmd], repo_root, out_dir, report_dir)
    except GateError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    for path in paths:
        print(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
