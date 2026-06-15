<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# AppManagerNG Project Context

AppManagerNG is a public Android package-manager fork of upstream App Manager,
with fork identity `io.github.sysadmindoc.AppManagerNG` and source namespace
kept as `io.github.muntashirakon.AppManager` for upstream compatibility.

## Current Stack

- Language: Java with a small Kotlin/tooling footprint from Android plugins.
- UI: Android Views, XML layouts, Material Components 1.13.0, no Compose.
- Build: Gradle 9.4.1, AGP 9.2.0, JDK 21, NDK 28.2.13676358, CMake.
- SDK floor: minSdk 21, targetSdk 36, compileSdk 36.
- Flavors: `floss` is default and removes optional online features at compile
  time; `full` keeps VirusTotal, Pithus, and debloat-definition update features
  behind user opt-ins.
- Modules: `app`, `benchmark`, `docs`, `hiddenapi`, `libcore:compat`,
  `libcore:io`, `libcore:ui`, `libopenpgp`, `libserver`, `server`.

## Canonical Planning Surfaces

- `ROADMAP.md`: live unchecked work only.
- `RESEARCH.md`: current research backing for live roadmap entries.
- `docs/roadmap/COMPLETED.md`: completed roadmap and stale-row closures.
- `CHANGELOG.md`: release history and shipped behavior changes.
- `docs/roadmap/archive/`: historical roadmap and research surfaces.

## Build Commands

```bash
git submodule update --init --recursive
./gradlew :app:processFlossDebugResources :app:compileFlossDebugJavaWithJavac
./gradlew :app:testFlossDebugUnitTest
./gradlew :docs:buildDocs
```

Windows local builds should set `JAVA_HOME` to the Android Studio JBR or another
JDK 21 installation and use `local.properties` for the Android SDK path.

## Review Priorities

- Preserve privileged-operation safety and clear failure states.
- Keep destructive actions authenticated, explained, and recoverable.
- Keep `floss` offline by construction and document any `full` flavor network
  behavior.
- Prefer small, tested data-layer changes before device-gated UI wiring.
- Leave root/ADB/Shizuku/Dhizuku behavior unmodified unless it can be verified
  with the matching device or emulator path.
