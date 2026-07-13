<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Maintainer-local historical archives are not
published with the repository. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

All remaining blocked items are in `Roadmap_Blocked.md`.

## Deep Audit Follow-ups (2026-07-02)

## Research-Driven Additions

Backing research: `RESEARCH.md` (2026-07-12) — user-state protection (snapshot secret
boundary, Room migration safety, snapshot portability, encrypted bundles, release/screenshot
truth) — combined with a host-verifiable code-level correctness audit of the intercept /
backup / apk-parser / search / debloat / filters / rules / uri subsystems (findings cited
inline per item). All items are fixable and unit-testable offline except the screenshot
refresh, which needs a capture environment.

### P1

### P2

- [ ] P2 — Expand the release-consistency gate to distribution packets and canonical documentation
  Why: the current gate passes while three listing packets still advertise v0.5.0/versionCode 7, Izzy documentation claims a removed CI workflow, and canonical contributor docs link to missing `PROJECT_CONTEXT.md`.
  Evidence: `scripts/verify-release-consistency.sh`; `docs/distribution/{fdroid,izzyondroid,accrescent}-listing.md`; `CLAUDE.md`; `CONTRIBUTING.md`; current v0.6.5 tag and `app/build.gradle` versionCode 13; F-Droid metadata/reproducible-build documentation.
  Touches: `scripts/verify-release-consistency.sh` (or a cross-platform helper it calls), the three distribution packets, `CLAUDE.md`, `CONTRIBUTING.md`, release-check documentation/tests.
  Acceptance: the gate fails on stale tag/version/versionCode/asset references, claims about absent workflows, and broken relative links in canonical root/distribution Markdown; all five identified documents describe v0.6.5 truth or deliberately version-independent instructions; artifact hashes/sizes are verified when a release-asset directory is supplied.
  Complexity: M

### P3
