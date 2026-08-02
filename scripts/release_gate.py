#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""One fail-closed local release gate for AppManagerNG.

Every release check the project owns runs here, in one command, in one order, and a release
is blocked unless all of them pass:

  source        the working tree is clean and HEAD is the exact commit being released
  consistency   every version-bearing surface agrees (scripts/verify-release-consistency.sh)
  floor         pinned dependencies have not drifted past their ceiling
  translation   no source-string regressions, and the reported counts are internally sane
  tests         the host unit-test suite passes
  lint          no lint issue outside the baseline, and the baseline carries no stale entries
  reproducible  two clean builds produce byte-identical APKs, plus SBOM, native alignment,
                and the blocking OWASP CVE gate (scripts/verify_reproducible_release.sh)
  artifact      the published APK really is this package, version, SDK level, and signer

The gate then emits a receipt binding the released commit and tag to the SHA-256 of every
artifact and report it produced, the signing certificate fingerprint, and the versions of the
tools that produced them. The receipt is written last and only on success, so a receipt can
never describe a build that did not pass.

There is no hosted CI in this project; this script is the release process.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Sequence

RECEIPT_SCHEMA_VERSION = 3
DEFAULT_OUT_DIR = Path("build") / "release-gate"
LINT_VARIANT = "flossRelease"


class GateError(RuntimeError):
    """A gate stage refused the release."""


# --------------------------------------------------------------------------------------
# Pure helpers (no subprocesses, no filesystem side effects) — these carry the policy.
# --------------------------------------------------------------------------------------


@dataclass(frozen=True)
class LintIssue:
    """A lint finding identified the way a baseline matches it: never by line number."""

    issue_id: str
    file: str
    message: str


@dataclass
class StageResult:
    name: str
    passed: bool
    detail: str = ""
    data: dict = field(default_factory=dict)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _collapse(path: str) -> str:
    """Resolves ``.``/``..`` segments textually, keeping forward slashes on every platform."""
    leading = "/" if path.startswith("/") else ""
    drive = ""
    if len(path) > 1 and path[1] == ":":
        drive, path = path[:2], path[2:]
        leading = "/" if path.startswith("/") else ""
    parts: list[str] = []
    for segment in path.split("/"):
        if not segment or segment == ".":
            continue
        if segment == ".." and parts and parts[-1] != "..":
            parts.pop()
            continue
        parts.append(segment)
    return drive + leading + "/".join(parts)


def module_prefixes(repo_root: Path | None) -> tuple[str, ...]:
    """Gradle module directories, longest first, read from ``settings.gradle``."""
    if repo_root is None:
        return ()
    settings = repo_root / "settings.gradle"
    if not settings.is_file():
        return ()
    prefixes = [
        match.replace(":", "/").strip("/")
        for match in re.findall(r"include\s+'([^']+)'", settings.read_text(encoding="utf-8"))
    ]
    return tuple(sorted((p for p in prefixes if p), key=len, reverse=True))


def _canonical_lint_path(raw: str, repo_root: Path | None, prefixes: Sequence[str],
                         baseline_module: str = "app") -> str:
    """Puts a lint location on one footing so the two sides can be compared.

    A lint results report records locations absolutely. A baseline records them relative to the
    module the baseline file lives in (``app/``), even for findings that belong to another module,
    which is why entries reach out through ``../libcore/ui/...`` or even past the repository root
    into the Gradle cache. Comparing the two verbatim makes every baselined issue look new — the
    exact failure this gate exists to catch — so both are reduced to a repository-relative path
    and then stripped of their owning module prefix.
    """
    path = raw.replace("\\", "/").strip()
    if not path:
        return ""
    is_absolute = path.startswith("/") or (len(path) > 1 and path[1] == ":")
    if is_absolute:
        if repo_root is None:
            return _collapse(path)
        try:
            path = os.path.relpath(path, str(repo_root)).replace("\\", "/")
        except ValueError:
            # A different drive; there is no relative form, so keep it absolute.
            return _collapse(path)
    elif baseline_module:
        path = f"{baseline_module}/{path}"
    path = _collapse(path)
    for prefix in prefixes:
        if path.startswith(prefix + "/"):
            return path[len(prefix) + 1:]
    return path


