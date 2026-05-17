<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — pass 35

Date: 2026-05-17

## Current state

Pass 35 implements the T5 `Shizuku Root-Backed Avoidance for Banking Apps`
roadmap row.

Local commit:

```text
feat(shizuku): avoid root-backed auto mode
```

## Verification status

- `strings.xml` parsed successfully through PowerShell's XML reader.
- `git diff --check`: passed before commit with only CRLF normalization warnings.
- `git diff --cached --check`: pass before committing this batch.
- Focused Gradle test to try:
  `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.shizuku.ShizukuBridgeTest`
- Known blocker: the local shell has no JDK on PATH and `JAVA_HOME` is unset, so the focused Gradle test did not run.

## Next roadmap item if pass 35 is committed

The next open `Now` row after Shizuku root-backed avoidance is:

```text
ROADMAP.md — OS-Revert Detection Banner
```

Implementation likely belongs in the existing privileged operation execution
surfaces, with a small reusable verifier/scheduler abstraction that can re-poll
Doze allowlist, freeze/component state, and AppOps state after NG writes an
intended state. Pair it with the already-shipped operation-history and reversible
recovery guidance surfaces.

## Caveats

- Push is expected to remain blocked unless GitHub auth is fixed. The remote is
  `https://github.com/SysAdminDoc/AppManagerNG.git`; previous `gh auth status`
  showed the default account as `MavenImaging`, which is not authorized for this repo.
- Shared-drive Git may print non-fatal repack warnings. Confirm the commit and
  fsck state after any commit.
