<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

<p align="center">
  <img src="docs/raw/images/icon.png" alt="AppManagerNG Logo" height="150">
</p>

<h1 align="center">AppManagerNG</h1>

<p align="center">
  <em>The next-generation Android package manager — all the power, half the friction.</em>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-0.3.0-blue.svg" />
  <img alt="License" src="https://img.shields.io/badge/license-GPL--3.0--or--later-green.svg" />
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%205.0%2B-brightgreen.svg" />
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-21-orange.svg" />
  <img alt="Target SDK" src="https://img.shields.io/badge/targetSdk-36-orange.svg" />
</p>

---

## What is AppManagerNG?

AppManagerNG is a continuation of the App Manager project — a full-featured, root/ADB-aware
package manager for Android — with a focus on **user experience, polish, and approachability**
without sacrificing any of the depth that makes the original a power user staple.

Think of it as AppManager with a friendlier front door: the same engine, the same root/ADB
capabilities, the same component blocking and tracker scanning — but layered behind a Material 3
interface that doesn't punish casual users for opening it.

> [!NOTE]
> This is an early-stage project. v0.1.0 is the rebranded baseline — the code below is the
> upstream AppManager source pinned at commit `3d11bcb` (2026-04-16). Subsequent releases will
> introduce the AppManagerNG UX overhaul incrementally, in working increments, with full
> attribution to upstream contributions preserved.

## Features (inherited from upstream baseline)

### General
- Material 3 with dynamic colours
- Rich app information page (activities, services, providers, receivers, app ops, permissions, signatures, shared libraries)
- Activity launcher and activity-shortcut creator
- Activity interceptor
- Tracker and library scanner with class dumps
- Manifest viewer/exporter
- App usage, data usage (mobile + Wi-Fi), storage info
- Install/uninstall APK / APKS / APKM / XAPK (with OBB support)
- APK sharing
- Backup/restore APK files
- Batch and single-click operations
- Logcat viewer, manager, exporter
- Profiles
- Debloater
- Code editor
- File manager
- Simple terminal emulator
- Aurora Store / F-Droid client launch integration
- APK signing with custom signatures
- Backup encryption: OpenPGP (OpenKeychain), RSA, ECC (hybrid + AES), AES
- Foreground UI component tracking

### Root/ADB
- Revoke runtime + development permissions
- App-op mode editing
- Display/kill/force-stop running apps and processes
- Clear app data/cache
- Net policy view/edit
- Battery optimization control
- Freeze/unfreeze apps

### Root only
- Block any component (activities/receivers/services/providers); native + Watt + Blocker import/export
- View/edit/delete shared preferences
- Backup/restore apps with data, rules, and extras (permissions, battery opt, SSAID, etc.)
- View/edit system configurations (blacklisted/whitelisted apps, permissions)
- View/change SSAID

## Roadmap

See [ROADMAP.md](ROADMAP.md) for the full prioritized roadmap. Version targets:

- **v0.2.0** — applicationId rename to `io.github.sysadmindoc.AppManagerNG`, fresh keystore, GitHub Actions release pipeline, NG CONTRIBUTING.md
- **v0.3.0** — Material 3 dashboard refresh, Pro Mode toggle, edge-to-edge (Android 15/16 compliance)
- **v0.4.0** — Onboarding flow (root/Shizuku/ADB capability detection + plain-language explainer)
- **v0.5.0** — Settings reorganization, global in-app search, contextual help

## Install

### Direct download
Grab the signed APK from [GitHub Releases](https://github.com/SysAdminDoc/AppManagerNG/releases/latest) — pick `AppManagerNG-<version>-arm64-v8a.apk` for modern devices or `AppManagerNG-<version>-universal.apk` for maximum compatibility (older 32-bit ARM and x86_64 emulators).

### Via Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) is the recommended path for users who want automatic update checks straight from GitHub Releases without going through any store.

1. Install Obtainium.
2. **Add App** → paste the URL: `https://github.com/SysAdminDoc/AppManagerNG`
3. *(Optional but recommended)* Use the bundled config file for fully pre-tuned settings (correct ABI auto-detection, version regex, prerelease-skipping):

   - Open `Obtainium → Settings → Import/Export → Import Apps From File`.
   - Select [`docs/distribution/obtainium-config.json`](docs/distribution/obtainium-config.json) (the file is wrapped in the standard Obtainium `{"apps":[…]}` backup format so the import flow accepts it directly).

Obtainium will then auto-track every signed release published to this repo and notify you on update.

> [!TIP]
> Pair Obtainium with [AppVerifier](https://github.com/soupslurpr/AppVerifier) so every Obtainium-fetched APK is checked against the published certificate fingerprint below before install.

## Verifying releases

APK signing certificate SHA-256 fingerprint:

```
21:5F:B4:70:63:2E:A6:CD:59:A4:BA:AB:35:0A:9E:0B:99:AD:11:0F:DD:FA:F5:A9:EA:64:61:E5:D0:C2:38:6C
```

Verify with [AppVerifier](https://github.com/soupslurpr/AppVerifier) or:

```bash
apksigner verify --print-certs AppManagerNG-<version>.apk | grep SHA-256
```

## Build instructions

See [BUILDING.rst](BUILDING.rst). Submodules must be initialized before building:

```bash
git submodule update --init --recursive
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Translation contributions go through Weblate (link TBD).

## License

Released under **GPL-3.0-or-later**. Per-file SPDX headers and the `LICENSES/` directory follow
the [REUSE](https://reuse.software/) specification — please preserve them.

See [COPYING](COPYING) for the full GPL-3.0 text. Vendored third-party components retain their
original licenses (Apache-2.0, BSD-2-Clause, BSD-3-Clause, CC-BY-SA-4.0, GPL-2.0, ISC, MIT, WTFPL)
as documented in `LICENSES/`.

## Credits — thank you to the original

AppManagerNG would not exist without the years of work that went into the upstream
[App Manager](https://github.com/MuntashirAkon/AppManager) project by **Muntashir Al-Islam** and
the broader contributor community. AppManagerNG was bootstrapped from upstream commit
[`3d11bcb`](https://github.com/MuntashirAkon/AppManager/commit/3d11bcbc399d3a4f995b544e26d86bd80487fd32)
on 2026-04-30.

The original project remains the canonical implementation; AppManagerNG is a parallel effort
focused on UX polish and approachability. If you want the upstream experience — or want to
contribute features broadly applicable to the package-manager domain — please direct your effort
[upstream](https://github.com/MuntashirAkon/AppManager) first.

A full list of credits and bundled libraries is available in the **About** section of the app.
