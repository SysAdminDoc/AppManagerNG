<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# PRIORITIZATION_MATRIX — 2026-05-17

Scored and tiered candidates from this research session. Limited to **net-new items** —
existing ROADMAP rows already carry their own effort / tier annotations and are not
re-scored here.

Scoring rubric (1–5 each, higher = stronger signal):

- **Impact** — what the change unblocks or repairs (1 = trivial polish, 5 = ships a feature theme / closes a security hole)
- **Effort** — engineering cost (1 = ≤30 min commit, 5 = multi-day work)
- **Risk** — potential to regress (1 = pure docs, 5 = touches load-bearing crypto or privilege path)
- **Evidence** — strength of the citation behind it (1 = hunch, 5 = directly observed in repo or upstream issue)

Tier verdict slots into the existing ROADMAP scheme: **Now / Next / Later / Under
Consideration / Rejected**.

---

## 1. Scoring table

| ID | Item | Impact | Effort | Risk | Evidence | Recommended tier |
|---|---|---:|---:|---:|---:|---|
| **F-NEW-01** | Finder regex predicate fix — commit existing fix | **5** | 1 | 2 | 5 | **Now** (commit-ready) |
| **F-NEW-02** | Install transcript URI redactor — commit security fix | **5** | 1 | 1 | 5 | **Now** (commit-ready) |
| **F-NEW-03** | Onboarding root-probe fragment-detach race fix | 3 | 1 | 1 | 5 | **Now** (commit-ready) |
| **F-NEW-04** | Refresh `CLAUDE.md` Status section to iter-23 / v0.4.2 | 3 | 1 | 1 | 5 | **Now** (governance) |
| **F-NEW-05** | Refresh `README.md` Roadmap preview to current targets | 3 | 1 | 1 | 5 | **Now** (governance) |
| **F-NEW-06** | "Now/Next/Later" summary at top of ROADMAP | 2 | 1 | 1 | 3 | **Under Consideration** (optional UX of the doc itself) |
| **F-NEW-07** | F-Droid 2.0-alpha9 source-register addition (S316) | 1 | 1 | 1 | 5 | **Now** (governance — source register hygiene) |
| **F-NEW-08** | Normalize `versions.gradle` inline minSdk notes | 2 | 1 | 1 | 5 | **Now** (low-effort governance) |
| **F-NEW-09** | Document Material-1.14 → minSdk-23 cascade in the ceiling ledger | 3 | 2 | 1 | 4 | **Next** (Eng-Debt, multi-line ledger work) |
| **F-NEW-10** | Issue-state refresh script for the S-source register | 2 | 2 | 1 | 3 | **Under Consideration** (depends on whether issue *state* matters as a roadmap signal) |
| **F-NEW-11** | JaCoCo coverage badge in `tests.yml` | 3 | 2 | 1 | 4 | **Next** (T11 observability adjunct) |
| **F-NEW-12** | `docs/audits/README.md` documenting the audit-doc pattern | 3 | 1 | 1 | 5 | **Now** (trivial governance) |
| **F-NEW-13** | Markdown link checker in `lint.yml` covering `PROJECT_CONTEXT.md` | 2 | 1 | 1 | 4 | **Next** (link-rot insurance) |
| **F-NEW-14** | `docs/architecture/{01-privilege,02-backup,03-hidden-api}.md` | **5** | 3 | 1 | 5 | **Next** (highest-leverage net-new item) |

---

## 2. Tier-grouped output

### Now (commit-ready or trivial governance, this iteration)

1. **F-NEW-01** — Commit the regex-predicate fix + new `FilterOptionTest.java`. (`fix(finder): user-supplied regex predicates no longer Pattern.quote'd into literals`)
2. **F-NEW-02** — Commit the install-transcript URI redactor hardening + expanded `InstallTranscriptTest.java`. (`security(installer): redact userinfo / query / fragment in install transcript URI`)
3. **F-NEW-03** — Commit the onboarding fragment-detach fix. (`fix(onboarding): capture Application context before background root-manager probe`)
4. **F-NEW-04** — Add `## Canonical Project Context` pointer to `CLAUDE.md` so future Claude sessions land at `PROJECT_CONTEXT.md` first. Optionally add a closing "Status pointer: see ROADMAP for iter-18 → iter-23" line.
5. **F-NEW-05** — Update README §"Roadmap" so v0.4.0 = Permission Inspector ✅ + Onboarding ✅, v0.5.0 = Settings & Discovery, v0.6.0 = Rootless Power (matches ROADMAP §"Committed Version Targets").
6. **F-NEW-07** — Append S316 → S320 to the ROADMAP Source Appendix per `SOURCE_REGISTER.md` §3.
7. **F-NEW-08** — Add minSdk-cascade comment to `material_version` line in `versions.gradle`.
8. **F-NEW-12** — Add `docs/audits/README.md` (audit-doc template + filename convention + verdict vocabulary).

