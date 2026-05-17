<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# SECURITY_AND_DEPENDENCY_REVIEW — 2026-05-17 pass 4

## Shizuku Android 17

Security posture improved by avoiding stale trust. Before pass 4, NG could show Shizuku
as healthy on Android 17 based on binder/permission probes while public reports showed
manager/app-list failures. Pass 4 adds an Android-17-only warning and Wireless ADB
fallback path while preserving user choice.

Dependency posture remains unchanged:

- Compile-time Shizuku API stays pinned at `13.1.5`.
- Runtime manager recommendation stays `13.6.0+` for Android 16 QPR1 / trusted WLAN.
- Android 17 compatibility floor stays unknown (`MIN_ANDROID_17_COMPATIBLE_VERSION = null`).

## ML-DSA

Android 17 ML-DSA APK-signing OIDs are now recognized for display. This is not a
signature-verification algorithm change; NG already displayed the raw OID and did not
branch on algorithm name. The change reduces ambiguity in security UI.

## CI

`shizuku-release-watch.yml` is read-only against upstream GitHub API and writes only an
issue in the NG repo. It does not execute upstream release artifacts.
