# Iter-94 Changeset Summary

Date: 2026-05-18

Roadmap item closed: T6 `Launcher Shortcuts for Backup Schedules`.

## Code

- Added `AutoBackupShortcutActivity`, an authenticated transparent shortcut target that queues `AutoBackupScheduler.enqueueManualRun()`.
- Added `AutoBackupShortcutActivity.requestPinShortcut()` for the Settings -> Backup pinned shortcut action.
- Extended `ShortcutDispatchActivity` and `res/xml/shortcuts.xml` with a static "Run scheduled backup" launcher shortcut.
- Added Settings -> Backup copy and preference wiring for "Pin backup shortcut".

## Verification

- `git diff --check`
- `.\gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`
