<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — after iter 122

## Current state

- Branch: `main`
- Latest completed row: T9 **InstallerX-Style Biometric Install Gate**
- Verification completed: `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## What just shipped

Privacy settings now include an optional sensitive-action gate. When enabled,
`ActionAuthGate` requires Android screen lock before final install commits,
direct uninstall/update-removal, direct clear-data, main-list batch
uninstall/clear-data, quick uninstall, and one-click orphan data cleanup.

This gate intentionally stays separate from the existing `BaseActivity`
session lock. Users can protect destructive app changes without requiring an
unlock on every AppManagerNG launch.

## Next visible roadmap work

The next unshipped visible row after iter 122 is:

| Row | Tier | Status |
| --- | --- | --- |
| **F-Droid 2.0 ROM JSON Pre-Seeding Format** | Docs | **Next** |

The adjacent **Debuggable-App Rootless Backup** row is marked **Later**, so do
not treat it as the immediate next task unless the roadmap is reprioritized.

## Notes for the next pass

- For the F-Droid ROM row, this should be docs/sample-file work only unless
  source inspection reveals an existing ROM integration path.
- Keep the source Appendix citation `[S167]` attached to the roadmap row.
- Continue updating `ROADMAP.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md`, and
  `.ai/research/2026-05-18-iter-123/` in the same changeset.
