<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-17 pass 4

Pass 4 completed the non-device-gated iter-27 items. The next session should not repeat
the Shizuku/ML-DSA implementation work.

## Next exact steps

1. Install or configure a JDK on this Windows host, then run:
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.shizuku.ShizukuBridgeTest`
   - `.\gradlew.bat :app:testDebugUnitTest --tests io.github.muntashirakon.AppManager.utils.UtilsCertificateAlgorithmTest`
2. Run a wider unit-test pass if time allows: `.\gradlew.bat :app:testDebugUnitTest`.
3. Verify Shizuku on an Android 17 Pixel image/device:
   - Install Shizuku 13.6.0.
   - Open NG onboarding replay.
   - Confirm the Android-17 Shizuku warning appears and the warning starts Wireless ADB setup.
   - Try a harmless privileged operation through Shizuku and record the actual failure/success.
4. When upstream Shizuku ships a verified fix, set
   `ShizukuBridge.MIN_ANDROID_17_COMPATIBLE_VERSION` to that release and update the audit doc.

## Known limitation

No Android 17 device/emulator and no local JDK were available in this shell.
