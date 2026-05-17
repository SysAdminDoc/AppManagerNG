<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# PRIORITIZATION_MATRIX — 2026-05-17 pass 2

Delta against [`../2026-05-17/PRIORITIZATION_MATRIX.md`](../2026-05-17/PRIORITIZATION_MATRIX.md).
Scoring of the two new iter-25 items + status update on the pass-1 backlog.

Scoring rubric carries over from pass-1 (1–5 each, higher = stronger signal):

- **Impact** — what the change unblocks or repairs
- **Effort** — engineering cost
- **Risk** — potential to regress
- **Evidence** — strength of the citation behind it

---

## 1. New iter-25 items scored

| ID | Item | Impact | Effort | Risk | Evidence | Recommended tier |
|---|---|---:|---:|---:|---:|---|
| **F-NEW-25-01** | Shizuku A17 compatibility audit & runtime fallback | **5** | 3 | 3 | 5 | **Now** — external deadline (Pixel A17 stable rollout June 2026) |
| **F-NEW-25-02** | Android 17 targetSdk=37 audit batch (5 sub-audits) | **5** | 4 (cumulative) | 4 | 5 | **Next** — sized for v0.7.x window, not urgent before stable rollout |

F-NEW-25-01 is **the single highest-priority item on the project today** by external-deadline
pressure. The audit is comparatively cheap (3/5) and the Shizuku integration is core to
NG's rootless mode. Failing to land before June 2026 means NG's Shizuku users lose
functionality on the day of the Pixel A17 rollout.

F-NEW-25-02's effort score is the *cumulative* across 5 sub-audits — each individual
sub-audit is 1-2/5 and could be done one-per-evening in the v0.7.x window. Risk is
elevated to 4 because mistakes during the targetSdk bump break the entire app for
A17 users.

---

## 2. Pass-1 backlog status (verbatim from `FEATURE_BACKLOG.md` §1)

| ID | Title | Status |
|---|---|---|
| F-NEW-01 | Finder regex predicate fix | ✅ Shipped (73387cd) |
| F-NEW-02 | Install transcript URI redactor | ✅ Shipped (bcb2874) |
| F-NEW-03 | Onboarding root-probe race fix | ✅ Shipped (25c629a) |
| F-NEW-04 | CLAUDE.md pointer | ✅ Shipped (2846225, local) |
| F-NEW-05 | README.md roadmap refresh | ✅ Shipped (2846225) |
| F-NEW-06 | Top-of-ROADMAP summary | UC — carried |
| F-NEW-07 | Source register S316-S320 | ✅ Shipped (2846225) |
| F-NEW-08 | versions.gradle annotation | ✅ Shipped (2846225) |
| F-NEW-09 | minSdk cascade analysis | Next — carried |
| F-NEW-10 | Issue-state refresh script | UC — carried |
| F-NEW-11 | JaCoCo in tests.yml | Next — carried |
| F-NEW-12 | docs/audits/README.md | ✅ Shipped (2846225) |
| F-NEW-13 | Markdown link checker | Next — carried |
| F-NEW-14 | docs/architecture/ | ✅ Shipped (pass-2, commit pending) |

**9 of 14 pass-1 items shipped within 24 hours of being identified.** The remaining 5
(F-NEW-06, -09, -10, -11, -13) are either design calls (UC) or sized for the
iter-26 / v0.7.x window.

---

## 3. Sequencing recommendation for iter-26

```
Iter-26 (next session):
  Now (urgent — external deadline):
  1. F-NEW-25-01 — Shizuku A17 compatibility audit + runtime fallback
                   (target: land before June 2026 Pixel A17 rollout)

  Next (sized for v0.7.x window):
  2. F-NEW-25-02 — Android 17 targetSdk=37 audit batch
                   (5 sub-audits, each ~2 hours)
                   - ACCESS_LOCAL_NETWORK runtime permission audit
                   - usesCleartextTraffic enforcement audit
                   - MODE_BACKGROUND_ACTIVITY_START_ALLOWED migration audit
                   - ECH default-on for TLS audit
                   - ML-DSA Keystore OID recognition audit
  3. F-NEW-11 — JaCoCo coverage badge
  4. F-NEW-13 — Markdown link checker in lint.yml
  5. F-NEW-09 — minSdk cascade analysis in ceiling ledger

  Under Consideration (design calls):
  6. F-NEW-06 — Top-of-ROADMAP summary
  7. F-NEW-10 — Issue-state refresh script
```

Items 1 and 2 are the heart of iter-26. Items 3-5 are velocity multipliers that
amortise across all future work. Items 6-7 are decisions to make rather than
to implement.

---

## 4. Mandatory category self-check

Re-running pass-1's category sweep against pass-2's added items:

- **Security** — F-NEW-25-01 + the InstallTranscript URI redactor commit are security work. F-NEW-25-02's batch includes several security-relevant sub-audits (CT, MODE_BACKGROUND_ACTIVITY_START_ALLOWED, ML-DSA).
- **Compatibility / platform** — F-NEW-25-02 is the largest single platform-compat item on the board. F-NEW-25-01 is platform-compat-via-third-party-dep.
- **Documentation** — `docs/architecture/` trio + iter-25 ROADMAP delta. Closes T11.
- **Observability / testing** — F-NEW-11 (JaCoCo) and F-NEW-25-01's audit will produce a new `docs/audits/<date>-shizuku-android17-compat.md` artifact.
- **Distribution** — no new finding this pass (F-Droid 2.0 alpha9 verified current; no breaking change).
- **Other categories** (accessibility, i18n, plugin ecosystem, multi-user) — no new findings, intentional thin coverage continues.

Coverage remains comprehensive. No category gap surfaced.
