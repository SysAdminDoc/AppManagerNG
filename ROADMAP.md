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

- [ ] P3 — DDG Tracker Radar as supplementary tracker source
  Why: TrackerControl demonstrates that layering DuckDuckGo's mobile-specific tracker database on top of Exodus catches mobile-specific trackers that Exodus misses. DDG Tracker Radar is MIT-licensed, JSON-formatted, and maintained by DuckDuckGo.
  Evidence: TrackerControl multi-source approach (Disconnect + DDG + in-house); DDG Tracker Radar mobile TDS at staticcdn.duckduckgo.com
  Touches: app/src/main/assets/ (tracker data), scanner/ (signature matching), full-flavor debloat-definition updater
  Acceptance: tracker scanner reports trackers found via DDG Tracker Radar alongside Exodus signatures; source attribution visible in tracker detail view; full flavor auto-updates the DDG list; floss flavor ships a bundled snapshot
  Complexity: M

All remaining blocked items are in `Roadmap_Blocked.md`.
