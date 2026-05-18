<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CONTINUE_FROM_HERE — 2026-05-18 iter 91

Iter 91 closed the safe Dhizuku provider-detection slice without adding the
official Dhizuku-API AAR.

## Current state

- `DhizukuBridge` detects:
  - official package `com.rosan.dhizuku`
  - official owner component `com.rosan.dhizuku/.server.DhizukuDAReceiver`
  - provider authority `com.rosan.dhizuku.server.provider`
  - API permission `com.rosan.dhizuku.permission.API`
  - declared Android 8-16 support bounds
- Settings -> Privileges, Mode Doctor, and onboarding now surface Dhizuku status.
- Full DPM operations are still not wired into `Ops`, `SelfPermissions`, or package-state helpers.

## Next exact step

Continue to the next unblocked roadmap row after the blocked Dhizuku DPM
operations carryover. At the time of this pass, that is T6 Scheduled Auto-Backup,
unless a smaller already-implemented/audit-closable row is found first in the
fresh roadmap scan.

## Dhizuku carryover constraint

Do not add `io.github.iamr0s:Dhizuku-API` as a direct implementation dependency
until one of these is chosen:

1. reflection/optional-provider integration that preserves API 21,
2. split artifact / dynamic loading strategy,
3. explicit minSdk bump decision backed by `docs/policy/minsdk-21-ceiling.md`.