def parse_lint_issues(xml_text: str, repo_root: Path | None = None) -> list[LintIssue]:
    """Reads either a lint results report or a lint baseline into comparable findings."""
    try:
        root = ElementTree.fromstring(xml_text)
    except ElementTree.ParseError as exc:
        raise GateError(f"could not parse lint XML: {exc}") from exc
    prefixes = module_prefixes(repo_root)
    issues: list[LintIssue] = []
    for element in root.iter("issue"):
        location = element.find("location")
        raw_file = location.get("file", "") if location is not None else ""
        issues.append(
            LintIssue(
                issue_id=element.get("id", ""),
                file=_canonical_lint_path(raw_file, repo_root, prefixes),
                message=(element.get("message", "") or "").strip(),
            )
        )
    return issues


def _multiset(issues: Iterable[LintIssue]) -> dict[LintIssue, int]:
    counts: dict[LintIssue, int] = {}
    for issue in issues:
        counts[issue] = counts.get(issue, 0) + 1
    return counts


def diff_lint(current: Sequence[LintIssue], baseline: Sequence[LintIssue]) -> tuple[list[LintIssue], list[LintIssue]]:
    """Splits the current findings against the baseline.

    Returns ``(new_issues, stale_entries)``. Both sides are compared as multisets so a baseline
    that records an issue three times does not silently absorb a fourth occurrence.
    """
    current_counts = _multiset(current)
    baseline_counts = _multiset(baseline)
    new_issues: list[LintIssue] = []
    stale_entries: list[LintIssue] = []
    for issue, count in current_counts.items():
        surplus = count - baseline_counts.get(issue, 0)
        new_issues.extend([issue] * max(0, surplus))
    for issue, count in baseline_counts.items():
        surplus = count - current_counts.get(issue, 0)
        stale_entries.extend([issue] * max(0, surplus))
    return new_issues, stale_entries


def prune_baseline_xml(xml_text: str, current: Sequence[LintIssue],
                       repo_root: Path | None = None) -> tuple[str, int]:
    """Drops baseline entries that the current run no longer reports.

    Returns the rewritten document and the number of entries removed. An entry is kept while the
    current run still reports it, counted so duplicates are pruned one at a time.
    """
    try:
        root = ElementTree.fromstring(xml_text)
    except ElementTree.ParseError as exc:
        raise GateError(f"could not parse the lint baseline: {exc}") from exc
    prefixes = module_prefixes(repo_root)
    remaining = _multiset(current)
    removed = 0
    for element in list(root.findall("issue")):
        location = element.find("location")
        raw_file = location.get("file", "") if location is not None else ""
        issue = LintIssue(
            issue_id=element.get("id", ""),
            file=_canonical_lint_path(raw_file, repo_root, prefixes),
            message=(element.get("message", "") or "").strip(),
        )
        if remaining.get(issue, 0) > 0:
            remaining[issue] -= 1
            continue
        root.remove(element)
        removed += 1
    if removed == 0:
        return xml_text, 0
    body = ElementTree.tostring(root, encoding="unicode")
    return '<?xml version="1.0" encoding="UTF-8"?>\n' + body + "\n", removed


_TRANSLATION_SOURCE_RE = re.compile(r"^Source strings:\s*(\d+)\s*$", re.MULTILINE)
_TRANSLATION_COVERAGE_RE = re.compile(r"^\s+(\S+):\s*(\d+)\s*/\s*(\d+)\s*\((\d+)%\)\s*$", re.MULTILINE)
_TRANSLATION_SUMMARY_RE = re.compile(r"^TRANSLATION_SUMMARY=(\{.*\})$", re.MULTILINE)


