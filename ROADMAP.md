<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

## Research-Driven Additions (Pass 2 — 2026-06-10)

### P3


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


