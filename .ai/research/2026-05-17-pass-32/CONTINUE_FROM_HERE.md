<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue From Here — Pass 32

Date: 2026-05-17

## Current state

The Android 17 / 16 KB native page-size row is implemented and documented.
AppManagerNG now builds `libam.so` with 16 KB ELF linker flags, surfaces
per-library `PT_LOAD.p_align` compatibility in APK-native-library details, and
fails reproducible release APKs that contain misaligned or unverifiable native
libraries.

The local branch was already ahead of `origin/main`; pushing remains
blocked/skipped unless GitHub auth is corrected because the configured remote
belongs to `SysAdminDoc/AppManagerNG` while local `gh auth status` has reported
an invalid `MavenImaging` token.

## Verification still needed when a JDK/device is available

Run:

```powershell
.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.scanner.NativeLibrariesTest
.\gradlew.bat :app:assembleRelease
python scripts\verify-native-page-alignment.py app\build\outputs\apk
```

Recommended manual/device spot checks:

- Open App Details -> native libraries for an APK with a known 16 KB-aligned
  `.so`; confirm the row shows `16 KB aligned`.
- Open an APK with a 4 KB-aligned `.so`; confirm the row shows
  `Not 16 KB aligned`.
- Install a release APK on a 16 KB page-size emulator/device and verify the main
  app list loads packages.

Expected local blocker in this shell:

```text
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

## Next roadmap candidates

First uncompleted `Now` rows after the closed 16 KB page-size row:

- T1 Android Developer Verification BR/ID/SG/TH Enforcement.
- T5 Shizuku 13.6.0 OEM Allowlist.
- T5 Shizuku Root-Backed Avoidance for Banking Apps.
- T9 OS-Revert Detection Banner.

Use `rg -n "\| \*\*.*\*\* \|.*\| \*\*Now\*\* \|" ROADMAP.md` and ignore struck-through
rows before choosing the next slice.
