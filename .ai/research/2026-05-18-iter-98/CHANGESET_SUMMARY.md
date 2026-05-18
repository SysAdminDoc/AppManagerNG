# Iter 98 Changeset Summary

Date: 2026-05-18

Roadmap item: T6 **Export/Import App List**

## Shipped

- Added a Gson-backed `ListImporter` for app-list JSON package extraction.
- Preserved the existing selection-mode export action and added a main-list overflow action that exports the current visible/filtered app list.
- Added a main-list JSON import action that de-dupes valid package names, selects matching installed apps, and opens the existing multi-select batch-operation toolbar.
- Added user-facing strings for visible-list export, JSON import status, and import match counts.
- Added focused JVM coverage for AppManagerNG export arrays, wrapped package arrays, duplicate names, and invalid package-name filtering.

## Verification

- `git diff --check`
- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.apk.list.ListImporterTest --console=plain`
- `.\gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## Notes

- Import deliberately drives the existing selection/batch-op pathway rather than creating a separate batch executor.
- JSON import accepts the current AppManagerNG export shape and simple `packages` / `apps` wrappers to support saved lists from scripts or future export envelopes.
