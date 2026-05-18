# Continue From Here - Iter 105

## Completed

- Closed the T8 Per-App Rollback / "Revert All Changes" roadmap row.
- App Details now queues inverse replay from successful operation history entries for the current package/user.
- Rollback intentionally excludes app data restore, reinstalling removed apps, backup deletion, and operations whose saved history lacks previous-state data.

## Next roadmap item

The next unchecked row in the iter-19 additions is T8 - Settings Import/Export Portability.

Scope from `ROADMAP.md`: export global app config, preferences, freeze list, debloat ruleset, and schedules into a single encrypted JSON bundle, then import with a diff/merge flow after factory reset or ROM flash.

## Suggested starting points

- Inspect existing snapshot/profile export surfaces before designing a new format:
  - `app/src/main/java/io/github/muntashirakon/AppManager/snapshot/`
  - `app/src/main/java/io/github/muntashirakon/AppManager/settings/`
  - `app/src/main/java/io/github/muntashirakon/AppManager/profiles/`
- Decide whether the new portability bundle can extend the existing snapshot bundle or should remain a settings-only format.
- Preserve the offline/local-first posture. Do not introduce cloud or network sync.

