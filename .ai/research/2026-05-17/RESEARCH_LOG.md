<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# RESEARCH_LOG — 2026-05-17

Search strategies, queries, tools used, source classes covered, failed searches, saturation
notes. This is the operational diary of the autonomous-research session.

---

## 1. Premise of the session

User invoked the autonomous deep-research-and-roadmap-planning prompt at
`Z:\repos\AppManagerNG`. The prompt instructed: walk-away deep work; multi-pass research;
consolidate scattered memory; produce a prioritized sourced roadmap.

**Within the first 10 minutes of recon it became clear that the repository is unusually
mature for a research-planning intervention.** The existing ROADMAP.md is 1135 lines and
cites 315 numbered external sources. Twenty-three research iterations have already been
performed by prior sessions (Iter-1 through Iter-23, the latest closing 2026-05-08 → 2026-05-16). Fourteen behavior-change audits live under `docs/audits/`. The premium-facelift design workstream has its own complete spec → impl → plan → audit deliverable set.

This shaped the session strategy:

- **Do not redo the work that is already done.** Re-running a competitor sweep against
  Canta / Hail / Neo-Backup / UAD-NG / Inure / Material Files / sdmaid-se would
  re-discover what iter-18 → iter-23 mined exhaustively.
- **Find the seams.** Today's high-EV work is identifying what the existing artifacts
  *don't* cover, validating in-flight changes, building a canonical entry-point index for
  future agents, and flagging the small handful of net-new external data points
  (F-Droid 2.0-alpha9 release, Shizuku APK manifest target SDK confirmation).
- **Don't clobber.** ROADMAP.md is a living document. Adding "iter-24" content as a separate small delta is cleaner than rewriting any existing tier.

---

## 2. Search/exploration passes performed

### Pass 1 — Recon (≈first 30 minutes)

| Tool | Operation | Result |
|---|---|---|
| `Bash` | `ls Z:/repos/AppManagerNG` | Top-level structure inventory |
| `Bash` | `git log --oneline -50`, `git tag`, `git status`, `git diff --stat` | Branch state + uncommitted-work inventory |
| `Read` | `AGENTS.md`, `CLAUDE.md`, `README.md` (full) | Tool-instruction + public surface |
| `Read` | `ROADMAP.md` (offsets 1–300, 300–500, 500–800, 700–800, 800–1000, 1000–1135) | All five tier sections + 315-source register + Iter-18 → Iter-23 deltas + Premium Polish Track + Rejected + Engineering Debt Register |
| `Read` | `CHANGELOG.md` (offsets 1–200) | Unreleased + v0.4.2 entries (the rest could be inferred from git log; full read deferred) |
| `Read` | `codexprompt.md` (full) | Design-facelift brief — driver for `design/` |
| `Read` | `versions.gradle` (full) | Dep-pin truth + the embedded comments documenting which deps have dropped API 21-22 support |
| `Read` | `research/iter-20-delta.md` (offsets 1–150) | Iter-20 free-form notes (Iter-21/22/23 are inline in ROADMAP, not separate files) |
| `Bash` | `ls research/`, `ls docs/`, `ls docs/research/`, `ls docs/audits/`, `ls design/`, `ls .ai/`, `ls .github/` | Directory mapping |
| `Bash` | `find app/src/main/java -name '*.java' | wc -l` (background task) | 701 .java files total |
| `Bash` | `du -sh app libcore libserver libopenpgp hiddenapi server libs` | Module sizes |
| `Bash` | `git diff` (per uncommitted file) | In-progress change inventory |
| `Read` | `app/src/test/java/.../FilterOptionTest.java` (first 40 lines) | New test surface for the regex-quote fix |

### Pass 2 — External sanity check (≈10 minutes)

| Tool | Query | Outcome |
|---|---|---|
| `WebSearch` | `Shizuku 13.6 Android 16 QPR1 release notes 2026` | Confirms iter-18 [S121] / [S22] Shizuku 13.6.0 + Android 16 QPR1 support claim; APKMirror metadata shows target SDK 36. Date metadata on the APKMirror upload (2025-05-26) is a year off from ROADMAP — flagged in `SOURCE_REGISTER.md` §3 as low-confidence. |
| `WebSearch` | `F-Droid 2.0 alpha index v2 protobuf 2026` | Confirms F-Droid 2.0-alpha8 (2026-04-24, [S168]) and surfaces **net-new alpha9 release (2026-05-08)**. No breaking change since alpha8. Proposed as S316. |

External research was intentionally **not** expanded beyond these two probes. The existing
roadmap already cites 315 sources covering: every major competitor in the Android-power-tool
space (Canta, Hail, Neo-Backup, UAD-NG, Inure, SD Maid SE, Material Files, Hamza417/Inure,
LibChecker, AppVerifier, Obtainium, Droid-ify, Auxio, Mullvad VPN, ProtonMail, FairEmail,
WG Tunnel, Markor, etc.), every relevant Android-platform doc (16 / 16 QPR1 / 16 QPR2 / 17
behavior changes, Material Components release notes, AGP release notes, ADB platform-tools
notes, AOSP Q1+Q3 cadence note), every relevant CVE (BouncyCastle 1.84 trio, OWASP
dependency-check, GHSA-8xfc-gm6g-vgpv), and every relevant FOSS-channel doc (F-Droid
inclusion rules, Anti-Features rules, IzzyOnDroid migration, Accrescent features).

