<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — pass 34

Date: 2026-05-17

## Current state

Pass 34 implemented the T5 `Shizuku 13.6.0 OEM Allowlist` roadmap row.

Local commit:

```text
feat(shizuku): warn on known-bad 13.6.0 OEMs
```

## Verification status

- `git diff --check`: passed before commit with only CRLF normalization warnings.
- `git diff --cached --check`: passed before commit.
- Focused Gradle test to try:
  `.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.shizuku.ShizukuBridgeTest`
- Known blocker: the local shell has no JDK on PATH and `JAVA_HOME` is unset, so the focused Gradle test did not run.
- After commit:
  - `git log -1 --oneline`: confirmed the Shizuku OEM-warning commit.
  - `git status --short --branch`: branch was ahead of origin.
  - `git fsck --no-progress --connectivity-only`: only the known dangling blob/tag.

## Next roadmap item if pass 34 is committed

The next open `Now` row after the Shizuku OEM allowlist is:

```text
ROADMAP.md:575 — Shizuku Root-Backed Avoidance for Banking Apps
```

Implementation likely belongs in `ShizukuBridge`, Settings -> Operating Mode, onboarding/Privilege Health copy, and `PrivilegeModeDoctor`. Research source is `ROADMAP.md` S181 (`RikkaApps/Shizuku#2052`).

## Caveats

- Push is expected to remain blocked unless GitHub auth is fixed. The remote is `https://github.com/SysAdminDoc/AppManagerNG.git`; previous `gh auth status` showed the default account as `MavenImaging`, which is not authorized for this repo.
- Shared-drive Git may print non-fatal repack warnings. Confirm the commit and fsck state after any commit.
