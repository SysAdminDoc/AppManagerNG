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

### P3

## Deep Audit Follow-ups (2026-07-14)

Findings from the 2026-07-14 deep audit that were not fixed in place because they
need a versioned data migration, a design decision, a broad multi-site refactor,
or on-device verification. High-confidence host-fixable bugs from the same audit
were fixed directly (see git history / CHANGELOG).

### P3

## Research-Driven Additions (2026-07-14)

Backing research: `RESEARCH.md` (2026-07-14). Fresh host-verifiable code audit plus an
upstream/ecosystem sweep (App Manager v4.1.0, LibChecker/Hail/Canta/InstallerX/SD Maid SE,
dependency CVEs, Android 16/17 APIs). All items below are host-verifiable and unit-testable
offline. Device-gated feature ideas from this pass are in `Roadmap_Blocked.md`.

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions (2026-07-22)

Backing research: `RESEARCH.md` (2026-07-22). Competitor harvest (InstallerX-Revived,
LibChecker, SD Maid, Hail) + a fresh host-verifiable audit and dependency/CVE sweep.
Upstream shipped no new tag since v4.1.0 (2026-06-29); all prior host findings are already
fixed, so these are net-new. Every item below is host-implementable and host-testable
(Robolectric/JUnit/Jazzer) except where a final on-device check is noted.

### P2

### P3


## Research-Driven Additions (2026-07-29)

### P0

### P1

- [ ] P1 — Bring the CVE gate's configurations under dependency verification
  Why: the release gate's blocking CVE scan cannot run at all. `dependencyCheckAggregate`
  resolves `:app:androidLintTool`, whose POMs have no entries in
  `gradle/verification-metadata.xml`, so Gradle aborts the task before the scanner starts.
  A release therefore currently has no CVE evidence, and the gate correctly refuses to
  produce a receipt without it.
  Evidence: `python scripts/run_dependency_cve_gate.py --out-dir reproducible-release/publish`
  → "Dependency verification failed for configuration ':app:androidLintTool'", 7 artifacts:
  manifest-merger-32.2.1.pom, guava-33.3.1-jre.pom, aapt2-proto-9.2.1-15009934.pom,
  builder-model-9.2.1.pom, kotlinx-coroutines-core-jvm-1.9.0.pom, kotlin-stdlib-2.2.10.pom,
  checker-qual-3.43.0.pom.
  Touches: `gradle/verification-metadata.xml`, `scripts/run_dependency_cve_gate.py`,
  `docs/distribution/dependency-verification.md`.
  Acceptance: the checksums are added from a verified source rather than by blanket
  `--write-verification-metadata` (which would trust whatever was downloaded); the CVE gate runs
  to completion and writes `dependency-cve-receipt.json`; a host test covers the failure mode so
  a future configuration addition surfaces as a gate failure rather than a silent skip.
  Complexity: M

### P2

