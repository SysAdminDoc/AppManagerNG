# Iter 101 Changeset Summary

Date: 2026-05-18

Roadmap item: T6 **Backup Path Exclusion Patterns**

## Shipped

- Added `BackupPathExclusionPatterns` for newline-delimited glob parsing, slash normalization, default throwaway-folder glob sets, and tar-relative regex generation.
- Wired data tar creation through default exclusions plus global Settings, one-off backup-dialog, and profile-specific custom glob lists.
- Kept cache semantics tied to the existing `BACKUP_CACHE` flag: nested cache defaults apply only when cache backup is disabled.
- Added Settings -> Backup/Restore global custom exclusions.
- Added per-run exclusions in the manual backup dialog.
- Added profile backup exclusion editing and persisted profile `backup_data.exclusion_globs`.
- Extended batch backup and backup operation option parcel/JSON payloads so queued/profile operations carry exclusion globs.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.BackupPathExclusionPatternsTest --tests io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptionsTest --console=plain`

## Notes

- ADB backup streaming remains unchanged because Android's ADB backup API does not expose per-file include/exclude filters.
- The existing root cache regex exclusions in `BackupUtils.getExcludeDirs()` remain in place for compatibility; the new glob layer adds nested cache coverage when cache backup is off.
