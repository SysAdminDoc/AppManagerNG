# Continue From Here - Iter 99

Status: T6 **SMB / WebDAV Network Backup Destination** provider-backed storage selection is implemented and verified.

## Current state

- Settings -> Backup/Restore now has a Network backup destination row.
- The row launches Android's SAF folder picker after a short provider-backed-network explanation.
- The selected folder is persisted with read/write access and stored as `Prefs.Storage.PREF_BACKUP_VOLUME_STR` through the same backup-volume setting used by the existing engine.
- `StorageUtilsTest` covers normalization of `tree/...` and `tree/.../document/...` URIs plus malformed single-document rejection.
- The destination matrix now points WebDAV/SMB/SFTP users at Settings -> Backup/Restore -> Network backup destination.

## Next roadmap item

The next uncompleted row in roadmap order is **Material Files User-Trust for Self-Signed WebDAV Certs**.

Recommended handling before code:

- Verify whether the provider-backed route fully satisfies this row for the current architecture.
- If it does, close or park the row as covered by provider-backed DocumentsProvider trust, with a note that native `KeyChain.getCertificateChain()` integration only becomes actionable if NG later adds native WebDAV clients.
- If native WebDAV client work is started later, keep certificate trust isolated behind a network-destination provider abstraction instead of wiring WebDAV trust directly into the current backup engine.

Other nearby T6 candidates after that:

- **Backup Path Exclusion Patterns**
- **Default-App Role Re-Binding After Restore**
- **Backup Scheduler Newest-Age Gate**
- **CIFS / SMB Backup Streaming Hardening**
