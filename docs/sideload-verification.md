<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Sideload Verification in AppManagerNG

This document describes how AppManagerNG interacts with Google's platform
verification systems when installing APKs, and what users should expect as
enforcement tightens.

## Google's Android Developer Verification program

Starting **2026-09-30**, Google enforces [Android Developer Verification](https://developers.google.com/android/play-protect/developer-verification)
on certified devices in **Brazil, Indonesia, Singapore, and Thailand**. This
means:

- Play Protect may block installs from unverified developers on certified
  devices in those regions.
- AppManagerNG (like every on-device installer) is subject to the platform
  verifier gate when performing installs.
- **ADB installs remain exempt** — installs via `adb install` bypass the
  verifier gate entirely.

## What AppManagerNG does

1. **Preserves verifier failure reasons.** When a platform verifier rejects an
   install, AppManagerNG captures the status code and reason from
   `PackageInstaller` and surfaces them in the install transcript
   (`InstallTranscript`).

2. **Offers ADB-mode retry.** When ADB mode is already reachable and an install
   fails due to verifier rejection, AppManagerNG can retry the install through
   the ADB channel, which is exempt from the verifier gate.

3. **Explains the situation.** Install failure screens include guidance specific
   to verifier rejections, distinguishing them from signature mismatches,
   permission denials, and other failure modes.

## What AppManagerNG does NOT do

- **Does not bypass verification.** AppManagerNG does not attempt to disable,
  circumvent, or interfere with Play Protect or the platform verifier.
- **Does not spoof installer identity.** The install is attributed to
  AppManagerNG's package name honestly.
- **Does not suppress warnings.** Platform-level warnings and confirmation
  dialogs are passed through to the user.

## Advanced Protection (Android 16+)

Android 16 introduces [Advanced Protection](https://developer.android.com/about/versions/17/features)
via `AdvancedProtectionManager`. When active, sideloading is blocked outright.
A future AppManagerNG update will detect this state and explain it before
attempting an install (tracked in ROADMAP as the Advanced Protection surfacing
item).

## Enhanced verification flow (August 2026)

Google's enhanced verification flow for power users requires:
1. Developer mode enabled
2. A one-day waiting period after enabling developer mode
3. Biometric confirmation per install

This flow applies globally from August 2026. AppManagerNG will document the
flow's status in the installer UI when it detects the enhanced verification
gate is active.

## Recommendations for affected users

- **Obtainium users**: Pair with [AppVerifier](https://github.com/soupslurpr/AppVerifier)
  to verify APK signatures before install.
- **ADB available**: Use ADB mode in AppManagerNG for verifier-exempt installs.
- **No ADB**: Accept the platform verification prompt when it appears.
- **Advanced Protection active**: Sideloading is not possible while Advanced
  Protection is enabled. This is a device-level policy, not an AppManagerNG
  limitation.