def validate_translation_output(output: str) -> list[str]:
    """Rejects a translation report whose own numbers do not add up.

    The gate exists because a silently-empty extraction reports "0 strings, everything covered",
    which reads as a pass. Counts that disagree with each other are treated as a failure of the
    check itself, not as a clean result.
    """
    problems: list[str] = []
    source_match = _TRANSLATION_SOURCE_RE.search(output)
    if source_match is None:
        return ["translation report did not state a source string count"]
    source_count = int(source_match.group(1))
    if source_count <= 0:
        problems.append("translation report counted no source strings at all")
    coverage_rows = _TRANSLATION_COVERAGE_RE.findall(output)
    if not coverage_rows:
        problems.append("translation report listed no locale coverage rows")
    for locale, counted, total, percent in coverage_rows:
        counted_int, total_int, percent_int = int(counted), int(total), int(percent)
        if total_int != source_count:
            problems.append(
                f"{locale}: coverage denominator {total_int} != source string count {source_count}"
            )
        if counted_int > total_int:
            problems.append(f"{locale}: {counted_int} translated strings exceeds {total_int} source strings")
        if not 0 <= percent_int <= 100:
            problems.append(f"{locale}: coverage percentage {percent_int} is out of range")
    return problems


def parse_translation_summary(output: str) -> dict:
    """Read the machine-readable coverage counts emitted by the translation checker."""
    match = _TRANSLATION_SUMMARY_RE.search(output)
    if match is None:
        raise GateError("translation report did not emit a machine-readable summary")
    try:
        summary = json.loads(match.group(1))
    except json.JSONDecodeError as exc:
        raise GateError(f"translation summary is not valid JSON: {exc}") from exc
    if not isinstance(summary, dict):
        raise GateError("translation summary is not an object")
    for key in ("sourceStrings", "localeCount", "newMissing", "staleStrings"):
        if not isinstance(summary.get(key), int) or isinstance(summary[key], bool):
            raise GateError(f"translation summary has no integer {key}")
    if summary["newMissing"] or summary["staleStrings"]:
        raise GateError("translation summary reports unhandled coverage or stale-string findings")
    return summary


_BADGING_PACKAGE_RE = re.compile(
    r"package:\s*name='([^']+)'\s+versionCode='(\d+)'\s+versionName='([^']*)'"
)
# aapt2 emits "minSdkVersion:'21'"; older aapt wrote it as "sdkVersion:'21'".
_BADGING_SDK_RE = re.compile(r"^(?:min)?[sS]dkVersion:'(\d+)'", re.MULTILINE)
_BADGING_TARGET_SDK_RE = re.compile(r"^targetSdkVersion:'(\d+)'", re.MULTILINE)


def parse_badging(output: str) -> dict:
    """Extracts the identity an installer would see from ``aapt2 dump badging``."""
    package_match = _BADGING_PACKAGE_RE.search(output)
    if package_match is None:
        raise GateError("aapt2 badging output carried no package identity line")
    sdk_match = _BADGING_SDK_RE.search(output)
    target_match = _BADGING_TARGET_SDK_RE.search(output)
    return {
        "packageName": package_match.group(1),
        "versionCode": int(package_match.group(2)),
        "versionName": package_match.group(3),
        "minSdk": int(sdk_match.group(1)) if sdk_match else None,
        "targetSdk": int(target_match.group(1)) if target_match else None,
    }


def check_artifact_identity(actual: dict, expected: dict) -> list[str]:
    """Compares a built APK against the identity the sources declare."""
    problems: list[str] = []
    for key, label in (
        ("packageName", "package name"),
        ("versionCode", "versionCode"),
        ("versionName", "versionName"),
        ("minSdk", "minSdk"),
        ("targetSdk", "targetSdk"),
    ):
        want = expected.get(key)
        got = actual.get(key)
        if want is None:
            continue
        if got is None:
            problems.append(f"APK does not declare a {label}; expected {want}")
        elif str(got) != str(want):
            problems.append(f"APK {label} is {got}, expected {want}")
    return problems


_CERT_SHA256_RE = re.compile(r"SHA-256 digest:\s*([0-9a-fA-F]{64})")


def parse_signing_certificate(output: str) -> str | None:
    """Reads the signer certificate SHA-256 out of ``apksigner verify --print-certs``."""
    match = _CERT_SHA256_RE.search(output)
    return match.group(1).lower() if match else None


