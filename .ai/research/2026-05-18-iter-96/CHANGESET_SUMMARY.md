# Iter-96 Changeset Summary

Date: 2026-05-18

Roadmap item parked: T6 `Separated Active/Paused Schedule Lists`.

## Decision

The row is blocked by a future multiple-schedule profile model. AppManagerNG currently has one global Scheduled Auto-Backup configuration in Settings -> Backup, not a list of schedule records. Adding active/paused sections around a single switch would be dead UI and would not satisfy the Neo Backup-style list behavior described by the row.

## Files

- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- Documentation-only change; no build command needed.

