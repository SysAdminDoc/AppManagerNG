# Iter 99 Changeset Summary

Date: 2026-05-18

Roadmap item: T6 **SMB / WebDAV Network Backup Destination**

## Shipped

- Added a Settings -> Backup/Restore **Network backup destination** action.
- The action opens Android's SAF folder picker for SMB, WebDAV, SFTP, or cloud folders exposed by user-installed DocumentsProvider apps.
- Selected trees are persisted with read/write URI permission, normalized through `StorageUtils.getFixedTreeUri`, stored as the active backup volume, and reflected through the existing backup-volume reload path.
- Made `StorageUtils.getFixedTreeUri` public for settings callers and added focused JVM coverage for tree/document URI normalization.
- Updated the backup destination matrix to describe the provider-backed network destination route as the supported user-facing path.

## Verification

- `git diff --check`
- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.utils.StorageUtilsTest --console=plain`
- `.\gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## Notes

- This intentionally uses Android's DocumentsProvider contract rather than adding native SMB/WebDAV protocol clients.
- Native protocol support remains tracked by the later WebDAV Snapshot Target / provider architecture rows.
- CIFS/SMB stream-corruption hardening remains tracked separately because it needs backup-writer behavior and provider-backed large-write testing, not only destination selection.
