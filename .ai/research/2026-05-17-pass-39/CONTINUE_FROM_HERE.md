<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — pass 39

Date: 2026-05-17

## Current state

Pass 39 closes the T5 `Restricted Settings Unlock Walkthrough` roadmap row.

Local commit:

```text
feat(settings): add restricted settings walkthrough
```

The user explicitly requested: **stop after the next commit**. Do not continue
the autonomous roadmap loop after this commit unless a later user message asks
to resume.

## Verification status

- The implementation adds a Settings -> Privileges Restricted Settings row and a
  Mode Doctor probe.
- The detector uses install-source metadata and deliberately avoids claiming a
  definitive blocked state because Android does not expose a public per-app
  restricted-settings-blocked bit.
- Unit coverage was added for install-source classification.
- `git diff --check`: passed with only expected CRLF normalization warnings.
- XML parse check passed for `strings.xml` and
  `preferences_privilege_health.xml`.
- Focused Gradle test attempted:

```text
.\gradlew.bat :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.settings.RestrictedSettingsDiagnosticsTest
```

- Environment blocker remains:

```text
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

## Next roadmap item if work resumes later

The next open `Now` row after Restricted Settings, by roadmap order, is:

```text
ROADMAP.md — Snapshot Bundle Export/Import
```

That row asks for one-button export/import of `prefs/`, `profiles/`, `tags/`,
`history.db`, audit log, and bundled-data manifest with a schema-version header,
before the applicationId rename risk window.

## Caveats

- Push is expected to remain blocked unless GitHub auth is fixed. The remote is
  `https://github.com/SysAdminDoc/AppManagerNG.git`; current `gh auth status`
  has shown the default account as `MavenImaging`, which is not authorized for
  this repo.
- Shared-drive Git may print non-fatal repack warnings. Confirm the commit and
  fsck state after any commit.
