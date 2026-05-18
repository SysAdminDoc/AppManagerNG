<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Changeset summary — iter 90

Date: 2026-05-18

## Roadmap item closed

- T13 `File Manager Compression`.

## Implementation

- Added `FmArchiveUtils` for File Manager ZIP create/extract operations.
- Added selection-mode "Create ZIP archive" and "Extract archive" actions.
- Added ZIP item-menu extraction, routed through `FmViewModel` and observed by
  `FmFragment`.
- Extraction rejects unsafe normalized entry paths, including `../` zip-slip
  attempts, and prompts on file conflicts with replace / keep both / skip / stop.
- Archive creation and extraction use File Manager progress dialogs with cancel
  support and destination reload.

## Verification fixes discovered during this pass

- Added `app/src/test/AndroidManifest.xml` to mirror the main manifest's
  Shizuku `tools:overrideLibrary` allowance so app JVM tests can merge the test
  manifest with minSdk 21.
- Fixed three stale compile blockers surfaced once the test task could progress:
  `SupportInfoBundle` now stringifies the inferred mode, `OnboardingFragment`
  resolves `colorPrimary` from AppCompat attrs, and
  `PrivilegeHealthPreferences` captures the bootstrap signature before posting
  it through a lambda.

## Verification

- `.\gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain` passed.
- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.fm.FmArchiveUtilsTest --console=plain` passed.