# --------------------------------------------------------------------------------------
# Environment / process helpers
# --------------------------------------------------------------------------------------


WINDOWS_BASH_CANDIDATES = (
    Path("C:/Program Files/Git/bin/bash.exe"),
    Path("C:/Program Files (x86)/Git/bin/bash.exe"),
)


def resolve_bash() -> str:
    """Picks the shell the repo's gate scripts are written for.

    On Windows a bare ``bash`` on PATH is usually WSL, which runs a whole separate Linux
    toolchain — a different python3, a different gradle, different file paths. A release gate
    must not silently change which toolchain produced its evidence, so Git for Windows' bash is
    preferred when it is installed.
    """
    if os.name == "nt":
        for candidate in WINDOWS_BASH_CANDIDATES:
            if candidate.is_file():
                return str(candidate)
    return "bash"


def bash_script(repo_root: Path, script: str) -> list[str]:
    """Invokes a repo script through bash by a path relative to the repo root.

    The path stays relative: an absolute Windows path handed to a bash that resolves paths
    differently (MSYS vs WSL) may not resolve at all, whereas a relative path needs no
    conversion on any platform.
    """
    del repo_root  # the caller always runs the process with repo_root as its working directory
    return [resolve_bash(), script]


def run(command: Sequence[str], cwd: Path, capture: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        list(command),
        cwd=str(cwd),
        check=False,
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.STDOUT if capture else None,
    )


def read_expected_identity(repo_root: Path) -> dict:
    build_gradle = (repo_root / "app" / "build.gradle").read_text(encoding="utf-8")
    versions_gradle = (repo_root / "versions.gradle").read_text(encoding="utf-8")

    def pick(pattern: str, text: str, label: str) -> str:
        match = re.search(pattern, text)
        if match is None:
            raise GateError(f"could not read {label} from the build scripts")
        return match.group(1)

    return {
        "packageName": pick(r"""applicationId\s*=\s*['"]([^'"]+)['"]""", build_gradle, "applicationId"),
        "versionName": pick(r"""versionName\s*=\s*['"]([^'"]+)['"]""", build_gradle, "versionName"),
        "versionCode": int(pick(r"versionCode\s*=\s*(\d+)", build_gradle, "versionCode")),
        "minSdk": int(pick(r"min_sdk\s*=\s*(\d+)", versions_gradle, "min_sdk")),
        "targetSdk": int(pick(r"target_sdk\s*=\s*(\d+)", versions_gradle, "target_sdk")),
    }


def find_build_tool(name: str) -> Path | None:
    """Locates an Android build-tool, preferring the newest installed build-tools release."""
    candidates: list[Path] = []
    sdk_roots = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    default_sdk = Path.home() / "AppData" / "Local" / "Android" / "Sdk"
    if default_sdk.is_dir():
        sdk_roots.append(str(default_sdk))
    posix_sdk = Path.home() / "Android" / "Sdk"
    if posix_sdk.is_dir():
        sdk_roots.append(str(posix_sdk))
    for root in sdk_roots:
        if not root:
            continue
        build_tools = Path(root) / "build-tools"
        if not build_tools.is_dir():
            continue
        for version_dir in sorted(build_tools.iterdir(), reverse=True):
            for suffix in ("", ".exe", ".bat"):
                candidate = version_dir / f"{name}{suffix}"
                if candidate.is_file():
                    candidates.append(candidate)
            if candidates:
                break
        if candidates:
            break
    if candidates:
        return candidates[0]
    found = shutil.which(name)
    return Path(found) if found else None


