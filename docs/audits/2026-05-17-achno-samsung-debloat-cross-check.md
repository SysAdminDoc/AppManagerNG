<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Audit: Achno Samsung Debloat List Cross-Check

**Date:** 2026-05-17
**Source:** https://github.com/Achno/debloat-samsung-ADB-shizuku
**Audited against:** `scripts/android-debloat-list@e5f4e64` in AppManagerNG `main`
**Roadmap row:** Iter-21 `Achno Samsung Debloat List Cross-Check`

## Premise

`Achno/debloat-samsung-ADB-shizuku` is a small GPL-3.0 Samsung-specific README
list of packages to remove with Shizuku / ADB shell. The roadmap asked for a
one-time validation pass against the local `scripts/android-debloat-list/oem.json`
Samsung entries to catch easy gaps after the S25 Ultra / UAD-NG import work.

## Sweep Methodology

- Pulled the raw README from:
  `https://raw.githubusercontent.com/Achno/debloat-samsung-ADB-shizuku/main/README.md`
- Extracted package-like tokens with this pattern:
  `(?:^|\s)([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+){2,})(?:\s|$|\||-)`
- Compared the resulting unique token set against:
  - `scripts/android-debloat-list/oem.json`
  - `scripts/android-debloat-list/aosp.json`
  - `scripts/android-debloat-list/google.json`
  - `scripts/android-debloat-list/carrier.json`
  - `scripts/android-debloat-list/misc.json`
- Manually reviewed exact misses against nearby Samsung entries in `oem.json`.

## Results

- Achno unique package-like tokens: **82**
- Samsung-like / Samsung-adjacent tokens: **60**
- Exact tokens missing from all local debloat lists: **6**
- Actionable dataset additions: **0**

## Exact Miss Review

| Achno token | Local coverage / verdict |
|-------------|--------------------------|
| `samsung.android.beaconmanager` | Local `oem.json` already has `com.samsung.android.beaconmanager`; Achno appears to omit the `com.` prefix. No new row. |
| `com.samsung.android.fmmm` | Local `oem.json` already has `com.samsung.android.fmm` for Find My Mobile. Public search found no independent support for the triple-m form. No new row. |
| `com.samsung.android.voc.LauncherActivity` | This is an activity/class under the already-covered `com.samsung.android.voc` package, not a package id. No new row. |
| `com.sec.android.mimage.avatar.stickers` | Local `oem.json` already has `com.sec.android.mimage.avatarstickers`; Achno appears to add an extra dot. No new row. |
| `com.sec.samsung.android.widgetapp.samsungapps` | Local `oem.json` already has `com.sec.android.widgetapp.samsungapps` and `com.sec.android.widget.samsungapps`. Public search found no independent support for the `com.sec.samsung...` variant beyond Achno. No new row. |
| `com.samsung.providers.calendar` | Achno labels this "Calendar Storage", but local data already covers `com.samsung.android.calendar` and AOSP `com.android.providers.calendar`-class calendar storage. Public search found no independent support for this exact Samsung provider package beyond Achno. No new row. |

## Verdict

**Closed as audit clean / no data change.**

The Achno list did not add a sufficiently corroborated Samsung package id that
is absent from the combined local debloat datasets. Adding the six exact misses
would lower dataset quality because five are apparent typos or non-package
identifiers, and one is an uncorroborated calendar-provider variant.

The local dataset already covers the relevant Samsung package families:

- `com.samsung.android.beaconmanager`
- `com.samsung.android.fmm`
- `com.samsung.android.voc`
- `com.sec.android.mimage.avatarstickers`
- `com.sec.android.widgetapp.samsungapps`
- `com.sec.android.widget.samsungapps`
- `com.samsung.android.calendar`

Future Samsung corpus work should use a device `pm list packages` export or a
second independent package dump before adding typo-looking IDs from prose lists.
