<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 19

## Roadmap item closed

- T1 `floss` vs `full` Build Flavors (F-Droid Clean Path)

## Implementation

- Added a `distribution` flavor dimension in `app/build.gradle`:
  - `floss` is default and sets `BuildConfig.ALLOW_OPTIONAL_NETWORK_FEATURES=false`;
  - `full` sets `BuildConfig.ALLOW_OPTIONAL_NETWORK_FEATURES=true`.
- Wired `FeatureController.areOptionalNetworkFeaturesAvailable()` into the
  existing optional-network gates so `floss` disables:
  - Settings -> Privacy -> "Use the Internet";
  - VirusTotal and Pithus scan-report lookups;
  - debloat-definition auto-update fetches.
- Preserved the manifest `INTERNET` permission because AppManagerNG still needs
  local ADB-over-TCP / wireless-pairing / localhost privileged-server networking
  in both flavors. The flavor flag only gates optional third-party / remote
  report surfaces.
- Added `docs/distribution/build-flavors.md` as the canonical F-Droid metadata
  contract. F-Droid metadata should build `flossRelease`; GitHub Releases /
  Obtainium users should target `full`.
- Updated reproducible-release scripts to collect flavored release APK outputs
  recursively and to fail if flavored APK basenames collide.
- Updated CI artifact globs for flavored unit-test and lint report names.
- Updated README install guidance and Obtainium config so Obtainium filters for
  `full` release assets.
- Added `DistributionFlavorTest` as a compile/runtime guard for the flavor flag.

## Files changed

- `.github/workflows/lint.yml`
- `.github/workflows/release.yml`
- `.github/workflows/tests.yml`
- `app/build.gradle`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/FeatureController.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivacyPreferences.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/VirusTotalPreferences.java`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/settings/DistributionFlavorTest.java`
- `docs/distribution/build-flavors.md`
- `docs/distribution/obtainium-config.json`
- `docs/distribution/reproducible-builds.md`
- `docs/policy/jacoco-coverage-rollout.md`
- `docs/policy/minsdk-21-ceiling.md`
- `README.md`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## External references checked

- F-Droid Anti-Features docs:
  <https://f-droid.org/en/docs/Anti-Features/>
- LibChecker flavor pattern:
  <https://github.com/LibChecker/LibChecker/blob/master/app/build.gradle.kts>

## Verification

- `git diff --check` passed.
- Gradle verification remains blocked because `JAVA_HOME` is unset and no `java`
  command is available in PATH:
  - `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.DistributionFlavorTest`

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
