<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 15

## Roadmap item closed

- T7 `Finder: Relevance-Based Search Scoring`

## Implementation

- Added `FinderRelevanceScorer`, a Finder-only post-filter sorter for literal
  package-name, component-name, and tracker-name searches.
- `FilterItem` now exposes a package-private immutable-ish snapshot of active
  `FilterOption` instances so the scorer can inspect search predicates without
  mutating the filter expression.
- `FinderViewModel` keeps the existing `FilterItem.getFilteredList()` inclusion
  behavior, then sorts the already-filtered rows when relevance terms are present.
- Scoring uses Levenshtein distance against:
  - full package names;
  - simple package names;
  - package/component tokens;
  - sliding windows of the candidate;
  - matched component and tracker class names recorded in `TestResult`.
- Regex and negative predicates are deliberately excluded because they are not
  literal relevance queries.
- Rows with no relevance score keep their original PackageManager/backup scan
  order, avoiding churn for results that only matched unrelated filter branches.
- Added `FinderRelevanceScorerTest` coverage for edit distance, exact-token
  preference, longer-token demotion, and case-insensitive scoring.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/filters/FilterItem.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/filters/FinderRelevanceScorer.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/filters/FinderViewModel.java`
- `app/src/test/java/io/github/muntashirakon/AppManager/filters/FinderRelevanceScorerTest.java`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- `git diff --check` passed after code and documentation updates.
- Targeted Gradle test attempt remained blocked because `JAVA_HOME` is unset and
  no `java` command is available in PATH.

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
