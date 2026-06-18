<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Audit Findings (2026-06-17)

### P3

- [ ] P3 — Code editor diff: background thread diff computation
  Why: showDiffDialog() computes the diff on the main thread; files with 5,000+ changed lines could cause ANR on mid-range devices despite the 20k-line and 500-display-line caps.
  Where: editor/CodeEditorFragment.java (showDiffDialog)

- [ ] P3 — Code editor diff: Myers algorithm for accurate move detection
  Why: The current line-by-line diff with a fixed lookahead of 5 produces incorrect output when lines are moved more than 5 positions; a proper LCS/Myers algorithm would handle refactoring scenarios correctly.
  Where: editor/CodeEditorFragment.java (showDiffDialog, containsAhead)
