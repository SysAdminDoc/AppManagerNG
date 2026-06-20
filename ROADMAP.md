<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

### P3

- [ ] P3 — clearApplicationUserData → IActivityManager migration
  Why: PackageManagerCompat.java:582 TODO notes that IActivityManager#clearApplicationUserData() is more stable than the current IPackageManager path, which has API-version-dependent method signatures.
  Evidence: PackageManagerCompat.java:582 TODO comment (dated 5/25/26)
  Touches: compat/PackageManagerCompat.java (clearApplicationUserDataViaIpc method)
  Acceptance: data-clear operations use IActivityManager when available (API 30+), falling back to IPackageManager on older APIs; existing backup/clear tests still pass.
  Complexity: S

- [ ] P3 — ZipFileSystem test stub completion
  Why: 8+ empty test methods in ZipFileSystemTest.java marked TODO since 2022. These represent untested VFS paths that could silently regress.
  Evidence: app/src/test/java/io/github/muntashirakon/io/fs/ZipFileSystemTest.java lines 63-912 (8 TODO markers)
  Touches: app/src/test/java/io/github/muntashirakon/io/fs/ZipFileSystemTest.java
  Acceptance: all 8+ stubbed test methods have implementations that exercise the ZipFileSystem paths they were designed to cover; test suite still passes.
  Complexity: S

- [ ] P3 — Gradle 9.5.1 → 9.6.0
  Why: Gradle 9.6.0 (released 2026-06-19) improves configuration cache hit rates. No security driver but keeps build infra current.
  Evidence: https://github.com/gradle/gradle/releases/tag/v9.6.0
  Touches: gradle/wrapper/gradle-wrapper.properties, lockfiles
  Acceptance: Gradle wrapper updated, ./gradlew assembleFlossDebug succeeds, all tests pass.
  Complexity: S

All remaining blocked items are in `Roadmap_Blocked.md`.
