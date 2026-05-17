<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 9

## Roadmap item closed

- T5 `Auto-Update Debloat Definitions`
- Duplicate iter-addition row `UAD-Style Auto-Fetch Debloat Definitions`

## Implementation

- Added `DebloatDefinitionsUpdater`, a launch-time updater gated by both:
  - Settings -> Privacy -> `Update debloat definitions on launch` (default off);
  - existing `FeatureController.isInternetEnabled()` / "Use the Internet" gate.
- Added `docs/debloat-definitions/manifest.json` as the pinned raw-GitHub update
  manifest. The app checks file byte counts and SHA-256 values from this manifest
  before accepting remote `debloat.json` or `suggestions.json`.
- Updated `StaticDataset` to prefer app-private cached definition files when
  available and fall back to bundled assets when no cache exists.
- Added app-private atomic cache writes under `files/debloat-definitions/`.
- Added pure-JVM coverage for SHA-256 formatting, approved raw-GitHub URL
  guardrails, and minimal dataset validation.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/debloat/DebloatDefinitionsUpdater.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/StaticDataset.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/AppManager.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivacyPreferences.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/Prefs.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/utils/AppPref.java`
- `app/src/main/res/xml/preferences_privacy.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/debloat/DebloatDefinitionsUpdaterTest.java`
- `docs/debloat-definitions/manifest.json`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- XML parse passed for `strings.xml` and `preferences_privacy.xml`.
- Manifest JSON byte counts and SHA-256 values matched the bundled assets.
- `git diff --check` passed.
- Gradle tests/build remain blocked in this shell because `JAVA_HOME` is unset and
  no `java` command is available.

## Push status

Push is intentionally skipped for this run because `origin` is
`https://github.com/SysAdminDoc/AppManagerNG.git` while the configured GitHub
credential authenticates as `MavenImaging`, producing a 403 on the first push
attempt in this session.
