<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

### P3

- [ ] P3 — UAD-ng debloat safety cross-reference
  Why: Canta's killer feature is showing Recommended/Advanced/Expert/Unsafe safety badges per OEM package from the UAD-ng community database. NG's debloater has OemBloatRiskTable and debloat presets but doesn't cross-reference the UAD-ng dataset (15k+ packages with safety ratings).
  Evidence: Canta (5k stars) — UAD-ng integration is its primary differentiator; Universal-Debloater-Alliance/universal-android-debloater-next-generation
  Touches: debloat/ (DebloaterViewModel, DebloaterRecyclerViewAdapter, OemBloatRiskTable), scripts/android-debloat-list submodule or new data source
  Acceptance: debloater list shows UAD-ng safety badge (Recommended/Advanced/Expert/Unsafe) alongside existing bloat risk when available; badge absent for packages not in the UAD-ng database; data refreshed via the existing debloat-definition updater in full flavor
  Complexity: M

- [ ] P3 — DDG Tracker Radar as supplementary tracker source
  Why: TrackerControl demonstrates that layering DuckDuckGo's mobile-specific tracker database on top of Exodus catches mobile-specific trackers that Exodus misses. DDG Tracker Radar is MIT-licensed, JSON-formatted, and maintained by DuckDuckGo.
  Evidence: TrackerControl multi-source approach (Disconnect + DDG + in-house); DDG Tracker Radar mobile TDS at staticcdn.duckduckgo.com
  Touches: app/src/main/assets/ (tracker data), scanner/ (signature matching), full-flavor debloat-definition updater
  Acceptance: tracker scanner reports trackers found via DDG Tracker Radar alongside Exodus signatures; source attribution visible in tracker detail view; full flavor auto-updates the DDG list; floss flavor ships a bundled snapshot
  Complexity: M

- [ ] P3 — MyAndroidTools rule import
  Why: Many power users migrating from MyAndroidTools or Blocker have existing component-blocking rule sets. Blocker already imports MAT backups and converts to IFW rules. NG should support the same import path.
  Evidence: Blocker MAT import feature (PR history); MyAndroidTools user base; existing IFW import infrastructure in rules/compontents/
  Touches: rules/ (new MAT format parser), settings/ (import UI entry point), debloat/ or details/ (import action)
  Acceptance: Settings → Rules → Import accepts a MyAndroidTools backup file; rules are converted to NG's IFW format and applied; import report shows converted/skipped/failed counts; round-trip test with a sample MAT export file
  Complexity: S

All remaining blocked items are in `Roadmap_Blocked.md`.
