<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 21

## Roadmap item closed

- T4 Support Info Bundle Composer

## Implementation

- Added `SupportInfoBundle`, a zero-network text-bundle composer that writes
  `support-info-<device>-<timestamp>.txt` into the app cache and shares it via
  `FmProvider` with explicit `ClipData` read grants.
- Added Settings -> Troubleshooting -> "Share support info".
- The bundle captures AppManagerNG version/flavor/build type, Android and ROM
  build fields, configured/inferred privilege mode, root manager source,
  ZygiskNext/Sui markers, Shizuku state, remote server/service state, feature
  flags, and the last recorded LocalServer bootstrap signature.
- Added a scrubbed 120-line logcat tail. The scrubber masks package-like tokens,
  file/content/http URIs, package URIs, storage paths, email addresses, UIDs, and
  large numeric identifiers before the file leaves the app.
- Persisted the latest LocalServer bootstrap signature when the smoke test runs
  or when the LocalServer failure logger emits a signature, so support bundles
  can include the most recent privileged-shell bootstrap context.
- Added unit coverage for filename generation and support-info scrubbing.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/misc/SupportInfoBundle.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/TroubleshootingPreferences.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServer.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivilegeHealthPreferences.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/utils/AppPref.java`
- `app/src/main/res/xml/preferences_troubleshooting.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/misc/SupportInfoBundleTest.java`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- `git diff --check` passed.
- Gradle verification remains blocked because `JAVA_HOME` is unset and no `java`
  command is available in PATH:
  - `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.misc.SupportInfoBundleTest`

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
