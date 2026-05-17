<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# FEATURE_BACKLOG — 2026-05-17 pass 3

Delta against pass-2's [`FEATURE_BACKLOG.md`](../2026-05-17-pass-2/FEATURE_BACKLOG.md).

Pass-3 closed the iter-25 priority queue. This file is a **closure ledger** for
pass-2's queued items plus the iter-27 carryover (one new item identified during the
ML-DSA audit).

---

## 1. Pass-2 + iter-25 backlog closure status

| ID | Title | Pass-3 outcome |
|---|---|---|
| **F-NEW-25-01** | Shizuku A17 compatibility audit + runtime fallback | ✅ **Design shipped** — `docs/audits/2026-05-17-shizuku-android17-compat.md` captures the runtime-detection helper sketch. Implementation gated on iter-27 device verification. |
| **F-NEW-25-02** | Android 17 targetSdk=37 sub-audit batch (5 sub-audits) | ✅ **All 5 shipped** — clean verdicts in `docs/audits/2026-05-17-android17-*.md`. Engineering Debt Register's targetSdk=37 row is now closed. |
| **F-NEW-09** | minSdk-21 cascade analysis | ✅ **Shipped** — `docs/policy/minsdk-21-ceiling.md` gained "Cascade analysis" sub-section. |
| **F-NEW-11** | JaCoCo coverage badge | ✅ **Plan shipped** — `docs/policy/jacoco-coverage-rollout.md` documents the 5-step wire-in. Implementation gated on maintainer's local-build verification. |
| **F-NEW-13** | Markdown link checker | ✅ **Shipped** — `.github/workflows/docs-link-check.yml` runs lychee on push/PR/weekly. |
| **F-NEW-06** | Top-of-ROADMAP "Now/Next/Later" summary | **Carried** — design call; not pursued |
| **F-NEW-10** | Issue-state refresh script | **Carried** — design call; not pursued |

**Closure rate this pass**: 5 of 5 priority items shipped, 1 priority item shipped as
design + plan (Shizuku A17 implementation deferred). The 2 carried Under-Consideration
items are decision calls, not implementation work.

---

## 2. New iter-27 finding

### F-NEW-27-01 — ML-DSA OID prettify-name map (polish)

- **Theme:** T9 (Privacy & Security) — polish
- **Source:** [`docs/audits/2026-05-17-android17-ml-dsa-keystore-oid.md`](../../../docs/audits/2026-05-17-android17-ml-dsa-keystore-oid.md) §"Polish opportunity"
- **Problem:** NG's APK cert display surfaces `getSigAlgName()` from the JDK. On Android < 17, ML-DSA-signed certs return either "Unknown" or the raw OID. The OID is shown separately (so users can copy-paste it), but a prettier name helps.
- **Action plan:**
  1. Add `prettifyAlgorithmName(String oid, String fallback)` to `utils/Utils` or new `utils/CertAlgorithmNames`.
  2. Switch on the two ML-DSA OIDs: `1.3.6.1.4.1.2.267.12.6.5` (ML-DSA-65), `1.3.6.1.4.1.2.267.12.8.7` (ML-DSA-87). Fall through to `fallback` (= `getSigAlgName()` output) for everything else.
  3. Wire into the two display sites: `PackageUtils.java:745` (sig algorithm row) and `ScannerFragment.java:439` (scanner cert display).
  4. Add a unit test mapping both OIDs.
- **Effort:** 1/5 (~30 minutes)
- **Tier:** **Next** — polish; ship alongside the targetSdk=37 bump in v0.7.x

---

## 3. Iter-27 carried backlog (effective)

Priority order:

1. **F-NEW-25-01 implementation** — Shizuku A17 runtime detection + onboarding banner (Now-tier; external June 2026 deadline)
2. **F-NEW-11 JaCoCo wire-in** — Maintainer's local-build commit (Next-tier; queued plan)
3. **F-NEW-27-01** — ML-DSA prettify-name map (Next-tier; polish)
4. **Shizuku release-feed CI watcher** — Process automation; analogous to `upstream-rename-watch.yml`
5. **F-NEW-06 / F-NEW-10** — Carried Under-Consideration items (design calls)

Items 1-4 are all small (≤5 hours each) and have natural deadlines or trigger
conditions. None require fresh research.

---

## 4. Items deliberately not added

Same shape as prior passes. The competitor landscape is stable, the audit batch is
closed, the Engineering Debt Register's largest open item is resolved. No new
research-prompted items surface from pass-3's purely-internal work.

The only direction that could open new items is a fresh external sweep — deliberately
skipped this pass per the saturation rule.
