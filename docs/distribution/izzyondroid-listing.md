<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# IzzyOnDroid Listing Packet

Status: ready for maintainer submission.
Checked: 2026-07-20.

This packet is the source-of-truth for the IzzyOnDroid inclusion request. It is
not proof that the external issue has been filed.

## Current Release

- Repository: `https://github.com/SysAdminDoc/AppManagerNG`
- Tag: `v0.6.22`
- Commit: `a3406d003c6e39889fca9f5421c59012ad305890`
- Package name: `io.github.sysadmindoc.AppManagerNG`
- Display name: `AppManagerNG`
- Version name: `0.6.22`
- Version code: `30`
- Preferred APK: `AppManagerNG-reproducible-floss-release.apk`
- APK size: 20,428,492 bytes, under IzzyOnDroid's current 30 MB rule of thumb.
- SHA-256: `f328f157f03ab4d9dd484c4510b65d179ca54a2c0dbc83ff2ad4e9a87df60ddf`
- Signing certificate SHA-256:
  `21:5F:B4:70:63:2E:A6:CD:59:A4:BA:AB:35:0A:9E:0B:99:AD:11:0F:DD:FA:F5:A9:EA:64:61:E5:D0:C2:38:6C`
- Release: `https://github.com/SysAdminDoc/AppManagerNG/releases/tag/v0.6.22`

The `floss` artifact is the listing target. The `full` artifact also exists on
GitHub Releases for Obtainium users, but it enables optional online report
surfaces behind user opt-in gates and should not be the IzzyOnDroid artifact.

## APK Size

Releases publish a single universal APK per flavor, carrying native libraries for
`armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`. It measures 20,028,420 bytes at
v0.6.22, well under IzzyOnDroid's 30 MB rule of thumb. The local release build
enforces a hard size gate (`APK_SIZE_LIMIT_BYTES`, default 30 MiB) per APK.

Per-ABI splits are configured in `app/build.gradle` but are **not enabled**, so no
split APKs are produced or published. IzzyOnDroid should be configured to match the
universal `AppManagerNG-*-floss-release.apk` artifact.

## Policy Checks

- Source is public and GPL-3.0-or-later.
- APKs are attached to a GitHub tagged release.
- Fastlane metadata is present at `fastlane/metadata/android/en-US/` with
  short description, full description, icon, screenshots, and version-code
  changelog.
- The metadata is NG-specific: title, package name, fork credit, and
  descriptions distinguish it from upstream App Manager.
- The default `floss` flavor disables optional third-party online report
  features at compile time. See `docs/distribution/build-flavors.md`.
- Package visibility is documented for reviewers. See
  `docs/distribution/package-visibility.md`.
- Reproducible-release verification is documented. See
  `docs/distribution/reproducible-builds.md`.

IzzyOnDroid's current policy says metadata should be in Fastlane structures,
APKs should be developer-signed and attached to tagged releases, and sensitive
permissions need clear rationale. Relevant current docs:

- `https://izzyondroid.org/docs/general/AppInclusionPolicy/`
- `https://izzyondroid.org/docs/general/Fastlane/`
- `https://izzyondroid.org/about/security/ApkScans/`

## Suggested Inclusion Request

```text
Please consider AppManagerNG for IzzyOnDroid inclusion.

Repository:
https://github.com/SysAdminDoc/AppManagerNG

Latest release:
https://github.com/SysAdminDoc/AppManagerNG/releases/tag/v0.6.22

Package:
io.github.sysadmindoc.AppManagerNG

Preferred APK:
AppManagerNG-reproducible-floss-release.apk

APK SHA-256:
f328f157f03ab4d9dd484c4510b65d179ca54a2c0dbc83ff2ad4e9a87df60ddf

Notes:
- AppManagerNG is a maintained fork of MuntashirAkon/AppManager with a unique
  package name and NG-specific metadata/screenshots.
- The preferred Izzy artifact is the floss build. Optional third-party online
  report features are compiled out in floss.
- The project has no ads, analytics, telemetry upload, or bundled tracking SDKs.
- Broad package visibility is required because this is an on-device package
  manager, debloater, backup/restore tool, and tracker scanner. The reviewer
  rationale is documented at docs/distribution/package-visibility.md.
- Reproducible-release verification is documented at
  docs/distribution/reproducible-builds.md.
- Published tag, commit, version, artifact, hash, size, and signing identity are
  pinned in docs/distribution/release-receipt.json.
```

## Maintainer Action Required

The remaining step requires a maintainer account on IzzyOnDroid's external
tracker: file the inclusion request, ask IzzyOnDroid to match only the
`AppManagerNG-*-floss-release.apk` asset, and link this packet plus the package
visibility dossier.
