<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Maintainer-local historical archives are not
published with the repository. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

All remaining blocked items are in `Roadmap_Blocked.md`.

## Deep Audit Follow-ups (2026-07-02)

## Research-Driven Additions

Backing research: `RESEARCH.md` (2026-07-12) — user-state protection (snapshot secret
boundary, Room migration safety, snapshot portability, encrypted bundles, release/screenshot
truth) — combined with a host-verifiable code-level correctness audit of the intercept /
backup / apk-parser / search / debloat / filters / rules / uri subsystems (findings cited
inline per item). All items are fixable and unit-testable offline except the screenshot
refresh, which needs a capture environment.

### P0

- [ ] P0 — Replace destructive AppsDb fallback with a complete, preservation-tested migration ladder
  Why: schema 10 has no 1→2 migration and enables destructive forward and downgrade fallbacks even though the database contains filters, history, favorites, freeze choices, and backup state that cannot all be rebuilt.
  Evidence: `db/AppsDb.java`; `app/schemas/io.github.muntashirakon.AppManager.db.AppsDb/1.json` through `10.json`; Android Room migration documentation; current `app/src/androidTest/java/io/github/muntashirakon/AppManager/db/AppsDbMigrationTest.java` coverage.
  Touches: `db/AppsDb.java` (add 1→2 and remove unrestricted destructive fallbacks), `app/src/androidTest/java/io/github/muntashirakon/AppManager/db/AppsDbMigrationTest.java` (every start version through current plus downgrade/failure cases), exported Room schemas, database-open recovery UI/logging.
  Acceptance: fixtures from every schema version 1–10 open at the current schema and preserve representative rows from every durable table; an unknown/missing path or downgrade fails closed with the original DB copied intact to a recoverable location and never recreates tables silently.
  Complexity: M

### P1

- [ ] P1 — Complete snapshot portability for DB-backed user state
  Why: Android transfer excludes `apps.db`, while manual snapshots preserve operation history but omit saved log filters, file-manager favorites, and per-package freeze methods, leaving migrations and device replacement incomplete.
  Evidence: `res/xml/backup_rules.xml`; `res/xml/full_backup_rules.xml`; `snapshot/SnapshotBundle.java`; `db/entity/{LogFilter,FmFavorite,FreezeType}.java`; Swift Backup and Neo Backup portability/recovery patterns.
  Touches: `snapshot/SnapshotBundle.java` (versioned JSON sections and deterministic merge rules), affected DAOs/entities, `settings/PrivacyPreferences.java`, snapshot strings, `snapshot/SnapshotBundleTest.java`.
  Acceptance: preview/export/import reports and round-trips log filters, favorites, and freeze methods; invalid or unavailable favorite paths are surfaced and skipped rather than trusted; cached app/scan tables remain excluded; backup metadata is rebuilt from archive manifests instead of copying device-specific rows.
  Complexity: M

- [ ] P1 — Add authenticated, passphrase-encrypted snapshot bundles
  Why: even after credentials are excluded, plaintext snapshots expose app notes, profiles, rules, tags, preferences, and operation history, and ZIP integrity does not authenticate the bundle before restore.
  Evidence: `snapshot/SnapshotBundle.java` (plain `ZipOutputStream`); `settings/PrivacyPreferences.java` (direct SAF export/import); OWASP Password Storage and Cryptographic Storage guidance; USENIX Security 2023 backup findings.
  Touches: `snapshot/SnapshotBundle.java` (versioned envelope), a focused snapshot-crypto helper using Bouncy Castle, `settings/PrivacyPreferences.java` (passphrase and warning flows), strings, unit/Robolectric tests.
  Acceptance: the default new format uses a magic/version header, Argon2id (`m=19456`, `t=2`, `p=1`, random 16-byte salt) and AES-256-GCM (random 12-byte nonce, 128-bit tag, authenticated header); wrong passwords or one-byte tampering fail before any state is written; passphrases are clearable and never persisted; legacy ZIPs remain importable only after a plaintext warning.
  Complexity: L

### P2

- [ ] P2 — Expand the release-consistency gate to distribution packets and canonical documentation
  Why: the current gate passes while three listing packets still advertise v0.5.0/versionCode 7, Izzy documentation claims a removed CI workflow, and canonical contributor docs link to missing `PROJECT_CONTEXT.md`.
  Evidence: `scripts/verify-release-consistency.sh`; `docs/distribution/{fdroid,izzyondroid,accrescent}-listing.md`; `CLAUDE.md`; `CONTRIBUTING.md`; current v0.6.5 tag and `app/build.gradle` versionCode 13; F-Droid metadata/reproducible-build documentation.
  Touches: `scripts/verify-release-consistency.sh` (or a cross-platform helper it calls), the three distribution packets, `CLAUDE.md`, `CONTRIBUTING.md`, release-check documentation/tests.
  Acceptance: the gate fails on stale tag/version/versionCode/asset references, claims about absent workflows, and broken relative links in canonical root/distribution Markdown; all five identified documents describe v0.6.5 truth or deliberately version-independent instructions; artifact hashes/sizes are verified when a release-asset directory is supplied.
  Complexity: M

- [ ] P2 — Replace legacy storefront screenshots with deterministic current NG captures
  Why: all nine Fastlane phone images predate the v0.6.x UI and show upstream-era identity/data, weakening F-Droid listing accuracy and release trust even though store graphics are an explicit F-Droid discovery input.
  Evidence: `fastlane/metadata/android/en-US/images/phoneScreenshots/{1..9}.png`; current V2 layouts/themes; F-Droid graphics and screenshots documentation.
  Touches: Fastlane phone screenshots, a privacy-safe debug/demo data fixture or capture setup, Fastlane metadata, release-consistency image checks.
  Acceptance: nine 1080×2160 captures show current NG onboarding, app list, app details, permissions/AppOps, scanner, backup/restore, installer preflight, file manager, and settings/search across both themes; no upstream package identity, real user/device data, stale version copy, clipping, or unreadable contrast remains; dimensions and count are mechanically validated.
  Complexity: M

### P3
