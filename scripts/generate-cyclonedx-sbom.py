#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later

"""Generate a CycloneDX SBOM from checked Gradle lockfiles."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import uuid
from dataclasses import dataclass
from urllib.parse import quote


PROJECT_NAME = "AppManagerNG"
SPEC_VERSION = "1.6"
DEFAULT_OUTPUT = pathlib.Path(
    "build/reproducible-release/publish/AppManagerNG-reproducible.cdx.json"
)
APP_COMPONENT_REF_PREFIX = "pkg:generic/AppManagerNG@"


@dataclass(frozen=True, order=True)
class Module:
    group: str
    name: str
    version: str

    @property
    def purl(self) -> str:
        group = quote(self.group, safe="._-")
        name = quote(self.name, safe="._-")
        version = quote(self.version, safe="._-+")
        return f"pkg:maven/{group}/{name}@{version}"


def run_git(root: pathlib.Path, *args: str) -> str | None:
    try:
        result = subprocess.run(
            ["git", *args],
            cwd=root,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
        )
    except (OSError, subprocess.CalledProcessError):
        return None
    return result.stdout.strip()


def tracked_lockfiles(root: pathlib.Path) -> list[pathlib.Path]:
    listing = run_git(root, "ls-files", "buildscript-gradle.lockfile", "*gradle.lockfile")
    if listing:
        return sorted(root / pathlib.Path(line) for line in listing.splitlines() if line)

    candidates = list(root.rglob("*gradle.lockfile"))
    buildscript = root / "buildscript-gradle.lockfile"
    if buildscript.is_file():
        candidates.append(buildscript)
    return sorted(
        candidate
        for candidate in candidates
        if ".gradle" not in candidate.parts and "build" not in candidate.parts
    )


def parse_lockfiles(lockfiles: list[pathlib.Path]) -> list[Module]:
    modules: set[Module] = set()
    for lockfile in lockfiles:
        for raw_line in lockfile.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            coordinate = line.split("=", 1)[0]
            parts = coordinate.split(":")
            if len(parts) != 3:
                continue
            group, name, version = parts
            if group and name and version:
                modules.add(Module(group, name, version))
    return sorted(modules)


def read_project_version(root: pathlib.Path) -> str:
    build_gradle = root / "app" / "build.gradle"
    text = build_gradle.read_text(encoding="utf-8")
    match = re.search(r"versionName\s*=\s*['\"]([^'\"]+)['\"]", text)
    if not match:
        raise SystemExit("Could not read app versionName from app/build.gradle")
    return match.group(1)


def commit_timestamp(root: pathlib.Path) -> str:
    timestamp = run_git(root, "show", "-s", "--format=%cI", "HEAD")
    if timestamp:
        return timestamp.replace("+00:00", "Z")
    return dt.datetime(1970, 1, 1, tzinfo=dt.timezone.utc).isoformat().replace("+00:00", "Z")


def app_component_ref(version: str) -> str:
    return f"{APP_COMPONENT_REF_PREFIX}{quote(version, safe='._-+')}"


def serial_number_for(version: str, modules: list[Module]) -> str:
    digest = hashlib.sha256()
    digest.update(PROJECT_NAME.encode("utf-8"))
    digest.update(b"\0")
    digest.update(version.encode("utf-8"))
    for module in modules:
        digest.update(b"\0")
        digest.update(module.purl.encode("utf-8"))
    generated = uuid.uuid5(uuid.NAMESPACE_URL, digest.hexdigest())
    return f"urn:uuid:{generated}"


def generate_bom(root: pathlib.Path, version: str, lockfiles: list[pathlib.Path]) -> dict:
    modules = parse_lockfiles(lockfiles)
    if not modules:
        raise SystemExit("No Gradle lockfile modules were found; cannot generate SBOM")

    app_ref = app_component_ref(version)
    components = [
        {
            "type": "library",
            "bom-ref": module.purl,
            "group": module.group,
            "name": module.name,
            "version": module.version,
            "purl": module.purl,
        }
        for module in modules
    ]
    dependencies = [
        {
            "ref": app_ref,
            "dependsOn": [module.purl for module in modules],
        }
    ]

    return {
        "$schema": f"https://cyclonedx.org/schema/bom-{SPEC_VERSION}.schema.json",
        "bomFormat": "CycloneDX",
        "specVersion": SPEC_VERSION,
        "serialNumber": serial_number_for(version, modules),
        "version": 1,
        "metadata": {
            "timestamp": commit_timestamp(root),
            "tools": {
                "components": [
                    {
                        "type": "application",
                        "name": "Gradle lockfile SBOM generator",
                        "version": "1",
                    }
                ]
            },
            "component": {
                "type": "application",
                "bom-ref": app_ref,
                "name": PROJECT_NAME,
                "version": version,
                "purl": app_ref,
            },
        },
        "components": components,
        "dependencies": dependencies,
    }


def validate_bom(bom: dict) -> list[str]:
    errors: list[str] = []
    if bom.get("bomFormat") != "CycloneDX":
        errors.append("bomFormat must be CycloneDX")
    if bom.get("specVersion") != SPEC_VERSION:
        errors.append(f"specVersion must be {SPEC_VERSION}")
    if not isinstance(bom.get("metadata"), dict):
        errors.append("metadata object is required")
    component = bom.get("metadata", {}).get("component", {})
    app_ref = component.get("bom-ref")
    if not app_ref or not str(app_ref).startswith(APP_COMPONENT_REF_PREFIX):
        errors.append("metadata.component bom-ref must identify AppManagerNG")

    components = bom.get("components")
    if not isinstance(components, list) or not components:
        errors.append("components must be a non-empty list")
        return errors

    refs: set[str] = set()
    for index, component in enumerate(components):
        if component.get("type") != "library":
            errors.append(f"components[{index}].type must be library")
        for field in ("bom-ref", "group", "name", "version", "purl"):
            if not component.get(field):
                errors.append(f"components[{index}].{field} is required")
        ref = component.get("bom-ref")
        if ref in refs:
            errors.append(f"duplicate bom-ref: {ref}")
        refs.add(ref)
        if component.get("purl") != ref or not str(ref).startswith("pkg:maven/"):
            errors.append(f"components[{index}] must use a Maven purl bom-ref")

    dependencies = bom.get("dependencies")
    if not isinstance(dependencies, list) or not dependencies:
        errors.append("dependencies must include the app aggregate")
        return errors
    aggregate = next((item for item in dependencies if item.get("ref") == app_ref), None)
    if aggregate is None:
        errors.append("dependencies must include the AppManagerNG aggregate ref")
    else:
        depends_on = set(aggregate.get("dependsOn", []))
        missing = refs - depends_on
        extra = depends_on - refs
        if missing:
            errors.append(f"aggregate dependency list misses {len(missing)} component(s)")
        if extra:
            errors.append(f"aggregate dependency list has {len(extra)} unknown ref(s)")
    return errors


def write_json(path: pathlib.Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(data, indent=2, sort_keys=False)
    path.write_text(text + "\n", encoding="utf-8")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=".", help="Repository root")
    parser.add_argument("--version", help="Release version; defaults to app/build.gradle versionName")
    parser.add_argument(
        "--output",
        type=pathlib.Path,
        default=DEFAULT_OUTPUT,
        help=f"Generated SBOM path; default {DEFAULT_OUTPUT}",
    )
    parser.add_argument(
        "--lockfile",
        action="append",
        type=pathlib.Path,
        help="Lockfile to include; may be specified more than once",
    )
    parser.add_argument("--check", type=pathlib.Path, help="Validate an existing generated SBOM")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    root = pathlib.Path(args.root).resolve()

    if args.check:
        bom = json.loads(args.check.read_text(encoding="utf-8"))
        errors = validate_bom(bom)
        if errors:
            for error in errors:
                print(f"SBOM validation error: {error}", file=sys.stderr)
            return 1
        print(f"CycloneDX SBOM validated: {args.check}")
        return 0

    version = args.version or read_project_version(root)
    lockfiles = [root / lockfile for lockfile in args.lockfile] if args.lockfile else tracked_lockfiles(root)
    missing = [str(path) for path in lockfiles if not path.is_file()]
    if missing:
        raise SystemExit("Missing lockfile(s): " + ", ".join(missing))

    bom = generate_bom(root, version, lockfiles)
    errors = validate_bom(bom)
    if errors:
        raise SystemExit("Generated SBOM is invalid: " + "; ".join(errors))

    output = args.output if args.output.is_absolute() else root / args.output
    write_json(output, bom)
    component_count = len(bom["components"])
    print(f"CycloneDX SBOM generated: {output} ({component_count} components)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
