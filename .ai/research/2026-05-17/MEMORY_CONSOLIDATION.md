<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# MEMORY_CONSOLIDATION — 2026-05-17

Inventory + reconciliation of every AI-memory / instruction / archival document in the
repository. Goal: identify what each artifact is for, what conflicts exist, and what was
folded into the canonical `PROJECT_CONTEXT.md` index.

---

## 1. Files found

### Tool-specific instruction files

| Path | Purpose | Verdict |
|------|---------|---------|
| [`CLAUDE.md`](../../../CLAUDE.md) | Claude Code working notes. Stack, build commands, origin from upstream `3d11bcb`, version status v0.1.0 → v0.4.2, gotchas (debug keystore intentional, applicationId vs source namespace, submodules required, hardcoded "App Manager" string debt). 129 lines. | **Keep — primary tool-specific entry.** Linked from `PROJECT_CONTEXT.md` §2. |
| [`AGENTS.md`](../../../AGENTS.md) | Pointer to `CLAUDE.md` for project-side notes + `~/.codex/AGENTS.md` for global rules + `~/.claude/CLAUDE.md` + shared codex memory dir. 9 lines. | **Keep — pointer file, do not expand.** Already correctly delegates. |
| [`codexprompt.md`](../../../codexprompt.md) | Standalone design-brief prompt for the Premium Facelift design system. Drove the `design/` directory output (Phase 0 recon → Phase 4 pain-point inventory). 346 lines. | **Keep — prompt artifact for the design facelift workstream.** It is not project memory; it is a job spec already executed. Treat as historical reference. |

### Roadmap / changelog / research

