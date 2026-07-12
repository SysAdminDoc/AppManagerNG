<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Research - AppManagerNG

Pass date: 2026-07-12 (second pass). Baseline: v0.6.5 (versionCode 13), compileSdk 37 /
targetSdk 36 / minSdk 21. The earlier pass today (upstream v4.1.0 + Android 17 re-diff) was
fully drained into shipped commits; this pass shifts from platform/competitor scanning to a
code-level correctness audit, the complete upstream v4.1.0 commit set, and dependency currency.

## Executive Summary

AppManagerNG is a GPL Android package-manager fork for power users who inventory, install,
freeze, archive, back up, restore, block, and audit apps across normal, root, ADB, Shizuku,
and Dhizuku paths. The codebase is mature and genuinely well-tested (SSRF/path-traversal/
checksum hardening, broad Robolectric coverage); the platform-facing and competitor-facing
opportunity surface is drained, and everything left in `Roadmap_Blocked.md` is device/external
gated. The remaining host-verifiable value is a short list of concrete parsing/serialization
correctness bugs, latent contract hazards, and one clean upstream port — not new subsystems.

Highest-value direction: close the two confirmed data-correctness bugs (backup import abort on
a corrupt icon; intent-extra array corruption on a trailing backslash), then harden the binary-XML
and search regex paths that are currently latent traps, then take the one drop-in upstream fix.

