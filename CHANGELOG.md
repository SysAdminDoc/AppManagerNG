# Changelog

All notable changes to AppManagerNG are documented in this file.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
