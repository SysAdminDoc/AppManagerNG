<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# FEATURE_BACKLOG — 2026-05-17 pass 4

## Closed in pass 4

| Item | Status | Evidence |
|------|--------|----------|
| Shizuku Android-17 runtime risk helper | ✅ shipped | `ShizukuBridge.hasAndroid17CompatibilityRisk(Context)` with fixed-version constant left `null`. |
| Shizuku Android-17 onboarding fallback | ✅ shipped | `warning_shizuku_android17` view; tapping it starts Wireless ADB setup. |
| Shizuku release watcher | ✅ shipped | `.github/workflows/shizuku-release-watch.yml`. |
| ML-DSA OID display names | ✅ shipped | `Utils.getCertificateSignatureAlgorithmName()` wired into Package Info, Scanner, and `getIssuerAndAlg()`. |

## Carryover

| Item | Why it remains | Next step |
|------|----------------|-----------|
| Android 17 device verification for Shizuku | No Android 17 device/emulator available in this shell. | Run NG on Pixel Android 17 image/device; verify Shizuku authorization and a privileged no-op. |
| Populate `MIN_ANDROID_17_COMPATIBLE_VERSION` | No official Shizuku fixed release yet. | Let watcher open an issue when a candidate release appears; verify before setting the floor. |
| JaCoCo wire-in | Requires local JDK/Gradle verification. | Install/configure JDK, then follow `docs/policy/jacoco-coverage-rollout.md`. |
