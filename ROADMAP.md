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

- [ ] P2 — IntentCompat: array extras with a trailing backslash corrupt on interceptor round-trip
  Why: `escapeComma` escapes `,`→`\,` but not the escape char, while `splitEscapedComma`
  splits on `(?<!\\),`, so an element ending in `\` (e.g. `["a\\","b"]`) flattens to `a\,b`
  and parses back as a single element `a,b` — silent data corruption in string/URI array extras.
  Evidence: intercept/IntentCompat.java:345 (split regex), :349-351 (escapeComma), :353-356 (unescapeComma).
  Touches: intercept/IntentCompat.java (escape `\` before `,`; unescape `\,` before `\\`), app/src/test/ (IntentCompatTest trailing-backslash round-trip).
  Acceptance: `flattenToString`/`unflattenFromString` round-trips `new String[]{"a\\","b"}` back to the identical array; existing comma round-trip tests still pass.
  Complexity: S

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

- [ ] P3 — Port upstream Wireless-Debugging instructions + developer-options deep link
  Why: the connect/pair dialogs reuse a generic title/message and the developer-options intent
  does not deep-link to the Wireless-Debugging toggle; upstream `133b5acb7f` adds dedicated copy
  and the `:settings:fragment_args_key` = `toggle_adb_wireless` extra. Confirmed unported.
  Evidence: upstream MuntashirAkon/AppManager@133b5acb7f; settings/Ops.java (still uses R.string.wireless_debugging for the connect + pair dialogs); res/values/strings.xml (new strings absent).
  Touches: settings/Ops.java (connectWirelessDebugging + pairAdbInput titles/messages + deep-link extra), res/values/strings.xml (adb_pairing_title, manual_wireless_debugging_title, manual_wireless_debugging_instructions).
  Acceptance: the connect and pair dialogs show the dedicated titles/instructions; the developer-options intents carry `:settings:fragment_args_key`=`toggle_adb_wireless`; only the two mapped title sites change (not the third unrelated one); build + string-presence test green.
  Complexity: S

- [ ] P3 — AdvancedSearchView single-string regex overload: catch PatternSyntaxException + align find() semantics
  Why: the `matches(String, String, int)` `SEARCH_TYPE_REGEX` case calls `text.matches(query)`
  with no `try/catch` (crash on a bad pattern) and uses full-match semantics, while the two
  collection overloads guard `PatternSyntaxException` and use `matcher.find()` (substring) — a
  latent crash/behavior-divergence trap for any future caller.
  Evidence: misc/AdvancedSearchView.java:334-346 (string overload) vs :362-374, :393-411 (collection overloads).
  Touches: misc/AdvancedSearchView.java (compile once in try/catch → return false; use matcher(text).find()), app/src/test/ (new AdvancedSearchView regex tests).
  Acceptance: `matches("[", "abc", SEARCH_TYPE_REGEX)` returns false without throwing; a substring case agrees between the string and collection overloads.
  Complexity: S

- [ ] P3 — Binary-XML decoders read the ByteBuffer window, not the whole backing array
  Why: `ByteBuffer.array()` ignores `arrayOffset()`/`position()`/`limit()` and throws
  `UnsupportedOperationException` on direct/read-only buffers, so the public
  `decode(ByteBuffer,...)`/`ManifestParser(ByteBuffer)` APIs silently mis-read a sliced or
  mmapped buffer; works today only because every caller passes `ByteBuffer.wrap(byte[])`.
  Evidence: apk/parser/AndroidBinXmlDecoder.java:73; apk/parser/ManifestParser.java:176; apk/ApkUtils.java:255.
  Touches: apk/parser/AndroidBinXmlDecoder.java, apk/parser/ManifestParser.java (read `buf.remaining()` via `duplicate().get(...)`), app/src/test/ (sliced-buffer parity test).
  Acceptance: parsing a known-good binary AndroidManifest from a non-zero-offset `.slice()` buffer yields the same result as the zero-offset case.
  Complexity: S

- [ ] P3 — AndroidBinXmlDecoder framework-package-block lazy init is an unsynchronized race
  Why: `getFrameworkPackageBlock()` lazily initializes a non-volatile static without
  synchronization; concurrent manifest parsing (per-app loaders, filters) can double-run the
  heavy framework-table init and observe a partially published reference.
  Evidence: apk/parser/AndroidBinXmlDecoder.java:135-144.
  Touches: apk/parser/AndroidBinXmlDecoder.java (synchronized / holder class / volatile DCL), app/src/test/ (concurrent-callers return same instance).
  Acceptance: N threads calling `getFrameworkPackageBlock()` all receive the same (`==`) instance; init runs once.
  Complexity: S

- [ ] P3 — DebloatObject.fillInstallInfo accumulates instead of overwriting per-user state
  Why: it accumulates `mInstalled` with `|=` then overwrites it with `=` inside the icon block,
  and overwrites `mSystemApp`/`mUpdatedSystemApp`/`mFrozen`/`mLabel` per user, so on a multi-user
  device the last-iterated user's state wins and an accumulated `installed=true` is clobbered.
  Evidence: debloat/DebloatObject.java:244-264.
  Touches: debloat/DebloatObject.java (extract a pure per-user merge that accumulates installed/system/frozen correctly), app/src/test/ (unit test the merge across multiple users).
  Acceptance: a unit test of the extracted merge asserts `installed` stays true if any user has it installed, and system/frozen flags reflect the intended accumulation rather than last-write-wins.
  Complexity: S

- [ ] P3 — Backup checksum writer swallows I/O errors (fail-open integrity)
  Why: `Checksum` wraps a `PrintWriter`, whose `IOException`s are only visible via `checkError()`,
  which is never called; if the stream fails mid-backup (disk full, revoked SAF permission),
  `checksums.txt` is silently truncated but the backup reports success, and later verify/restore
  treats the missing entries as "no checksum recorded" — a fail-open integrity path that hides the
  original write failure.
  Evidence: backup/BackupItems.java:699 (PrintWriter), :731-733 (println/flush unchecked), :745-751 (close discards error).
  Touches: backup/BackupItems.java (call `checkError()` after write/flush and in close → throw IOException, or use a Writer that propagates), app/src/test/ (failing OutputStream surfaces an error).
  Acceptance: a `Checksum` over a stream that throws IOException on write surfaces the failure from `add()`/`close()` instead of returning normally.
  Complexity: S
