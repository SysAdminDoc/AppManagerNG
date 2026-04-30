# Changelog

All notable changes to AppManagerNG are documented in this file.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## v0.2.0 — 2026-04-30

Identity milestone: AppManagerNG now has its own install identity, signing key, and release
pipeline, fully separated from the upstream package.

### Added
- **`applicationId` rename**: install identity changed from `io.github.muntashirakon.AppManager`
  to `io.github.sysadmindoc.AppManagerNG`. Source namespace kept at
  `io.github.muntashirakon.AppManager` (full namespace rename is future work).
- **New release keystore**: `AppManagerNG-release.jks` — 4096-bit RSA, 10,000-day validity.
  SHA-256: `21:5F:B4:70:63:2E:A6:CD:59:A4:BA:AB:35:0A:9E:0B:99:AD:11:0F:DD:FA:F5:A9:EA:64:61:E5:D0:C2:38:6C`
- **GitHub Actions release pipeline** (`.github/workflows/release.yml`): tag push → build →
  sign → upload arm64-v8a + universal APKs to GitHub Releases.
- **CONTRIBUTING.md**: NG-specific contribution guidelines (replaces upstream CONTRIBUTING.rst
  reference); covers AI code policy, commit format, upstream sync protocol, translation note.
- **ROADMAP.md**: comprehensive prioritized roadmap through v0.6.0+ (17 themes, 37 sources).
- **AppVerifier fingerprint** in README for release verification.
- **16KB page size compliance**: `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` CMake argument
  added to `app/build.gradle` for Android 15+ physical device compatibility.

### Fixed
- `LocalFileOverlay.java`: hardcoded application ID fallback now uses `BuildConfig.APPLICATION_ID`
  instead of a literal string, so it tracks applicationId changes automatically.
- `settings.gradle`: `rootProject.name` updated to `AppManagerNG`.

## v0.1.0 — 2026-04-30

Initial AppManagerNG release. Repo bootstrap from upstream
[App Manager](https://github.com/MuntashirAkon/AppManager) commit
[`3d11bcb`](https://github.com/MuntashirAkon/AppManager/commit/3d11bcbc399d3a4f995b544e26d86bd80487fd32)
(2026-04-16, upstream tag context: post-v4.0.5).

### Added
- AppManagerNG-branded README.md with shields.io badges, GPL-3.0-or-later notice, and upstream credit
- CHANGELOG.md (this file)
- Branding/logo prompts directory (`branding/logo-prompts.md`)

### Changed
- App display name (`app_name` resValue): `App Manager` → `AppManagerNG` (release), `AM Debug` → `AM-NG Debug` (debug)
- Android `versionName`: `4.0.5` → `0.1.0`; `versionCode`: `445` → `1`

### Preserved (unchanged from upstream)
- All Java/Kotlin/Native sources
- Package name (`io.github.muntashirakon.AppManager`) and namespace — rebrand deferred to v0.2.0
- License files: `COPYING`, `LICENSES/` directory (REUSE-compliant), per-file SPDX headers
- Build configuration (Gradle, AGP version, dependencies, signing config)
- Documentation: `BUILDING.rst`, `CONTRIBUTING.rst`, `PRIVACY_POLICY.rst`, `docs/`
- F-Droid metadata (`fastlane/`)
- Submodule pointers (`scripts/android-libraries`, `scripts/android-debloat-list`)

### Roadmap
- **v0.2.0** — applicationId + namespace rename to `io.github.sysadmindoc.AppManagerNG`; fresh keystore
- **v0.3.0** — Material 3 dashboard refresh + Pro-mode toggle for advanced features
- **v0.4.0** — Onboarding flow (root/ADB capability detection + plain-language explainer)
- **v0.5.0** — Settings reorganization + in-app search and help
