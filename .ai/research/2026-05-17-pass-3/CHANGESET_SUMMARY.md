<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET_SUMMARY — 2026-05-17 pass 3

Files created or modified by pass-3 of the 2026-05-17 autonomous-research walk-away
session. See pass-1's [`../2026-05-17/CHANGESET_SUMMARY.md`](../2026-05-17/CHANGESET_SUMMARY.md)
and pass-2's [`../2026-05-17-pass-2/CHANGESET_SUMMARY.md`](../2026-05-17-pass-2/CHANGESET_SUMMARY.md)
for the prior pass deliverables.

---

## 1. Source-code commits landed this pass

**Zero source-code modifications.** Pass-3 is policy + audit + CI + documentation only.

The Shizuku A17 detection helper sketched in
[`docs/audits/2026-05-17-shizuku-android17-compat.md`](../../../docs/audits/2026-05-17-shizuku-android17-compat.md)
deliberately did not land as a code change — implementation is gated on Android 17 Beta
device verification, which is the iter-27 priority.

---

## 2. New documentation (committed in [`2adbe49`](https://github.com/SysAdminDoc/AppManagerNG/commit/2adbe49))

### Android 17 targetSdk=37 audit batch (5 audits, all clean)

| File | Verdict | Methodology |
|---|---|---|
| [`docs/audits/2026-05-17-android17-cleartext-traffic-enforcement.md`](../../../docs/audits/2026-05-17-android17-cleartext-traffic-enforcement.md) | ✅ clean | manifest + network_security_config + grep `http://` |
| [`docs/audits/2026-05-17-android17-access-local-network.md`](../../../docs/audits/2026-05-17-android17-access-local-network.md) | ✅ clean | grep `NsdManager` / `MulticastSocket` / mDNS / `NetworkInterface.getNetworkInterfaces` |
| [`docs/audits/2026-05-17-android17-bal-intentsender.md`](../../../docs/audits/2026-05-17-android17-bal-intentsender.md) | ✅ clean | grep `MODE_BACKGROUND_ACTIVITY_START` / `setPendingIntentBackgroundActivityStartMode` |
| [`docs/audits/2026-05-17-android17-ech-default-on.md`](../../../docs/audits/2026-05-17-android17-ech-default-on.md) | ✅ clean | enumerate network destinations + verify cleanly handle ECH |
| [`docs/audits/2026-05-17-android17-ml-dsa-keystore-oid.md`](../../../docs/audits/2026-05-17-android17-ml-dsa-keystore-oid.md) | ✅ clean (audit) | grep `getSigAlgOID` / `getSigAlgName` + read 3 call sites |

### Shizuku Android-17 compatibility audit (design-pending)

| File | Verdict | Methodology |
|---|---|---|
| [`docs/audits/2026-05-17-shizuku-android17-compat.md`](../../../docs/audits/2026-05-17-shizuku-android17-compat.md) | ⚠ `confirmed, needs-design` | Full read of `ShizukuBridge.java`; mapped 7 probes to Throwable-catch surface; identified silent op-failure as the failure mode; captured runtime-detection helper design with a `MIN_ANDROID_17_COMPATIBLE_VERSION = null` placeholder. |

### Policy docs

| File | Purpose |
|---|---|
| [`docs/policy/minsdk-21-ceiling.md`](../../../docs/policy/minsdk-21-ceiling.md) | **Extended** with new "Cascade analysis: what `minSdk = 23` would unlock" sub-section per iter-24 backlog F-NEW-09. |
| [`docs/policy/jacoco-coverage-rollout.md`](../../../docs/policy/jacoco-coverage-rollout.md) | **New file** — 5-step JaCoCo wire-in plan for the maintainer. iter-24 backlog F-NEW-11 design. |

### CI workflow

| File | Purpose |
|---|---|
| [`.github/workflows/docs-link-check.yml`](../../../.github/workflows/docs-link-check.yml) | **New file** — `lycheeverse/lychee-action@v2` on push, PR, weekly Tuesday 11:11 UTC. Scope: `*.md` + `docs/**/*.md` + `.ai/**/*.md` + top-level docs. iter-24 backlog F-NEW-13 — link-rot insurance. |

### Planning surface modifications

