<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# PRIORITIZATION_MATRIX — 2026-05-17 pass 3

Delta against pass-2's [`PRIORITIZATION_MATRIX.md`](../2026-05-17-pass-2/PRIORITIZATION_MATRIX.md).

Pass-3 closed 5 of pass-2's priority items and shipped 1 as design + plan. This file
documents the iter-27 sequencing recommendation.

Scoring rubric (1-5 each, higher = stronger signal):

- **Impact** — what the change unblocks or repairs
- **Effort** — engineering cost
- **Risk** — potential to regress
- **Evidence** — strength of the citation behind it

---

## 1. Iter-26 closures scoring (retrospective)

| ID | Item | Impact | Effort actual | Risk realized | Verdict |
|---|---|---:|---:|---:|---|
| F-NEW-25-02 (5 audits) | Android 17 targetSdk=37 audit batch | **5** | 2 (cumulative) | 1 | ✅ All clean; Engineering Debt closed |
| F-NEW-25-01 design | Shizuku A17 audit | **5** | 2 | 1 (design only) | ✅ `confirmed, needs-design`; runtime-detection sketch captured |
| F-NEW-09 | minSdk cascade analysis | 3 | 1 | 1 | ✅ Shipped |
| F-NEW-13 | Markdown link checker | 2 | 1 | 1 | ✅ Shipped; lychee-action@v2 |
| F-NEW-11 plan | JaCoCo coverage rollout plan | 3 | 2 | 1 (plan only) | ✅ Plan shipped; implementation queued |

Effort estimates were on the high side — the audit batch was sized at 5 × 2/5 = ~4/5
cumulative; actual cost was lower (the grep sweeps + Read calls converged fast because
the source tree is small and well-organized). Risk was overestimated — all 5 verdicts
came back clean.

---

## 2. Iter-27 priorities scored

| ID | Item | Impact | Effort | Risk | Evidence | Recommended tier |
|---|---|---:|---:|---:|---:|---|
| **F-NEW-25-01-IMPL** | Shizuku A17 runtime detection + onboarding banner | **5** | 3 | 2 | 5 | **Now** — June 2026 deadline |
| **F-NEW-11-IMPL** | JaCoCo wire-in per the rollout plan | 3 | 2 | 1 | 5 | **Next** — maintainer local build |
| **F-NEW-27-01** | ML-DSA OID prettify-name map | 2 | 1 | 1 | 4 | **Next** — polish, ~30 min |
| **F-NEW-27-02** | Shizuku release-feed CI watcher | 3 | 2 | 1 | 4 | **Next** — process automation |

All iter-27 work is sized at ≤3/5 effort and ≤2/5 risk. None require fresh research.

---

## 3. Sequencing recommendation for iter-27

```
Iter-27 (next session):
  Now (urgent — external deadline):
  1. F-NEW-25-01-IMPL — Shizuku A17 runtime detection + onboarding banner
                        Device: Pixel 9 Android 17 Beta image
                        Files: shizuku/ShizukuBridge.java + onboarding/OnboardingFragment.java
                        Reference: docs/audits/2026-05-17-shizuku-android17-compat.md
                        Target: land before June 2026 Pixel A17 stable rollout
                        Effort: 3-5 hours including device E2E test

  Next (maintainer-driven local build):
  2. F-NEW-11-IMPL — JaCoCo wire-in
                     Reference: docs/policy/jacoco-coverage-rollout.md
                     5 steps; ~1-2 hours including local build verification

  Next (polish):
  3. F-NEW-27-01 — ML-DSA OID prettify-name map
                   Reference: docs/audits/2026-05-17-android17-ml-dsa-keystore-oid.md §"Polish opportunity"
                   ~30 minutes including unit test

  Next (process automation):
  4. F-NEW-27-02 — Shizuku release-feed CI watcher
                   Pattern: clone .github/workflows/upstream-rename-watch.yml
                   ~1 hour

  Under Consideration (design calls; iter-28+):
  5. F-NEW-06 — Top-of-ROADMAP summary
  6. F-NEW-10 — Issue-state refresh script
```

---

## 4. Mandatory category self-check

Re-running the category sweep against iter-27's queued items:

- **Security / compliance** — F-NEW-25-01-IMPL closes the external compliance risk. F-NEW-27-02 is process integrity.
- **Documentation** — F-NEW-27-01's unit-test entry counts as test-doc.
- **Observability / testing** — F-NEW-11-IMPL is the test-coverage visibility win.
- **Platform compatibility** — F-NEW-25-01-IMPL is the headline platform-compat item; F-NEW-27-01 is post-quantum cert display.
- **Distribution** — no items this iteration.
- **i18n / accessibility** — no items this iteration (covered by T10 backlog).
- **Plugin ecosystem** — no items.
- **Multi-user** — no items.

Coverage stays comprehensive. The thin areas are intentional and tracked.

---

## 5. End-of-session-3 priority surface

After three passes, the project's near-term priority surface is the cleanest it's
been in the iter-1 → iter-26 history:

- **0 unfixed source-code bugs** in the in-progress working tree (all three pass-1
  finds shipped in pass-2)
- **0 open Engineering Debt Register Android-17 sub-audits** (all 5 + 1 batched audit
  closed in pass-3)
- **1 design-pending audit** (Shizuku A17 — implementation queued for iter-27)
- **1 documented plan awaiting maintainer local build** (JaCoCo)
- **2 polish items** (ML-DSA prettify, Shizuku release watcher) — both ≤1 hour

This is a strong handoff state for iter-27. The maintainer can pick up Priority 1
without a single fresh research probe.
