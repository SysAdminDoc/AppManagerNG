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

## Deep Audit Follow-ups (2026-07-14)

Findings from the 2026-07-14 deep audit that were not fixed in place because they
need a versioned data migration, a design decision, a broad multi-site refactor,
or on-device verification. High-confidence host-fixable bugs from the same audit
were fixed directly (see git history / CHANGELOG).

### P3

## Research-Driven Additions (2026-07-14)

Backing research: `RESEARCH.md` (2026-07-14). Fresh host-verifiable code audit plus an
upstream/ecosystem sweep (App Manager v4.1.0, LibChecker/Hail/Canta/InstallerX/SD Maid SE,
dependency CVEs, Android 16/17 APIs). All items below are host-verifiable and unit-testable
offline. Device-gated feature ideas from this pass are in `Roadmap_Blocked.md`.

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions (2026-07-22)

Backing research: `RESEARCH.md` (2026-07-22). Competitor harvest (InstallerX-Revived,
LibChecker, SD Maid, Hail) + a fresh host-verifiable audit and dependency/CVE sweep.
Upstream shipped no new tag since v4.1.0 (2026-06-29); all prior host findings are already
fixed, so these are net-new. Every item below is host-implementable and host-testable
(Robolectric/JUnit/Jazzer) except where a final on-device check is noted.

### P2

### P3

- [ ] P3 — Native-lib readiness Finder filter + App Details chip (16 KB / 32-bit-only / compressed)
  Why: NG already detects 16 KB load-segment alignment but only as a scanner string; making it
  a Finder predicate + at-a-glance chip lets users sweep the whole device for apps that will
  break or bloat on Android 15/16 (LibChecker parity).
  Evidence: `scanner/NativeLibraries.java:256` (`has16KbLoadSegmentAlignment`), :339-341
  (`native_lib_16kb_aligned` string); `filters/options/` (no native-lib predicate);
  github.com/LibChecker/LibChecker.
  Touches: `filters/options/` (new `NativeLibOption`), the `IFilterableAppInfo` seam (expose
  ELF-alignment / 32-bit-only / compressed-native-libs flags), `details/` + main-list row chip.
  Cross-ref: shares the `IFilterableAppInfo` extension with the blocked "weak-signature Finder
  filter" item — do both once the seam is extended.
  Acceptance: a Finder filter lists apps that are not 16 KB-ready / 32-bit-only / ship
  compressed native libs; App Details and the main row show a readiness chip; a host test
  covers the predicate over fixture ELF data. (Chip visual check needs on-device theme pass.)
  Complexity: M

- [ ] P3 — Restricted-settings detector for sideloaded apps
  Why: Android 14+ gates accessibility, notification-listener, and health access behind the
  "allow restricted settings" prompt for sideloaded apps; surfacing which installed apps are
  currently blocked is a genuine inspector signal no NG screen provides.
  Evidence: developer.android.com/about/versions/16/behavior-changes-16 (restricted settings);
  `permission/` and `details/` permission views (no restricted-settings indicator);
  `compat/AppOpsManagerCompat.java`.
  Touches: `compat/AppOpsManagerCompat.java` (query the restricted-settings op),
  `details/`/`permissions/` (indicator row), strings.
  Acceptance: apps blocked by the restricted-settings gate show a labelled indicator in the
  permission view; a host test covers the mapping. (The exact AppOps op / API across API 34-36
  needs on-device confirmation — see RESEARCH Open Questions.)
  Complexity: M

## Research-Driven Additions (2026-07-29)

### P0

### P1

### P2

