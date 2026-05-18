# Continue From Here — 2026-05-18 iter 104

## Completed

- T7 OEM Debloat-Blocker Bypass:
  - `OemBloatRiskTable` now exposes uninstall fallback policy for known
    OEM-protected debloat targets.
  - Debloater list/details surfaces identify protected removals before action.
  - Batch removal defaults protected selections to the existing freeze path and
    leaves destructive removal as an explicit override.
  - Focused JVM coverage verifies Samsung One UI 8.5 SmartSuggestions, MIUI
    core, and OPlus guarded-package matching.

## Next likely roadmap item

- Resume from `ROADMAP.md` after iter-104. The next open iter-19 carryover row
  is `Per-App Rollback / "Revert All Changes"`.

## Notes

- The OPlus package policy intentionally uses exact guarded package IDs rather
  than broad prefixes to avoid over-blocking benign OPlus system packages.
- The safe fallback uses NG's existing batch freeze operation and default
  freeze method preference, so it inherits current privilege/mode behavior.
