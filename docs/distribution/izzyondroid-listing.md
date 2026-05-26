<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# IzzyOnDroid Listing Packet

Status: ready for maintainer submission.
Checked: 2026-05-26.

This packet is the source-of-truth for the IzzyOnDroid inclusion request. It is
not proof that the external issue has been filed.

## Current Release

- Repository: `https://github.com/SysAdminDoc/AppManagerNG`
- Tag: `v0.5.0`
- Package name: `io.github.sysadmindoc.AppManagerNG`
- Display name: `AppManagerNG`
- Preferred APK: `AppManagerNG-0.5.0-floss-release.apk`
- APK size: 29,331,150 bytes, under IzzyOnDroid's current 30 MB rule of thumb.
- SHA-256: `d4ad0cdbf7505dcff4b6f03d1018021f8e9395b98c0028e65751cb106a83ecde`
- Release: `https://github.com/SysAdminDoc/AppManagerNG/releases/tag/v0.5.0`

The `floss` artifact is the listing target. The `full` artifact also exists on
GitHub Releases for Obtainium users, but it enables optional online report
surfaces behind user opt-in gates and should not be the IzzyOnDroid artifact.

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
https://github.com/SysAdminDoc/AppManagerNG/releases/tag/v0.5.0

Package:
io.github.sysadmindoc.AppManagerNG

Preferred APK:
AppManagerNG-0.5.0-floss-release.apk

APK SHA-256:
d4ad0cdbf7505dcff4b6f03d1018021f8e9395b98c0028e65751cb106a83ecde

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
```

## Maintainer Action Required

The remaining step requires a maintainer account on IzzyOnDroid's external
tracker: file the inclusion request, ask IzzyOnDroid to match only the
`AppManagerNG-*-floss-release.apk` asset, and link this packet plus the package
visibility dossier.
