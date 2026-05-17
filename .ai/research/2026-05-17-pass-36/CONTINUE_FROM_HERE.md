<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — pass 36

Date: 2026-05-17

## Current state

Pass 36 implements the T9 `OS-Revert Detection Banner` roadmap row.

Local commit:

```text
feat(revert): detect OS reverted state changes
```

## Verification status

- `strings.xml` parsed successfully through PowerShell's XML reader.
- `git diff --check`: passed before commit with only CRLF normalization warnings.
- `git diff --cached --check`: run before committing this batch.
- Focused Gradle test attempted:
  `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.revert.OsRevertMonitorTest`
- Known blocker: the local shell has no JDK on PATH and `JAVA_HOME` is unset, so Gradle exits before test execution.

## Next roadmap item if pass 36 is committed

The next open `Now` row after OS-revert detection is:

```text
ROADMAP.md — Backup-Aware Doze Allowlist Diff Banner
```

Pass 36 already ships the generic Doze expected/current allowlist mismatch
surface. The remaining row is narrower: enrich the Doze case with a deeper
`device_idle_constants` / OEM-policy explanation rather than only reporting the
generic expected/current state.

## Caveats

- Push is expected to remain blocked unless GitHub auth is fixed. The remote is
  `https://github.com/SysAdminDoc/AppManagerNG.git`; previous `gh auth status`
  showed the default account as `MavenImaging`, which is not authorized for this repo.
- Shared-drive Git may print non-fatal repack warnings. Confirm the commit and
  fsck state after any commit.
