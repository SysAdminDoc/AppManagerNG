# Continue From Here - Iter 107

## Completed

- Closed T10 Install-Date Filter + Filter-Applied Indicator.
- Finder exposes an `install_date` filter with date-picker backed before/after predicates.
- Main list persists and applies an install-date range from the quick filter strip and list-options sheet.
- Main list and Finder show clearable "N filters active - clear" chips whenever non-default filters are active.

## Next roadmap item

Continue scanning `ROADMAP.md` for the next unchecked `Next` / `Now` row after the shipped install-date row. The nearest visible follow-up in the same roadmap additions is T13 - File Manager Recursive In-Folder Search.

## Suggested starting points

- File manager activity and adapter:
  - `app/src/main/java/io/github/muntashirakon/AppManager/fm/FmActivity.java`
  - `app/src/main/java/io/github/muntashirakon/AppManager/fm/FmAdapter.java`
- Existing search/filter UI patterns:
  - `app/src/main/java/io/github/muntashirakon/AppManager/main/MainActivity.java`
  - `app/src/main/java/io/github/muntashirakon/AppManager/filters/FinderActivity.java`