Top opportunities (priority order):
1. TBConverter: a single malformed Titanium-Backup icon aborts the entire package import (P2, data-import correctness).
2. IntentCompat: string/URI-array extras whose element ends in `\` corrupt on interceptor round-trip (P2, data correctness).
3. Port upstream `133b5acb7f`: correct Wireless-Debugging dialog copy + deep-link to the developer-options toggle (P3, clean drop-in).
4. AdvancedSearchView single-string regex overload: uncaught `PatternSyntaxException` + semantics divergence from the collection overloads (P3, latent crash trap).
5. Binary-XML `ByteBuffer.array()` ignores offset/limit and fails on direct/read-only buffers (P3, contract landmine).
6. `AndroidBinXmlDecoder` framework-package-block lazy static init is an unsynchronized data race (P3, thread-safety).
7. `DebloatObject.fillInstallInfo` overwrites instead of accumulating per-user state (P3, multi-user correctness; extract a pure, testable merge).

## Product Map

- Core workflows: app inventory/search/filter/sort, app details, APK install/export/verify,
  Activity Interceptor command generation, backup/restore/conversion (incl. Titanium/Swift
  Backup import), archive/freeze/unfreeze, component/app-op/permission rules, profiles + routine
  triggers, debloat guidance, file management, privilege health diagnostics.
- User personas: rooted power users, Shizuku/ADB/Dhizuku users without root, ROM/device
  maintainers, privacy auditors, APK/app developers, offline FLOSS users.
- Platforms and distribution: Android minSdk 21 / targetSdk 36 / compileSdk 37, FLOSS+FULL
  flavors, Java/Kotlin Android Views, Material Components 1.13.0, Gradle 9.6.1 / AGP 9.2.1,
  native + server helper modules, GPL-3.0-or-later.
- Key integrations and data flows: PackageManager/PackageInstaller, root/libsu, Shizuku, ADB
  pairing/local server, Dhizuku, app archiving, Room metadata, backup archives/manifests,
  OpenPGP/Bouncy Castle, tracker/library scanners, optional FULL-flavor network sources.

## Competitive Landscape

- **Upstream App Manager (v4.1.0, 2026-06-29):** the full post-baseline commit set (16 commits)
  is now accounted for. Three host-verifiable fixes were already handled (`836c7248ea` A17
  enumeration ported, `916eeb85d5` non-default-user inactive ported, `ca038d6611` choice-dialog
  ruled N/A). The only remaining clean drop-in is `133b5acb7f` (Wireless-Debugging copy +
  deep-link). Learn: upstream's log-viewer/scroll/highlight fixes are all coupled to its
  `ListAdapter`/`DiffUtil` migration (`8cf2c1ef11`/`69b28cbe51`) — a direction NG deliberately
  did not take. Avoid: porting those as cherry-picks; they are architecture-divergent rewrites,
  and their payoff (scroll feel, highlight) is device-gated anyway.
- **Titanium Backup / Swift Backup (import targets):** NG imports both formats. The lesson from
  their real-world archives is that a non-essential field (icon) must never fail a whole import;
  NG's `SBConverter.backupIcon` already treats the icon as best-effort but `TBConverter` does not
  (the bug below). Match the best-effort contract across both converters.
- **Hail / Canta / Neo Backup / LibChecker / Inure:** unchanged from the prior pass — the
  rule/policy layer the field converges on is already built here (profiles + `RoutinePackageChangeReceiver`
  + rules export/import + tags). No new net-new subsystem is warranted.

## Security, Privacy, and Reliability

- Verified (P2, data-import correctness): `backup/convert/TBConverter.java` calls `readPropFile()`
  at `convert():134` — outside the surrounding try — and `readPropFile()` decodes the icon with
  `Base64.decode(base64Icon, 0)` at line 451, which throws unchecked `IllegalArgumentException`
  on malformed base64 (the `catch` at 455 only handles `IOException`). A Titanium Backup with a
  valid APK + data but one corrupt `app_gui_icon` fails the whole package import. `backupIcon()`
  (line 157) and `SBConverter.backupIcon` already treat the icon as best-effort; `readPropFile`
  must too.
- Verified (P2, data correctness): `intercept/IntentCompat.java` `escapeComma` (line 350) escapes
  `,`→`\,` but not the escape char itself, while `splitEscapedComma` (line 345) splits on
  `(?<!\\),`. An array element ending in `\` (e.g. `["a\\","b"]`) flattens to `a\,b` and parses
  back as one element `a,b`. Reachable through the interceptor save/paste/share round-trip for
  `TYPE_STRING_ARR`/`TYPE_STRING_AL`/`TYPE_URI_ARR`/`TYPE_URI_AL`.
- Verified (P3, latent crash trap): `misc/AdvancedSearchView.java` single-string
  `matches(String, String, int)` (lines 334-346) runs the `SEARCH_TYPE_REGEX` case via
  `text.matches(query)` with no `try/catch`, unlike the two collection overloads which guard
  `PatternSyntaxException` and use `matcher.find()` (substring) rather than full-match. No current
  caller routes a regex here, so it is latent, but the divergent semantics + crash risk are a trap.
- Verified (P3, contract landmine): `apk/parser/ManifestParser.java:176`,
  `apk/parser/AndroidBinXmlDecoder.java:73`, and `apk/ApkUtils.java:255` read `ByteBuffer.array()`,
  which ignores `arrayOffset()`/`position()`/`limit()` and throws `UnsupportedOperationException`
  on direct/read-only buffers. Every current caller passes `ByteBuffer.wrap(byte[])` so it works
  today, but the public `decode(ByteBuffer,...)` API silently mis-reads a sliced/mmapped buffer.
- Verified (P3, thread-safety): `apk/parser/AndroidBinXmlDecoder.java:135-144`
  `getFrameworkPackageBlock()` lazily initializes a non-volatile static without synchronization;
  concurrent manifest parsing (per-app detail loaders, filters) can double-run the heavy framework
  table init and observe a partially published reference.

## Architecture Assessment

- `TBConverter`/`SBConverter` should share one best-effort icon contract: extract the
  base64→Bitmap decode into a guarded helper that returns null on failure, so neither converter
  can abort an import on a non-essential field. Unit-testable with a corrupt-icon `.properties`
  fixture (the existing `TBConverterTest` already drives real fixtures).
- `IntentCompat` escape/unescape should be a symmetric codec: escape `\` before `,`, unescape
  `\,` before `\\`. Extend `IntentCompatTest`'s existing comma round-trip cases with a
  trailing-backslash case (currently fails).
- Binary-XML entry points should read the buffer's logical window
  (`byte[] b = new byte[buf.remaining()]; buf.duplicate().get(b);`) instead of `array()`, and the
  framework-block init should be `synchronized`/holder-class/`volatile`-DCL.
- `debloat/DebloatObject.java:244-264` `fillInstallInfo` accumulates `mInstalled` with `|=` then
  overwrites it with `=` inside the icon block, and overwrites `mSystemApp`/`mFrozen`/`mLabel`
  per-iteration, so on a multi-user device the last-iterated user's state wins. It reads
  `AppDb`/`PackageManager`, so make the per-user merge a pure function and unit-test the
  accumulate-vs-overwrite semantics offline.
- Test/toolchain: Robolectric has no SDK-37 release yet (tracking `robolectric#11239`; 4.17 is
  snapshot-only), so the A17 enumeration branch stays compile-verified until 4.17 ships — a
  dependency-gated future, not actionable now.

