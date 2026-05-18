<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue From Here — Iter 115

## Current state

- KernelSU diagnostics in Settings -> Privileges now show active App Profile
  shape: UID, GID, supplementary groups, SELinux context, raw `id`, and CapEff.
- Restricted KernelSU profiles are summarized when UID/GID differ from root or
  expected AppManagerNG root capabilities are missing.
- The implementation observes the actual active `su` process rather than
  reading KernelSU private config files.
- `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md` are updated for
  iter-115.

## Next roadmap candidates

1. T11 **Inure-Style Batch APK Installer** — batch install flow over selected
   APK paths.
2. T9 **Termux SELinux Context Display** — app-security surface for process and
   file SELinux context display.
3. T7 **Amarok-Hider-Style `pm hide` Toggle** — expose hidden package state and
   action in App Info.

## Verification to preserve

- Keep `KernelSuDiagnosticsTest` as the focused guard for App Profile parsing.
- Run `:app:assembleFlossDebug` after changing Settings resources or
  diagnostics UI copy.
