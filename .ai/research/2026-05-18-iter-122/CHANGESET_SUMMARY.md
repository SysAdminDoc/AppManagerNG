<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 122 — InstallerX-style sensitive action authentication gate

## Roadmap row

T9 **InstallerX-Style Biometric Install Gate** is shipped.

## What changed

- Added `ActionAuthGate`, a shared `BiometricPrompt` wrapper for optional
  per-action authentication.
- Added Settings -> Privacy toggle `enable_action_auth_gate` ("Require
  authentication for app changes"), backed by `Prefs.Privacy` and `AppPref`.
- Installer final commit now prompts before install/reinstall/downgrade work is
  started. The prompt is independent from the existing app/session lock.
- Direct App Info uninstall, uninstall-updates, work-profile system uninstall
  handoff, and clear-data flows now prompt after the user's confirmation dialog
  and before the package/data operation starts.
- Main-list batch uninstall and batch clear-data now prompt before starting
  `BatchOpsService`.
- Quick uninstall and one-click orphan-data cleanup now prompt before dispatching
  their destructive flows.

## Verification

- `.\gradlew.bat :app:assembleFlossDebug --console=plain`

The build completed successfully. Existing resource-format and deprecation
warnings are unchanged project noise observed during the build.