## Rejected Ideas

- Port upstream log-viewer scroll/filter fix `936cb3021b`: rejected as a drop-in — it is built on
  upstream's `ListAdapter`/`DiffUtil` migration that NG deliberately did not adopt; the fork's
  `LogViewerRecyclerAdapter extends MultiSelectionView.Adapter` has no `submitList`/`ItemCallback`,
  so it is an architecture-divergent rewrite whose real payoff (autoscroll feel) is device-gated.
  (Source: upstream `936cb3021b`, `8cf2c1ef11`, `69b28cbe51`.)
- Port upstream `daa54ac02b` (custom-expression filter-profile fetch): rejected as already fixed —
  NG's `MainViewModel.filterItemsByFlags` already sums `getTimesUsageInfoUsed()` across the flag
  and profile-membership filters and applies both per item. (Source: upstream `daa54ac02b`;
  main/MainViewModel.java.)
- Port upstream ListAdapter migration / scroll-restore / highlight (`8cf2c1ef11`, `69b28cbe51`,
  `54180381e3`, `886ad90d31`): rejected — reverses a deliberate fork architecture decision across
  ~50 files for device-gated payoff. (Source: those commits.)
- Dependency bumps for currency: rejected as roadmap items — every pin is already at its minSdk-21
  ceiling (Room 2.7.2, WorkManager 2.10.5, Material 1.13.0, Biometric 1.4.0-alpha04 are hard
  ceilings; Gson 2.14.0, zstd-jni 1.5.7-11 already latest). The only headroom is androidx.core
  1.17→1.18 (compileSdk gate met), but NG consumes none of 1.18's new APIs (PiP-UI-state, projected
  notifications), so it is a no-consumer currency bump; core 1.19 raises core-ktx minSdk to 23
  (blocked). BouncyCastle 1.85 (2026-07-12) is all security hardening (excluded per prior clean
  audit). (Source: AndroidX/Material/BC release notes.)
- Compose / Material 1.14 migration: rejected — CONTRIBUTING forbids Compose; minSdk-21 policy
  pins Material 1.13.0. (Source: CONTRIBUTING.md, docs/policy/minsdk-21-ceiling.md.)

## Sources

Upstream commits:
- https://github.com/MuntashirAkon/AppManager/commit/133b5acb7f
- https://github.com/MuntashirAkon/AppManager/commit/936cb3021b
- https://github.com/MuntashirAkon/AppManager/commit/daa54ac02b
- https://github.com/MuntashirAkon/AppManager/releases/tag/v4.1.0

Dependencies:
- https://developer.android.com/jetpack/androidx/releases/core
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/work
- https://github.com/material-components/material-components-android/releases
- https://github.com/robolectric/robolectric/issues/11239
- https://github.com/bcgit/bc-java/blob/main/docs/releasenotes.html

Code (this repo):
- app/src/main/java/io/github/muntashirakon/AppManager/backup/convert/TBConverter.java
- app/src/main/java/io/github/muntashirakon/AppManager/intercept/IntentCompat.java
- app/src/main/java/io/github/muntashirakon/AppManager/misc/AdvancedSearchView.java
- app/src/main/java/io/github/muntashirakon/AppManager/apk/parser/AndroidBinXmlDecoder.java
- app/src/main/java/io/github/muntashirakon/AppManager/apk/parser/ManifestParser.java
- app/src/main/java/io/github/muntashirakon/AppManager/debloat/DebloatObject.java

## Open Questions

- None that block the items above — all are host-verifiable against existing test infrastructure.
  The only external dependency is Robolectric 4.17's SDK-37 support (tracked upstream), which gates
  runtime-level testing of the already-shipped A17 enumeration branch, not any item in this pass.