def tool_versions(repo_root: Path, gradle_cmd: str) -> dict:
    versions = {
        "python": platform.python_version(),
        "platform": f"{platform.system()} {platform.release()}",
    }
    wrapper = repo_root / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if wrapper.is_file():
        match = re.search(r"gradle-([0-9.]+)-bin\.zip", wrapper.read_text(encoding="utf-8"))
        if match:
            versions["gradle"] = match.group(1)
    versions_gradle = repo_root / "versions.gradle"
    if versions_gradle.is_file():
        text = versions_gradle.read_text(encoding="utf-8")
        for key, label in (("agp_version", "agp"), ("ndk_version", "ndk")):
            match = re.search(rf"{key}\s*=\s*['\"]([^'\"]+)['\"]", text)
            if match:
                versions[label] = match.group(1)
    java_home = os.environ.get("JAVA_HOME")
    java_binary = Path(java_home) / "bin" / "java" if java_home else None
    java_cmd = str(java_binary) if java_binary and java_binary.exists() else "java"
    result = run([java_cmd, "-version"], repo_root)
    if result.returncode == 0 and result.stdout:
        match = re.search(r'version "([^"]+)"', result.stdout)
        if match:
            versions["java"] = match.group(1)
    for tool in ("aapt2", "apksigner"):
        located = find_build_tool(tool)
        if located is not None:
            versions[tool] = str(located)
    del gradle_cmd  # recorded through the wrapper version above
    return versions


# --------------------------------------------------------------------------------------
# Stages
# --------------------------------------------------------------------------------------


def stage_source(repo_root: Path, tag: str | None, allow_dirty: bool) -> StageResult:
    status = run(["git", "status", "--porcelain"], repo_root)
    if status.returncode != 0:
        raise GateError(f"git status failed: {status.stdout}")
    dirty = [line for line in status.stdout.splitlines() if line.strip()]
    if dirty and not allow_dirty:
        listing = "\n  ".join(dirty[:20])
        raise GateError(
            "the working tree is not clean, so the receipt could not describe what was built:\n  "
            + listing
        )
    head = run(["git", "rev-parse", "HEAD"], repo_root)
    if head.returncode != 0:
        raise GateError("could not resolve HEAD")
    head_sha = head.stdout.strip()
    data = {"head": head_sha, "clean": not dirty}
    if tag:
        resolved = run(["git", "rev-list", "-n", "1", tag], repo_root)
        if resolved.returncode != 0:
            raise GateError(f"tag {tag} does not exist")
        if resolved.stdout.strip() != head_sha:
            raise GateError(
                f"tag {tag} points at {resolved.stdout.strip()[:12]}, not the checked-out HEAD "
                f"{head_sha[:12]}; the release would not be built from the tagged source"
            )
        data["tag"] = tag
    return StageResult("source", True, f"HEAD {head_sha[:12]}", data)


def stage_script(name: str, repo_root: Path, script: str, extra: Sequence[str] = ()) -> StageResult:
    result = run([*bash_script(repo_root, script), *extra], repo_root)
    if result.returncode != 0:
        raise GateError(f"{name} failed:\n{result.stdout}")
    return StageResult(name, True, f"{script} passed", {"output": result.stdout})


def stage_translation(repo_root: Path) -> StageResult:
    result = run(bash_script(repo_root, "scripts/verify-translation-quality.sh"), repo_root)
    if result.returncode != 0:
        raise GateError(f"translation quality gate failed:\n{result.stdout}")
    problems = validate_translation_output(result.stdout)
    if problems:
        raise GateError("translation report is malformed:\n  " + "\n  ".join(problems))
    summary = parse_translation_summary(result.stdout)
    return StageResult(
        "translation",
        True,
        f"{summary['sourceStrings']} source strings across {summary['localeCount']} locales; no new gaps",
        summary,
    )


def stage_tests(repo_root: Path, gradle_cmd: str) -> StageResult:
    result = run([gradle_cmd, ":app:testFlossDebugUnitTest", ":app:testFullDebugUnitTest"], repo_root)
    if result.returncode != 0:
        raise GateError(f"host unit tests failed:\n{result.stdout[-8000:]}")
    total, failed = 0, 0
    for report in (repo_root / "app" / "build" / "test-results").rglob("TEST-*.xml"):
        header = report.read_text(encoding="utf-8", errors="replace")[:2000]
        for pattern, target in ((r'tests="(\d+)"', "total"), (r'failures="(\d+)"', "failed"), (r'errors="(\d+)"', "failed")):
            match = re.search(pattern, header)
            if not match:
                continue
            if target == "total":
                total += int(match.group(1))
            else:
                failed += int(match.group(1))
    if failed:
        raise GateError(f"{failed} host test(s) failed")
    if total <= 0:
        raise GateError("the test task reported success but produced no test results")
    return StageResult("tests", True, f"{total} tests passed", {"tests": total})


