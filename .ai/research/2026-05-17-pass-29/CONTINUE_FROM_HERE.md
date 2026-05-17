<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue From Here — Pass 29

Date: 2026-05-17

## Current state

The Hidden-API Compatibility Harness row is implemented and should be committed after
local static checks. The local branch was already ahead of `origin/main`; pushing remains
blocked/skipped unless GitHub auth is corrected because the configured remote belongs to
`SysAdminDoc/AppManagerNG` while local `gh auth status` has previously reported an invalid
`MavenImaging` token.

## Verification still needed when a JDK/device is available

Run:

```powershell
.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.compat.HiddenApiDescriptorBaselineTest
.\gradlew.bat :app:connectedFullDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.muntashirakon.AppManager.compat.HiddenApiCompatibilityInstrumentedTest
```

Expected local blocker in this shell:

```text
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

## Next roadmap candidates

First uncompleted `Now` rows after the closed Hidden-API row:

- T5 Shizuku Permission Auto-Revoke Warning.
- T8 Freeze / Operation Audit Log.
- Eng-Debt Android 17 16 KB Page-Size Compatibility.
- T1 Android Developer Verification BR/ID/SG/TH Enforcement.
- T5 Shizuku 13.6.0 OEM Allowlist.

Use `rg -n "\| \*\*.*\*\* \|.*\| \*\*Now\*\* \|" ROADMAP.md` and ignore struck-through
rows before choosing the next slice.
