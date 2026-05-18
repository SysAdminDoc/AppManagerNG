<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue From Here — Iter 111

## Completed

T9 **Magisk `--drop-cap` Opt-In Surface** is implemented in the existing
Privileges capability diagnostic.

Relevant files:

- `app/src/main/java/io/github/muntashirakon/AppManager/runner/RootCapabilityDiagnostics.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivilegeHealthPreferences.java`
- `app/src/test/java/io/github/muntashirakon/AppManager/runner/RootCapabilityDiagnosticsTest.java`
- `app/src/main/res/values/strings.xml`

## Verification to rerun after edits

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.runner.RootCapabilityDiagnosticsTest --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`
- `git diff --check`

## Next roadmap item

Re-scan `ROADMAP.md` from the top. The next uncompleted rows after this one are
T9 **KernelSU Sulog & Seccomp Status**, T2 **Android 16 `SDK_INT_FULL` Plumbing
Audit**, and T12 **APKEditor `--smali-comment-level basic`**; choose the first
actionable item that fits the current repo state.
