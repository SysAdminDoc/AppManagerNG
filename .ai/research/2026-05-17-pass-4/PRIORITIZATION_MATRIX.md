<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# PRIORITIZATION_MATRIX — 2026-05-17 pass 4

| Candidate | Impact | Effort | Risk | Decision |
|-----------|--------|--------|------|----------|
| Shizuku Android-17 warning | High | Low | Low | Shipped. External deadline is June 2026 Android 17 rollout; non-destructive warning avoids disabling useful paths. |
| Shizuku release watcher | Medium | Low | Low | Shipped. Reduces chance of missing an upstream fixed release. |
| ML-DSA display-name map | Medium | Low | Very low | Shipped. User-visible polish for Android 17 post-quantum APK signing; no behavior risk. |
| JaCoCo wire-in | Medium | Medium | Medium | Deferred. Needs local JDK/Gradle verification. |
| Android 17 device verification | High | Medium | Medium | Next. Required before setting Shizuku fixed-version floor or targetSdk=37 release sign-off. |
