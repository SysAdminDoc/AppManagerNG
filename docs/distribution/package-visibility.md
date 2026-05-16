<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# AppManagerNG Package-Visibility Declaration Dossier

**Status:** Policy document
**Date:** 2026-05-16
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — Iter-23 / T3 / Now row "Package-Visibility Declaration Dossier"
**Audience:** F-Droid, IzzyOnDroid, Accrescent, Obtainium, and any future distribution channel reviewing AppManagerNG's permission posture; AppManagerNG maintainers gating future manifest changes.

---

## TL;DR

- AppManagerNG declares `android.permission.QUERY_ALL_PACKAGES` in [`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) (line 122). The declaration carries `tools:ignore="QueryAllPackagesPermission"` because the permission is the only API-30+ surface that lets a third-party app manager see every installed user and system package on the device.
- The permission is **load-bearing for the core product**, not incidental. AppManagerNG is an on-device package manager / debloater / backup tool. Hiding packages from the app would silently produce wrong answers in every list, search, batch operation, profile, and backup manifest.
- AppManagerNG is **not** distributed via Google Play and does not request `QUERY_ALL_PACKAGES` for advertising, tracking, or telemetry. We have no network back-end. We do not enumerate the package list off-device.
- This document is the canonical justification we hand to F-Droid / IzzyOnDroid / Accrescent / Obtainium reviewers, and the gate AppManagerNG maintainers walk through before changing the manifest's `<uses-permission>` or `<queries>` blocks.

---

## What the permission does

`android.permission.QUERY_ALL_PACKAGES` was introduced in Android 11 (API 30) as part of the platform's [package-visibility restrictions](https://developer.android.com/training/package-visibility). On API 30+ devices, an app can only see other packages it has either:

- Declared an explicit `<queries>` entry for (by package name, intent filter, or provider authority),
- Been granted system-level visibility for (e.g., a default-handler role),
- Or — in the case of a true package-manager-class app — declared `QUERY_ALL_PACKAGES`.

Without `QUERY_ALL_PACKAGES`, the platform silently filters out packages from `PackageManager.getInstalledPackages()`, `getApplicationInfo()`, and every related API. An app manager that depends on `<queries>` would have to enumerate every conceivable package up front, which is both infeasible (there is no manifest-time way to know which packages the user will install) and unsound (the user's "the list is wrong" experience would be indistinguishable from a real bug).

---

## Why AppManagerNG requires it

The following surfaces would produce **wrong, silently-filtered output** if AppManagerNG ran without `QUERY_ALL_PACKAGES`:

| Surface | Path | Effect without `QUERY_ALL_PACKAGES` |
|---------|------|--------------------------------------|
| Main list | [`MainActivity` / `MainViewModel`](../../app/src/main/java/io/github/muntashirakon/AppManager/main) — "All apps" view | Hides every app that does not match an explicit `<queries>` filter; user perceives apps as "uninstalled" |
| App Details | [`AppDetailsActivity`](../../app/src/main/java/io/github/muntashirakon/AppManager/details/AppDetailsActivity.java) | Cannot resolve `PackageInfo` for arbitrary packages typed/selected via launcher shortcuts or external intents |
| Backup / Restore | [`BackupOp`](../../app/src/main/java/io/github/muntashirakon/AppManager/backup/BackupOp.java) | Manifests and dependency lookups would miss split APKs and library APKs from invisible packages |
| Debloater | [`debloat` package](../../app/src/main/java/io/github/muntashirakon/AppManager/debloat) | "Debloat list" categorization needs the full installed set to flag which OEM bloat is actually present |
| Profiles | [`profiles` package](../../app/src/main/java/io/github/muntashirakon/AppManager/profiles) | Profiles persist per-package state; saving a profile with invisible packages drops members silently |
| Batch operations | [`batchops` package](../../app/src/main/java/io/github/muntashirakon/AppManager/batchops) | Freeze / uninstall / clear-data on a stored package list would skip invisible members without warning |
| Tracker / library scan | [`scanner` package](../../app/src/main/java/io/github/muntashirakon/AppManager/scanner) | Trackers/libraries listing aggregates across all installed APKs |
| Finder / search | [`filters` package](../../app/src/main/java/io/github/muntashirakon/AppManager/filters) — `FinderActivity` and friends | Cross-package symbol search needs the full set of installed APKs |
| Logcat per-app filter | [`logcat` package](../../app/src/main/java/io/github/muntashirakon/AppManager/logcat) | Per-app log filter UI populates from the installed-package list |

The product category — an on-device package manager — is precisely the category Google Play documents as a valid use case for the permission ([Google Play `QUERY_ALL_PACKAGES` policy](https://support.google.com/googleplay/android-developer/answer/10158779)):

> *"App stores, antivirus apps, file managers and browsers."* Device-management and package-management tools that need full visibility to function are listed alongside.

AppManagerNG is, functionally, both an app manager and an antivirus-adjacent tracker scanner. The permission is required for the product to be honest about what is and is not installed.

## What AppManagerNG does *not* do with the permission

AppManagerNG's manifest declaration carries the same posture as every other privacy-respecting on-device manager. We commit to:

- **No telemetry, no analytics, no crash reporting upload.** [`CONTRIBUTING.md`](../../CONTRIBUTING.md) bans adding network services, telemetry, crash reporting, or cloud dependencies without an explicit opt-in design and maintainer approval. There is no code path in AppManagerNG that uploads the installed-package list.
- **No on-disk persistence of the full installed set outside the user's own profiles / backups.** Profile and backup files only contain the packages the user explicitly selected.
- **No ad / attribution SDKs.** AppManagerNG ships no ad SDK, no SDK dependency that calls home, and no remote-config layer. The dependency ledger is the union of `versions.gradle` and the AAR submodules under `libs/`.
- **No background scanning.** Package enumeration runs only when a user surface needs it (main list, scan, backup). There is no `JobScheduler` / `WorkManager` job that enumerates and uploads packages.

## Distribution-channel posture

| Channel | Status | Notes |
|---------|--------|-------|
| **F-Droid (official)** | Eligible | F-Droid permits `QUERY_ALL_PACKAGES` for app-manager-class apps. Reviewers should treat this document as the rationale. |
| **IzzyOnDroid** | Eligible | IzzyOnDroid mirrors F-Droid permission policy and accepts the same justification. |
| **Accrescent** | Eligible — read-only metadata only | Accrescent enforces strict metadata; this declaration must accompany the signed APK and be linked from the manifest's website metadata. |
| **Obtainium** | Eligible — direct GitHub Releases | Obtainium does not gate permissions but inherits whatever the signed release advertises. The pre-published Obtainium config at [`docs/distribution/obtainium-config.json`](obtainium-config.json) points at the same artifact set. |
| **Google Play** | **Not pursued.** | AppManagerNG is not currently distributed via Play. If a future maintainer chose to submit, this document plus Google's `QUERY_ALL_PACKAGES` policy answer ([source](https://support.google.com/googleplay/android-developer/answer/10158779)) would be the required declaration. The privileged permissions AppManagerNG also declares (`MANAGE_USERS`, `WRITE_SECURE_SETTINGS`, `INSTALL_PACKAGES`, etc.) would themselves block any Play listing; the permission is mentioned here only for completeness. |

## Maintainer gate for future manifest changes

Before changing `<uses-permission>` or `<queries>` in [`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml), confirm:

