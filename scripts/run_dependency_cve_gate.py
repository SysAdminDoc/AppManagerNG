#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Run the blocking local OWASP release gate and preserve its reports.

The scan is only as current as Dependency-Check's local vulnerability database, so the gate
refuses to scan with a database of unknown or excessive age. With ``NVD_API_KEY`` set it first
refreshes the database and records when that succeeded. Without a key it accepts only a database
whose recorded refresh is younger than ``MAX_FEED_AGE_HOURS``.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Callable, Mapping, Sequence


BLOCKING_CVSS = "9.0"
REPORT_NAMES = {
    "HTML": "dependency-check-report.html",
    "SARIF": "dependency-check-report.sarif",
}
RECEIPT_NAME = "dependency-cve-receipt.json"

# A week keeps a keyless release inside the NVD's normal publication rhythm while still allowing
# a release to be cut from a machine that refreshed a few days earlier.
MAX_FEED_AGE_HOURS = 168
# A refresh receipt dated slightly ahead of this clock is tolerated; anything further is a clock
# problem, and a future date cannot vouch for anything.
CLOCK_SKEW_TOLERANCE = timedelta(minutes=5)
# Where the gate keeps the database by default: Dependency-Check's own default location under
# GRADLE_USER_HOME. Both Gradle runs are pinned to this directory, so the checked database and the
# scanned one cannot drift apart even if a plugin upgrade changes its default.
DATA_FORMAT_DIRECTORY = "11.0"
DATABASE_NAME = "odc.mv.db"
FEED_RECEIPT_NAME = "appmanagerng-feed-refresh.json"


class GateError(RuntimeError):
    pass


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(moment: datetime) -> str:
    return moment.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def default_data_directory(env: Mapping[str, str]) -> Path:
    gradle_home = env.get("GRADLE_USER_HOME")
    base = Path(gradle_home) if gradle_home else Path.home() / ".gradle"
    return base / "dependency-check-data" / DATA_FORMAT_DIRECTORY


def refresh_feed(
    gradle_command: Sequence[str],
    repo_root: Path,
    data_dir: Path,
    clock: Callable[[], datetime],
) -> None:
    """Refreshes the database online and records when that succeeded.

    The build reads ``NVD_API_KEY`` from the environment. Nothing is recorded unless the refresh
    exits cleanly and leaves a database behind, so a failed refresh can never look recent.
    """
    receipt_path = data_dir / FEED_RECEIPT_NAME
    database = data_dir / DATABASE_NAME
    command = [
        *gradle_command,
        "--no-daemon",
        "--stacktrace",
        "dependencyCheckUpdate",
        "-PdependencyCheckAutoUpdate=true",
        data_directory_argument(data_dir),
    ]
    try:
        result = subprocess.run(command, cwd=repo_root, check=False)
    except OSError as exc:
        raise GateError(f"could not start the vulnerability feed refresh: {exc}") from exc
    if result.returncode != 0:
        raise GateError(
            f"vulnerability feed refresh failed (exit code {result.returncode}); release is "
            "blocked because the local database could not be brought up to date"
        )
    if not database.is_file():
        raise GateError(
            f"vulnerability feed refresh reported success but left no database at {database}"
        )
    stat = database.stat()
    receipt_path.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "refreshedAt": _iso(clock()),
                "gradleTask": "dependencyCheckUpdate",
                "result": "succeeded",
                # Ties the record to the database it vouches for, so a database copied or
                # restored over this one afterwards is not taken as the refreshed one.
                "databaseBytes": stat.st_size,
                "databaseModifiedNs": stat.st_mtime_ns,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def data_directory_argument(data_dir: Path) -> str:
    """Pins Dependency-Check to the database directory the gate checks (see build.gradle)."""
    return f"-PdependencyCheckDataDirectory={data_dir}"


