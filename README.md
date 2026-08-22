<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

<p align="center">
  <img src="docs/raw/images/icon.png" alt="AppManagerNG Logo" height="150">
</p>

<h1 align="center">AppManagerNG</h1>

<p align="center">
  <em>Full Android package control without the clutter.</em>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-0.6.15-blue.svg" />
  <img alt="License" src="https://img.shields.io/badge/license-GPL--3.0--or--later-green.svg" />
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%205.0%2B-brightgreen.svg" />
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-21-orange.svg" />
  <img alt="Target SDK" src="https://img.shields.io/badge/targetSdk-36-orange.svg" />
</p>

---

## What is AppManagerNG?

AppManagerNG continues the App Manager project as a full-featured, root/ADB-aware
package manager for Android. The interface focuses on **clarity and approachability**
without sacrificing any of the depth that makes the original a power user staple.

Think of it as AppManager with a friendlier front door: the same engine, the same root/ADB
capabilities, the same component blocking and tracker scanning, behind a Material 3
interface that doesn't punish casual users for opening it.

> [!NOTE]
> AppManagerNG began as a rebranded baseline of the upstream AppManager source at commit
> `3d11bcb`, bootstrapped on 2026-04-30. The NG UX overhaul has been landing incrementally
> since, in working increments, with attribution to upstream contributions preserved. See
> [CHANGELOG.md](CHANGELOG.md) for what each release changed.

## What's new in NG

Everything below is additive to the upstream feature set. The inherited capabilities are
listed further down. For the release-by-release detail, see [CHANGELOG.md](CHANGELOG.md).

### Permission Inspector

Flips the standard "app → permissions" view on its head. Pick a permission group (Camera,
Microphone, Location, Contacts, SMS, Phone, Files & media, Calendar, Body sensors, Physical
activity, Nearby devices, Notifications) and see every installed app that holds it, with a
one-tap toggle per app and a master **Revoke for all apps** action. Changes persist through the
same rule store the per-app permissions tab uses, so they survive reinstalls.

### App Archiving (Android 15+)

Reclaim storage without losing anything: archiving removes an app's APK and cache but keeps its
data and launcher icon, so unarchiving picks up where you left off. Drives the native Android 15
archiving API. Archive one app from App Info or many in a batch operation. Archived apps
are detected and labelled in the app list. Works on user apps without root.

### Routine ops

Profiles that run themselves. Schedule a profile, or trigger it on app install/update/uninstall
with an optional package glob (`com.vendor.*`) so it fires only for the apps you care about.
Backups gained ordered per-tag policies: the first matching tag decides which parts are backed
up, how they are encrypted, how long they are kept, and whether they land locally or on a SAF
destination. A preview shows which rule wins before you commit.

### Finder

Query your device instead of scrolling it. Filter apps by trackers (including by class name or
regex), permissions, app ops, signature, installer, SDK levels, size, usage, backup state,
bloatware classification, intent actions, and domain links. Save the filter and reuse it.
Native-library readiness is a predicate too: sweep for apps that are not 16 KB page-aligned (so
they will not run on Android 15+ devices using 16 KB pages), ship only 32-bit code, or store
their libraries compressed.

### Snapshot bundles

Export a portable, encrypted record of your app state, including rules, profiles, and preferences, then
restore it selectively on another device, previewing each section before it is applied. Bundles
are AES-256-GCM with an Argon2id-derived key, authenticated before anything is written, and
streamed rather than held in memory, so a large bundle neither exhausts RAM nor half-applies.

### An installer that tells you what it is doing

Before an install commits, the prompt names the sensitive permissions the APK requests and the
API levels it targets and supports, and reports the trackers it found. Installs that cannot fit
are refused up front, with required-vs-free storage and a shortcut to the system storage
manager. APK and OBB installation is rollback-safe: expansion files are staged and validated
before the live ones are touched, so a failed install leaves the previous version intact.

### A scanner that states its limits

Tracker and library results say what they are evidence of. An empty result reads "No known
tracker matches" and explains why absence is not proof. Renamed identifiers, reflection, and
runtime-loaded code all evade class-name matching. Each match is labelled confirmed or
tentative and names the detector behind it, and exported reports carry the same provenance.

### Sideload-aware diagnostics

App Details explains Android's restricted-settings gate, which silently greys out accessibility,
notification-listener, and health toggles for apps installed outside a store. It also explains how to lift
it. Alongside it: privileged-mode capability detection (root, Shizuku, ADB, Dhizuku, KernelSU),
an installer privilege cascade that falls back gracefully, and a biometric gate on the terminal
and on backup deletion.

### Discovery and polish

Material 3 with dynamic colours and a pure-black theme, an onboarding capability wizard, a Pro
Mode toggle that keeps advanced surfaces out of the way until you want them, global in-app
Settings search, an in-app changelog viewer, and Quick Settings tiles for freeze and force-stop.

### Releases you can check

