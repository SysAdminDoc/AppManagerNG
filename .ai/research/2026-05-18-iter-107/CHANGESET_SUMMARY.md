# Iter 107 Changeset Summary

## Closed row

- T10 - Install-Date Filter + Filter-Applied Indicator.

## Code changes

- Added Finder filter option `install_date` with `before` and `after` date predicates over `IFilterableAppInfo.getFirstInstallTime()`.
- Registered `install_date` in the Finder filter factory and filter picker array.
- Added persisted main-list install-date range state in `Prefs.MainPage` / `AppPref`.
- Applied the install-date range in `MainViewModel` alongside existing flag, profile, user, and search filtering.
- Added install-date controls to the main quick filter strip and list-options bottom sheet.
- Updated main list and Finder to show a persistent clearable active-filter count chip when filters are active.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.filters.options.InstallDateOptionTest --console=plain`