1. **The change is required by a roadmap feature.** Manifest additions are not incidental cleanup.
2. **The new permission is documented in this dossier.** If it is sensitive under Google Play policy or Accrescent/F-Droid review, add a row to the "What AppManagerNG does *not* do with the permission" section and any required code references.
3. **`QUERY_ALL_PACKAGES` stays the only `tools:ignore="QueryAllPackagesPermission"` declaration.** If a maintainer ever drops `QUERY_ALL_PACKAGES`, the change must also remove the now-stale `tools:ignore`.
4. **Removing the permission is itself a breaking change.** Profiles, backups, scanner output, and Finder results would silently shrink on the next launch. Plan a deprecation message in onboarding before any such change ships.

## References

- Google Play, ["Use of the broad package (App) visibility (`QUERY_ALL_PACKAGES`) permission"](https://support.google.com/googleplay/android-developer/answer/10158779).
- Android Developers, ["Package visibility filtering on Android"](https://developer.android.com/training/package-visibility).
- AppManagerNG roadmap row "Package-Visibility Declaration Dossier" — Iter-23 / T3 / Now. Reference: [`ROADMAP.md`](../../ROADMAP.md) source `[S308]`.
- [`CONTRIBUTING.md`](../../CONTRIBUTING.md) — no-telemetry / no-cloud / no-network-without-explicit-opt-in contract.
- [`docs/sideload-verification.md`](../sideload-verification.md) — companion document covering the Android Developer Verification install gate that interacts with AppManagerNG's installer surface.
