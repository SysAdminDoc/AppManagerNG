<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# MEMORY_CONSOLIDATION — 2026-05-17 pass 2

Delta against [`../2026-05-17/MEMORY_CONSOLIDATION.md`](../2026-05-17/MEMORY_CONSOLIDATION.md).

This pass executed the recommendations from pass-1's §5 ("Recommended pointer
additions"). No new instruction files were discovered.

---

## 1. Pass-1 recommendations applied

| Recommendation | Status | Where |
|---|---|---|
| Add `## Canonical Project Context` pointer to `CLAUDE.md` | ✅ applied (local-only; `CLAUDE.md` is gitignored at `.gitignore:32`) | Commit `2846225`'s working-tree-only edit |
| Add `## Canonical Project Context` pointer to `AGENTS.md` | ✅ applied | Commit `2846225` |
| Refresh `CLAUDE.md` Status section to reflect iter-23 / v0.4.2 | ✅ partial — the new "Canonical Project Context" section explicitly notes "The Status section below stops at factory-iter-7 (2026-05-02); iter-18 → iter-23 (through 2026-05-16, v0.4.2) live in `ROADMAP.md` + `CHANGELOG.md` and the 2026-05-17 consolidation pass artifacts at `.ai/research/2026-05-17/`" | Commit `2846225` |
| Refresh `README.md` "Roadmap" preview to match ROADMAP "Committed Version Targets" | ✅ applied | Commit `2846225` |

The four items above closed in pass-1. The remaining pass-1 backlog items (F-NEW-09, F-NEW-11, F-NEW-13, F-NEW-14) were classified as **Next**-tier — pass-2 executed only the highest-leverage one (F-NEW-14, architecture docs).

---

## 2. No new conflicts found

A re-scan of `AGENTS.md` / `CLAUDE.md` / `README.md` / `ROADMAP.md` / `CHANGELOG.md` /
`PROJECT_CONTEXT.md` after pass-1's edits shows **no contradictions** introduced. The
pass-1 pointer-addition contract was non-destructive and all internal cross-references
resolve.

---

## 3. New canonical content in pass-2

| Surface | Purpose |
|---|---|
| `docs/architecture/01-privilege-providers.md` | Canonical doc for the privilege-provider stack (Root / Shizuku / Sui / ADB / no-root). Cited from `PROJECT_CONTEXT.md` §"Privilege provider matrix" and from `ROADMAP.md` T5. |
| `docs/architecture/02-backup-format.md` | Canonical doc for backup engine + crypto envelope + metadata schema v1–v6. Cited from `PROJECT_CONTEXT.md` §"Backup engine" and from `ROADMAP.md` T6. |
| `docs/architecture/03-hidden-api-bypass.md` | Canonical doc for the four-layer hidden-API stack. Cited from `PROJECT_CONTEXT.md` and from the targetSdk=37 audit batch in the Engineering Debt Register. |
| `docs/architecture/README.md` | Navigation guide for the architecture dir. |

Future contributors discovering one of the load-bearing subsystems can now find the
architecture doc, the corresponding ROADMAP tier, the audit doctrine, and the code in
~3 clicks instead of doing a fresh source-tree walk.

---

## 4. Updated `MEMORY.md` index?

The user-level memory file at `C:\Users\Xray\.claude\projects\C--Users-Xray\memory\MEMORY.md`
(per the auto-memory system in this Claude Code config) does not yet have an entry for
AppManagerNG. No entry was added in this session because:

- The project-state context lives in the **repo** (PROJECT_CONTEXT.md), which any AI session can read from the working directory.
- A user-memory-file pointer at `C:\` would only help sessions where the user is in a different working directory and references the project by name. That's a useful future addition; left for explicit user direction.

The existing user memory has entries for [vmware-pc-repos-root](C:\Users\Xray\.claude\projects\C--Users-Xray\memory\vmware-pc-repos-root.md), [SwiftFloris](C:\Users\Xray\.claude\projects\C--Users-Xray\memory\swiftfloris-project.md), and [SwiftFloris git auth](C:\Users\Xray\.claude\projects\C--Users-Xray\memory\swiftfloris-git-auth.md) but no AppManagerNG entry.

---

## 5. Open conflicts: none

Pass-1 closed at "no hard conflicts"; pass-2 added work consistent with that posture.
The Shizuku Android-17 regression is a **new external risk**, not a project-memory
conflict — it's appropriately captured in ROADMAP iter-25 as a Now-tier T5 row.
