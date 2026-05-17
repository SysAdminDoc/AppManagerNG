<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# FEATURE_BACKLOG — 2026-05-17

**Net-new harvest, beyond the existing ROADMAP.** The 1135-line `ROADMAP.md` already
catalogs ~250 feature rows across 20+ tiers (T1–T21) plus an Engineering Debt Register, a
Premium Polish Track, and five Iter-18 → Iter-23 research deltas. Re-running a feature
sweep would duplicate that work.

This file lists only items the existing roadmap does not already capture, or where
today's session surfaced a new dimension. Each item is annotated with where it should
attach to the roadmap, the evidence, and whether it's a feature, hardening, or
governance / ops item.

---

## 1. From uncommitted in-progress work (highest signal — observed today)

### F-NEW-01 — Finder regex predicate fix (already implemented, not yet committed)
- **Theme:** T7 (Finder / Cross-App Search) — bug fix
- **Evidence:** `git diff` of `app/src/main/java/io/github/muntashirakon/AppManager/filters/options/FilterOption.java` (this session, 2026-05-17)
- **Problem:** `Pattern.compile(Pattern.quote(value))` neutered every user-supplied regex predicate into a literal-string match. The iter-23 work that added `name_regex` to `TrackersOption` and `regex` to `ComponentsOption` would have failed in production.
- **Fix:** Already present in the working tree. Removes `Pattern.quote()`, catches `PatternSyntaxException`, adds missing `break` between the `TYPE_REGEX` and `TYPE_STR_MULTIPLE` switch cases (the fall-through was overwriting `stringValues`).
- **Test surface:** new `app/src/test/java/io/github/muntashirakon/AppManager/filters/options/FilterOptionTest.java` (89 lines, Robolectric).
- **Action:** Commit. Add CHANGELOG `Unreleased` → "Fixed — Finder regex predicates compiled as regex, not literals".

### F-NEW-02 — Install transcript URI redactor — userinfo / query / fragment leak
- **Theme:** T3 / T9 — security
- **Evidence:** `git diff` of `app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/InstallTranscript.java` (this session)
- **Problem:** Pre-fix redactor only stripped after the first `/` of the path, so `https://host?token=secret` (no path) was returned verbatim including the query. Same hole for `https://user:pass@host/...` (userinfo not stripped) and fragments (`#`).
- **Fix:** Already present in the working tree per RFC 3986 §3.2 authority parsing.
- **Action:** Commit. CHANGELOG `Unreleased` → "Security — Install transcript URI redactor closes userinfo / query / fragment leak".

### F-NEW-03 — Onboarding root-manager probe — fragment-detach race
- **Theme:** T4 (Observability) — bug fix
- **Evidence:** `git diff` of `app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java`
- **Problem:** Background-thread root-manager detection called `requireContext()` on the worker pool; a detached fragment by then would crash the worker silently with `IllegalStateException`.
- **Fix:** Already present. Captures `Application` context on the main thread before posting.
- **Action:** Commit. Minor; folds into a chunked fix commit.

---

## 2. Roadmap copy-debt / consistency items

These are not new features; they are places where the existing planning surface drifted
between docs. Resolving them costs zero engineering effort and helps the project not
trip itself.

### F-NEW-04 — Refresh `CLAUDE.md` Status section to reflect iter-23 / v0.4.2
- **Theme:** Governance / Eng-Debt
- **Evidence:** `CLAUDE.md` § Status ends at "factory iter-7" (2026-05-02). The intervening iter-18 → iter-23 work and v0.4.0 → v0.4.2 releases are not represented.
- **Action:** Add a closing paragraph pointing at `PROJECT_CONTEXT.md` for the current state, and leave the historical iter-6/iter-7 notes intact for archaeology. (Suggested pointer text is in `MEMORY_CONSOLIDATION.md` §5 of this research run.)

