<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Maintainer-local historical archives are not
published with the repository. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

All remaining blocked items are in `Roadmap_Blocked.md`.

## Deep Audit Follow-ups (2026-07-02)

## Research-Driven Additions (2026-07-12)

Backing research: `RESEARCH.md` (this pass re-diffs against upstream App Manager
v4.1.0, 2026-06-29, and Android 17 / API 37, stable since June 2026). All items
below are host-verifiable (buildable + unit-testable offline); runtime confirmation
that requires an API-37 device stays in `Roadmap_Blocked.md`.

### P3

- [ ] P3 — Confirm parity for non-default-user inactive-app check
  Why: upstream fixed inactive-app detection for non-default users; NG's `ApplicationItem` already
  passes `userId` with a guard, so this needs a diff-and-confirm plus multi-user unit coverage
  rather than an assumed bug fix.
  Evidence: upstream MuntashirAkon/AppManager@916eeb85d5; app/src/main/java/io/github/muntashirakon/AppManager/main/ApplicationItem.java:353-357; compat/UsageStatsManagerCompat.
  Touches: main/ApplicationItem.java, compat/UsageStatsManagerCompat.java, unit test for non-default userId path.
  Acceptance: a unit test asserts `isAppInactive` resolves per-user for a non-default userId (no cross-user leakage); if a divergence from upstream exists it is corrected and attributed in CHANGELOG.md.
  Complexity: S
