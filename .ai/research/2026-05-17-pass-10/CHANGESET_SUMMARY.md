<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 10

## Roadmap item closed

- T5 `Privilege Health-Check Screen`

## Implementation

- Added `PrivilegeHealthPreferences`, exposed as Settings -> Privileges.
- The screen reports:
  - configured mode, inferred mode, working UID, and app UID;
  - active-mode self-test pass/fail;
  - root manager detection via `RootManagerInfo` with ZygiskNext and Sui markers;
  - Shizuku manager version, Shizuku API version, minimum UserService API, binding
    permission status, and Android 17 risk warning;
  - USB debugging, Wireless debugging, and last wireless pairing port;
  - local remote-server and remote-service state;
  - AppManagerNG battery optimization state.
- The battery row reuses `SelfBatteryOptimization` and attempts the privileged
  root/ADB whitelist path before falling back to Android's exemption prompt.
- Added `ShizukuBridge.getVersionOrZero()` so diagnostics can display the Shizuku
  API version without direct UI references to the Shizuku SDK.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivilegeHealthPreferences.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java`
- `app/src/main/res/xml/preferences_privilege_health.xml`
- `app/src/main/res/xml/preferences_main.xml`
- `app/src/main/res/values/strings.xml`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- XML parse passed for `strings.xml`, `preferences_main.xml`, and
  `preferences_privilege_health.xml`.
- `git diff --check` passed.
- Targeted Gradle test attempt remains blocked because `JAVA_HOME` is unset and
  no `java` command is available in PATH.

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
