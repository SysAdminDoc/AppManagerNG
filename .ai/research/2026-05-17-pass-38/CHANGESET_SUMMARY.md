<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Pass 38 changeset summary — Achno Samsung debloat cross-check

Date: 2026-05-17

Implementation commit: `docs(debloat): close Achno Samsung cross-check`

## Roadmap item closed

- T7 `Achno Samsung Debloat List Cross-Check` in `ROADMAP.md`.

## Files changed

- `docs/audits/2026-05-17-achno-samsung-debloat-cross-check.md`
  - Documents the source, extraction regex, local datasets compared, exact misses, and no-data-change verdict.
- `CHANGELOG.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`
  - Recorded the audit-clean closure and warned future maintainers not to add the six exact misses without a second independent package dump.

## Evidence

- `ROADMAP.md` S215: `Achno/debloat-samsung-ADB-shizuku`, a small GPL-3.0 Samsung-specific README package list.
- Local source: `scripts/android-debloat-list/oem.json`, `aosp.json`, `google.json`, `carrier.json`, and `misc.json`.
- Local submodule state: `scripts/android-debloat-list@e5f4e64`.

## Verification

- Extracted 82 unique package-like tokens from the Achno README.
- Compared against the combined local debloat datasets:
  - 76 tokens already covered.
  - 6 exact misses reviewed manually.
  - 0 data entries added because the misses are typo-like, activity names, or uncorroborated single-source IDs.
- `git diff --check` passed before commit with only CRLF normalization warnings.
- No Gradle test was needed for this docs/audit-only closure.
- `git diff --cached --check` should pass before committing this batch.
- Post-commit verification should confirm the commit hash, branch-ahead state, and shared-folder fsck state.
