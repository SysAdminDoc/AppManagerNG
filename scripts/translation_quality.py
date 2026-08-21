#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Deterministic host-only translation coverage and stale-resource checker."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1
BASELINE_PATH = Path("scripts") / "translation-coverage-baseline.json"
LOCALE_RE = re.compile(r"^(?:[a-z]{2,3}(?:-r[A-Z]{2})?|b\+[A-Za-z0-9+]+)$")


def resource_names(path: Path, *, translatable_only: bool = False) -> set[str]:
    """Return direct resource names from a strings resource file."""
    try:
        root = ElementTree.parse(path).getroot()
    except (OSError, ElementTree.ParseError) as exc:
        raise ValueError(f"could not parse {path}: {exc}") from exc
    names: set[str] = set()
    for element in root:
        name = element.get("name")
        if not name:
            continue
        if translatable_only and element.get("translatable", "true") == "false":
            continue
        names.add(name)
    return names


def locale_files(res_dir: Path) -> list[tuple[str, Path]]:
    """Find language-qualified strings files, excluding night/API/size qualifiers."""
    files: list[tuple[str, Path]] = []
    for directory in sorted(res_dir.glob("values-*")):
        locale = directory.name.removeprefix("values-")
        strings_file = directory / "strings.xml"
        if LOCALE_RE.fullmatch(locale) and strings_file.is_file():
            files.append((locale, strings_file))
    return files


def snapshot(repo_root: Path) -> dict[str, Any]:
    source_file = repo_root / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    res_dir = source_file.parent.parent
    source_all = resource_names(source_file)
    source_names = resource_names(source_file, translatable_only=True)
    locales: dict[str, dict[str, Any]] = {}
    for locale, path in locale_files(res_dir):
        translated_names = resource_names(path)
        translated = len(source_names & translated_names)
        locales[locale] = {
            "translated": translated,
            "missing": len(source_names - translated_names),
            "stale": sorted(translated_names - source_all),
        }
    region_locales_without_base = []
    for locale in locales:
        match = re.fullmatch(r"([a-z]{2,3})-r[A-Z]{2}", locale)
        if match and not (res_dir / f"values-{match.group(1)}" / "strings.xml").is_file():
            region_locales_without_base.append(locale)
    return {
        "sourceCount": len(source_names),
        "sourceAllCount": len(source_all),
        "locales": locales,
        "regionLocalesWithoutBase": region_locales_without_base,
    }


def load_baseline(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"could not read translation baseline {path}: {exc}") from exc
    if not isinstance(data, dict) or data.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError(f"translation baseline {path} has an unsupported schema")
    locales = data.get("locales")
    if not isinstance(locales, dict) or not locales:
        raise ValueError(f"translation baseline {path} has no locale floors")
    for locale, entry in locales.items():
        if not isinstance(locale, str) or not isinstance(entry, dict):
            raise ValueError(f"translation baseline {path} has an invalid locale entry")
        floor = entry.get("maxMissing")
        if not isinstance(floor, int) or isinstance(floor, bool) or floor < 0:
            raise ValueError(f"translation baseline {path} has an invalid missing floor for {locale}")
    return data


def build_baseline(data: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": SCHEMA_VERSION,
        "policy": "A locale may not gain missing translatable source strings without a reviewed baseline update.",
        "sourceCount": data["sourceCount"],
        "locales": {
            locale: {"maxMissing": values["missing"]}
            for locale, values in sorted(data["locales"].items())
        },
    }


def evaluate(data: dict[str, Any], baseline: dict[str, Any]) -> tuple[list[str], dict[str, Any]]:
    problems: list[str] = []
    baseline_locales = baseline["locales"]
    coverage: dict[str, dict[str, int]] = {}
    new_missing_total = 0
    stale_total = 0
    for locale, values in sorted(data["locales"].items()):
        floor_entry = baseline_locales.get(locale, {})
        floor = floor_entry.get("maxMissing", 0)
        missing = values["missing"]
        newly_missing = max(0, missing - floor)
        new_missing_total += newly_missing
        stale_total += len(values["stale"])
        coverage[f"values-{locale}"] = {
            "translated": values["translated"],
            "missing": missing,
            "baselineMissing": floor,
            "newMissing": newly_missing,
        }
        if newly_missing:
            problems.append(
                f"values-{locale}: {newly_missing} newly missing source string(s) "
                f"({missing} missing, reviewed floor {floor})"
            )
        if values["stale"]:
            problems.append(
                f"values-{locale}: {len(values['stale'])} stale string(s) not present in source"
            )
    absent_locales = sorted(set(baseline_locales) - set(data["locales"]))
    if absent_locales:
        problems.append("baseline locale directories are missing: " + ", ".join(absent_locales))
    for locale in data.get("regionLocalesWithoutBase", []):
        language = locale.split("-r", maxsplit=1)[0]
        problems.append(
            f"values-{locale}: region-specific strings have no values-{language}/strings.xml fallback"
        )
    summary = {
        "schemaVersion": SCHEMA_VERSION,
        "sourceStrings": data["sourceCount"],
        "localeCount": len(data["locales"]),
        "newMissing": new_missing_total,
        "staleStrings": stale_total,
        "regionLocalesWithoutBase": len(data.get("regionLocalesWithoutBase", [])),
        "coverage": coverage,
    }
    return problems, summary


def pseudo_locales_enabled(repo_root: Path) -> bool:
    build_file = repo_root / "app" / "build.gradle"
    try:
        text = build_file.read_text(encoding="utf-8")
    except OSError as exc:
        raise ValueError(f"could not read {build_file}: {exc}") from exc
    return re.search(r"pseudoLocalesEnabled\s*=\s*true", text) is not None


def print_report(data: dict[str, Any], summary: dict[str, Any], problems: list[str]) -> None:
    source_count = data["sourceCount"]
    print(f"Source strings: {source_count}")
    print("")
    print(f"Checked {len(data['locales'])} locale directories.")
    print("")
    print("=== Coverage Report (bottom 5) ===")
    rows = sorted(
        summary["coverage"].items(),
        key=lambda item: (item[1]["translated"], item[0]),
    )[:5]
    for locale, values in rows:
        percent = values["translated"] * 100 // source_count if source_count else 0
        print(f"  {locale}: {values['translated']} / {source_count} ({percent}%)")
    print("")
    if problems:
        for problem in problems:
            print(f"::error::{problem}")
        print("FAILED: translation quality gate.")
    else:
        print("PASSED: no new missing source strings or stale locale strings detected.")
    print("TRANSLATION_SUMMARY=" + json.dumps(summary, sort_keys=True, separators=(",", ":")))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--baseline", type=Path, default=None)
    parser.add_argument("--write-baseline", action="store_true")
    args = parser.parse_args(argv)
    repo_root = args.repo_root.resolve()
    baseline_path = args.baseline or repo_root / BASELINE_PATH
    try:
        data = snapshot(repo_root)
        if not pseudo_locales_enabled(repo_root):
            raise ValueError("pseudoLocalesEnabled is not set to true in app/build.gradle")
        if args.write_baseline:
            baseline = build_baseline(data)
            baseline_path.parent.mkdir(parents=True, exist_ok=True)
            baseline_path.write_text(json.dumps(baseline, indent=2) + "\n", encoding="utf-8")
            print(f"Wrote translation coverage baseline: {baseline_path}")
            return 0
        baseline = load_baseline(baseline_path)
        problems, summary = evaluate(data, baseline)
        print_report(data, summary, problems)
        return 1 if problems else 0
    except ValueError as exc:
        print(f"::error::{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