Every release is built twice from a clean checkout and published only if both builds are
byte-identical. A single fail-closed gate runs the tests, lint, version-consistency, and
artifact-identity checks and emits a receipt binding the commit and tag to every artifact hash,
the signing fingerprint, and the tool versions. A CycloneDX SBOM ships with each release, and
the signing fingerprint is published at a [stable URL](https://raw.githubusercontent.com/SysAdminDoc/AppManagerNG/main/docs/fingerprints.txt)
for tools like AppVerifier. Release `BUILD_TIME_MILLIS` values come from
`SOURCE_DATE_EPOCH` or the Git commit timestamp, and release builds fail when
neither deterministic source is available. The release verifier also rebuilds
the privileged server JARs from a second checkout path with different locale
and timezone settings, then records both hash pairs.

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
- Backup imports from OAndBackup, current Neo Backup, Swift Backup 3.0 to 3.2, and Titanium Backup
- Ordered per-tag backup policies with shared manual/scheduled resolution, per-rule parts, encryption, retention, local/SAF destinations, and winner previews
- Foreground UI component tracking
- No-root force-stop and app data/cache controls through responsive accessibility settings automation

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

See [ROADMAP.md](ROADMAP.md) for planned work, [RESEARCH.md](RESEARCH.md) for
the current research backing, and
[`docs/roadmap/COMPLETED.md`](docs/roadmap/COMPLETED.md) for completed or stale
items. Maintainer-local historical archives are intentionally excluded from
published checkouts; shipped work remains traceable through this changelog and Git history.
Version targets:

- **v0.6.15** ✅ 2026-08-20. Introduced a modular open-package logo across the launcher, themed icon, splash screen, store listing, documentation, and TV banner.
- **v0.6.14** ✅ 2026-08-20. Quieter typography, compact headers, flat status text, divider-led lists, and denser diagnostic screens.
- **v0.6.13** ✅ 2026-08-11. Removed the defunct Pithus scanner and corrected several misleading backup, privilege, and batch-operation states. Android 17 package enumeration gained device coverage.
- **v0.6.12** ✅ 2026-08-08. Kept the bundled BouncyCastle keystore implementation in release builds so recovery passwords persist correctly.
- **v0.6.11** ✅ 2026-08-07. Fixed recovery-password storage and prevented unreadable keystore data from being replaced.
- **v0.6.8 through v0.6.10** ✅ 2026-08-02. Hardened privileged parsing and IPC, restored the CVE gate, improved accessibility checks, and tightened release verification.
- **v0.6.7** ✅ 2026-07-29. Added installer storage checks, clearer scan provenance, native-library readiness filters, and a fail-closed local release gate.
- **v0.6.1 through v0.6.5** ✅. Improved resource handling and refreshed Settings search, one-click operations, theming, terminal, scanner, and widgets.
- **v0.6.0** ✅ 2026-06-14. Added routine operations, scheduling, app-event triggers, safer backup restore, and IPC reliability work.
- **v0.5.x** ✅. Added the in-app changelog, Settings search, scheduled backup controls, ADB reuse, snapshot portability, component rules, and file-manager search and archives.
- **v0.4.0** ✅. Added Permission Inspector and the access-mode onboarding flow.
- **v0.3.0** ✅. Introduced the Material 3 interface, edge-to-edge layouts, and AMOLED, dark, and light themes.
- **v0.2.0** ✅. Renamed the application ID and established local release publishing.

## Install

### FLOSS vs FULL builds

Every AppManagerNG release ships **two build flavors**. Both are the same app; the only difference is whether the optional online features are compiled in.

| Flavor | For | Optional online features (VirusTotal, debloat-definition auto-updates, Settings → Privacy → "Use the Internet") |
|---|---|---|
| **`floss`** | F-Droid, IzzyOnDroid, reproducibility audits, anyone who wants a fully offline build | **Removed at compile time.** There is no setting to turn them on. Local networking (ADB-over-TCP, wireless pairing, the localhost privileged-server) still works. |
| **`full`** | GitHub Releases / Obtainium power users who want online scan reports and the debloat-definition auto-updater | **Available, opt-in.** Every online feature stays gated behind its existing user toggle and the master "Use the Internet" preference. Nothing reaches the network without you turning it on. |

`floss` is the default flavor in source; `full` is the optional variant. If you don't need VirusTotal or debloat-definition auto-updates, `floss` is the right choice. See [docs/distribution/build-flavors.md](docs/distribution/build-flavors.md) for the maintainer contract.

### Direct download
Grab the APK from [GitHub Releases](https://github.com/SysAdminDoc/AppManagerNG/releases/latest), then pick `full` or `floss` using the table above. Each release ships one universal APK per flavor, carrying native libraries for `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`, so the same file installs on every supported device. Per-ABI split APKs are not currently published.

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
> **Brazil / Indonesia / Singapore / Thailand users:** Google's [Android Developer Verification](https://developers.google.com/android/play-protect/developer-verification) program begins enforcement on certified devices in your region on **2026-09-30**. After that date, AppManagerNG (like every other on-device installer) is subject to the platform verifier gate. AppManagerNG preserves verifier failure reasons in install results and can offer an ADB-mode retry when ADB is already reachable, because ADB installs remain exempt.

## Verifying releases

APK signing certificate SHA-256 fingerprint:

```
21:5F:B4:70:63:2E:A6:CD:59:A4:BA:AB:35:0A:9E:0B:99:AD:11:0F:DD:FA:F5:A9:EA:64:61:E5:D0:C2:38:6C
```

Verify with [AppVerifier](https://github.com/soupslurpr/AppVerifier) or:

```bash
apksigner verify --print-certs AppManagerNG-<version>.apk | grep SHA-256
```

Before publishing, maintainers run one fail-closed local release gate,
[`scripts/release_gate.py`](scripts/release_gate.py). It checks, in order, that the
working tree is clean and matches the release tag, that every version-bearing
surface agrees, that pinned dependencies have not drifted, that the translation
report is internally consistent, that the host test suite passes, that no lint
issue sits outside the baseline, that two clean builds produce byte-identical
APKs, and that the built APK's package, version, SDK levels, and signing
certificate are the ones the sources declare. A receipt binding the commit and
tag to every artifact hash, the signing fingerprint, and the tool versions is
written only after all of it passes. Details, including the two-build
reproducibility check invoked by the gate, are in
[docs/distribution/reproducible-builds.md](docs/distribution/reproducible-builds.md).

The translation stage parses only language-qualified `values-*` resources, excludes
night/API/size qualifiers, rejects stale source keys, and applies the reviewed
per-locale floors in [`scripts/translation-coverage-baseline.json`](scripts/translation-coverage-baseline.json).
When a deliberate translation baseline changes, regenerate it with
`py -3.12 scripts/translation_quality.py --write-baseline` and review the resulting diff.

The gate also runs OWASP Dependency-Check as a blocking stage (no unsuppressed
CVSS 9.0+ findings), retaining the HTML/SARIF reports and their hash receipt
alongside the SBOM and APK sidecars.

> [!NOTE]
> In v0.6.7 that CVE stage could not run at all: `dependencyCheckAggregate`
> resolves configurations whose POMs were absent from
> `gradle/verification-metadata.xml`, so Gradle aborted it before the scanner
> started, and **v0.6.7 shipped without CVE evidence**. That was fixed in v0.6.8.
> the scanner runs to completion and writes its receipt.
>
> The first complete run reported 12 findings above the CVSS 9.0 threshold. All
> twelve were assessed in v0.6.8 and dispositioned in
> [`config/owasp-suppressions.xml`](config/owasp-suppressions.xml), each rule
> naming its CVE, the exact artifact family and version, and the reason it does
> not apply: `androidx.sqlite` matches the CPE of the upstream SQLite C library
> rather than the Java wrapper these artifacts ship (and no `androidx/sqlite`
> classes are present in the release DEX files); the `io.netty` artifacts come
> from the Android Gradle plugin's unified test platform and are never packaged
> into the APK; and the Kotlin findings are against build and toolchain jars that
> are likewise absent from the shipped application. No suppression is a blanket
> ignore. Dependency-Check reports a rule as unused once a scan stops seeing the
> matching finding, and the gate fails on that, so a disposition that stops being
> true becomes visible instead of silently persisting.

Untrusted app-list, rule, snapshot-manifest, and archive inputs also have a
bounded local Jazzer gate. Run `./gradlew :app:fuzzUntrustedImports` (or pass
`-PfuzzRuns=<count>`); each target uses a fixed seed and a 64 KiB input ceiling.
Crashes are written under `app/build/fuzz-crashes/`. Copy a minimized reproducer
into the matching `app/src/test/resources/fuzz-corpus/` directory so the normal
unit suite retains it as a regression fixture.

Run [`scripts/verify-release-consistency.sh`](scripts/verify-release-consistency.sh)
before release notes or APK publication to confirm the README badges, Fastlane
changelog, Gradle wrapper, SDK pins, and local `CLAUDE.md` match the build
metadata.

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

See [CONTRIBUTING.md](CONTRIBUTING.md). Translation intake is planned, but the
fork-owned Weblate/Crowdin project is not live yet.

## License

Released under **GPL-3.0-or-later**. Per-file SPDX headers and the `LICENSES/` directory follow
the [REUSE](https://reuse.software/) specification. Please preserve them.

See [COPYING](COPYING) for the full GPL-3.0 text. Vendored third-party components retain their
original licenses (Apache-2.0, BSD-2-Clause, BSD-3-Clause, CC-BY-SA-4.0, GPL-2.0, ISC, MIT, WTFPL)
as documented in `LICENSES/`.

## Credits and upstream

AppManagerNG would not exist without the years of work that went into the upstream
[App Manager](https://github.com/MuntashirAkon/AppManager) project by **Muntashir Al-Islam** and
the broader contributor community. AppManagerNG was bootstrapped from upstream commit
[`3d11bcb`](https://github.com/MuntashirAkon/AppManager/commit/3d11bcbc399d3a4f995b544e26d86bd80487fd32)
on 2026-04-30.

The original project remains the canonical implementation; AppManagerNG is a parallel effort
focused on UX polish and approachability. If you want the upstream experience or want to
contribute features broadly applicable to the package-manager domain, please direct your effort
[upstream](https://github.com/MuntashirAkon/AppManager) first.

A full list of credits and bundled libraries is available in the **About** section of the app.
