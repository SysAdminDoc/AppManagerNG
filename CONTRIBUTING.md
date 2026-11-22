<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Contributing to AppManagerNG

AppManagerNG is a GPL-3.0-or-later Android package manager fork focused on
power-user workflows, privacy, reliability, and approachable UX. Contributions
should preserve upstream App Manager credit while targeting this fork's package
identity and release channels.

## Before Opening Work

- Read `README.md`, `ROADMAP.md`, `RESEARCH.md`, and any linked doc under
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

### Reporting A Security Problem

Send it privately through GitHub's form at https://github.com/SysAdminDoc/AppManagerNG/security/advisories/new (the Security tab's "Report a vulnerability" button). Only you and the maintainer can see it. Please don't open a public issue or pull request about it, and keep exploit steps, proof-of-concept code, private device data, backup contents and credentials out of anything public.

Fixes go into the newest release on GitHub Releases. Older releases don't get their own fixes, so check that the problem is still there in the newest one. If it's in upstream App Manager code that NG hasn't changed, upstream should hear about it too.

A useful report has:

- the AppManagerNG version and where you installed it from
- the device and its Android version (and the ROM, if it isn't stock)
- whether the app runs with no root, root, ADB, Shizuku or Dhizuku
- what an attacker needs (another installed app, a USB cable, a spot on the same network, the unlocked phone) and what they get out of it
- steps to reproduce, plus a proof of concept if you have one
- whether it's already public anywhere

You should hear back within a week, and updates come in the same private report. Please keep the details to yourself until a fix ships or 90 days pass, whichever comes first. The advisory goes public with the release that fixes it and credits you, unless you'd rather not be named.

## Translations

The fork-owned translation pipeline is not live yet. Do not add placeholder
Weblate or Crowdin config until the hosted project exists. Small English string
fixes are welcome; broad localization catch-up should wait for the translation
project so locale updates can be reviewed consistently.