def check_feed(
    data_dir: Path,
    now: datetime,
    update_mode: str,
    refresh_result: str,
) -> dict:
    """Returns the feed provenance for the receipt, or blocks when the age is unknown or too old."""
    database = data_dir / DATABASE_NAME
    if not database.is_file():
        raise GateError(
            f"no vulnerability database at {database}; set NVD_API_KEY and run the gate to "
            "download one"
        )
    receipt_path = data_dir / FEED_RECEIPT_NAME
    if not receipt_path.is_file():
        raise GateError(
            f"the vulnerability database at {database} has no refresh record, so its age is "
            "unknown; set NVD_API_KEY and run the gate once to refresh it"
        )
    try:
        recorded = json.loads(receipt_path.read_text(encoding="utf-8"))
        refreshed_at = datetime.strptime(recorded["refreshedAt"], "%Y-%m-%dT%H:%M:%SZ").replace(
            tzinfo=timezone.utc)
        if recorded.get("result") != "succeeded":
            raise ValueError("refresh result is not 'succeeded'")
        recorded_bytes = int(recorded["databaseBytes"])
        recorded_modified = int(recorded["databaseModifiedNs"])
    except (OSError, ValueError, KeyError, TypeError) as exc:
        raise GateError(f"the vulnerability feed refresh record {receipt_path} is unreadable: {exc}") from exc
    stat = database.stat()
    if stat.st_size != recorded_bytes or stat.st_mtime_ns != recorded_modified:
        raise GateError(
            f"the vulnerability database at {database} changed after its recorded refresh, so its "
            "age is unknown; set NVD_API_KEY and run the gate once to refresh it"
        )
    age = now - refreshed_at
    if age < -CLOCK_SKEW_TOLERANCE:
        raise GateError(
            f"the vulnerability feed refresh record is dated {_iso(refreshed_at)}, after the "
            f"current time {_iso(now)}; check the system clock"
        )
    age_hours = max(age, timedelta(0)).total_seconds() / 3600
    if age_hours > MAX_FEED_AGE_HOURS:
        raise GateError(
            f"the vulnerability database was last refreshed {age_hours / 24:.1f} days ago, on "
            f"{_iso(refreshed_at)}; releases require a refresh within {MAX_FEED_AGE_HOURS // 24} "
            "days. Set NVD_API_KEY and run the gate again"
        )
    return {
        "updateMode": update_mode,
        "refreshResult": refresh_result,
        "databaseRefreshedAt": _iso(refreshed_at),
        "ageHours": round(age_hours, 1),
        "maxAgeHours": MAX_FEED_AGE_HOURS,
        "decision": "fresh",
    }


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
    data_dir: Path | None = None,
    env: Mapping[str, str] | None = None,
    clock: Callable[[], datetime] = _utc_now,
) -> list[Path]:
    gradle_command = resolve_gradle_command(gradle_command, repo_root)
    env = os.environ if env is None else env
    data_dir = data_dir or default_data_directory(env)
    report_dir = report_dir or repo_root / "build" / "reports"
    report_paths = [report_dir / name for name in REPORT_NAMES.values()]
    nested_report_paths = [report_dir / "dependency-check" / name for name in REPORT_NAMES.values()]
    for report in (*report_paths, *nested_report_paths):
        report.unlink(missing_ok=True)
    for name in (*REPORT_NAMES.values(), RECEIPT_NAME):
        (out_dir / name).unlink(missing_ok=True)

    # Settle the database's age before any advisory is evaluated. A blocked feed leaves no
    # receipt, because nothing was scanned.
    if env.get("NVD_API_KEY"):
        refresh_feed(gradle_command, repo_root, data_dir, clock)
        feed = check_feed(data_dir, clock(), "online-refresh", "succeeded")
    else:
        feed = check_feed(data_dir, clock(), "local", "not-attempted")

    command = [
        *gradle_command,
        "--no-daemon",
        "--stacktrace",
        "dependencyCheckAggregate",
        f"-PdependencyCheckFailBuildOnCvss={BLOCKING_CVSS}",
        # The database was settled above; the scan must use exactly that data.
        "-PdependencyCheckAutoUpdate=false",
        data_directory_argument(data_dir),
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
                "schemaVersion": 2,
                "scanner": "OWASP Dependency-Check",
                "gradleTask": "dependencyCheckAggregate",
                "blockingCvss": BLOCKING_CVSS,
                "passed": result.returncode == 0,
                "scannerExitCode": result.returncode,
                "vulnerabilityFeed": feed,
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