These eight items together are ≤4 hours of work and close every "easy" delta surfaced
today.

### Next (one-iteration-out, should land in v0.5.x or v0.6.x)

- **F-NEW-09** — Extend `docs/policy/minsdk-21-ceiling.md` with the cascade analysis.
- **F-NEW-11** — Wire JaCoCo into `tests.yml` for a visible coverage signal.
- **F-NEW-13** — Add `lychee` (or similar) to `lint.yml` for repo-link sanity.
- **F-NEW-14** — Stand up `docs/architecture/` with three load-bearing docs. **The highest-leverage item on this list** — it directly addresses ROADMAP T11's "Architecture Documentation" row and reduces iter-N+1 contributor onboarding time materially.

### Later

- (none yet — the surface above is small enough that "Later" is empty for today's net-new items)

### Under Consideration

- **F-NEW-06** — Top-of-ROADMAP summary block: nice but the doc is currently navigable with anchor links, and the "Last updated" header line is already doing some of the work.
- **F-NEW-10** — Issue-state refresh script: only worth building if the maintainer wants issue *state* as a first-class signal on the source register. Current usage cites issue *content*, which doesn't age.

### Rejected

- (none — every candidate scored above 1 on at least one dimension)

---

## 3. Sequencing recommendation

```
Iter-24 (this iteration, no new tag needed):
  [Commit-ready]
  1. F-NEW-01 — Finder regex fix
  2. F-NEW-02 — Install transcript redactor
  3. F-NEW-03 — Onboarding race fix
  [Trivial governance]
  4. F-NEW-04 — CLAUDE.md pointer
  5. F-NEW-05 — README roadmap preview
  6. F-NEW-07 — Source register S316–S320 append
  7. F-NEW-08 — versions.gradle annotation
  8. F-NEW-12 — docs/audits/README.md

Iter-25 (next planning iteration):
  9.  F-NEW-09 — minSdk cascade analysis in ceiling ledger
  10. F-NEW-11 — JaCoCo in tests.yml
  11. F-NEW-13 — Markdown link checker in lint.yml
  12. F-NEW-14 — docs/architecture/ stand-up
```

The Iter-24 batch is purely additive (commits + docs + register append) — no
architectural decisions required. Iter-25's F-NEW-14 is the only item that meaningfully
shifts the project's docs surface; everything else is small.

---

## 4. Mandatory category self-check

Per the prompt's instruction, this session re-checked coverage across these categories
against existing ROADMAP rows:

- **Security** — covered exhaustively (ROADMAP T3, T9; audits dir; CVE register). Today added the URI redactor hardening as F-NEW-02.
- **Accessibility / i18n** — T10 row covers this; iter-19 added pseudolocales (en-XA / en-XB) on debug build. No net-new finding.
- **Observability / testing** — T11 / T4 cover this; today's `FilterOptionTest` is a small addition. F-NEW-11 (JaCoCo) is the structural follow-up.
- **Docs / distribution** — T1 row + `docs/distribution/` covers this. F-NEW-12 + F-NEW-14 are doc improvements; F-NEW-13 is link-rot insurance.
- **Plugin ecosystem** — Existing T8 / T11 UC rows cover this (UpgradeAll plugin contract, JADX plugin API). No net-new finding.
- **Mobile / offline / resilience** — T6 (backup) + T7 (Finder) cover; iter-19 SMB / WebDAV destinations in T6 are open. No net-new finding.
- **Multi-user / collab** — T7 Finder multi-user TODO + T8 cross-user package state; ROADMAP iter-19 explicitly notes thin coverage as intentional. No net-new finding.
- **Migration / upgrade** — `docs/policy/minsdk-21-ceiling.md` covers; F-NEW-08 + F-NEW-09 reinforce.

Coverage is comprehensive; the few thin areas (multi-user / Wear / Auto) are
intentional scope choices.
