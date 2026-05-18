# Continue From Here — Iter 98

Status: T6 **Export/Import App List** is implemented, verified, and ready to commit.

## Current state

- Main-list overflow has `Export visible app list` and `Import app list`.
- Selection mode still has the existing selected-app `Export app list` action.
- Imported JSON package names become selected installed apps in the currently loaded main list, so the multi-select batch toolbar is the follow-up operation surface.
- `ListImporterTest` covers supported import shapes and validation.

## Next roadmap item

The next uncompleted unblocked T6 row is **SMB / WebDAV Network Backup Destination**.

Before implementing it, inspect the existing backup destination abstractions and SAF path storage under:

- `app/src/main/java/io/github/muntashirakon/AppManager/backup/`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/`
- `docs/distribution/backup-destinations-matrix.md`

Also account for the later **CIFS / SMB Backup Streaming Hardening** row in the Engineering Debt section; any network-destination implementation must avoid short-write/corruption behavior on SAF-mediated SMB/CIFS providers.
