<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

<p align="center">
  <img src="docs/raw/images/icon.png" alt="AppManagerNG Logo" height="150">
</p>

<h1 align="center">AppManagerNG</h1>

<p align="center">
  <em>The next-generation Android package manager — all the power, half the friction.</em>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-0.4.2-blue.svg" />
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

## What's new in NG

### Permission Inspector

A new main-menu entry that flips the standard "app -> permissions" view on its head. Pick a permission group (Camera, Microphone, Location, Contacts, SMS, Phone, Files & media, Calendar, Body sensors, Physical activity, Nearby devices, Notifications) and see every installed app that holds it, with a one-tap toggle per app and a master **Revoke for all apps** action in the toolbar. Changes persist through the same rule store the per-app permissions tab uses, so they survive reinstalls.

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

See [ROADMAP.md](ROADMAP.md) for the full prioritized roadmap and
[RESEARCH_FEATURE_PLAN_2026-05-25.md](RESEARCH_FEATURE_PLAN_2026-05-25.md)
for the current active backlog. Version targets:

- **v0.2.0** ✅ — applicationId rename to `io.github.sysadmindoc.AppManagerNG`, fresh keystore, GitHub Actions release pipeline, NG CONTRIBUTING.md
- **v0.3.0** ✅ — Material 3 dashboard refresh, Pro Mode toggle, edge-to-edge (Android 15/16 compliance), AMOLED/dark/light themes
- **v0.4.0** ✅ — Permission Inspector (review/bulk-revoke dangerous permissions across all apps; critical-package guard; recovery action) + Onboarding capability wizard
- **v0.5.0** ✅ 2026-05-25 — Discovery & Polish: in-app changelog viewer + auto-display after update, global in-app Settings search, plus the Iter-91 → Iter-142 batch (scheduled auto-backup polish, AES metadata v7 HKDF per-archive keys, ADB tcpip reuse, KernelSU/Magisk drop-cap diagnostics, Dhizuku detection, Restricted Settings unlock walkthrough, installer privilege cascade, OEM debloat-blocker bypass, per-app rollback, snapshot-bundle portability v2, Component rules preview, Tasker am:// intents, QS freeze tile, FM recursive search and ZIP create/extract, AGP 9.2.0). See `CHANGELOG.md`.
- **v0.5.x** — Settings reorganization by task, contextual help tooltips (deferred from v0.5.0).
- **v0.6.0** — Rootless Power: Routine Operations / Scheduler, Multi-Tag per App, Saved Filter Presets, Tracker Blocking via AppOps, Premium Polish Phase 2.

## Install

### Direct download
Grab the signed APK from [GitHub Releases](https://github.com/SysAdminDoc/AppManagerNG/releases/latest) — pick the `full` APK for optional online scan reports / debloat-definition updates, or the `floss` APK for the F-Droid-clean bundled-only build. Use `arm64-v8a` for modern devices or `universal` for maximum compatibility (older 32-bit ARM and x86_64 emulators). Details: [distribution build flavors](docs/distribution/build-flavors.md).

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

### ROM images

ROM builders who pre-seed F-Droid repositories should ship both the F-Droid 2.0
JSON file and the legacy XML file during the migration window. Templates and
placement notes live in [docs/distribution/rom-fdroid-preseed.md](docs/distribution/rom-fdroid-preseed.md).

> [!IMPORTANT]
> **Brazil / Indonesia / Singapore / Thailand users:** Google's [Android Developer Verification](https://developers.google.com/android/play-protect/developer-verification) program begins enforcement on certified devices in your region on **2026-09-30**. After that date, AppManagerNG (like every other on-device installer) is subject to the platform verifier gate. See [docs/sideload-verification.md](docs/sideload-verification.md) for what AppManagerNG does and does not do regarding developer verification.

## Verifying releases

APK signing certificate SHA-256 fingerprint:

```
21:5F:B4:70:63:2E:A6:CD:59:A4:BA:AB:35:0A:9E:0B:99:AD:11:0F:DD:FA:F5:A9:EA:64:61:E5:D0:C2:38:6C
```

Verify with [AppVerifier](https://github.com/soupslurpr/AppVerifier) or:

```bash
apksigner verify --print-certs AppManagerNG-<version>.apk | grep SHA-256
```

Release publishing also runs a two-build reproducibility gate: CI builds the
signed APK twice from a clean checkout, compares the binary SHA-256 hashes, and
uploads a `.sha256` sidecar only when the bytes match. Maintainers can run the
same check locally with [`scripts/verify_reproducible_release.ps1`](scripts/verify_reproducible_release.ps1)
on Windows or [`scripts/verify_reproducible_release.sh`](scripts/verify_reproducible_release.sh)
on Linux CI; details are in [docs/distribution/reproducible-builds.md](docs/distribution/reproducible-builds.md).

### Stable fingerprint URL (for programmatic verification)

The same fingerprint is published in machine-parseable form at a stable URL so
AppVerifier and similar tools can fetch it without scraping the README:

> <https://raw.githubusercontent.com/SysAdminDoc/AppManagerNG/main/docs/fingerprints.txt>

The file is comment-tolerant (`#` prefix) and uses the same `package:` /
`sha256:` record pairs as [SD Maid SE's published fingerprints](https://github.com/d4rken-org/sdmaid-se).

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
