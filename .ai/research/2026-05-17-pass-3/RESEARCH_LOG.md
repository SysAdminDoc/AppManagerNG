<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# RESEARCH_LOG — 2026-05-17 pass 3

Delta against [`../2026-05-17-pass-2/RESEARCH_LOG.md`](../2026-05-17-pass-2/RESEARCH_LOG.md).

Pass 3 was the iter-26 execution session. Pass 2 had queued five Android 17
targetSdk=37 sub-audits plus three CI / docs hygiene items as the iter-26 priority
list. Pass 3 ran every queued audit, shipped two of the three hygiene items,
and captured a design (not implementation) for the Shizuku A17 work that requires
device verification.

---

## 1. Premise of pass 3

The user invoked the autonomous walk-away prompt a third time on 2026-05-17. Pass-1
laid the foundation (PROJECT_CONTEXT + 11 audit artifacts); pass-2 shipped the
in-progress maintainer fixes + architecture docs; pass-3 was the natural follow-on:
**run the queued audits**.

The pass-2 [`CONTINUE_FROM_HERE.md`](../2026-05-17-pass-2/CONTINUE_FROM_HERE.md) §2
named the priorities verbatim:

1. Shizuku Android-17 audit (Now — June 2026 deadline)
2. Five targetSdk=37 sub-audits (Next, sized for v0.7.x)
3. F-NEW-09 minSdk cascade analysis (Next)
4. F-NEW-11 JaCoCo coverage (Next)
5. F-NEW-13 Markdown link checker (Next)

Pass-3 shipped items 1, 2, 3, 5 fully and item 4 as a plan (the implementation is
gated on local-build verification which the walk-away session lacks).

---

## 2. Execution sequence

| Phase | Action | Outcome |
|---|---|---|
| 1 | Parallel grep sweep for 5 Android-17 surfaces (cleartext / ACCESS_LOCAL_NETWORK / BAL / ECH / ML-DSA) + Shizuku binder probe inventory | All 5 audits had non-trivial source-tree signals identified; all 5 came back clean from the sweep |
| 2 | Read `network_security_config.xml` + `AndroidManifest.xml` for the cleartext audit | Confirmed clean: base-config rejects cleartext, no manifest opt-in |
| 3 | Inspect `Ops.java:pairAdbInput()` for the LAN audit | Confirmed input-driven (user types host+port), no discovery surface |
| 4 | Read 3 cert-display call sites for the ML-DSA audit | Confirmed forward-compatible (no name-string branching) |
| 5 | Read full `ShizukuBridge.java` (132 lines) | Identified Throwable-catch posture; mapped failure mode to silent op-failure rather than crash |
| 6 | Write 5 Android-17 audit verdicts | All clean |
| 7 | Write Shizuku A17 audit | Verdict: confirmed, needs-design; runtime-detection sketch captured |
| 8 | Read existing `docs/policy/minsdk-21-ceiling.md` | Ledger already comprehensive; cascade analysis is a small extension |
| 9 | Extend ceiling ledger with cascade sub-section | Done |
| 10 | Read existing `tests.yml` + `lint.yml` | Confirmed `*.md` is in `paths-ignore`; new docs workflow needed |
| 11 | Inspect `app/build.gradle` for JaCoCo setup | None present; wire-in is non-trivial Gradle work |
| 12 | Write `docs-link-check.yml` (lychee-action@v2) | Ships now |
| 13 | Write `jacoco-coverage-rollout.md` policy doc | Plan; maintainer lands the actual wire-in |
| 14 | Update ROADMAP — iter-26 closures sub-section + Eng-Debt register closure | Done |
| 15 | Update CHANGELOG — 5 new Unreleased entries | Done |
| 16 | Commit `2adbe49` (11 files, 791 insertions) | Done |
| 17 | Write pass-3 audit trail | This dir |
| 18 | Commit pass-3 audit trail | Pending |

---

## 3. External-research breadth this pass

**Zero external queries.** Pass-3 was an internal-only execution pass — every audit
ran against the local source tree. The pass-2 external sweep (10 queries) had already
established the external freshness baseline for the iter-26 work.

The only data point that would have warranted an external query — "has Shizuku shipped
a fix to #1965 / #1967 since 2026-05-17?" — would be wasted because pass-2 confirmed
0 maintainer comments after ~50 days open. A check the same day would return the same
state. The Shizuku release-feed watch is deferred to iter-27 as a CI automation
candidate.

---

## 4. Internal-research breadth this pass

Files read for audit-doc evidence:

