<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# COMPETITOR_MATRIX — 2026-05-17 pass 3

Delta against pass-2's [`COMPETITOR_MATRIX.md`](../2026-05-17-pass-2/COMPETITOR_MATRIX.md).

**No competitor drift this pass.** Pass-3 was an internal-execution pass (audit batch +
hygiene items) and did not run an external competitor sweep. Pass-2's freshness
baseline remains current.

---

## 1. Why no drift check this pass

Pass-2 (same day) confirmed:

- Shizuku 13.6.0 still latest; Android-17 regression open
- Neo-Backup 8.3.18 (2026-05-04) is latest
- Material Components 1.14.0 still alpha
- Hail v1.10.0 still latest
- Magisk v30.7 still latest
- KernelSU v3.2.4 still latest
- Canta v3.2.2 still latest
- F-Droid 2.0-alpha9 (2026-05-08) latest

Re-checking the same day is wasted effort. The next meaningful drift event will be:

- A Shizuku release fixing #1965 / #1967 (probable June 2026 alongside A17 stable)
- Material Components 1.14.0 stable (no estimated date)
- F-Droid 2.0 beta / RC (probable Q3 2026)
- Android 17 stable to Pixels (June 2026)

The iter-27 CI watcher (Priority 4) will automate the Shizuku release-feed check.

---

## 2. Iter-26's findings against the competitor landscape

The 5 Android-17 audit verdicts confirm NG is **structurally aligned with the broader
competitor ecosystem on the targetSdk=37 path**:

- **Cleartext rejection at network-security-config base** — matches Mullvad VPN, ProtonMail, Auxio, Markor (S110, S111, S109, S117) and the rest of the FOSS Anti-Features-compliant cohort. The pin-set posture is stricter than most.
- **Zero `NsdManager` / mDNS surface** — distinguishes NG from network-discovery-driven peers like KDE Connect (S278) and Trusted Web Activity hosts (S107). NG's wireless ADB is input-driven by design; LAN-discovery is out of scope unless the Wear OS companion (T18) or KDE Connect protocol mode lands.
- **No BAL `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` usage** — distinguishes NG from the AM upstream profile-trigger path noted in iter-20. The iter-23 `am://` broadcast surface is the architecturally-aligned model and dovetails with Hail's URI automation pattern (S130).
- **ECH default-on safe** — no behaviour delta vs. competitors; all three NG endpoints are modern TLS.
- **ML-DSA forward-compatible** — NG's APK cert display is already at the LibChecker / AppVerifier polish level (S72, S63 / S90); the prettify-name map polish (iter-27 Priority 3) lifts the user-visible quality.

The Shizuku 13.6.0 / A17 regression hits **every Shizuku-using AM-class competitor**:
Canta, InstallerX-Revived, Amarok-Hider, OwnDroid, Language-Selector, Universal-Installer,
debuggable-app-data-backup, etc. (S19, S159, S162, S158, S164, S199, S165). NG's
runtime-detection fallback (iter-25 design) is therefore the entire-cohort's natural
mitigation pattern, not NG-specific.

---

## 3. New competitor entries

None this pass.

---

## 4. Watching list (iter-27+)

The maintainer should watch:

| Project | Watch for | Source |
|---|---|---|
| Shizuku 13.x | Release fixing #1965 / #1967 on Android 17 | S321, S322 |
| Material Components | 1.14.0 stable release | S57, S201, S325 |
| F-Droid 2.0 | alpha10+ / beta / RC | S168, S316 |
| AOSP Android 17 | Pixel rollout (June 2026) + behavior-change update | S324 |
| KernelSU | v3.3 with new SELinux / seccomp surface | S166 |
| Magisk | v30.8 with Android 17 QPR support | S52, S122 |

None are weekly-cadence; monthly spot-checks are sufficient. The Shizuku release-feed
watcher (iter-27 Priority 4) automates the highest-priority probe.
