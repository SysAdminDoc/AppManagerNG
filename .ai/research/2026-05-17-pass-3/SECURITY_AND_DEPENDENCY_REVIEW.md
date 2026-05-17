<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# SECURITY_AND_DEPENDENCY_REVIEW — 2026-05-17 pass 3

Delta against pass-2's [`SECURITY_AND_DEPENDENCY_REVIEW.md`](../2026-05-17-pass-2/SECURITY_AND_DEPENDENCY_REVIEW.md).

Pass-3 closed the Android 17 targetSdk=37 audit batch and captured the Shizuku A17
design. This file documents the net security posture after pass-3.

---

## 1. The Android 17 targetSdk=37 path is now source-side clear

After pass-3's 5 new audit verdicts:

| Audit area | Verdict | File |
|---|---|---|
| `usesCleartextTraffic` enforcement | ✅ clean | `docs/audits/2026-05-17-android17-cleartext-traffic-enforcement.md` |
| `ACCESS_LOCAL_NETWORK` runtime permission | ✅ clean | `docs/audits/2026-05-17-android17-access-local-network.md` |
| BAL hardening + `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` migration | ✅ clean | `docs/audits/2026-05-17-android17-bal-intentsender.md` |
| ECH default-on for TLS | ✅ clean | `docs/audits/2026-05-17-android17-ech-default-on.md` |
| ML-DSA Keystore OID recognition | ✅ clean (audit) | `docs/audits/2026-05-17-android17-ml-dsa-keystore-oid.md` |
| Static-final reflection ban | 1 fix + 1 deferred (iter-20) | `docs/audits/2026-05-08-android17-static-final-reflection.md` |
| `System.load()` read-only | ✅ clean (iter-20) | `docs/audits/2026-05-08-android17-system-load-readonly.md` |
| `MessageQueue` lock-free impl | ✅ clean (iter-19) | `docs/audits/2026-05-02-android17-messagequeue.md` |
| Per-UID 50K keystore key cap | ✅ clean (iter-19) | `docs/audits/2026-05-02-android17-keystore-key-cap.md` |

**Total Android 17 audit coverage: 9 audit verdicts** — 8 clean, 1 with a deferred
finding. Engineering Debt Register's "Android 17 targetSdk=37 compliance" row is now
closed.

---

## 2. The Shizuku A17 risk

Captured in [`docs/audits/2026-05-17-shizuku-android17-compat.md`](../../../docs/audits/2026-05-17-shizuku-android17-compat.md):

- **Verdict**: `confirmed, needs-design`
- **Failure mode**: silent op-failure (NG shows green on onboarding but privileged ops time out or no-op) — worst-case stale-trust window
- **NG-side defence**: all 7 probes in `ShizukuBridge.java` are `Throwable`-caught; **NG will not crash on Android 17**, only fail-soft
- **Design captured**: non-destructive `hasAndroid17CompatibilityRisk(Context)` probe + onboarding banner recommending Wireless ADB fallback
- **Implementation queued**: iter-27 Priority 1 (device verification required)
- **External dependency**: Shizuku 13.x release containing the upstream fix

This is the **only open compliance-class risk** on the runway. Once the iter-27
runtime-detection helper ships, the user-visible posture is "warn + recommend
fallback" until Shizuku publishes a fix.

---

## 3. Active dependency surface (no changes this pass)

The full inventory in pass-1's `SECURITY_AND_DEPENDENCY_REVIEW.md` §1 remains
current. No `versions.gradle` changes this pass.

The two version pins queued for iter-27:

- **`jacoco_version = "0.8.13"`** — to be added when the JaCoCo wire-in lands per `docs/policy/jacoco-coverage-rollout.md`
- **`shizuku_version`** at compile-time — stays at `13.1.5` until a fix release ships; runtime pin (`MIN_ANDROID_17_COMPATIBLE_VERSION`) lives in source

---

## 4. CVE / advisory inventory (unchanged)

No new CVEs against current pins since pass-2:

- **CVE-2026-0073** (ADB-mode RCE) — onboarding patch-level warning shipped 2026-05-14
- **CVE-2026-3505 / 5588 / 5598** (BouncyCastle) — closed in 1.84 bump

---

## 5. Audit-doc inventory delta

Pass-2 audit count: 14.
Pass-3 added: 6 new audit verdicts.
**Pass-3 audit count: 20**.

The doctrine scales linearly. Each new audit costs ~1-2 hours including the methodology
+ verdict + cross-reference work. The audit dir is now the largest single source of
truth for NG's compliance posture against external-platform changes.

---

## 6. CI / process integrity (one addition this pass)

- `dependency-scan.yml`, `codeql.yml`, `tests.yml`, `lint.yml`, `release.yml`, `upstream-rename-watch.yml` — unchanged
- **`docs-link-check.yml`** ✨ new — runs `lycheeverse/lychee-action@v2` on push, PR, weekly Tuesday 11:11 UTC. Scope: `*.md` + `docs/**/*.md` + `.ai/**/*.md` + ROADMAP / CHANGELOG / PROJECT_CONTEXT.

**Total active CI workflows: 7** (was 6 at pass-2).

The JaCoCo coverage workflow addition (queued iter-27) would bring the total to 8.
The Shizuku release-feed watcher (queued iter-27) would bring the total to 9.

---

## 7. License posture (unchanged)

GPL-3.0-or-later, REUSE-compliant. Every new file in pass-3 carries the SPDX header.

The `docs-link-check.yml` workflow uses `lycheeverse/lychee-action@v2` — Apache-2.0,
GPL-3-compatible. No new third-party dep license risk.

The proposed JaCoCo wire-in adds `jacoco` 0.8.13 (EPL-2.0 — GPL-3-compatible per the
project's vendored deps inventory in `LICENSES/`).

---

## 8. Summary

After three passes the security and dependency posture is **measurably stronger** than
at session start:

- 3 hardenings shipped (Finder regex, InstallTranscript URI redactor, onboarding race)
- 6 new audit verdicts (5 clean + 1 design-pending)
- 0 new CVEs against current pins
- 1 new CI workflow (link checker)
- 2 plans queued (JaCoCo wire-in, Shizuku release-feed watcher)

The Android 17 cliff is now mapped, audited, and structurally clear. The one
remaining iter-27 risk (Shizuku A17) is captured with a non-destructive runtime
detection design and a clear external deadline (June 2026).
