<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue From Here — Iter 116

## Current state

- File Manager selection mode now exposes **Install selected APKs** for readable
  APK-family files only.
- The action sends selected paths to `PackageInstallerActivity` as content URIs
  with `ACTION_SEND_MULTIPLE`, `ClipData`, and a batch-mode extra.
- Batch mode reuses the existing installer queue and foreground installer
  service. Split APK containers get default required/best-supported split
  selections before each install is launched.
- `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md` are updated for
  iter-116.

## Next roadmap candidates

1. T9 **Termux SELinux Context Display** — app-security surface for process and
   file SELinux context display.
2. T7 **Amarok-Hider-Style `pm hide` Toggle** — expose hidden package state and
   action in App Info.
3. T12 **JADX 1.5.5 `.apks` Ingestion + UI Zoom** — later-tier JADX integration
   follow-up, only if T12 backend work is deliberately selected.

## Verification to preserve

- Keep `FmBatchApkInstallUtilsTest` as the focused guard for the file-selection
  policy and batch installer intent shape.
- Run `:app:assembleFlossDebug` after resource/menu changes.