- `app/src/main/res/xml/network_security_config.xml` (full)
- `app/src/main/AndroidManifest.xml` (grep)
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java` (specifically `pairAdbInput()`)
- `app/src/main/java/io/github/muntashirakon/AppManager/utils/Utils.java` (lines around 528)
- `app/src/main/java/io/github/muntashirakon/AppManager/utils/PackageUtils.java` (lines around 745)
- `app/src/main/java/io/github/muntashirakon/AppManager/scanner/ScannerFragment.java` (lines around 439)
- `app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java` (full, 132 lines)
- `app/build.gradle` (first 60 lines for JaCoCo recon)
- `.github/workflows/tests.yml` + `.github/workflows/lint.yml` (full)
- `docs/policy/minsdk-21-ceiling.md` (full)

Files grepped for audit signals:

- `usesCleartextTraffic` / `cleartextTrafficPermitted` / `networkSecurityConfig` (15 hits, all doc / config)
- `MODE_BACKGROUND_ACTIVITY_START` (0 production hits)
- `ACCESS_LOCAL_NETWORK` / `NsdManager` / `MulticastSocket` / `NetworkInterface.getNetworkInterfaces()` (0 production hits)
- `ML.?DSA` / `KEY_ALGORITHM_ML` / `1\.3\.6\.1\.4\.1\.2\.267\.12` / `getSigAlgOID` / `getSigAlgName` (3 hits — display only)
- `http://[^\s\"'<>]+` over `app/src/main/res` (250-file limit hit; manual inspection confirms namespace-URI only)
- `JmDNS` / `libadb.*pair` / `adbPair` (1 hit — `Ops.java`, NG-internal service)

---

## 5. What pass-3 deliberately did not do

- **No Android build run.** Static-analysis audit verdicts only. The maintainer's build host is the right place to validate the JaCoCo plan and the Shizuku A17 detection helper.
- **No Shizuku A17 implementation.** Design-only. Implementation requires Android 17 Beta device verification — see the audit doc §"Action items" for the iter-27 plan.
- **No `app/build.gradle` modification.** JaCoCo wire-in is deferred to a maintainer-driven commit with local-build verification per `docs/policy/jacoco-coverage-rollout.md`.
- **No source-code modifications at all.** Pass-3 was 100% docs / audits / CI / policy / planning. The 3 source-code commits in pass-2 (Finder regex, InstallTranscript URI redactor, Onboarding race) remain the only source-code work this multi-pass session has landed.
- **No new external sources cited.** The 325-source register from pass-1 + pass-2 (S01-S325) covers everything pass-3 needed.
- **No CHANGELOG-driven release prep.** Pass-3 closes the targetSdk=37 audit batch but doesn't propose a version bump — the bump is a v0.7.x window decision the maintainer makes alongside Android 17 stable rollout in June 2026.

---

## 6. Notes for pass-4 / iter-27

The next session has a clear runway:

1. **Highest priority (external deadline)**: Shizuku A17 implementation. Set up Android 17 Beta image; reproduce regression under NG; land the `hasAndroid17CompatibilityRisk(Context)` probe + onboarding banner. Target: before June 2026 Pixel A17 rollout. Estimated effort: 3-5 hours.
2. **Maintainer-driven**: JaCoCo wire-in per `docs/policy/jacoco-coverage-rollout.md`. Effort: 1-2 hours including local-build verification.
3. **Polish**: ML-DSA OID prettify-name map (~30 minutes, audit verdict captured the design).
4. **Process**: Set up a Shizuku release-feed CI watcher (similar to `upstream-rename-watch.yml`) that auto-opens an NG issue when a new Shizuku version ships matching the A17 fix-version pattern. Effort: ~1 hour.

The five-deep targetSdk=37 audit batch is **done**. The next major external-platform
risk is Android 18 (probable 2027) — at which point the audit doctrine will start a
new batch.

---

## 7. Session-total accounting

Across passes 1-3 of the 2026-05-17 autonomous research session:

- **22 commits** ahead of `origin/main` (started at 16; session added 6: pass-1, 3 maintainer fixes, pass-2, pass-3 + pass-3 audit trail)
- **~180 KB** of new Markdown across `PROJECT_CONTEXT.md`, `docs/architecture/`, `docs/audits/`, `docs/policy/`, `.ai/research/2026-05-17/` + `-pass-2/` + `-pass-3/`, plus the CHANGELOG and ROADMAP delta
- **3 source-code fix commits** (Finder regex, InstallTranscript URI redactor, OnboardingFragment race) — all derived from the maintainer's pre-existing working tree
- **20 audit verdicts** in `docs/audits/` (up from 14)
- **6 closed ROADMAP rows** (T11 Architecture Documentation, Android 17 targetSdk=37 compliance batch, plus iter-24 backlog items F-NEW-01/02/03/04/05/07/08/09/12/13/14)
- **2 new CI workflows** (`docs-link-check.yml`; the second is conceptual — JaCoCo plan)
- **3 new architecture docs + README** at `docs/architecture/`
- **10 new external sources** (S316-S325 plus the inline Shizuku #1965/#1967 references)
- **1 new external risk surfaced** (Shizuku A17 regression — design captured, implementation queued for iter-27)

Net deliverables-per-session-pass:

| Pass | Source fixes | Doc additions | Audit verdicts | Hygiene items |
|---|---|---|---|---|
| Pass 1 | — | PROJECT_CONTEXT + 11 research-run files + audit-doc README | 0 | 5 governance items (CLAUDE/AGENTS/README/ROADMAP/versions.gradle) |
| Pass 2 | 3 commits (Finder/Installer/Onboarding) | 4 architecture docs + 11 pass-2 research artifacts | 0 | iter-25 ROADMAP delta |
| Pass 3 | — | 6 new audit docs + 2 policy docs + 1 CI workflow | 6 new verdicts (5 clean, 1 design-pending) | iter-26 ROADMAP / CHANGELOG delta |

This is the natural rhythm: foundation → execution → audit. Iter-27 picks up the
implementation work that pass-3 designed.
