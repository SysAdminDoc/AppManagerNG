<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Pass 39 changeset summary — Restricted Settings unlock walkthrough

Date: 2026-05-17

Implementation commit: `feat(settings): add restricted settings walkthrough`

## Roadmap item closed

- T5 `Restricted Settings Unlock Walkthrough` in `ROADMAP.md`.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/settings/RestrictedSettingsDiagnostics.java`
  - New helper that reads AppManagerNG install-source metadata and classifies
    Android 13+ installs as trusted-store, likely sideloaded, unknown, or
    review-recommended.
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivilegeHealthPreferences.java`
  - Adds the Settings -> Privileges row, walkthrough dialog, App info deep-link,
    and Accessibility settings fallback.
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivilegeModeDoctor.java`
  - Adds a Restricted Settings probe with source details and support-ready fix
    guidance.
- `app/src/main/res/xml/preferences_privilege_health.xml` and
  `app/src/main/res/values/strings.xml`
  - Add the preference row and user-facing copy.
- `app/src/test/java/io/github/muntashirakon/AppManager/settings/RestrictedSettingsDiagnosticsTest.java`
  - Covers install-source classification boundaries.
- `CHANGELOG.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`
  - Record the closure and the intentional limitation: Android exposes install
    source metadata, not a public per-app restricted-settings-blocked bit.

## Evidence

- `ROADMAP.md` S222: Android Restricted Settings documentation.
- Local source:
  - `PreferenceHealthPreferences` already owns Settings -> Privileges.
  - `PrivilegeModeDoctor` already owns copyable provider diagnostics.
  - `IntentUtils.getAppDetailsSettings()` already builds the package App info
    Settings intent.

## Verification

- `git diff --check` passed with only expected CRLF normalization warnings in
  this shared-folder checkout.
- XML parse check passed for `strings.xml` and `preferences_privilege_health.xml`.
- Focused Gradle test attempted:

```text
.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.RestrictedSettingsDiagnosticsTest
```

- Local Gradle execution remains blocked because no JDK is installed and
  `JAVA_HOME` is unset:

```text
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

- After commit, verify HEAD, branch-ahead state, and the known shared-folder fsck
  dangling blob/tag state.
