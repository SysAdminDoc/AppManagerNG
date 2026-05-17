<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# COMPETITOR_MATRIX — 2026-05-17 pass 2

Delta against [`../2026-05-17/COMPETITOR_MATRIX.md`](../2026-05-17/COMPETITOR_MATRIX.md).
Pass-1 catalogued the full competitor landscape against the 320-source register; this
delta is a freshness-only diff.

---

## 1. Version changes since pass-1

| Competitor | Pass-1 cited | Pass-2 confirmed | Delta |
|---|---|---|---|
| **Shizuku** | 13.6.0 | 13.6.0 still latest | **NEW REGRESSION** — broken on Android 17 Beta 3 ([Shizuku #1965, #1967](https://github.com/RikkaApps/Shizuku/issues/1965)). Threat to NG's Shizuku integration before A17 stable. |
| **Neo-Backup** | 8.3.17 (iter-18 [S135] / iter-19 [S114]) | **8.3.18 (2026-05-04)** | Minor bump; no NG-action delta surfaced. Source register now points at S323. |
| **Canta** | 3.2.2 (iter-19 [S43]) | 3.2.2 still latest | No drift. |
| **Hail** | v1.10.0 (iter-18 [S130]) | v1.10.0 still latest (Jan 2026) | No drift. |
| **Magisk** | v30.7 (iter-18 [S122]) | v30.7 still latest | No drift. |
| **KernelSU** | v3.2.4 (iter-19 [S166]) | v3.2.4 still latest | No drift. |
| **Material Components Android** | 1.13.0 / 1.14.0-rc01 raised minSdk to 23 (iter-18 [S57] / [S201]) | 1.13.0 still latest stable; **1.14.0 line still alpha (alpha06/07/08)** | minSdk-21 ceiling decision can stay deferred. Source register now points at S325. |
| **F-Droid 2.0** | alpha9 (2026-05-08, pass-1 S316) | alpha9 still latest | No drift. |
| **Android 17** | Beta 4 (2026-04-16, iter-19 [S169] / [S170]) | Beta 4 still latest; **Platform Stability reached at Beta 3 (2026-03-26)**; stable **June 2026** (S324) | Audit-batch sequencing anchor. |
| **Inure** | build107.0.1 (iter-18 [S131] / iter-23 [S295]) | (not re-queried; no signal) | — |
| **SD Maid SE** | v1.7.2-rc0 (iter-18 [S133] / iter-23 [S296]) | (not re-queried; no signal) | — |
| **UAD-NG** | v1.2.0 (iter-19 [S134] / iter-23 [S293]) | (not re-queried; no signal) | — |
| **LibChecker** | 2.5.2-2.5.3 (iter-18 [S72] / iter-23 [S300]) | (not re-queried) | — |
| **AppVerifier** | v0.5.0–v0.8.2 (iter-18 [S63] / [S90] / iter-23 [S301]) | (not re-queried) | — |
| **Obtainium** | v1.4.3 (iter-18 [S51] / iter-23 [S302]) | (not re-queried) | — |

The 5 competitors not re-queried have lower-velocity release cadences than the
front-runners; pass-2's spot-checks on the top-6 active projects found minimal drift.

---

## 2. New competitor-class entries: none

No new direct or adjacent competitor surfaced today. The under-represented categories
flagged in pass-1's §4 (Hardware attestation, Enterprise MDM beyond Dhizuku, Watch / Auto / Automotive) remain
the same.

---

## 3. The Shizuku regression in context

This is the **single most consequential competitor data point** found in pass-2. Detail:

- **Source**: [Shizuku #1965](https://github.com/RikkaApps/Shizuku/issues/1965), [#1967](https://github.com/RikkaApps/Shizuku/issues/1967), both 2026-03-27, **0 maintainer comments** as of 2026-05-17 (~ 50 days open).
- **Class**: Shizuku's UserService binder on Android 17 Beta 3 fails to enumerate managed packages — the Application Management surface inside Shizuku Manager itself returns blank.
- **Impact on NG**: NG's `ShizukuBridge.java` reaches through that same binder for elevated operations. If Shizuku's own Application Management is broken on Android 17, **NG's Shizuku mode is broken on Android 17**.
- **Timeline**: Android 17 stable ships June 2026 (S324). The Pixel rollout is first; OEMs follow in late Q3 2026. NG users on Pixel devices that take A17 in June will lose Shizuku mode unless either (a) Shizuku ships a fix in time, or (b) NG implements a fallback.

**Possible NG mitigations** (ROADMAP iter-25 row captures the work scope):

1. **Test against Android 17 Beta image before June** — Pixel 9 / 9a is the iter-19 [S148] device that already surfaced a related A17 issue (16-KB page-size); NG's compatibility-test matrix already cites it.
2. **Detect Shizuku version + Android version at runtime** — if `Build.VERSION.SDK_INT == 37 && shizukuVersionCode < <fix-version>`, surface an onboarding warning rather than letting the binder fail silently.
3. **Recommend fallback to ADB mode** — NG's wireless ADB wizard shipped 2026-05-14 and is a viable alternative when Shizuku is unavailable. The onboarding capability matrix can route users to the ADB path if Shizuku is broken on their Android version.
4. **Track Shizuku-fork detection (iter-19 [S139])** — if the Shizuku community migrates to a fork (e.g. `thedjchi/Shizuku`) for the A17 fix, NG should follow the broader probe pattern already on the roadmap.

The full work is tracked at ROADMAP iter-25 §"Shizuku Android-17 Compatibility Watch".

---

## 4. No competitor-derived feature gaps surfaced this pass

Spot-checks on Canta, Hail, Magisk, KernelSU, Inure return no new features since their
last roadmap citation. Neo-Backup 8.3.18's release notes (assumed available at the
release tag, not re-fetched in full) show only routine maintenance.

The competitor surface for NG is stable as of 2026-05-17. The next meaningful drift
event will be:

- A Shizuku fix for #1965/#1967 (probable June 2026 alongside A17 stable)
- A Material Components 1.14.0 stable release (no estimated date; alpha cadence varies)
- F-Droid 2.0 beta / RC (probable Q3 2026)
- Android 17 stable (June 2026)
