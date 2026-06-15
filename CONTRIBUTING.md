<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Contributing to AppManagerNG

AppManagerNG is a GPL-3.0-or-later Android package manager fork focused on
power-user workflows, privacy, reliability, and approachable UX. Contributions
should preserve upstream App Manager credit while targeting this fork's package
identity and release channels.

## Before Opening Work

- Read `README.md`, `PROJECT_CONTEXT.md`, `ROADMAP.md`, and any linked doc under
  `docs/` that matches your change.
- Search existing issues and roadmap entries before proposing a new feature.
- Keep changes focused. Do not mix unrelated roadmap, formatting, and dependency
  work in one patch.
- Preserve REUSE/SPDX headers and the GPL-3.0-or-later license.

## Build And Test

Initialize submodules before building:

```bash
git submodule update --init --recursive
```

Recommended local checks for app changes:

```bash
./gradlew :app:processFlossDebugResources :app:compileFlossDebugJavaWithJavac
./gradlew :app:testFlossDebugUnitTest
```

Run narrower module checks when touching only a library module. For docs-only
changes, run:

```bash
./gradlew :docs:buildDocs
```

## Patch Standards

- Use the existing Android Views + Material Components architecture. This project
  is not a Compose app.
- Keep `minSdk = 21` unless a coordinated roadmap decision raises the floor.
- Do not change `applicationId`, signing, release keys, package names, or fork
  attribution as drive-by cleanup.
- Do not add network behavior to the `floss` flavor. Optional online features
  belong in the `full` flavor and must remain user opt-in.
- Do not add secrets, test credentials, signing keys, analytics keys, or
  environment-specific config.
- For UI changes, cover empty, loading, error, long-text, dark/AMOLED, and
  accessibility states where practical.

## Security And Privacy

This app can operate with root, ADB, Shizuku, Dhizuku, or privileged-server
capabilities. Treat destructive operations, backup/restore, app data access,
network opt-ins, and release provenance as security-sensitive surfaces.

Report sensitive security issues privately through the maintainer contact listed
on the GitHub repository profile. Do not post exploit details, private device
data, backup contents, or credentials in public issues.

## Translations

The fork-owned translation pipeline is not live yet. Do not add placeholder
Weblate or Crowdin config until the hosted project exists. Small English string
fixes are welcome; broad localization catch-up should wait for the translation
project so locale updates can be reviewed consistently.
