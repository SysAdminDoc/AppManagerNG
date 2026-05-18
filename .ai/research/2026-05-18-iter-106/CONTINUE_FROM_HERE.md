# Continue From Here - Iter 106

## Completed

- Closed T8 Settings Import/Export Portability.
- Snapshot exports include preferences, profiles, tags, operation history, and blocking/freeze rule TSVs.
- Snapshot imports merge preferences and rules while preserving the existing append/overwrite semantics for operation history, profiles, and tags.

## Next roadmap item

The next unchecked row in the iter-19 additions is T10 - Install-Date Filter + Filter-Applied Indicator.

Scope from `ROADMAP.md`: add recently-installed sort/filter support and a visible active-filter indicator so users can tell when the main list or Finder is hiding apps.

## Suggested starting points

- Main-list sort/filter definitions:
  - `app/src/main/java/io/github/muntashirakon/AppManager/main/MainListOptions.java`
  - `app/src/main/java/io/github/muntashirakon/AppManager/main/MainViewModel.java`
- Existing filter UI and parser:
  - `app/src/main/java/io/github/muntashirakon/AppManager/filters/`
- Finder filter plumbing:
  - `app/src/main/java/io/github/muntashirakon/AppManager/finder/`

