<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Audit: Android 17 targetSdk 37 compatibility batch

**Date:** 2026-05-18
**Sources:** [Android 17 behavior changes for apps targeting Android 17](https://developer.android.com/about/versions/17/behavior-changes-17), [Android 17 behavior changes for all apps](https://developer.android.com/about/versions/17/behavior-changes-all), [Android Developers Blog: The Fourth Beta of Android 17](https://android-developers.googleblog.com/2026/04/the-fourth-beta-of-android-17.html)
**Audited against:** `2728af1` plus this iter-124 changeset
**Roadmap row:** Engineering Debt Register / **Android 17 ACCESS_LOCAL_NETWORK + Static-Final Reflection Ban**
**Verdict:** ✅ **remediated** for affected Wireless ADB and static-final reflection sites; other sub-audits clean.

## Premise

Android 17 introduces multiple targetSdk=37 and runtime behaviors that can affect
AppManagerNG before the project bumps `target_sdk` from 36 to 37:

- local network access moves behind `ACCESS_LOCAL_NETWORK` for targetSdk=37 apps;
- reflective writes to `static final` fields fail on Android 17 when targeting
  API 37;
- Android Keystore adds per-UID key-count caps;
- process exit diagnostics can include `MemoryLimiter:AnonSwap`;
- native dynamic code loaded through `System.load()` must be read-only;
- background activity launch inheritance no longer covers legacy
  `IntentSender` paths.

## Sweep methodology

Source roots covered: `app/src/main/java`, `libcore`, `libserver`, `server`, and
`hiddenapi`.

Commands used:

```powershell
rg -n "Socket\(|SSLSocket|InetAddress|InetSocketAddress|NetworkInterface|getNetworkInterfaces|MulticastSocket|DatagramSocket|NsdManager|ACCESS_LOCAL_NETWORK|adb.*host|host.*adb|Wireless" app/src/main/java libcore libserver server hiddenapi
rg -n "class AdbMdns|AdbMdns|NsdManager|MulticastLock|ACCESS_LOCAL_NETWORK|Socket\(|InetSocketAddress|ServerSocket\(|DatagramSocket|MulticastSocket" app/src/main/java libcore libserver server hiddenapi
rg -n "AndroidKeyStore|KeyGenParameterSpec|ApplicationExitInfo|getHistoricalProcessExitReasons|MemoryLimiter|AnonSwap|SwapFull|IntentSender|sendIntent\(|MODE_BACKGROUND_ACTIVITY_START|ActivityOptions\.makeBasic|System\.load\(|System\.loadLibrary|DexClassLoader|PathClassLoader|InMemoryDexClassLoader|BaseDexClassLoader" app/src/main/java libcore libserver server hiddenapi
rg -n "Resources\.getSystem|ResourcesWrapper|mSystem|systemResField|getSystem\(\)" server app/src/main/java libcore libserver
```

## Findings

| Area | Matches | Verdict |
| --- | --- | --- |
| Local network / mDNS | `AdbUtils` and `AdbPairingService` use `AdbMdns` to discover Wireless ADB pairing/connect services. App-side ADB transport still connects to loopback or emulator host through `ServerConfig.getAdbHost()`. | **Remediated.** The manifest now declares `android.permission.ACCESS_LOCAL_NETWORK`. `Ops` blocks targetSdk=37 + Android 17 Wireless ADB mDNS flows until the permission is granted, shows a recovery dialog from startup, Settings, onboarding, and chooser paths, and logs the background reconnect blocker. ADB-over-TCP keeps the default-port loopback fallback instead of forcing mDNS. |
| Static-final reflection | `TypefaceUtil` was already fixed in the 2026-05-08 audit. `RootServiceMain` still had the LG `Resources.mSystem` write. | **Remediated.** `RootServiceMain` now runs the LG static-final write only on `Build.VERSION.SDK_INT < 37`, where the legacy workaround is still relevant and Android 17's ban is not active. |
| Android Keystore key caps | `CompatUtil` creates the bounded AndroidKeyStore aliases used for local key protection; `KeyStoreManager` user key storage is BKS/file-backed, not unbounded AndroidKeyStore alias generation. | **Clean (audit).** No source path creates unbounded AndroidKeyStore aliases. Existing audit: [`2026-05-02-android17-keystore-key-cap.md`](2026-05-02-android17-keystore-key-cap.md). |
| MemoryLimiter exit reasons | Only hidden API stubs reference `ApplicationExitInfo` / `getHistoricalProcessExitReasons`; AppManagerNG has no production exit-reason decoder or UI that would mislabel `MemoryLimiter:AnonSwap`. | **Clean.** No user-facing parser or policy path to update in this row. |
| Native DCL read-only requirement | No `System.load(<path>)` call sites were found. Existing calls are `System.loadLibrary("am")` for packaged JNI, and file-manager/signature code reads `.so` files as APK contents. | **Clean (audit).** The Android 17 rule targets dynamic native file loads through `System.load()`, not packaged `loadLibrary` usage. |
| `IntentSender` BAL hardening | Production code creates package-installer and OpenPGP `IntentSender` status/confirmation surfaces, but the sweep found no direct `IntentSender.sendIntent()`, `ActivityOptions.makeBasic()`, or legacy BAL mode usage in app code. | **Clean.** Hidden API stubs expose framework signatures only; they are not BAL launches. |

## Code changes

- Added `ACCESS_LOCAL_NETWORK` to the manifest and `ManifestCompat.permission`.
- Added `Ops.STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED`.
- Added `Ops.isLocalNetworkPermissionMissing()` and
  `Ops.displayLocalNetworkPermissionMessage()`.
- Routed startup, Settings, onboarding, Wireless ADB chooser, pairing, and
  background reconnect paths through the local-network preflight.
- Gated the LG `Resources.mSystem` workaround in `RootServiceMain` to
  `Build.VERSION.SDK_INT < 37`.

## Verification

```powershell
.\gradlew.bat :app:assembleFlossDebug --console=plain
```

Result: passed. Existing Java 8 source/target and project deprecation warnings
remain; no new compile or manifest failures.

## Follow-ups

- Re-test Wireless ADB pairing on an Android 17 targetSdk=37 build once
  compile SDK 37 and an Android 17 device/emulator image are available.
- Keep `ACCESS_LOCAL_NETWORK` out of unrelated networking surfaces unless a
  future feature adds LAN discovery or direct LAN device communication.
