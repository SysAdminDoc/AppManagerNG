<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 116 — Inure-Style Batch APK Installer

## Roadmap item

Shipped T11 **Inure-Style Batch APK Installer**.

## Implementation

- Added a File Manager multi-selection action, **Install selected APKs**, shown
  for readable `.apk`, `.apks`, `.apkm`, and `.xapk` files.
- Added `FmBatchApkInstallUtils` to keep the selection policy and installer
  intent construction testable.
- Added `PackageInstallerActivity.getBatchInstallInstance()` for explicit
  in-app batch launches using `ACTION_SEND_MULTIPLE`, content-URI grants, and
  `ClipData`.
- Added installer batch mode so selected APK paths are consumed by the existing
  installer queue/service path instead of forcing users through separate file
  opens.
- Added default split selection for batch flow: required split entries and the
  first supported ABI/density/locale split per selected feature are selected
  automatically. Existing destructive reinstall/signature safety prompts remain
  in place.
- Added focused Robolectric coverage for supported package extensions, selected
  item policy, and generated installer intent shape.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.fm.FmBatchApkInstallUtilsTest --console=plain`

## Sources used

- Roadmap source S131: Inure build107 batch installer reference.
