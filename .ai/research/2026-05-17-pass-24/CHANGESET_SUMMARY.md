<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 24

## Roadmap item closed

- T4 Mode Self-Test "Doctor"

## Implementation

- Added `PrivilegeModeDoctor`, an active diagnostic report separate from the
  existing passive Privileges health rows.
- The doctor probes:
  - configured/inferred mode and working UID;
  - root binary/grant and root-manager/Sui markers;
  - Shizuku binder/UserService/permission/API/UID;
  - USB debugging, Wireless debugging, and saved ADB pairing state;
  - LocalServer `id -u`;
  - SELinux domain via the active shell path;
  - supported ABIs.
- Added Settings -> Privileges -> "Mode doctor" to run the report and show it in
  the existing copyable diagnostic dialog pattern.
- Added `PrivilegeModeDoctorTest` for stable report formatting.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivilegeModeDoctor.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivilegeHealthPreferences.java`
- `app/src/main/res/xml/preferences_privilege_health.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/settings/PrivilegeModeDoctorTest.java`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- `git diff --check` passed.
- Gradle verification remains blocked because `JAVA_HOME` is unset and no `java`
  command is available in PATH:
  - `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.PrivilegeModeDoctorTest`

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