| File | Change |
|---|---|
| [`ROADMAP.md`](../../../ROADMAP.md) | (1) Last-updated header refreshed with iter-26 summary; iter-25 preserved as `Prior:` line. (2) New "Iter-26 Closures (2026-05-17)" sub-section with 8 entries. (3) Engineering Debt Register's "Android 17 targetSdk=37 compliance" row marked closed with cross-references to all 5 audit files. |
| [`CHANGELOG.md`](../../../CHANGELOG.md) | 5 new Unreleased entries: Compliance — Android 17 targetSdk=37 audit batch closed clean; Audit — Shizuku Android-17 compatibility; Added — minSdk-21 cascade analysis; Added — Docs Markdown link checker CI workflow; Added — JaCoCo coverage rollout plan. |

### Pass-3 audit trail (this dir, committed separately)

| File | Purpose |
|---|---|
| `STATE_OF_REPO.md` | Repo state after iter-26 commit; audit-doc directory delta (14→20); CI workflow inventory |
| `MEMORY_CONSOLIDATION.md` | Pass-1/-2 recommendations status; no new instruction files |
| `SOURCE_REGISTER.md` | No new external sources cited in pass-3; pointer to S01-S325 in ROADMAP |
| `RESEARCH_LOG.md` | Operational diary; zero external queries; 18-phase execution sequence |
| `COMPETITOR_MATRIX.md` | No competitor drift this pass; pointer to pass-2's freshness baseline |
| `FEATURE_BACKLOG.md` | iter-26 closure ledger + iter-27 carryover |
| `PRIORITIZATION_MATRIX.md` | iter-27 sequence (Shizuku impl → JaCoCo → polish) |
| `SECURITY_AND_DEPENDENCY_REVIEW.md` | targetSdk=37 batch closed clean; Shizuku regression in context |
| `DATASET_MODEL_INTEGRATION_REVIEW.md` | Stays thin (intentional) |
| `CHANGESET_SUMMARY.md` | This file |
| `CONTINUE_FROM_HERE.md` | iter-27 priorities (Shizuku impl first) |

---

## 3. Total bytes / commits this pass

- **Pass-3 iter-26 commit (`2adbe49`)**: 11 files, 791 insertions, 2 deletions. Closes 6 ROADMAP backlog items.
- **Pass-3 audit-trail commit (pending)**: 11 files in `.ai/research/2026-05-17-pass-3/`.

Net pass-3 deliverable: **~50 KB of Markdown across 22 files**, no source-code changes,
6 ROADMAP rows closed, 1 design queued for iter-27.

---

## 4. Files left unchanged

Same shape as pass-1 and pass-2: `AGENTS.md`, `CLAUDE.md`, `README.md`, `PROJECT_CONTEXT.md`,
`versions.gradle`, every audit doc from pre-existing 14, all of `app/src/main/java/`, all
of `app/src/test/java/`, all of `LICENSES/`, all of `design/`. The architecture docs from
pass-2 also unchanged.

---

## 5. Action items for the maintainer

1. **Pull `main`** to get commit `2adbe49` plus the pass-3 audit-trail commit (queued).
2. **Review** the 5 Android 17 audit verdicts. Each is short (35-90 lines) and follows the doctrine template.
3. **Review** the Shizuku A17 audit (~145 lines). The runtime-detection sketch is the iter-27 implementation path.
4. **Plan iter-27**: Shizuku A17 implementation (Pixel 9 Beta device or emulator + the runtime probe + onboarding banner). JaCoCo wire-in per the new policy doc (1-2 hours including local-build verification).
5. **Land the JaCoCo wire-in** as a separate single-commit PR — the plan is in `docs/policy/jacoco-coverage-rollout.md`.
6. **Watch Shizuku release feed** for a fix to #1965 / #1967. When a fix Shizuku ships, populate `MIN_ANDROID_17_COMPATIBLE_VERSION` in the runtime probe and remove the onboarding banner.

---

## 6. What deliberately wasn't done

Same shape as pass-2 §5, plus:

- **No `app/build.gradle` modification** — JaCoCo wire-in benefits from local-build verification.
- **No `ShizukuBridge.java` modification** — runtime detection deferred to device-verified iter-27 commit.
- **No Android 17 Beta install / emulator setup** — outside the scope of a Markdown-and-audit walk-away session on a Windows host.
- **No `versions.gradle` modification** — the JaCoCo pin (`0.8.13`) is in the plan doc; bump lands with the JaCoCo wire-in.
- **No release tag** — v0.4.3 / v0.5.0 release prep is a separate maintainer decision; the iter-26 work is `Unreleased` accumulation.
