<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

### P3

- [ ] P3 — Gradle 9.5.1 → 9.6.0
  Why: Gradle 9.6.0 (released 2026-06-19) improves configuration cache hit rates. No security driver but keeps build infra current.
  Evidence: https://github.com/gradle/gradle/releases/tag/v9.6.0
  Touches: gradle/wrapper/gradle-wrapper.properties, lockfiles
  Acceptance: Gradle wrapper updated, ./gradlew assembleFlossDebug succeeds, all tests pass.
  Complexity: S

All remaining blocked items are in `Roadmap_Blocked.md`.
