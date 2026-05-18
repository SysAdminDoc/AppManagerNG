<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 124 — Android 17 targetSdk 37 compatibility batch

## Roadmap row

Eng-Debt **Android 17 ACCESS_LOCAL_NETWORK + Static-Final Reflection Ban** is
shipped.

## What changed

- Declared `android.permission.ACCESS_LOCAL_NETWORK` in the app manifest.
- Added `ManifestCompat.permission.ACCESS_LOCAL_NETWORK`.
- Added a targetSdk=37 + Android 17 Wireless ADB preflight in `Ops`.
- Added a local-network permission blocker dialog for startup, Settings,
  onboarding, Wireless ADB chooser, pairing, and background reconnect paths.
- Preserved ADB-over-TCP loopback/default-port fallback when local-network mDNS
  cannot be used.
- Gated the legacy LG `Resources.mSystem` static-final reflection workaround in
  `RootServiceMain` to runtimes below API 37.
- Added `docs/audits/2026-05-18-android17-targetsdk37-batch.md`.
- Updated `ROADMAP.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md`, and the older
  static-final reflection audit.

## Source verification

- Android 17 targetSdk behavior docs confirm `ACCESS_LOCAL_NETWORK`, static-final
  reflection blocking, `IntentSender` BAL hardening, and native DCL requirements.
- Android 17 all-app behavior docs confirm MemoryLimiter exit descriptions and
  Android Keystore per-app key caps.
- Android Developers Blog Beta 4 post confirms the local-network, native DCL,
  app-memory, and ML-DSA context used by adjacent roadmap rows.

## Local validation

- `.\gradlew.bat :app:assembleFlossDebug --console=plain` passed.
- Existing project Java/source deprecation warnings remain.