| Path | Purpose | Verdict |
|------|---------|---------|
| [`ROADMAP.md`](../../../ROADMAP.md) | Living plan. Tier-organised (Now / Next / Later / Under Consideration / Rejected), Iter-18 → Iter-23 research deltas, Premium Polish Track (v0.4.x → v0.7.x), Engineering Debt Register, Upstream Sync Strategy, Source Appendix (S01–S315). **1135 lines.** Last updated 2026-05-16. | **Source of truth for planned work.** Do not rewrite — extend in iter-N+1 sections. |
| [`CHANGELOG.md`](../../../CHANGELOG.md) | Per-release notes back to v0.1.0; "Unreleased" currently holds 2026-05-14 → 2026-05-16 shipped work. 880 lines. Loose Keep-a-Changelog format. | **Source of truth for shipped work.** |
| [`research/iter-20-delta.md`](../../../research/iter-20-delta.md) | Free-form 2026-05-08 issue-mining notes (MuntashirAkon/AppManager #1956–#1968, Canta, Hail, Neo-Backup, sdmaid-se, UAD-NG, Inure, Material Files). | **Keep** — historical research artifact. Subsequent iters (21/22/23) live inline in ROADMAP rather than as separate research files. |
| [`docs/research/2026-05-02-android-power-tools.md`](../../../docs/research/2026-05-02-android-power-tools.md) | Competitive landscape sweep. | Keep — referenced from ROADMAP top. |
| [`docs/research/2026-05-09-capability-extension.md`](../../../docs/research/2026-05-09-capability-extension.md) | Datasets, privacy intelligence, modern Android APIs. | Keep. |
| [`docs/research/2026-05-09-observability-testing-audit.md`](../../../docs/research/2026-05-09-observability-testing-audit.md) | Observability + testing audit. | Keep. |
| [`docs/research/2026-05-09-roadmap-extension-phase-2.md`](../../../docs/research/2026-05-09-roadmap-extension-phase-2.md) | Reliability, migration, automation, form factors, i18n, observability. | Keep. |
| [`docs/research/iter-6-delta.md`](../../../docs/research/iter-6-delta.md) | Older research delta. | Keep — historical. |
| [`docs/audits/`](../../../docs/audits/) | 14 dated audit files (elegantTextHeight, adaptive layout, Android-17 MessageQueue, Android-17 keystore key cap, Android-18 implicit URI grant, GCM cipher reuse, zip-slip, libsu Shell.cmd, Gson 2.14, BouncyCastle 1.84, predictive-back WebView, Play contacts/location policy, Android-17 System.load read-only, Android-17 static-final reflection). | Keep — pattern is `<date>-<topic>.md`. |
| [`docs/policy/minsdk-21-ceiling.md`](../../../docs/policy/minsdk-21-ceiling.md) | Ledger of every dep that has dropped or imminently drops API 21-22 support. | Keep — load-bearing for any version bump. |
| [`docs/distribution/`](../../../docs/distribution/) | 5 distribution docs (Obtainium config + REUSE license sidecar, reproducible-builds, backup-destinations matrix, package-visibility dossier). | Keep. |
| [`docs/security-advisories/`](../../../docs/security-advisories/) | CVE-2026-0073 advisory. | Keep. |
| [`docs/intent-api.md`](../../../docs/intent-api.md) | Deep-link contract. | Keep. |
| [`docs/sideload-verification.md`](../../../docs/sideload-verification.md) | Position document for Google's developer-verification rollout. | Keep. |
| [`design/`](../../../design/) | Premium-facelift output (spec, impl XML, plan, audit). 7 deliverable files per `codexprompt.md` definition-of-done. | Keep. |

### What does **not** exist that the prompt template asked about

- `~/.codex/AGENTS.md` — referenced by `AGENTS.md` but lives in user home, not repo. Not a project memory; not in scope here.
- `.cursor/rules/**`, `.cursorrules`, `.windsurfrules`, `GEMINI.md`, `COPILOT_INSTRUCTIONS.md`, `.github/copilot-instructions.md` — none present.
- `.claude/`, `.claude-instructions` — none present.
- `ARCHITECTURE.md` — none present; architecture notes live in `CLAUDE.md` §"Architecture (high-level)" and in `docs/audits/`. Roadmap T11 row "Architecture Documentation" tracks the gap.
- `CONTRIBUTING.md` — present at repo root per CHANGELOG (replaces upstream's `CONTRIBUTING.rst`).
- `.ai/` — did **not** exist before this session. Created today at `.ai/research/2026-05-17/`.

---

## 2. Conflicts and reconciliation

This pass found **no hard conflicts** between `AGENTS.md`, `CLAUDE.md`, `ROADMAP.md`, and
`CHANGELOG.md`. The three places where claims overlap and how they're reconciled:

| Claim | `CLAUDE.md` says | `ROADMAP.md` says | `CHANGELOG.md` says | Resolution |
|---|---|---|---|---|
| Current version | v0.4.2 (2026-05-13) | v0.4.2 ✅; v0.5.0 next | v0.4.2 (2026-05-13); Unreleased section accumulating | **Agreed.** Three docs in lockstep. |
| Stack — Compose? | "NOT Compose — would be a multi-year migration" | T21 row "Material 3 Adaptive" cross-references Compose `androidx.compose.material3.adaptive` but for view-based equivalents (`SlidingPaneLayout`) | n/a | **Agreed: Compose is out of scope.** Roadmap rows that *reference* Compose patterns are aspirational benchmarks, not migration plans. `codexprompt.md` reinforces ("Compose migration is a multi-year project and is explicitly out of scope"). |
| Iter status | "factory iter-7 (2026-05-02)" mentioned | Roadmap goes through Iter-23 (2026-05-08+) | Last-updated 2026-05-16 | **Roadmap is freshest.** `CLAUDE.md` "Status" section is **stale** at iter-6/iter-7 — last edited around v0.3.0 post-iter-7 drain (2026-05-02). The iter-18 → iter-23 research deltas live in ROADMAP, not `CLAUDE.md`. **Open item** — `CLAUDE.md` should be refreshed to cover at least the iter-23 / v0.4.2 milestone. See §3 below. |
| Multi-format backup | Implicit | T6 backup polish | Metadata v6 shipped 2026-05-16 | **Agreed.** Crypto modes: AES (Android Keystore-backed, hardware-isolated), RSA, ECC (hybrid), OpenPGP. AES was "PBKDF2 sketch" in older roadmap text; current implementation uses Keystore — roadmap row was annotated 2026-05-16 to reflect ground truth. |

### Stale claims discovered

1. `CLAUDE.md` § Status — ends at iter-7 (2026-05-02). The iter-18 → iter-23 deltas (six iterations, ~70 roadmap rows, three security audits, three platform-compliance audits, multiple shipped features) are not represented. `PROJECT_CONTEXT.md` is now the up-to-date index; future contributors should look there first per the new top-of-file note.
2. `CLAUDE.md` §"Pulling fixes and features from upstream aggressively" — strategy still accurate, but `upstream-rename-watch.yml` (added 2026-05-09) and the weekly issue-template support-bundle intake (added 2026-05-16) are newer process surfaces not yet reflected.
3. `README.md` "Roadmap" section preview lists v0.2/v0.3/v0.4/**v0.5.0 = Onboarding** — but the **v0.4.0 Onboarding Capability Wizard** already shipped 2026-05-14 (via `feat: refine capability onboarding`), and the actual v0.5.0 theme per ROADMAP is **Settings & Discovery + global in-app search + in-app changelog viewer**. README copy lags behind ROADMAP.

These are minor copy debt, not contradictions. None are load-bearing.

---

## 3. What was extracted into `PROJECT_CONTEXT.md`

The canonical entry-point index extracts these durable facts from the scattered sources:

- One-paragraph project thesis (consolidates `CLAUDE.md` §"What this is" + `README.md` §"What is AppManagerNG?")
- Reading-order table for the load-bearing files (linked, not duplicated)
- Build commands + key dep pins (cross-references `versions.gradle` line-by-line)
- The in-progress uncommitted work (new — none of the existing docs flagged this)
- Hard project facts (license, identity, debug keystore intentional, submodules) — consolidated from `CLAUDE.md` §"Gotchas" + §"License obligations" + §"applicationId vs source namespace"
- Privilege provider matrix (consolidated from multiple ROADMAP T5 rows + commit history)
- Backup engine summary (consolidated from ROADMAP T6 rows + recent CHANGELOG entries)

What was **not** extracted (because it lives best where it is):

- Per-iteration research deltas — those belong in `ROADMAP.md` or `research/`
- Per-audit verdicts — those belong in `docs/audits/`
- Per-release notes — those belong in `CHANGELOG.md`
- Tool-specific instructions for Claude / Codex — those stay in `CLAUDE.md` / `AGENTS.md`

---

## 4. Open conflicts (none)

None. The repo's planning surface is internally consistent.

---

## 5. Recommended pointer additions (suggested but NOT automatically applied)

Per the prompt's safety rule ("do not destructively rewrite project files, ... add pointers from older files"), the following additions would be **safe to apply** but were left for human review:

### Suggested addition to `CLAUDE.md`

A one-line pointer near the top:

```md
## Canonical Project Context

For consolidated project memory, current architecture, known gaps, and roadmap context,
see `PROJECT_CONTEXT.md`. This file remains the tool-specific working-notes file for
Claude sessions on AppManagerNG.
```

### Suggested addition to `AGENTS.md`

A one-line pointer:

```md
## Canonical Project Context

For consolidated project memory and entry-point reading order, see `PROJECT_CONTEXT.md`.
```

(Both were drafted but not committed in this session — they are mechanically applied
once the maintainer approves the new `PROJECT_CONTEXT.md` as a load-bearing file.)

---

## 6. Future-AI breadcrumb

If you're an AI agent reading this on a future session: **start at
[`PROJECT_CONTEXT.md`](../../../PROJECT_CONTEXT.md)**, then follow its §2 reading-order
table. Do not begin a fresh recon from `find . -name '*.md'`; that wastes a substantial
context window and re-discovers what 23 iterations have already mined.

The 315-source register in ROADMAP §"Source Appendix" is the bottom-of-stack reference
for external claims. Add new sources there as `[S316]`, `[S317]`, etc. — do not
recapture the same competitor / API / CVE under a fresh ID.
