<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — pass 37

Date: 2026-05-17

## Current state

Pass 37 implements the T5 `Backup-Aware Doze Allowlist Diff Banner` roadmap row.

Local commit:

```text
feat(revert): explain Doze allowlist reverts
```

## Verification status

- `strings.xml` parsed successfully through PowerShell's XML reader.
- `git diff --check`: passed before commit with only CRLF normalization warnings.
- `git diff --cached --check`: run before committing this batch.
- Focused Gradle test attempted:
  `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.revert.DozeAllowlistDiagnosticsTest`
- Known blocker: the local shell has no JDK on PATH and `JAVA_HOME` is unset, so Gradle exits before test execution.

## Next roadmap item if pass 37 is committed

The next open `Now` row after Backup-Aware Doze, by roadmap order, is:

```text
ROADMAP.md — Achno Samsung Debloat List Cross-Check
```

That row asks for a one-time validation pass against `oem.json`'s Samsung block
using `Achno/debloat-samsung-ADB-shizuku` as a small prose-only Samsung package
source. If no actionable package delta exists, close it as an audit-backed
no-code row; otherwise add narrowly scoped Samsung package/risk entries.

## Caveats

- Push is expected to remain blocked unless GitHub auth is fixed. The remote is
  `https://github.com/SysAdminDoc/AppManagerNG.git`; current `gh auth status`
  shows the default account as `MavenImaging`, which is not authorized for this repo.
- Shared-drive Git may print non-fatal repack warnings. Confirm the commit and
  fsck state after any commit.
