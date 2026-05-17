<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# STATE_OF_REPO — 2026-05-17 pass 4

Pass 4 continues from `.ai/research/2026-05-17-pass-3/CONTINUE_FROM_HERE.md`.
The worktree started clean on `main`, **23 commits ahead of `origin/main`**.

## Local state checked

- `git status --short --branch`: clean at start, `main...origin/main [ahead 23]`.
- Latest local commit before edits: `457deca docs(research): 2026-05-17 pass-3 audit trail`.
- Required instruction files inspected in earlier passes remain unchanged: `AGENTS.md`,
  `CLAUDE.md`, `PROJECT_CONTEXT.md`.
- Pass-4 implementation touched source, tests, CI, roadmap/changelog/context, and two
  existing audit docs.

## Implementation state

- Shizuku Android-17 risk detection now exists in `ShizukuBridge`.
- Onboarding now shows a Shizuku Android-17 warning that launches Wireless ADB setup.
- ML-DSA OIDs now render as readable Dilithium names in certificate display surfaces.
- A GitHub Actions Shizuku release watcher now scans for Android-17 fix-candidate releases.

## Verification state

- XML resource parse checks passed for:
  - `app/src/main/res/layout/fragment_onboarding.xml`
  - `app/src/main/res/values/strings.xml`
- Gradle unit-test execution is blocked locally because `JAVA_HOME` is unset and no
  `java` command is available on `PATH`.
- GitHub API checks confirmed official Shizuku latest release remains `v13.6.0`; issues
  `#1965` and `#1967` remain open.