### F-NEW-05 — Refresh `README.md` "Roadmap" preview block
- **Theme:** Governance / docs
- **Evidence:** README lines 86–94 list v0.5.0 = "Onboarding flow, settings reorganization, global in-app search, contextual help". But the v0.4.0 release **already shipped** the onboarding capability wizard (2026-05-14 commit `6759f35 feat: refine capability onboarding`). The actual current v0.5.0 theme is in ROADMAP §"Committed Version Targets": Settings & Discovery + global in-app search + contextual help + in-app changelog viewer.
- **Action:** Update README's version-target preview to match ROADMAP's truth (Onboarding → done at v0.4.0; v0.5.0 = Settings & Discovery; v0.6.0 = Rootless Power).

### F-NEW-06 — Add `Now/Next/Later` quick-summary at top of ROADMAP
- **Theme:** Governance / docs (UX of the roadmap doc itself)
- **Evidence:** ROADMAP is 1135 lines. The top is good context but a returning reader has to scroll a long way before finding actionable items.
- **Action (optional)**: A 10-line summary block of the top-3 "Now" items at the top of the doc would lower the cost of re-entry for occasional contributors. Currently the "Last updated" line gives one row's worth of recent shipped status — that's the right intent, just one row.

---

## 3. Net-new evidence-backed items not already in ROADMAP

The following items emerged during today's recon and are **not present** in ROADMAP iter-1 → iter-23. Each is small enough to propose as an iter-24 row.

### F-NEW-07 — F-Droid 2.0-alpha9 release watch
- **Theme:** T1 distribution / governance
- **Evidence:** F-Droid published `2.0-alpha9` on 2026-05-08 (web search confirmed). ROADMAP iter-19 [S168] cites alpha8 (2026-04-24); the alpha9 release is the next data point and no breaking change has been reported.
- **Action:** Add proposed S316 to the source register pointing at `f-droid.org/en/news/` for the alpha-release feed. The existing iter-19 row "F-Droid 2.0 Index v2 Protobuf" already captures the work — no new feature row needed. **Effort: 0** (governance / source-register update only).

### F-NEW-08 — `versions.gradle` minSdk pointer comment
- **Theme:** Eng-Debt / Governance
- **Evidence:** `versions.gradle` has a great in-line comment ("Bumping min_sdk past 21 is a one-way door...") at line 5. **However**, individual dep version pins that explicitly note the API 21-22 ceiling have inconsistent annotation styles:
  - `activity_version` at L17 → `"// API 21-22 support dropped in 1.12.x"`
  - `biometric_version` at L24 → `"// API 21-22 support dropped in 1.4.0-alpha05"`
  - `room_version` at L39 → `"// API 21-22 support dropped in 2.8.x"`
  - `webkit_version` at L48 → `"// API 21-22 support dropped in 1.15.x"`
  - `material_version` at L36 → no inline note (although it is the **ceiling** dep — Material 1.14.0-rc01 raises to minSdk 23, which would unblock several other ceilings)
- **Action:** Normalize the inline notes — at minimum, add a comment on `material_version` to note the ceiling implication. **Effort: trivial**.

### F-NEW-09 — Document the `material_version 1.14` → other-dep-line-unblock cascade
- **Theme:** Eng-Debt
- **Evidence:** If `min_sdk = 23` is taken (driven by Material 1.14.0+), several other deps could move up in lockstep. The minSdk-21 ceiling ledger at `docs/policy/minsdk-21-ceiling.md` should document the cascade explicitly.
- **Action:** Extend the ledger with a "Cascade analysis" sub-section showing the chain of dep-lines that unblock. Minor doc work; saves a future contributor the discovery time. **Effort: low.**

### F-NEW-10 — Iter-24 issue-state refresh script
- **Theme:** Process / governance
- **Evidence:** The S121 → S195 issue-references in ROADMAP cite issue *content* (problem statements) but not *current state*. A future agent looking at S141 ("Per-app rollback / 'revert all changes'") cannot tell from the roadmap whether the upstream issue is still open / now closed / superseded.
- **Action:** Either (a) accept this as deliberate (issue content > state) or (b) add a `scripts/refresh-issue-states.sh` that takes the S-numbered URLs and emits a CSV. Decision call for the maintainer. **Effort: ~2 hours if pursued.**

