<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Contributing to AppManagerNG

AppManagerNG is a GPL-3.0-or-later Android package-management tool with privileged root, ADB, and
Shizuku workflows. Changes should be defensive, reversible where possible, and respectful of the
project's API 21 support floor.

## Local setup

1. Install JDK 21 and the Android SDK.
2. Fetch required submodules:

   ```bash
   git submodule update --init --recursive
   ```

3. Run the baseline checks before opening a pull request:

   ```bash
   ./gradlew test
   ./gradlew :app:lint
   ./gradlew :app:assembleDebug
   ```

On Windows, use `.\gradlew.bat` with the same tasks.

## Project constraints

- Keep the app GPL-3.0-or-later and preserve existing SPDX headers.
- New source files must include an SPDX header appropriate for the file type.
- Maintain `minSdk 21` unless a roadmap item explicitly changes that support contract.
- Do not introduce a Compose migration or a broad package/namespace rename as incidental cleanup.
- The app `applicationId` is `io.github.sysadmindoc.AppManagerNG`; Java/Kotlin source packages still
  use the upstream namespace for compatibility.
- Do not add network services, telemetry, crash reporting, or cloud dependencies without an explicit
  opt-in design and maintainer approval.
- Do not submit bulk machine-generated code. Tool-assisted changes must be reviewed line by line by
  the contributor, and the contributor is responsible for licensing, correctness, and maintainability.

## Pull request checklist

- Explain the user-visible behavior change and the failure mode it fixes.
- Add or update focused tests for bug fixes and parser/crypto/installer/data-integrity changes.
- Include screenshots or screen recordings for UI changes.
- Update `CHANGELOG.md`, `README.md`, or `docs/` when commands, setup, distribution, or user-facing
  behavior changes.
- Keep destructive operations guarded by clear review/confirmation flows.
- Avoid style-only churn in unrelated files.

## Security-sensitive areas

Take extra care in backup/restore, package installation, shell execution, file-manager operations,
signature verification, tracker/debloater actions, and permission-state changes. Validate paths and
external input, keep failures explicit, and prefer bounded waits/timeouts over indefinite background
work.