def stage_lint(repo_root: Path, gradle_cmd: str, refresh: bool) -> StageResult:
    """Runs lint against the full issue set, then judges it against the baseline ourselves.

    Lint is deliberately run with the committed baseline moved aside. Letting lint apply the
    baseline hides the entries that no longer match, so a baseline can rot indefinitely; running
    unfiltered answers both questions from a single analysis — what is new, and what is stale.
    Lint regenerates a baseline of its own while ours is out of the way, and that regenerated file
    is what ``--refresh-lint-baseline`` installs, so the committed baseline is always something
    lint itself wrote rather than something this script edited into shape.
    """
    baseline_path = repo_root / "app" / "lint-baseline.xml"
    if not baseline_path.is_file():
        raise GateError("app/lint-baseline.xml is missing; refusing to accept an unbaselined lint run")
    baseline_text = baseline_path.read_text(encoding="utf-8")
    report_path = repo_root / "app" / "build" / "reports" / f"lint-results-{LINT_VARIANT}.xml"
    stashed = baseline_path.with_suffix(".xml.gate-stash")
    report_path.unlink(missing_ok=True)
    shutil.move(str(baseline_path), str(stashed))
    regenerated: str | None = None
    try:
        run([gradle_cmd, f":app:lint{LINT_VARIANT[0].upper()}{LINT_VARIANT[1:]}"], repo_root)
        if not report_path.is_file():
            raise GateError(f"lint produced no XML report at {report_path}")
        current = parse_lint_issues(report_path.read_text(encoding="utf-8", errors="replace"), repo_root)
        if baseline_path.is_file():
            regenerated = baseline_path.read_text(encoding="utf-8")
    finally:
        # Always restore the committed baseline before deciding anything about it.
        baseline_path.unlink(missing_ok=True)
        if stashed.exists():
            shutil.move(str(stashed), str(baseline_path))
    baseline = parse_lint_issues(baseline_text, repo_root)
    new_issues, stale_entries = diff_lint(current, baseline)
    if not new_issues and not stale_entries:
        return StageResult(
            "lint",
            True,
            f"{len(current)} issue(s), all baselined, no stale entries",
            {"issues": len(current)},
        )
    if refresh:
        if regenerated is None:
            raise GateError("lint did not regenerate a baseline; cannot refresh")
        baseline_path.write_text(regenerated, encoding="utf-8", newline="\n")
        raise GateError(
            f"app/lint-baseline.xml was out of date ({len(new_issues)} unbaselined, "
            f"{len(stale_entries)} stale) and has been rewritten from this lint run. "
            "Review the diff, confirm nothing genuinely new was absorbed, commit it, "
            "then re-run the gate."
        )
    if new_issues:
        preview = "\n  ".join(
            f"{issue.issue_id} {issue.file}: {issue.message[:120]}" for issue in new_issues[:15]
        )
        raise GateError(
            f"{len(new_issues)} lint issue(s) are not in the baseline "
            f"(and {len(stale_entries)} baseline entry/entries are stale):\n  {preview}"
        )
    raise GateError(
        f"app/lint-baseline.xml carries {len(stale_entries)} stale entry/entries; "
        "re-run with --refresh-lint-baseline to bring it back in sync"
    )


def stage_reproducible(repo_root: Path, gradle_cmd: str, out_dir: Path) -> StageResult:
    # Matches scripts/verify_reproducible_release.sh: outside build/, which each clean empties.
    env_out = repo_root / "reproducible-release"
    result = run(
        bash_script(repo_root, "scripts/verify_reproducible_release.sh"),
        repo_root,
    )
    if result.returncode != 0:
        raise GateError(f"reproducible release verification failed:\n{result.stdout[-8000:]}")
    publish_dir = env_out / "publish"
    if not publish_dir.is_dir():
        raise GateError("the reproducible build produced no publish directory")
    artifacts = sorted(p for p in publish_dir.iterdir() if p.is_file())
    if not any(p.suffix == ".apk" for p in artifacts):
        raise GateError("the reproducible build published no APK")
    out_dir.mkdir(parents=True, exist_ok=True)
    recorded = []
    for artifact in artifacts:
        recorded.append(
            {"name": artifact.name, "size": artifact.stat().st_size, "sha256": sha256_file(artifact)}
        )
    return StageResult(
        "reproducible",
        True,
        f"{len(recorded)} artifact(s) reproduced byte-for-byte",
        {"artifacts": recorded, "publishDir": str(publish_dir)},
    )