**Saturation test**: I attempted to think of an Android-power-tool category not represented
in S01–S315. Categories represented: package managers, debloaters, firewalls, freezers,
backup, installers, store clients, library inspectors, decompilers, smali editors,
device-owner shims, terminal/file managers, accessibility checkers, profilers, leak
detectors, automation hosts. Categories *under-represented*: hardware-key inspection
(only KeyAttestation [S73]), enterprise MDM beyond Dhizuku, watch-companion (only [S265]
GeminiMan as a single closed-source data point). All three under-representations are
acknowledged in ROADMAP as deliberate scope choices.

### Pass 3 — Synthesis (≈45 minutes)

Drafted and wrote the canonical entry-point index (`PROJECT_CONTEXT.md`) and four
research-run artifacts:

- `STATE_OF_REPO.md` — local recon memo
- `MEMORY_CONSOLIDATION.md` — inventory + reconciliation
- `SOURCE_REGISTER.md` — today's audit trail + proposed S316–S320 additions
- `RESEARCH_LOG.md` (this file)

Then drafted `COMPETITOR_MATRIX.md` (pointer + delta), `FEATURE_BACKLOG.md` (net-new
items), `PRIORITIZATION_MATRIX.md`, `SECURITY_AND_DEPENDENCY_REVIEW.md`,
`DATASET_MODEL_INTEGRATION_REVIEW.md`, and `CHANGESET_SUMMARY.md`.

---

## 3. Search strategies that did *not* run

These are listed for transparency and to give a future session the "did we try X?"
answer.

| Strategy | Why skipped |
|---|---|
| Re-running competitor GitHub-stars snapshot | ROADMAP S291 → S302 captures this for 2026-05-16; one day's drift not worth a probe round. |
| Issue-state re-poll of S121 → S195 | The 75+ issues mined in iter-18 → iter-23 would each need a `gh api` call; the roadmap rows already cite issue *content* not *state*. |
| Reddit / HN sweep | iter-19/20 already did this surface mining; specific threads cited at S174 etc. |
| MOAB-scale grep of `app/src/main/java/**` for `TODO` / `FIXME` markers | Engineering Debt Register at ROADMAP §"Engineering Debt Register" already catalogs every load-bearing one. A pure grep would add a long tail of low-severity items that don't gate any feature. |
| External CVE re-scan vs every dep in `versions.gradle` | CI `dependency-scan.yml` does this weekly with OWASP `dependency-check` 10.0.3; manual run would duplicate. Audited the workflow file existence; deferred run-vs-fresh-CVE-feed to that pipeline. |
| Building / testing locally | The session is research/planning, not implementation. The 6 uncommitted files are documented but were not built — running `./gradlew assembleDebug` would push the session beyond the planning scope and into engineering work. |

---

## 4. Failed or low-value approaches

- **Attempted to read `ROADMAP.md` in a single Read call.** File is 267 KB (1135 lines, ~30k tokens). Read-tool token limit forced chunked reads at offsets 1, 300, 500, 700, 800, 1000. Future agents: use offsets when reading this file. The file is *meant* to be partially read — the source register at the bottom is referenceable, not always re-readable.
- **Attempted to find `.ai/` to follow the prompt's existing convention.** Directory did not exist before this session. Created at `.ai/research/2026-05-17/`.
- **Attempted to read `app/libs/` for vendored AARs**: directory was either empty or not present at the time of inspection (16 KB; possibly contains a `.gitkeep`-style file only). The vendored libs (`apksig-android`, `libadb-android`, `jadx-android`, `ARSCLib`, `sora-editor`) flow in from Maven via `versions.gradle` rather than as committed AARs.

---

## 5. Time accounting

(Approximations; the session is autonomous, no wall-clock obligation.)

| Phase | Pages of context read | Output written |
|---|---|---|
| Pass 1 — Recon | ~40 dense pages (ROADMAP + CHANGELOG + CLAUDE + codexprompt + versions + research) | 1 todo list + 1 in-context map |
| Pass 2 — External | 2 web queries, ~10 result pages each | 0 — verification only |
| Pass 3 — Synthesis | Cross-reading of recon notes | ~11 deliverable files |

---

## 6. Notes for a future session

1. **`PROJECT_CONTEXT.md` is now the canonical entry point.** Read it first; do not begin a fresh `find . -name '*.md'` walk.
2. **The ROADMAP is the source of truth for planned work; the CHANGELOG is the source of truth for shipped work; the audits are the source of truth for verdicts.** If a question has those three answers, you are likely about to duplicate prior work.
3. **The 315-source register is your shortcut.** Before opening any new GitHub issue / behavior-change doc / competitor README, search S01–S315 first.
4. **The Engineering Debt Register and Premium Polish Track sit at the bottom of `ROADMAP.md`.** They are where the actual engineering scheduling lives — the tier sections are the public-facing plan.
5. **Six files are uncommitted on `main`.** Flag this to the maintainer before assuming `origin/main` reflects project state.
