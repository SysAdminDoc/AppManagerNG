<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 108 — File Manager Recursive In-Folder Search

## Roadmap item

Closed T13 **File Manager Recursive In-Folder Search** for AM #1964.

## Changes

- Added a File Manager toolbar `SearchView` through `activity_fm_actions`.
- Added a clearable active-search chip below the File Manager path bar.
- Wired `FmFragment` to debounce query changes by 250 ms and clear stale search state from the chip or empty-state recovery action.
- Added `FmSearchUtils.searchRecursive()` for recursive file/folder name matching under the current location.
- Allowed relative-path matching when the query contains `/`, so users can target nested paths directly.
- Preserved the existing "display dot files" option by skipping hidden dot-paths during recursive search unless that option is enabled.
- Added containing-folder context to recursive result subtitles via `FmItem.getSearchLocation()`.
- Covered recursive matching, nested path matching, and hidden-dot handling in `FmSearchUtilsTest`.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.fm.FmSearchUtilsTest --console=plain`

