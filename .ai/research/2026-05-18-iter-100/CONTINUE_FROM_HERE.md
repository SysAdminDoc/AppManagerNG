# Continue From Here - Iter 100

Status: T6 **Material Files User-Trust for Self-Signed WebDAV Certs** is closed as covered by the provider-backed destination architecture.

## Current state

- There is no native WebDAV/SMB backup client in app code.
- Provider-backed network backup destinations route through Android SAF and the selected DocumentsProvider app.
- WebDAV self-signed certificate trust is delegated to the provider today.
- If NG later adds native WebDAV, the native client must integrate Android user-installed CA material through `KeyChain` or the platform trust store rather than a private trust store.

## Next roadmap item

Continue the T6 backup-polish batch with **Backup Path Exclusion Patterns**.

Suggested first inspection points:

- `app/src/main/java/io/github/muntashirakon/AppManager/backup/BackupManager.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/backup/BackupFlags.java`
- backup option preferences in `Prefs.BackupRestore`
- profile backup option plumbing, if backup flags can be saved per profile
- tests under `app/src/test/java/io/github/muntashirakon/AppManager/backup/`

Implementation direction:

- Start with engine-level exclusion matching and a small default throwaway-dir list.
- Keep user-editable UI minimal unless the existing settings architecture has an obvious low-risk list preference pattern.
- Add focused tests around glob matching, default exclusions, and inclusion of non-matching paths.
