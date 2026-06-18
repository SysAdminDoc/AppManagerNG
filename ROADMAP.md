<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

## Research-Driven Additions (Pass 2 — 2026-06-10)

### P3

- [ ] P3 — Boot-component manager view
  Why: A dedicated "what starts at boot" surface (BOOT_COMPLETED receivers across all apps, batch-blockable) is a classic MyAndroidTools/Inure feature NG can build almost entirely from existing component-blocking plumbing; today only NG's own BootReceiver references BOOT_COMPLETED (verified).
  Evidence: Inure boot manager (FEATURES.md); https://github.com/lihenggui/blocker (MyAndroidTools rule import demand); grep: no cross-app boot view in tree (verified)
  Touches: new view under main menu (reuse component list UI), rules/compontents/ (existing IFW/disable paths), filters/
  Acceptance: a screen lists every app with BOOT_COMPLETED/LOCKED_BOOT_COMPLETED receivers and their enable state; per-row and batch block/unblock work through the existing rule store with undo.
  Complexity: M


- [ ] P3 — Tracker report rollup: company → category → jurisdiction
  Why: TrackerControl's grouped presentation (parent company, ads/analytics/social category, HQ country) with plain-language blurbs is meaningfully more legible than flat library lists and is pure offline metadata; NG's TrackerInfoDialog already resolves tracker identity (verified) — the rollup is presentation work.
  Evidence: https://trackercontrol.org/ ; scanner/TrackerInfoDialog.java + rules/compontents/TrackerCategory.java (verified)
  Touches: scanner/ (report screen grouping), tracker metadata (extend the bundled dataset with company/category/country columns), strings (blurbs)
  Acceptance: the per-app tracker report groups findings by parent company with category chips and a one-line "what this category means"; flat list remains available as a toggle; works fully offline.
  Complexity: M

## Deep Audit Follow-ups (2026-06-11)

Deferred from the 2026-06-11 deep engineering/QA/UX audit pass. The fixed half
of that pass is in the commit history / CHANGELOG `Unreleased`. Device-gated
items (badge touch targets, sibling row V2 verification) moved to
`Roadmap_Blocked.md`.

## Improvement Sweep (2026-06-11)

Findings from a six-domain codebase sweep (settings/onboarding, file-manager/editor,
installer/scanner, running-apps/usage, rules/profiles/automation, CI/build/distribution).
Deduplicated against all sections above.

### P3

- [ ] P3 — Code editor: undo history browser + diff view
  Why: The editor supports undo/redo but no UI to browse history depth or see what changed. No file-version diff view for comparing current edits against the on-disk version.
  Evidence: editor/CodeEditorFragment.java:652-656 (undo/redo wired, no history UI)
  Touches: editor/ (undo history panel, simple inline diff)
  Acceptance: a toolbar action shows the undo stack; a diff toggle highlights changes vs. the saved file.
  Complexity: M

## Research-Driven Additions (Pass 3 — 2026-06-13)

### P3

- [ ] P3 — Scheduled cache/data clearing as routine operation type
  Why: SD Maid SE's scheduled cache-clearing is the #1 feature users associate with automated Android maintenance. NG's RoutineScheduler (v0.6.0 target) already has the executor pattern for scheduled operations but does not include cache or expendable-data clearing as an operation type.
  Evidence: https://github.com/d4rken-org/sdmaid-se (scheduled cache clearing); profiles/RoutineScheduler.java and profiles/RoutineWorker.java (verified: no CLEAR_CACHE operation type); compat/PackageManagerCompat.java (freeStorageAndNotify available for privileged modes)
  Touches: profiles/ (add CLEAR_CACHE and CLEAR_DATA operation types to RoutineScheduler), compat/PackageManagerCompat.java (cache-clearing wrapper), settings/ (per-profile operation type selector)
  Acceptance: a routine can include "clear cache" or "clear expendable data" as an operation, scoped to specific apps or app-set filters; scheduled execution clears cache for matched apps and logs byte counts; requires root or Shizuku privilege; operation type cleanly refused with explanation on no-root mode.

## Audit Findings (2026-06-17)

### P3

- [ ] P3 — Component intent-action finder
  Why: Users need to understand why apps start and which broadcast/action paths exist, while adjacent component managers expose intent-action filtering as a key discovery tool.
  Evidence: upstream App Manager startup-question issue; Blocker component search and intent-action filter issues; existing AppManagerNG component/rules packages.
  Touches: component list/search UI; rules/component scanner; app details component tabs; search/filter tests.
  Acceptance: users can search or filter components by intent action, category, exported state, and enabled rule state across apps, then jump directly to the component control; boot-manager work can reuse the same finder backend.
  Complexity: M
