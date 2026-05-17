<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET_SUMMARY — 2026-05-17 pass 8

## Modified

- `ApplicationItem.java` — adds enabled/disabled/uninstalled user-id buckets for
  collapsed multi-user rows.
- `MainViewModel.java` — populates the per-user state buckets while folding DB
  app rows by package name.
- `MainRecyclerAdapter.java` — displays compact per-user state on main-list rows
  and appends state to the multi-user picker labels.
- `FinderViewModel.java` — loads all selected users through `Users.getUsersIds()`
  instead of only the current user.
- `FinderAdapter.java` — shows user id and enabled/disabled/not-installed state
  for each Finder result.
- `strings.xml` — adds user-state labels.
- `ROADMAP.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md` — marks T5 cross-user state
  detection and T7 Finder multi-user scope shipped.

## Verification

- XML parse check passed for `app/src/main/res/values/strings.xml`.
- `git diff --check` passed before documentation updates.
- Gradle tests could not run locally: no `JAVA_HOME` / no `java` command available.
