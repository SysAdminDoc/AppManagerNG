<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# SOURCE_REGISTER — 2026-05-17 pass 4

## External sources re-checked

| ID | URL | How used |
|----|-----|----------|
| S22 / S121 | https://github.com/RikkaApps/Shizuku/releases | Confirmed official Shizuku latest release is still `v13.6.0`; no release note mentions Android 17 fix. |
| S321 | https://github.com/RikkaApps/Shizuku/issues/1965 | Confirmed open Android 17 Beta 3 blank Application Management report; body says apps still appear to function. |
| S322 | https://github.com/RikkaApps/Shizuku/issues/1967 | Confirmed open companion Android 17 Beta 3 report where apps cannot get Shizuku. |
| S326 | https://github.com/RikkaApps/Shizuku/issues/1988 | Added to ROADMAP source appendix; repeats empty managed-app list on Android 17 / Pixel 9 Pro XL. |
| S327 | https://github.com/thedjchi/Shizuku/issues/172 | Added to ROADMAP source appendix; fork-maintained Android 17 support tracker assigned to v14.0.0. |

## Local sources used

- `app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java`
- `app/src/main/res/layout/fragment_onboarding.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/io/github/muntashirakon/AppManager/utils/Utils.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/utils/PackageUtils.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/scanner/ScannerFragment.java`
- `docs/audits/2026-05-17-shizuku-android17-compat.md`
- `docs/audits/2026-05-17-android17-ml-dsa-keystore-oid.md`
- `ROADMAP.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md`
- `.github/workflows/upstream-rename-watch.yml` as the workflow pattern for the Shizuku release watcher.
