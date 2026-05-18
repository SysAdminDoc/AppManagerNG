# Continue From Here - Iter 101

Status: T6 **Backup Path Exclusion Patterns** is implemented and documented.

## Current state

- Backup data tar creation now uses default throwaway-folder globs plus global, per-run, and profile-specific custom glob lists.
- Settings -> Backup/Restore exposes a global custom exclusion editor.
- Manual backup runs can add one-off extra exclusions before starting the batch.
- Profile backup configs persist `backup_data.exclusion_globs` and carry them through `BatchBackupOptions` / `BackupOpOptions`.
- Cache-like defaults are active only when the existing Backup cache flag is disabled.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.backup.BackupPathExclusionPatternsTest --tests io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptionsTest --console=plain`

## Next roadmap item

Continue the iter-19 Next rows with **Tasker Parameterized Intent API**, unless a higher-priority row is deliberately selected from `ROADMAP.md`.

Suggested first inspection points:

- `docs/intent-api.md`
- `app/src/main/java/io/github/muntashirakon/AppManager/automation/AutomationReceiver.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/self/SelfUriManager.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/uri/`
- existing external-operation confirmation paths in the batch/profile surfaces
