<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 18

## Roadmap item closed

- T8 `App Shortcut: Freeze / Force-Stop / Clear Cache Per-App`

## Implementation

- Added `AppActionShortcutInfo`, the parcelable shortcut model for package/user
  scoped app actions.
- Added `AppActionShortcutActivity`, a non-exported `BaseActivity` handler that
  runs after normal AppManagerNG authentication and dispatches:
  - freeze through `FreezeUtils.freeze()`;
  - force-stop through `PackageManagerCompat.forceStopPackage()`;
  - clear-cache through `PackageManagerCompat.deleteApplicationCacheFilesAsUser()`.
- Added `AppActionShortcutPublisher`, called after main-list app loading, to
  publish dynamic launcher shortcuts for recent installed apps. It subtracts the
  existing three static launcher shortcuts from the platform max and only emits
  actions the current Root/Shizuku/ADB privilege path supports.
- App Details now supports long-press pinning for force-stop and clear-cache
  action chips, while existing freeze/unfreeze shortcut creation now defaults to
  explicit `Freeze <app>` / `Unfreeze <app>` names.
- Added `AppActionShortcutPublisherTest` coverage for recent-app ordering,
  capability gates, and self/uninstalled/stopped filtering.

## Files changed

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/main/MainViewModel.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/shortcut/AppActionShortcutActivity.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/shortcut/AppActionShortcutInfo.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/shortcut/AppActionShortcutPublisher.java`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/shortcut/AppActionShortcutPublisherTest.java`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- `git diff --check` passed.
- Targeted Gradle test attempt remained blocked because `JAVA_HOME` is unset and
  no `java` command is available in PATH:
  `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.shortcut.AppActionShortcutPublisherTest`

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
