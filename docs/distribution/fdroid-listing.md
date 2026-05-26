<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# F-Droid Listing Packet

Status: ready for fdroiddata merge-request preparation.
Checked: 2026-05-26.

This packet is the source-of-truth for preparing AppManagerNG's F-Droid.org
submission. It is not proof that an external fdroiddata merge request has been
filed or accepted.

## Current Release

- Repository: `https://github.com/SysAdminDoc/AppManagerNG`
- Tag: `v0.5.0`
- Commit: `e46ea2615ea6e035eea41d50e88a731ec36a7c1d`
- Package name: `io.github.sysadmindoc.AppManagerNG`
- Display name: `AppManagerNG`
- Version name: `0.5.0`
- Version code: `7`
- Release: `https://github.com/SysAdminDoc/AppManagerNG/releases/tag/v0.5.0`
- License: `GPL-3.0-or-later`

## Build Target

- F-Droid target flavor: `floss`.
- Gradle task produced locally: `:app:assembleFlossRelease`.
- Local validation: `.\gradlew.bat assembleFlossRelease` passed on
  2026-05-26 using JDK 21.
- Output observed locally: `app/build/outputs/apk/floss/release/app-floss-release.apk`.
- Submodules are required: `scripts/android-libraries` and
  `scripts/android-debloat-list`.

F-Droid metadata should list the Gradle flavor, not the full task name.
F-Droid's build metadata reference says the `gradle` field specifies flavors and
fdroidserver composes the release assemble task from them.

## Suggested fdroiddata Metadata

```yaml
Categories:
  - System
License: GPL-3.0-or-later
AuthorName: SysAdminDoc
WebSite: https://github.com/SysAdminDoc/AppManagerNG
SourceCode: https://github.com/SysAdminDoc/AppManagerNG
IssueTracker: https://github.com/SysAdminDoc/AppManagerNG/issues
Changelog: https://github.com/SysAdminDoc/AppManagerNG/releases

RepoType: git
Repo: https://github.com/SysAdminDoc/AppManagerNG.git

Builds:
  - versionName: 0.5.0
    versionCode: 7
    commit: e46ea2615ea6e035eea41d50e88a731ec36a7c1d
    submodules: true
    gradle:
      - floss
    rm:
      - app/src/test/resources/AppManager_v2.5.22.apks
      - app/src/test/resources/oandbackups/ademar.textlauncher/classes.dex

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags
CurrentVersion: 0.5.0
CurrentVersionCode: 7
```

The `rm` entries remove binary test fixtures that are not part of the release
build. The tracked Gradle wrapper jar is expected for Gradle projects; the app's
debug keystore is ignored and not present in the tracked source tree.

## Policy Checks

- Source is public and GPL-3.0-or-later.
- Fastlane metadata is present at `fastlane/metadata/android/en-US/` with
  title, summary, full description, icon, screenshots, and a version-code
  changelog under 500 characters.
- The `floss` flavor disables optional third-party online report features at
  compile time. See `docs/distribution/build-flavors.md`.
- Package visibility is documented for reviewers. See
  `docs/distribution/package-visibility.md`.
- Reproducible-release verification is documented. See
  `docs/distribution/reproducible-builds.md`.
- AppManagerNG does not require root to launch; root, Shizuku, Sui, ADB, and
  related privileged providers unlock optional power-user actions.

Relevant F-Droid references checked on 2026-05-26:

- `https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/`
- `https://f-droid.org/en/docs/Inclusion_Policy/`
- `https://f-droid.org/en/docs/Build_Metadata_Reference/`
- `https://f-droid.org/docs/FAQ_-_App_Developers/`

## Maintainer Action Required

File the external fdroiddata merge request with the metadata above, watch the
F-Droid CI scanner/build output, and adjust the `rm` or scanner notes only if
the external reviewer asks for a narrower treatment of the tracked test
fixtures.
