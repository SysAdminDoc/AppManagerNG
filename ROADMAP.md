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

Backing research: `RESEARCH.md` (2026-07-12 — code-level correctness audit across the
intercept/backup-convert/apk-parser/search/debloat and crypto/backup-core/filters/scanner/
rules/uri subsystems, plus the complete upstream v4.1.0 commit set and dependency currency).
All items below are host-verifiable (fixable and unit-testable offline, no device/emulator).

### P1

- [ ] P1 — URI-permission grants are silently dropped on restore (modern-Android round-trip)
  Why: on current Android, captured grants carry `userHandle == USER_NULL (-1)` (the legacy
  `ATTR_USER_HANDLE` is absent), `UriGrant.flattenToString` writes `-1` at field 2, and restore's
  `unflattenFromString` runs `parseNonNegativeInt(parts[2])` which rejects negatives — so the
  rule line throws and the loader silently skips every URI grant. Backup reports success; the
  grant data class is lost on restore.
  Evidence: uri/UriManager.java:137-138 (USER_NULL default), :206 (serialize field 2), :219 + parseNonNegativeInt (rejects -1); rules/struct/UriGrantRule.java:25 (tokenizer ctor); rules/RulesStorageManager.java:300-305 (per-line IllegalArgumentException skip).
  Touches: uri/UriManager.java (permit USER_NULL/-1 for the `userHandle` field only, or stop persisting/validating it since restore reconstructs user IDs), app/src/test/ (UriGrant round-trip test).
  Acceptance: `UriGrant.unflattenFromString(g.flattenToString())` round-trips a grant with `userHandle == -1` without throwing; a crafted `-1` rule line loads instead of being skipped; other negative fields still rejected.
  Complexity: S

### P2

- [ ] P2 — FilterPresetStore: one corrupt/forward-incompatible preset crashes the whole store
  Why: the per-entry guard catches only `JSONException`, but `new FilterItem(json)` →
  `FilterOption.setKeyValue`/`FilterOptions.create` throw unchecked `NumberFormatException`/
  `IllegalArgumentException` (bad numeric value, unknown/removed filter type, bad regex). These
  escape both catches, so `readMap()` throws and every caller (`all()`/`find()`/`hasAny()`/`save()`)
  crashes on access — a persistent feature-level DoS, contrary to the code's stated "do not crash /
  reset to empty" intent.
  Evidence: filters/preset/FilterPresetStore.java:188 (inner `catch (JSONException)`) vs FilterOption.java:126,132,142 (unchecked throws), FilterOptions.java:43 (unknown type); same latent gap in FilterItem.java:307-317 and FilterOption.fromJson:222-230.
  Touches: filters/preset/FilterPresetStore.java (widen inner catch to `JSONException | RuntimeException`), optionally harden `FilterOption.fromJson`, app/src/test/ (bad-value preset skipped, not thrown).
  Acceptance: a preset blob with one valid entry and one whose option value is non-numeric for an int/long/size key returns only the valid preset; no exception escapes `readMap()`.
  Complexity: S

- [ ] P2 — TBConverter: a corrupt backup icon aborts the whole Titanium Backup import
  Why: `readPropFile()` runs outside `convert()`'s try and decodes the icon with
  `Base64.decode(...)`, which throws unchecked `IllegalArgumentException` on malformed
  base64 (only `IOException` is caught), so one bad `app_gui_icon` fails an otherwise
  valid package import; the icon is best-effort everywhere else.
  Evidence: backup/convert/TBConverter.java:134 (call site outside try), :449-452 (decode),
  :455 (only IOException caught); contrast backup/convert/SBConverter.java backupIcon (best-effort).
  Touches: backup/convert/TBConverter.java (guard the icon decode → null on failure), app/src/test/ (TBConverterTest corrupt-icon fixture).
  Acceptance: a Robolectric test feeding a `.properties` fixture with invalid base64 `app_gui_icon` imports the package without throwing and populates the rest of the metadata; icon is null.
  Complexity: S

- [ ] P2 — IntentCompat: array extras with a trailing backslash corrupt on interceptor round-trip
  Why: `escapeComma` escapes `,`→`\,` but not the escape char, while `splitEscapedComma`
  splits on `(?<!\\),`, so an element ending in `\` (e.g. `["a\\","b"]`) flattens to `a\,b`
  and parses back as a single element `a,b` — silent data corruption in string/URI array extras.
  Evidence: intercept/IntentCompat.java:345 (split regex), :349-351 (escapeComma), :353-356 (unescapeComma).
  Touches: intercept/IntentCompat.java (escape `\` before `,`; unescape `\,` before `\\`), app/src/test/ (IntentCompatTest trailing-backslash round-trip).
  Acceptance: `flattenToString`/`unflattenFromString` round-trips `new String[]{"a\\","b"}` back to the identical array; existing comma round-trip tests still pass.
  Complexity: S

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
