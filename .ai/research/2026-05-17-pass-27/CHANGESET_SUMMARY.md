<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# CHANGESET SUMMARY — 2026-05-17 pass 27

## Roadmap item parked

- Eng-Debt Apktool 3.0.2 "Remastered" Migration

## Audit result

- Re-ran targeted source and Gradle searches for:
  - `apktool`
  - `Apktool`
  - `org.apktool`
  - `brut.apktool`
  - `apktool-lib`
  - `apktool_version`
  - `smali`
  - `baksmali`
- Current NG source has no Apktool dependency, no Apktool 2.x integration, and
  no `brut.apktool` call site to migrate.
- APK-editing-related dependencies today are ARSCLib, apksig, Google
  smali/baksmali `3.0.9`, and JADX.
- Maven Central confirms `org.apktool:apktool-lib:3.0.2` is available, but it
  pulls the iBotPeaches smali fork plus Guava/commons runtime dependencies.
  Adding it without an actual Apktool backend would add unused weight and could
  create duplicate smali classpath risk.

## Documentation changes

- Moved the Apktool 3.0.2 row out of the active `Now` queue and marked it parked
  behind a future T12 Apktool-backed decode/rebuild backend.
- Updated the Engineering Debt Register with the dependency/call-site audit
  result.
- Added Source Appendix entries for the official Apktool 3.0.2 release blog and
  Maven Central `apktool-lib` artifact metadata.
- Updated `CHANGELOG.md` and `PROJECT_CONTEXT.md`.

## Files changed

- `ROADMAP.md`
- `CHANGELOG.md`
- `PROJECT_CONTEXT.md`
- `.ai/research/2026-05-17-pass-27/CHANGESET_SUMMARY.md`
- `.ai/research/2026-05-17-pass-27/CONTINUE_FROM_HERE.md`

## Verification

- `git diff --check` passed.
- No Gradle test was applicable because this pass intentionally made no code
  changes.

## External sources used

- `https://apktool.org/blog/apktool-3.0.2/`
- `https://central.sonatype.com/artifact/org.apktool/apktool-lib`
- `https://repo.maven.apache.org/maven2/org/apktool/apktool-lib/maven-metadata.xml`
- `https://repo.maven.apache.org/maven2/org/apktool/apktool-lib/3.0.2/apktool-lib-3.0.2.pom`

## Push status

Push remains skipped because `origin` is `https://github.com/SysAdminDoc/AppManagerNG.git`
while the configured GitHub credential authenticates as `MavenImaging` and is
currently invalid in `gh auth status`.
