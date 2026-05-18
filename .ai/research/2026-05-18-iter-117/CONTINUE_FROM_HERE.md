<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue From Here — Iter 117

## Current state

- App Info More Info now displays copyable SELinux policy info when
  `ApplicationInfo.seInfo` is available.
- App Info records data-directory and source-file SELinux contexts via the
  existing `Path` / `ExtendedFile` SELinux context bridge.
- Running app processes are matched to the current package and their process
  domains are read from `/proc/<pid>/attr/current`.
- `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md` are updated for
  iter-117.

## Next roadmap candidates

1. T12 **JADX 1.5.5 `.apks` Ingestion + UI Zoom** — later-tier follow-up that
   may be blocked until the planned JADX viewer/backend exists.
2. T12/T10 **JADX 1.5.5 FlatLaf CJK Composite Font** — same JADX-viewer
   prerequisite; likely an audit/blocked row if no embedded viewer exists.
3. T8 **Hail-Style Digital-Assistant Launch** — standalone feature candidate
   after the JADX-prerequisite rows are triaged.
4. T7 **Amarok-Hider-Style `pm hide` Toggle** — app-state action/status surface.

## Verification to preserve

- Keep `AppSelinuxContextsTest` as the focused guard for process matching and
  context normalization.
- Run `:app:assembleFlossDebug` after App Info resource or UI-surface changes.
