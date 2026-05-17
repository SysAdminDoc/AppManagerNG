<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 11

## Roadmap item closed

- T5 `Android 16 Capability Dropping UI`

## Implementation

- Added `RootCapabilityDiagnostics`, a runner-side diagnostic that probes the
  active privileged shell instead of parsing root-manager private config.
- The probe runs `id -u` and reads `CapEff` from `/proc/$$/status`, then classifies
  the session as:
  - unavailable when no root/ADB/Shizuku privileged shell is active;
  - root when the active shell UID is `0`;
  - dropped when a non-root shell has an all-zero effective capability mask;
  - present when a non-root shell still has effective Linux capabilities;
  - unknown when procfs output is missing or malformed.
- Added a Settings -> Privileges row titled "Capability dropping (--drop-cap)".
  Tapping the row reruns the capability probe.
- Added pure-JVM parser coverage for dropped, present, root, and malformed probe
  output.

## Files changed

- `app/src/main/java/io/github/muntashirakon/AppManager/runner/RootCapabilityDiagnostics.java`
- `app/src/main/java/io/github/muntashirakon/AppManager/settings/PrivilegeHealthPreferences.java`
- `app/src/main/res/xml/preferences_privilege_health.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/io/github/muntashirakon/AppManager/runner/RootCapabilityDiagnosticsTest.java`
- `CHANGELOG.md`
- `ROADMAP.md`
- `PROJECT_CONTEXT.md`

## Verification

- XML parse passed for `strings.xml` and `preferences_privilege_health.xml`.
- `git diff --check` passed.
- Targeted Gradle test attempt remains blocked because `JAVA_HOME` is unset and
  no `java` command is available in PATH.

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging`, producing
a 403 earlier in this session.
