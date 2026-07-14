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

## Research-Driven Additions

Backing research: `RESEARCH.md` (2026-07-12) — user-state protection (snapshot secret
boundary, Room migration safety, snapshot portability, encrypted bundles, release/screenshot
truth) — combined with a host-verifiable code-level correctness audit of the intercept /
backup / apk-parser / search / debloat / filters / rules / uri subsystems (findings cited
inline per item). All items are fixable and unit-testable offline except the screenshot
refresh, which needs a capture environment.

### P1

### P2

### P3

## Research-Driven Additions (2026-07-14)

Backing research: `RESEARCH.md` (2026-07-14). Fresh host-verifiable code audit plus an
upstream/ecosystem sweep (App Manager v4.1.0, LibChecker/Hail/Canta/InstallerX/SD Maid SE,
dependency CVEs, Android 16/17 APIs). All items below are host-verifiable and unit-testable
offline. Device-gated feature ideas from this pass are in `Roadmap_Blocked.md`.

### P2

### P3

- [ ] P3 — Surface low-cost inspection signals from data NG already computes
  Why: cheap power-user differentiators that require no new data source — a weak-signature flag (v1-scheme-only APKs) as an at-a-glance security signal; the Android 16 `BODY_SENSORS → android.permissions.health` granular mapping in the permission catalog; and LibChecker-class signals (modern-vs-legacy Xposed API, live-update-notification capability, themed-icon/alias detection) as App Details / Finder rows.
  Evidence: LibChecker 2026 releases; Android 16 behavior-changes (health permissions); `utils/PackageUtils.java` signing-scheme data; existing `XposedModuleInfo`/permission parsing.
  Touches: `details/info/` (signing + capability chips), `permission/` catalog, `filters/options/` (new predicates), `scanner/`/`details/` Xposed/icon inspection.
  Acceptance: an app signed only with scheme v1 shows a weak-signature chip; health permissions map to the granular group; at least one new LibChecker-class signal appears in App Details and is filterable in the Finder; all verified by host unit tests over fixture package data.
  Complexity: M

