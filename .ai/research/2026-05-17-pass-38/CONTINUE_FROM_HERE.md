<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — pass 38

Date: 2026-05-17

## Current state

Pass 38 closes the T7 `Achno Samsung Debloat List Cross-Check` roadmap row as
audit-clean / no data change.

Local commit:

```text
docs(debloat): close Achno Samsung cross-check
```

## Verification status

- Achno README package extraction found 82 unique package-like tokens.
- Combined local debloat dataset comparison found 76 already-covered tokens and
  6 exact misses.
- The six exact misses were rejected in the audit doc because they are apparent
  typos, a non-package activity name, or a single-source package id with no
  corroboration beyond Achno.
- `git diff --check`: passed before commit with only CRLF normalization warnings.
- `git diff --cached --check`: run before committing this batch.
- No Gradle test was needed because this pass did not change runtime code.

## Next roadmap item if pass 38 is committed

The next open `Now` row after the Achno cross-check, by roadmap order, is:

```text
ROADMAP.md — Restricted Settings Unlock Walkthrough
```

That row asks the T5 Privilege Health-Check to detect Android 13+ Restricted
Settings sideload friction for Accessibility / NotificationListener /
DevicePolicy-style grants, explain why it exists, and deep-link the user toward
the correct Settings surface with a manual long-press unlock hint.

## Caveats

- Push is expected to remain blocked unless GitHub auth is fixed. The remote is
  `https://github.com/SysAdminDoc/AppManagerNG.git`; current `gh auth status`
  shows the default account as `MavenImaging`, which is not authorized for this repo.
- Shared-drive Git may print non-fatal repack warnings. Confirm the commit and
  fsck state after any commit.
