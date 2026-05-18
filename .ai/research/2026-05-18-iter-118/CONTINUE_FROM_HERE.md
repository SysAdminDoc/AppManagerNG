<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue From Here — Iter 118

## Current state

- The two immediate JADX 1.5.5 follow-up rows are parked as blocked by the
  future T12 JADX viewer.
- No code was changed for the parked rows because the live app has no JADX GUI,
  no external JADX handoff, no zoom setting, and no FlatLaf host.
- `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md` are updated for
  iter-118.

## Next roadmap candidates

1. T8 **Hail-Style Digital-Assistant Launch** — next open row in the iter-18
   ledger, likely implementable as an `ACTION_ASSIST` entry point plus a fast
   action sheet.
2. T7 **Amarok-Hider-Style `pm hide` Toggle** — concrete package-state action
   and badge if the assistant row proves blocked by platform role constraints.
3. T8 **Glance Widget Parity Audit** — under-consideration exploration after
   the nearby later/next rows are handled.

## Verification to preserve

- Before future T12 JADX work, re-check `versions.gradle`, `app/build.gradle`,
  and `DexUtils` because those are the current JADX dependency/call-site
  anchors.