def stage_artifact(repo_root: Path, apk_paths: Sequence[Path], expected: dict,
                   expected_cert: str | None) -> StageResult:
    if not apk_paths:
        raise GateError("no APK was available to verify")
    aapt2 = find_build_tool("aapt2")
    if aapt2 is None:
        raise GateError("aapt2 was not found; the APK identity cannot be verified")
    apksigner = find_build_tool("apksigner")
    if apksigner is None:
        raise GateError("apksigner was not found; the signing certificate cannot be verified")
    certificates: set[str] = set()
    verified = []
    for apk in apk_paths:
        badging = run([str(aapt2), "dump", "badging", str(apk)], repo_root)
        if badging.returncode != 0:
            raise GateError(f"aapt2 could not read {apk.name}:\n{badging.stdout}")
        identity = parse_badging(badging.stdout)
        problems = check_artifact_identity(identity, expected)
        if problems:
            raise GateError(f"{apk.name} is not the artifact these sources describe:\n  " + "\n  ".join(problems))
        signer = run([str(apksigner), "verify", "--print-certs", str(apk)], repo_root)
        if signer.returncode != 0:
            raise GateError(f"{apk.name} failed signature verification:\n{signer.stdout}")
        fingerprint = parse_signing_certificate(signer.stdout)
        if fingerprint is None:
            raise GateError(f"{apk.name} reported no signing certificate digest")
        if expected_cert and fingerprint != expected_cert.lower():
            raise GateError(
                f"{apk.name} is signed by {fingerprint}, not the expected {expected_cert.lower()}"
            )
        certificates.add(fingerprint)
        verified.append({"name": apk.name, "signingCertificateSha256": fingerprint, **identity})
    if len(certificates) > 1:
        raise GateError(f"the published APKs do not share one signer: {sorted(certificates)}")
    return StageResult(
        "artifact",
        True,
        f"{len(verified)} APK(s) match the declared identity and signer",
        {"apks": verified, "signingCertificateSha256": next(iter(certificates))},
    )


# --------------------------------------------------------------------------------------
# Orchestration
# --------------------------------------------------------------------------------------


ALL_STAGES = ("source", "consistency", "floor", "translation", "tests", "lint", "reproducible", "artifact")


def collect_release_apks(repo_root: Path, explicit: Sequence[str] | None) -> list[Path]:
    """The APKs whose identity is checked, preferring the ones the release actually publishes."""
    if explicit:
        paths = [Path(item) if Path(item).is_absolute() else repo_root / item for item in explicit]
        missing = [str(path) for path in paths if not path.is_file()]
        if missing:
            raise GateError("these APKs do not exist: " + ", ".join(missing))
        return paths
    publish_dir = repo_root / "reproducible-release" / "publish"
    if publish_dir.is_dir():
        published = sorted(publish_dir.glob("*.apk"))
        if published:
            return published
    # Fallback so the identity check can be run on its own against an ordinary release build.
    return sorted((repo_root / "app" / "build" / "outputs" / "apk").glob("*/release/*.apk"))


