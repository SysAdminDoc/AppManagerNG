<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET_SUMMARY — 2026-05-17 pass 6

## Created

- `app/src/test/java/io/github/muntashirakon/AppManager/apk/installer/InstallChecksumDisplayTest.java`
- `.ai/research/2026-05-17-pass-6/*`

## Modified

- `PackageInstallerCompat.java` — computes SHA-256 over the bytes streamed into
  `PackageInstaller.Session.openWrite()` before session commit.
- `PackageInstallerBroadcastReceiver.java` — carries the session digest through
  the pending-user-action handoff.
- `PackageInstallerActivity.java` — shows a checksum dialog before launching
  Android's system install confirmation prompt.
- `strings.xml` — adds installer checksum dialog copy.
- `ROADMAP.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md` — marks the installer
  checksum row shipped and records the Dhizuku minSdk-26 integration constraint.

## Verification

- XML parse check passed for `app/src/main/res/values/strings.xml`.
- `git diff --check` passed before staging.
- Gradle tests could not run locally: no `JAVA_HOME` / no `java` command available.
