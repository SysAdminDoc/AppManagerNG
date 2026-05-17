<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# MEMORY_CONSOLIDATION — 2026-05-17 pass 4

No new tool-specific instruction files appeared in pass 4. `AGENTS.md` and `CLAUDE.md`
already point to `PROJECT_CONTEXT.md`; no pointer update was needed.

## Consolidated facts

- The pass-1 `PROJECT_CONTEXT.md` uncommitted-work section was stale. The listed Finder,
  installer, and onboarding fixes are now committed locally and documented in `CHANGELOG.md`.
- Pass 4 refreshed `PROJECT_CONTEXT.md` to describe the four-pass sequence and current
  Shizuku / ML-DSA state instead of the older `97a339a` + six-file WIP snapshot.
- Source appendix range is now `S01` through `S327`.

## Reconciled conflict

Earlier Shizuku audit wording overstated the Android-17 failure mode as a universal
package-enumeration/UserService failure. Live issue re-check shows mixed evidence:

- `#1965`: Shizuku manager list is blank, but the reporter says apps still function.
- `#1967`: broader "app won't get Shizuku" failure.
- `#1988`: repeats the empty managed-app list symptom on Android 17 / Pixel 9 Pro XL.

The canonical wording is now "Android 17 compatibility risk" rather than "guaranteed
Shizuku operation failure."
