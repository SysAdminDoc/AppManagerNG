<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

<p align="center">
  <img src="docs/raw/images/icon.png" alt="AppManagerNG Logo" height="150">
</p>

<h1 align="center">AppManagerNG</h1>

<p align="center">
  <em>The next-generation Android package manager — all the power, half the friction.</em>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-0.1.0-blue.svg" />
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

AppManagerNG-specific direction (subject to change):

- **v0.2.0** — Package rename to `io.github.sysadmindoc.AppManagerNG`, namespace migration, fresh keystore
- **v0.3.0** — Material 3 dashboard refresh — friendlier home screen, progressive disclosure of advanced features, "Pro mode" toggle
- **v0.4.0** — Onboarding flow for new users (root/ADB capability detection + plain-language explanation of what each tier unlocks)
- **v0.5.0** — Settings reorganization, search, in-app help
- **Later** — New features called out in upstream's "Upcoming features" list (Finder, basic APK editing, routine operations, crash monitor, etc.)

## Build instructions

See [BUILDING.rst](BUILDING.rst). Submodules must be initialized before building:

```bash
git submodule update --init --recursive
```

## Contributing

See [CONTRIBUTING.rst](CONTRIBUTING.rst).

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
