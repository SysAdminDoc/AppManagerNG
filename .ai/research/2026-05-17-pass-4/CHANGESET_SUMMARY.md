<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET_SUMMARY — 2026-05-17 pass 4

## Created

- `.github/workflows/shizuku-release-watch.yml`
- `app/src/test/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridgeTest.java`
- `app/src/test/java/io/github/muntashirakon/AppManager/utils/UtilsCertificateAlgorithmTest.java`
- `.ai/research/2026-05-17-pass-4/*`

## Modified

- `ShizukuBridge.java` — Android 17 compatibility risk helper + fixed-version constant.
- `OnboardingFragment.java`, `fragment_onboarding.xml`, `strings.xml` — Shizuku Android-17 warning with Wireless ADB fallback.
- `Utils.java`, `PackageUtils.java`, `ScannerFragment.java` — ML-DSA display-name helper and call sites.
- `docs/audits/2026-05-17-shizuku-android17-compat.md` — updated from design-only to mitigated + remaining verification.
- `docs/audits/2026-05-17-android17-ml-dsa-keystore-oid.md` — updated to mark polish shipped.
- `ROADMAP.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md` — pass-4 planning and canonical context refresh.

## Verification

- XML parse checks passed for touched layout/string resources.
- Gradle tests could not run locally: no `JAVA_HOME` / no `java` command available.
