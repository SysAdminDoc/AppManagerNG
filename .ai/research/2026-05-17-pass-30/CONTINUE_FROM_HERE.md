<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue From Here — Pass 30

Date: 2026-05-17

## Current state

The Shizuku Permission Auto-Revoke Warning row is implemented in this pass.
`git diff --check` completed cleanly apart from expected CRLF working-copy
warnings, and the focused Gradle test was attempted but blocked by the missing
JDK. The local branch was already ahead of `origin/main`; pushing remains
blocked/skipped unless GitHub auth is corrected because the configured remote
belongs to `SysAdminDoc/AppManagerNG` while local `gh auth status` has reported
an invalid `MavenImaging` token.

## Verification still needed when a JDK/device is available

Run:

```powershell
.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.shizuku.ShizukuBridgeTest
```

Recommended manual/device spot checks:

- App Info -> AppManagerNG -> Clear data shows the self Shizuku warning.
- App Info -> Shizuku Manager -> Clear data shows the manager warning.
- App Info -> a Shizuku client app -> Clear data shows the client-provider warning.
- Clearing AppManagerNG data from a privileged path after a valid Shizuku grant opens Settings -> Mode of operation if the grant is revoked.

Expected local blocker in this shell:

```text
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

## Next roadmap candidates

First uncompleted `Now` rows after the closed Shizuku clear-data row:

- T8 Freeze / Operation Audit Log.
- Eng-Debt Android 17 16 KB Page-Size Compatibility.
- T1 Android Developer Verification BR/ID/SG/TH Enforcement.
- T5 Shizuku 13.6.0 OEM Allowlist.
- T5 Shizuku Sui Detection / Wording Accuracy.

Use `rg -n "\| \*\*.*\*\* \|.*\| \*\*Now\*\* \|" ROADMAP.md` and ignore struck-through
rows before choosing the next slice.
