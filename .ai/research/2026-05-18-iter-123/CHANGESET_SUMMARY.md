<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 123 — F-Droid 2.0 ROM repository preseed docs

## Roadmap row

Docs **F-Droid 2.0 ROM JSON Pre-Seeding Format** is shipped.

## What changed

- Added `docs/distribution/rom-fdroid-preseed.md` for ROM builders.
- Added the F-Droid 2.0 JSON template at
  `docs/distribution/samples/fdroid-additional-repos.json`.
- Added the legacy XML transition template at
  `docs/distribution/samples/fdroid-additional-repos.xml`.
- Added a REUSE sidecar for the JSON sample.
- Linked the guide from the README install/distribution section.
- Updated `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md`.

## Source verification

- Official F-Droid 2026-03-28 ROM preseed note confirms the JSON file paths,
  the app-specific package-name paths, and the recommendation to ship JSON plus
  legacy XML during the F-Droid 2.0 migration window.
- F-Droid Forum legacy examples confirm the old XML `string-array` item ordering.

## Local validation

- PowerShell `ConvertFrom-Json` parsed
  `docs/distribution/samples/fdroid-additional-repos.json`.
- PowerShell `[xml]` parsed
  `docs/distribution/samples/fdroid-additional-repos.xml` and confirmed the
  seven legacy ordered values.
