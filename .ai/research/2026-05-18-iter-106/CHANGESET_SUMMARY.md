# Iter 106 Changeset Summary

## Roadmap item

T8 - Settings Import/Export Portability

## What changed

- Extended `SnapshotBundle` to schema v2 and added `rules/` entries for AppManagerNG rule TSV files from `files/conf`.
- Added manifest and toast counts for exported/imported rule files.
- Changed snapshot preference import from whole-file replacement to key-level merge so imported keys update matching settings without removing unrelated local settings.
- Changed rule TSV import to append distinct incoming rows while preserving existing local rows.
- Updated Settings -> Privacy copy to describe the exact merge/overwrite behavior.
- Added `SnapshotBundleTest` coverage for SharedPreferences XML merge and rule-row dedupe/order.

## Architecture note

The roadmap's older "encrypted JSON" wording is intentionally closed through the existing snapshot ZIP architecture. The snapshot bundle excludes keystore secrets, and backup crypto tied to the current install would not survive factory reset or ROM-flash migration. Keeping the migration bundle local, explicit, and keystore-independent preserves the portability goal.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.snapshot.SnapshotBundleTest --console=plain`

