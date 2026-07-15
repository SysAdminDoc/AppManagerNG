<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# IzzyOnDroid Listing Packet

Status: ready for maintainer submission.
Checked: 2026-07-15.

This packet is the source-of-truth for the IzzyOnDroid inclusion request. It is
not proof that the external issue has been filed.

## Current Release

- Repository: `https://github.com/SysAdminDoc/AppManagerNG`
- Tag: `v0.6.5`
- Commit: `fc03e0332c834c161fcd419525e8530c23f22706`
- Package name: `io.github.sysadmindoc.AppManagerNG`
- Display name: `AppManagerNG`
- Version name: `0.6.5`
- Version code: `13`
- Preferred APK: `AppManagerNG-0.6.5-floss-release.apk`
- APK size: 18,948,513 bytes, under IzzyOnDroid's current 30 MB rule of thumb.
- SHA-256: `986da6fc19e325c5fe35d03523021fa30dd2b483466c5f98a3a8c7c64d6a5fa0`
- Signing certificate SHA-256:
  `21:5F:B4:70:63:2E:A6:CD:59:A4:BA:AB:35:0A:9E:0B:99:AD:11:0F:DD:FA:F5:A9:EA:64:61:E5:D0:C2:38:6C`
- Release: `https://github.com/SysAdminDoc/AppManagerNG/releases/tag/v0.6.5`

The `floss` artifact is the listing target. The `full` artifact also exists on
GitHub Releases for Obtainium users, but it enables optional online report
surfaces behind user opt-in gates and should not be the IzzyOnDroid artifact.

## Per-ABI Split Sizes

The build produces per-ABI splits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`)
plus a universal APK. Per-ABI splits contain only the native libraries for one
architecture and are strictly smaller than the universal APK (18,948,513 bytes measured at
v0.6.5). The local release build enforces a hard size gate
(`APK_SIZE_LIMIT_BYTES`, default 30 MiB) per APK — any split exceeding it fails
the build. IzzyOnDroid should be configured to match the universal
`AppManagerNG-*-floss-release.apk` artifact (sub-30 MB) or the `arm64-v8a` split
for the smallest download.

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
https://github.com/SysAdminDoc/AppManagerNG/releases/tag/v0.6.5

Package:
io.github.sysadmindoc.AppManagerNG

Preferred APK:
AppManagerNG-0.6.5-floss-release.apk

APK SHA-256:
986da6fc19e325c5fe35d03523021fa30dd2b483466c5f98a3a8c7c64d6a5fa0

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