def build_receipt(repo_root: Path, results: Sequence[StageResult], expected: dict,
                  tag: str | None, generated_at: str | None, gradle_cmd: str) -> dict:
    by_name = {result.name: result for result in results}
    source = by_name.get("source")
    reproducible = by_name.get("reproducible")
    artifact = by_name.get("artifact")
    receipt = {
        "schemaVersion": RECEIPT_SCHEMA_VERSION,
        "gate": "scripts/release_gate.py",
        "generatedAt": generated_at,
        "source": {
            "head": source.data.get("head") if source else None,
            "tag": tag,
            "clean": source.data.get("clean") if source else None,
        },
        "identity": expected,
        "stages": [
            {
                "name": result.name,
                "passed": result.passed,
                "detail": result.detail,
                **({"data": result.data} if result.data else {}),
            }
            for result in results
        ],
        "artifacts": reproducible.data.get("artifacts", []) if reproducible else [],
        "apks": artifact.data.get("apks", []) if artifact else [],
        "signingCertificateSha256": artifact.data.get("signingCertificateSha256") if artifact else None,
        "tools": tool_versions(repo_root, gradle_cmd),
    }
    return receipt


def main(argv: Sequence[str] | None = None) -> int:
    repo_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tag", help="release tag that must resolve to the checked-out HEAD")
    parser.add_argument("--gradle-cmd", default=str(repo_root / ("gradlew.bat" if os.name == "nt" else "gradlew")))
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT_DIR)
    parser.add_argument("--expected-signing-cert", help="SHA-256 the release APKs must be signed by")
    parser.add_argument("--apk", action="append", help="APK to identity-check (repeatable); "
                                                      "defaults to the reproducible publish set")
    parser.add_argument("--generated-at", help="timestamp recorded in the receipt (ISO-8601)")
    parser.add_argument(
        "--only",
        action="append",
        choices=ALL_STAGES,
        help="run only these stages (repeatable); the receipt is still written from what ran",
    )
    parser.add_argument(
        "--skip",
        action="append",
        choices=ALL_STAGES,
        default=[],
        help="skip a stage; a receipt from a skipped-stage run records exactly which ran",
    )
    parser.add_argument(
        "--refresh-lint-baseline",
        action="store_true",
        help="reinstall the baseline lint regenerated during this run, then stop for review",
    )
    parser.add_argument("--allow-dirty", action="store_true", help="maintainer iteration only; never for a release")
    args = parser.parse_args(sys.argv[1:] if argv is None else argv)

    selected = [stage for stage in (args.only or ALL_STAGES) if stage not in args.skip]
    out_dir = args.out_dir if args.out_dir.is_absolute() else repo_root / args.out_dir
    results: list[StageResult] = []

    try:
        expected = read_expected_identity(repo_root)
        print(f"Releasing {expected['packageName']} {expected['versionName']} ({expected['versionCode']})")
        print(f"Stages: {', '.join(selected)}\n")
        for stage in selected:
            print(f"--- {stage} ---", flush=True)
            if stage == "source":
                result = stage_source(repo_root, args.tag, args.allow_dirty)
            elif stage == "consistency":
                result = stage_script("consistency", repo_root, "scripts/verify-release-consistency.sh")
            elif stage == "floor":
                result = stage_script("floor", repo_root, "scripts/verify-dependency-floor.sh")
            elif stage == "translation":
                result = stage_translation(repo_root)
            elif stage == "tests":
                result = stage_tests(repo_root, args.gradle_cmd)
            elif stage == "lint":
                result = stage_lint(repo_root, args.gradle_cmd, args.refresh_lint_baseline)
            elif stage == "reproducible":
                result = stage_reproducible(repo_root, args.gradle_cmd, out_dir)
            elif stage == "artifact":
                result = stage_artifact(repo_root, collect_release_apks(repo_root, args.apk),
                                        expected, args.expected_signing_cert)
            else:  # pragma: no cover - argparse constrains the choices
                raise GateError(f"unknown stage {stage}")
            results.append(result)
            print(f"PASS: {result.detail}\n", flush=True)
    except GateError as exc:
        print(f"\nRELEASE BLOCKED: {exc}", file=sys.stderr)
        return 1

    receipt = build_receipt(repo_root, results, expected, args.tag, args.generated_at, args.gradle_cmd)
    out_dir.mkdir(parents=True, exist_ok=True)
    receipt_path = out_dir / "release-gate-receipt.json"
    receipt_path.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(f"PASSED: {len(results)} stage(s). Receipt: {receipt_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