### F-NEW-11 — Test coverage metric in CI
- **Theme:** T11 / Observability
- **Evidence:** ROADMAP T11 row "Unit Test Coverage Expansion" describes the work but there is no measurable coverage gate today. Three test files are visibly worked on (`AESCryptoTest`, `OperationHistoryExporterTest`, `InstallTranscriptTest`, today: `FilterOptionTest`) but no JaCoCo / coverage report.
- **Action:** Wire JaCoCo into `tests.yml` and publish a coverage badge. Doesn't need to be a quality gate (initial coverage will be low); does need to be visible so progress is tracked. **Effort: 2/5.**

### F-NEW-12 — Coverage of the audit-doc pattern
- **Theme:** Governance / process
- **Evidence:** 14 audit files exist under `docs/audits/` with a consistent shape: heading + behaviour-change source + sweep methodology + verdict. **The pattern is great but not documented.** A future contributor doing an Android-18 audit has to reverse-engineer it from existing files.
- **Action:** Add `docs/audits/README.md` documenting the pattern (template + filename convention `<date>-<topic>.md` + verdict vocabulary). **Effort: trivial.**

### F-NEW-13 — `PROJECT_CONTEXT.md` enrollment in CI link check
- **Theme:** Governance
- **Evidence:** Today's added `PROJECT_CONTEXT.md` carries ~20 relative links into the repo. If any of those move (e.g. `design/spec/1-design-system.md` is renamed), the context file silently bit-rots.
- **Action:** Add a Markdown link-checker (e.g. `lychee`) to `lint.yml` to assert all `PROJECT_CONTEXT.md` links resolve. **Effort: 1/5.**

### F-NEW-14 — Architecture sketch as the missing piece
- **Theme:** T11 row "Architecture Documentation"
- **Evidence:** Roadmap row T11 calls out "Document AIDL service architecture, privilege escalation paths, backup format spec in `/docs/architecture/`" but no work has shipped against it. The closest current artifact is the high-level paragraph in `CLAUDE.md` §"Architecture".
- **Action:** Stand up `docs/architecture/01-privilege-providers.md` (root / Shizuku / ADB / Sui / Dhizuku-UC); `02-backup-format.md` (metadata v5 vs v6, IV derivation, AES-GCM, BKS keystore, OpenPGP wrapping); `03-hidden-api-bypass.md` (LSPosed AndroidHiddenApiBypass via `hiddenapi/`, the api-versions JSON, refine plugin). This addresses the row directly. **Effort: 3/5; high leverage** since it'd cut iter-N→iter-N+1 onboarding time substantially.

---

## 4. Items deliberately *not* added

For transparency, these emerged in discussion-with-self but were rejected as ROADMAP additions:

- **Add an architecture-decision-record (ADR) directory.** The existing audit-doc pattern serves the same purpose; adding an ADR convention would dilute the existing one without a clear win.
- **Add a "memory-bank" / Knowledge-Graph integration directory.** YAGNI — the existing CLAUDE.md + PROJECT_CONTEXT.md split serves the AI-context-management need at this project's scale.
- **Add CI status badges to README.md.** Reasonable but low-priority; the user-visible signal that matters most is the AppVerifier-compatible signing fingerprint, which is already prominent.
- **Add Wear OS / Auto / Automotive support.** ROADMAP-T20 / T11 already deliberately stake these as out-of-scope or under-consideration; not re-litigating.

---

## 5. Priority of the F-NEW items

See [`PRIORITIZATION_MATRIX.md`](PRIORITIZATION_MATRIX.md) for scoring + tier verdicts.

Headline: **F-NEW-01, F-NEW-02, F-NEW-03 are immediate-commit work already in the
working tree.** F-NEW-04 + F-NEW-05 are trivial copy-debt fixes. F-NEW-14 (architecture
docs) is the highest-leverage net-new item; F-NEW-11 + F-NEW-12 are useful process/CI
additions. Everything else is small or optional.
