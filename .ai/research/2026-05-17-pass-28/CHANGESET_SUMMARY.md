<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 28

## Roadmap item closed

- T8 Hail-Style Auto-Freeze QuickSettings Tile

## Implementation

- Added `QuickFreezeTileService`, an Android Quick Settings `TileService`.
- The tile is declared with:
  - `android.permission.BIND_QUICK_SETTINGS_TILE`;
  - `android.service.quicksettings.action.QS_TILE`;
  - snowflake icon;
  - non-toggleable metadata.
- Added `QuickFreezeTileController` to store the selected profile ID and gate
  eligibility to profiles whose `misc` actions include `freeze`.
- The Profiles list popup now shows "Use for Quick Settings freeze tile" only
  for freeze-enabled profiles, or a clear action for the currently selected
  profile.
- Tile tap behavior:
  - unavailable when no profile is configured;
  - prompts unlock via `unlockAndRun()` when tapped from the lock screen;
  - opens Profiles when the selected profile is missing or no longer
    freeze-capable;
  - starts `ProfileApplierService` with `BaseProfile.STATE_ON` so the existing
    profile application/progress/history path performs the freeze.
- Added `QuickFreezeTileControllerTest` for the freeze-profile eligibility gate.

## Files changed

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/io/github/muntashirakon/AppManager/profiles/QuickFreezeTileController.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/profiles/QuickFreezeTileService.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/profiles/ProfilesActivity.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/Prefs.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/utils/AppPref.java`
- `app/src/main/res/menu/activity_profiles_popup_actions.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/profiles/QuickFreezeTileControllerTest.java`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- `git diff --check` passed.
- Targeted Gradle verification was attempted but remains blocked because
  `JAVA_HOME` is unset and no `java` command is available in PATH:
  - `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.profiles.QuickFreezeTileControllerTest`

## External sources used

- `https://github.com/aistra0528/Hail/releases`
- `https://developer.android.com/develop/ui/views/quicksettings-tiles`
- `https://developer.android.com/reference/android/service/quicksettings/TileService`

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging` and is
currently invalid in `gh auth status`.
