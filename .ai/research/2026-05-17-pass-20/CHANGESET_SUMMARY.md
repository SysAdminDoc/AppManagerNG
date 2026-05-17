<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 20

## Roadmap item closed

- T4 LocalServer Bootstrap Smoke Test

## Implementation

- Refactored LocalServer's bootstrap-failure diagnostic line into
  `LocalServer.buildBootstrapSignature(...)` so both failure logs and success
  smoke-test reports share the same formatter.
- Extended the signature with device/build, configured mode, working UID, app
  UID, optional LineageOS build, elapsed time, privileged probe exit code/output,
  and exception/cause fields.
- Added a Settings -> Privileges "LocalServer bootstrap smoke test" row. It runs
  the LocalServer privileged-shell handshake for the current mode, executes
  `id -u`, and displays the result in a copyable dialog.
- Kept this under the existing Privileges diagnostics screen instead of adding a
  new Developer screen because pass 10 made Settings -> Privileges the canonical
  mode, remote-service, and provider health-check surface.
- Added unit coverage for the shared signature formatter's success and failure
  variants.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServer.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivilegeHealthPreferences.java`
- `app/src/main/res/xml/preferences_privilege_health.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/servermanager/LocalServerBootstrapSignatureTest.java`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- `git diff --check` passed.
- Gradle verification remains blocked because `JAVA_HOME` is unset and no `java`
  command is available in PATH:
  - `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.servermanager.LocalServerBootstrapSignatureTest`

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
