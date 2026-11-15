#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Regenerate the framework resource data the binary XML decoder reads.

Writes app/src/main/resources/io/github/muntashirakon/AppManager/apk/parser/frameworks/
(the newest SDK's framework table and disputed-ids.txt) with
scripts/framework-tables/GenerateFrameworkTables.java.

Needs every SDK platform from android-21 up installed, minor SDKs included
(android-36.1, android-37.0, android-37.1, ...), JDK 17 or newer, and the
ARSCLib jar in the Gradle cache (any build of the app fetches it).

    python3 scripts/generate_framework_tables.py [--sdk <Android SDK dir>]

Run it again when a new SDK platform comes out or ARSCLib is updated, then run
AndroidBinXmlDecoderTest and FrameworkTablesTest.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "app/src/main/resources/io/github/muntashirakon/AppManager/apk/parser/frameworks"
GENERATOR = REPO / "scripts/framework-tables/GenerateFrameworkTables.java"


def find_sdk(given):
    candidates = [given, os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    local = REPO / "local.properties"
    if local.is_file():
        m = re.search(r"^sdk\.dir=(.+)$", local.read_text(encoding="utf-8"), re.MULTILINE)
        if m:
            candidates.append(m.group(1).replace("\\:", ":").replace("\\\\", "\\"))
    if os.environ.get("LOCALAPPDATA"):
        candidates.append(str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"))
    candidates.append(str(Path.home() / "Android/Sdk"))
    for c in candidates:
        if c and (Path(c) / "platforms").is_dir():
            return Path(c)
    sys.exit("No Android SDK found; pass --sdk")


def find_arsclib():
    versions = (REPO / "versions.gradle").read_text(encoding="utf-8")
    m = re.search(r'arsclib_version\s*=\s*"([^"]+)"', versions)
    if not m:
        sys.exit("arsclib_version not found in versions.gradle")
    version = m.group(1)
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    base = gradle_home / "caches/modules-2/files-2.1/com.github.REAndroid/ARSCLib" / version
    jars = sorted(base.glob(f"*/ARSCLib-{version}.jar"))
    if not jars:
        sys.exit(f"ARSCLib {version} isn't in {base}; build the app once so Gradle fetches it")
    return jars[0]


def find_java():
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        exe = Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if exe.is_file():
            return str(exe)
    exe = shutil.which("java")
    if not exe:
        sys.exit("No java found; set JAVA_HOME")
    return exe


def main():
    parser = argparse.ArgumentParser(description="Regenerate the binary XML decoder's framework resource data.")
    parser.add_argument("--sdk", help="Android SDK directory")
    args = parser.parse_args()
    sdk = find_sdk(args.sdk)
    cmd = [find_java(), "-Xmx2g", "-cp", str(find_arsclib()), str(GENERATOR), str(sdk / "platforms"), str(OUT)]
    return subprocess.call(cmd)


if __name__ == "__main__":
    sys.exit(main())
