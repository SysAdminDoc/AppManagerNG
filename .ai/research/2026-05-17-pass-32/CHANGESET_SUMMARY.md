<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Pass 32 Changeset Summary — Android 17 / 16 KB Native Page-Size Compatibility

Date: 2026-05-17

## Roadmap item

Closed the Eng-Debt **Android 17 16 KB Page-Size Compatibility** row from iter-19.

## What changed

- Updated `app/src/main/cpp/CMakeLists.txt` so NG's JNI library links with both
  `-Wl,-z,max-page-size=16384` and `-Wl,-z,common-page-size=16384`.
- Extended `NativeLibraries` to parse ELF32/ELF64 program headers and capture
  the minimum `PT_LOAD.p_align` for each `.so` discovered in an APK.
- Added App Details / scanner labels for native libraries:
  - `16 KB aligned`
  - `Not 16 KB aligned`
  - `16 KB alignment unknown`
- Added JVM tests for aligned, misaligned, unknown, and truncated ELF inputs.
- Added `scripts/verify-native-page-alignment.py`, a release verifier that fails
  on misaligned ELF load segments, unverifiable `.so` entries, or uncompressed
  native ZIP entries whose local data offset is not 16 KB-aligned.
- Wired the verifier into both reproducible-release wrappers.
- Documented the remediation in
  `docs/audits/2026-05-17-android17-16kb-native-page-size.md` and
  `docs/distribution/reproducible-builds.md`.

## Boundary

This pass closes NG's source/release-side compatibility work. Device-side
installation and runtime validation on a 16 KB page-size Android image still
requires a JDK/Android toolchain and a 16 KB emulator or device.

## Verification

- Verified the Python release gate locally with generated aligned and misaligned
  APK fixtures.
- `git diff --check` completed cleanly apart from expected CRLF working-copy
  warnings.
- Attempted focused Gradle execution; full Gradle/JVM execution remains blocked
  in this shell because no JDK is installed and `JAVA_HOME` is unset.

## Files changed

- `app/src/main/cpp/CMakeLists.txt`
- `app/src/main/java/io/github/muntashirakon/AppManager/scanner/NativeLibraries.java`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/scanner/NativeLibrariesTest.java`
- `scripts/verify-native-page-alignment.py`
- `scripts/verify_reproducible_release.sh`
- `scripts/verify_reproducible_release.ps1`
- `docs/audits/2026-05-17-android17-16kb-native-page-size.md`
- `docs/audits/README.md`
- `docs/distribution/reproducible-builds.md`
- `ROADMAP.md`
- `CHANGELOG.md`
- `PROJECT_CONTEXT.md`
