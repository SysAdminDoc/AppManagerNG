# Changelog

All notable changes to AppManagerNG are documented in this file.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

### Added — Package visibility chip in App Details (NF-11, 2026-05-25)

- App Details info card now surfaces a package-visibility tag-cloud chip
  for any app that holds `QUERY_ALL_PACKAGES` or declares a non-empty
  `<queries>` manifest block. The chip is failure-coloured for
  `QUERY_ALL_PACKAGES` and caution-coloured for targeted `<queries>` so
  the difference between an app that can enumerate everything vs. an
  app with narrow targets is visible at a glance.
- Tapping the chip opens a scrollable dialog listing the queried
  packages and intent actions, plus a "Find callers" action that
  performs an O(N) inverse scan across installed apps to surface every
  package whose own `<queries>` block lists this one.
- Pure-JVM coverage in [`PackageVisibilityInfoTest`](app/src/test/java/io/github/muntashirakon/AppManager/details/info/PackageVisibilityInfoTest.java).

### Added — Multi-tag store + Finder filter (NF-08 data layer, 2026-05-25)

- New `AppTagStore` is a SharedPreferences-backed multi-tag store for
  installed apps. Tags are normalised on write (trim, lower-case, restrict
  to `[a-z0-9_-]` with an alphanumeric first character, 32-char cap) and
  persisted as a per-package JSON array.
- New `TagsOption` Finder predicate registers under the `tags` key and
  supports `any`, `none`, `has_all`, `has_any`, `missing_all` selectors —
  so saved filter chains can target tagged sets without touching the App
  Details UI yet.
- Pure-JVM coverage in [`AppTagStoreTest`](app/src/test/java/io/github/muntashirakon/AppManager/tags/AppTagStoreTest.java)
  pins normalisation, dedup, role-back-down-to-empty, cross-package
  rollup, and persistence round-trip.
- Follow-up: App Details tag editor + main-list tag filter chip + Room
  migration (when the JSON-only shape stops scaling).

### Added — Tracker blocking intensity (NF-07, 2026-05-25)

- Settings -> Privacy -> "Tracker blocking intensity" picks which detected
  tracker categories are blocked when "Block trackers" runs.
- Three tiers: **Detect only** (block nothing), **Standard** (block ad /
  analytics / identification, leave crash / push / location / social SDKs
  intact), **Strict** (block every detected tracker, matches the
  pre-v0.5.x behaviour, default).
- The intensity is honoured by both the batch `OP_BLOCK_TRACKERS` op and
  the installer's "Block trackers after install" toggle.
- Pure-JVM coverage in [`TrackerBlockingIntensityTest`](app/src/test/java/io/github/muntashirakon/AppManager/rules/compontents/TrackerBlockingIntensityTest.java).

### Changed — Per-app rollback now shows a dry-run preview (2026-05-25)

- App Details -> "Revert AppManager changes" now opens a multi-choice
  dialog listing every inverse `BatchOp` the planner produced so the user
  can untick rows before confirming. Closes EI-09 of the 2026-05-25 plan.
- `PerAppRollbackManager.start(Context, RollbackPlan, boolean[] keep)` is
  the new entry point; the existing single-arg variant delegates so older
  callers behave unchanged.

### Added — Onboarding "Next steps" tiles on the final card (2026-05-25)

- The replayable onboarding wizard now closes with three outlined-button
  tiles guiding the first-run user to the next high-value surface:
  Settings -> Privileges (Mode Doctor home), Permission Inspector, and
  Settings -> Backup/Restore. Tapping a tile dismisses the sheet, marks
  onboarding as shown, and launches the destination.

### Added — Keystore password lifecycle invariant test (2026-05-25)

- New `KeyStorePasswordLifecycleTest` pins the T3 keystore-password
  contract: methods and fields under `KeyStoreManager` whose names look
  password-related must use `char[]`, not `String`, so
  `Utils.clearChars(char[])` can zero the buffer afterwards. A drive-by
  refactor that reintroduces a `String` password parameter now breaks
  the build in `tests.yml`.

### Changed — Mode Doctor can share results with a Support info bundle (2026-05-25)

- Settings -> Privileges -> Mode Doctor result dialog now offers a "Share
  with bundle" action that prepends the PASS/WARN/FAIL/SKIP probe report
  to a freshly-generated `SupportInfoBundle` text file and launches the
  system share chooser, replacing the previous copy-and-collect dance.
- `SupportInfoBundle.writeTextBundle(Context, CharSequence)` accepts an
  optional preamble that callers can use to inline any free-form section
  before the standard environment dump.

### Changed — Onboarding re-checks capability badges on resume (2026-05-25)

- The replayable onboarding wizard now re-runs `refreshCapabilityStatuses`
  in `onResume`, so a user who jumps into Developer options to toggle USB
  debugging or Wireless debugging and returns sees the fresh state
  without manually tapping Re-check.

### Added — Settings -> About -> Glossary & how-to (2026-05-25)

- New `GlossaryPreferences` screen lists 14 plain-language explainers
  covering Mode of operations, Shizuku, root, ADB, app ops, freezing,
  component blocking, trackers, debloater, backup encryption, scheduled
  backup, Pro Mode, Finder, and Intent Interceptor.
- Each row opens a scrollable dialog with the full topic body.
- Topics are automatically searchable through the global Settings search
  shipped in v0.5.0 because the glossary XML registers as a new source in
  `SettingsSearchIndex`; searching "Shizuku" or "AES" lands on the right
  glossary row.
- Pure-JVM coverage in [`GlossaryPreferencesTest`](app/src/test/java/io/github/muntashirakon/AppManager/settings/GlossaryPreferencesTest.java)
  pins key->body mapping integrity and the search reach.

## v0.5.0 — 2026-05-25

Discovery & Polish release. Banks the Iter-91 → Iter-142 work that
accumulated after v0.4.2 (2026-05-13). Highlights:

- **Discovery** — Settings → About → "What's new" entry point, automatic
  release-notes display after in-place updates, global in-app Settings
  search wired through the existing toolbar SearchView, and a NG-native
  bundled changelog so the in-app viewer no longer ships upstream
  v4.0.5 release notes.
- **Backup engine** — AES backup metadata v7 derives per-archive AES-GCM
  content keys from the single `am_keystore.bks` master key through
  HKDF-SHA256, CIFS/SMB streaming hardening, scheduled-backup freshness
  gate, scheduled-backup launcher shortcut, scheduled-backup progress
  notifications with API-36 ProgressStyle, default-app role rebind after
  restore, backup path-exclusion globs, root-only Android System data
  backups, provider-backed Network destination, profile blocklist
  enumerates backup roots.
- **Installer** — Privilege cascade (ADB → Shizuku → root) with route
  chips and Dhizuku / MIUI diagnostics, split-APK cert-mismatch dialog,
  batch APK install from File Manager, installer SHA-256 checksum
  confirmation dialog, sensitive-action authentication gate for
  install / uninstall / clear-data, Android 17 cleartext-traffic
  deprecation warning, Android 17 ML-DSA key algorithm display.
- **Rules** — New Settings → Rules → "Component rules" preview screen,
  per-app rollback planner (App Details → "Revert AppManager changes"),
  settings snapshot bundle portability v2 (rule TSVs included), OEM
  uninstall-blocker bypass (Samsung One UI 8.5 SmartSuggestions / MIUI
  core / OPlus uninstall-guarded packages), debloat-definition auto-update.
- **Privileges** — KernelSU diagnostics (seccomp / sulog / App Profile),
  Magisk `--drop-cap` policy surface, Dhizuku Provider detection,
  Restricted Settings unlock walkthrough, OS-revert detection banner +
  Doze allowlist diff, capability-dropping probe, persistent ADB
  `tcpip 5555` detection + reuse in the onboarding Wireless ADB setup
  branch.
- **Filters & Finder** — Install-date filter + filter-applied chip,
  permission-flags filter, Finder relevance scoring, backup-only Finder
  results, tracker-name search, per-app language picker, `pm hide`
  toggle, SELinux context display, audio-volume AppOps preset.
- **Automation** — Tasker-parameterised `am://` intents, signature-gated
  broadcast API, Quick Settings freeze tile, profile import (Canta /
  UAD-NG / Hail), Material You / Monet AppWidget theming, assistant
  quick actions on `ACTION_ASSIST`.
- **File Manager** — Recursive in-folder search, ZIP create / extract
  with zip-slip guard, smali decode-level option (`none` / `basic` /
  `verbose`).
- **Distribution & build** — AGP 9.2.0 / Gradle 9.4.1 migration with
  Gradle-10-safe build scripts, F-Droid 2.0 ROM preseed templates,
  LocalServer bootstrap smoke test, Support Info Bundle composer.
- **Permission monitoring** — Permission Change Monitor and Signing-Cert
  Change Alert (both opt-in, default OFF).
- **Security** — Deep-link parser crash / validation-bypass fix,
  install-transcript URI redactor hardening, onboarding root-manager
  probe fragment-detach fix, OperationHistoryExporter CSV / formula
  injection defuse, static launcher shortcut export hardening, GitHub
  Actions script-injection mitigations.

Full per-slice notes follow.

### Added — Global Settings search (2026-05-25)

- The Settings toolbar SearchView is now wired to an in-app preference index
  that walks every static `preferences_*.xml` file under `res/xml/` and
  matches `app:title` / `app:summary` against the live query.
- Each result row shows the parent breadcrumb (Appearance / Privacy /
  Privileges / Installer / Backup / ...) and the matching preference's title
  and summary; tapping the row navigates through the existing
  `am://settings/<parentKey>/<targetKey>` deep-link path so the user lands
  on the matching preference within the parent fragment.
- New `SettingsSearchIndex` is a singleton built lazily on the first query
  and invalidated on locale change. Pure-JVM coverage in
  [`SettingsSearchIndexTest`](app/src/test/java/io/github/muntashirakon/AppManager/settings/SettingsSearchIndexTest.java).
- Runtime-built preference rows (ComponentRulesPreferences, ModeOfOpsPreference,
  ChangeLanguageFragment) are intentionally not indexed because their rows do
  not exist until the fragment is created.

### Added — Settings -> About -> What's new + auto-display after update (2026-05-25)

- New Settings -> About -> "What's new" preference opens the in-app changelog
  viewer directly, instead of relying on tapping the version row.
- After an in-place app update, the main screen now surfaces the latest
  release block once via the existing changelog dialog using
  `ChangelogAutoDisplay`. Fresh installs do not see the dialog; the auto-display
  flag is consumed atomically so screen rotation cannot redisplay.
- Pure-JVM coverage in [`ChangelogAutoDisplayTest`](app/src/test/java/io/github/muntashirakon/AppManager/changelog/ChangelogAutoDisplayTest.java).

### Changed — NG-native bundled changelog + doc drift fixes (2026-05-25)

- Replaced `app/src/main/res/raw/changelog.xml` with NG-native content covering
  v0.1.0 -> v0.4.2 plus a note that `Unreleased` work lands as v0.5.0; the parser
  no longer references the upstream DTD URL at runtime.
- README `Roadmap` block now points at the active backlog
  (`RESEARCH_FEATURE_PLAN_2026-05-25.md`), marks v0.5.0 as in flight with the
  Iter-91 -> Iter-142 batch summary, and bills v0.6.0 as Rootless Power.
- `CLAUDE.md` `Status` section now points at `PROJECT_CONTEXT.md` /
  `ROADMAP.md` / `CHANGELOG.md` / the iter research dirs for current state
  instead of stopping at factory-iter-7.

### Added — Settings -> Rules -> Component rules (2026-05-25)

- New Settings -> Rules -> "Component rules" entry opens a read-only viewer that
  scans every package with an AppManagerNG rule file plus the active IFW XML
  directory and lists the matching packages.
- Each row shows a per-package breakdown of IFW entries, disabled-only entries,
  provider entries, and pending-write entries; tap to see a generated
  Blocker-style IFW XML preview plus a copyable component-rule listing.
- "App info" jumps straight to the App Details Components tab for visual edits;
  "Copy" places the IFW XML on the clipboard for export to root tools.

### Added — Persistent ADB tcpip setup path (2026-05-18)

- Onboarding now probes `127.0.0.1:5555` for an existing fixed
  `adb tcpip 5555` session.
- When reachable, the ADB-over-TCP card shows a **Use tcpip 5555** action and
  the Wireless ADB setup path offers the existing session before pairing.
- The action switches to ADB-over-TCP, pins port 5555, and reuses the existing
  LocalServer ADB connection flow.

### Changed — AES backup archive-key derivation (2026-05-18)

- New AES-mode backups now use metadata version 7 and derive a per-archive
  AES-GCM content key from the single `am_keystore.bks` AES master key plus the
  archive IV.
- Restores remain backward compatible: v6-and-older AES backups keep the
  historical master-key path, while v7+ backups use HKDF-SHA256.
- The HKDF helper preserves 128-bit vs 256-bit master-key length and leaves
  RSA/ECC hybrid per-archive keys unchanged.

### Changed — Android 17 ML-DSA key algorithm display (2026-05-18)

- Package Info public-key rows and signer verification logs now map Android 17
  `ML-DSA`, `ML-DSA-65`, and `ML-DSA-87` key algorithm strings to readable
  ML-DSA / Dilithium labels.
- The mapping uses local string constants until compile SDK 37 exposes
  `KeyProperties.KEY_ALGORITHM_ML_DSA*`, preserving the current SDK 36 build.
- Focused JVM coverage now verifies both certificate OID and key algorithm
  fallback behavior.

### Changed — AGP 9.2 build migration (2026-05-18)

- Android Gradle Plugin is now pinned to 9.2.0 with Gradle wrapper 9.4.1 and
  NDK 28.2.13676358.
- Build scripts use Gradle-10-safe assignment syntax, the server jar packaging
  task now uses `androidComponents` / `sdkComponents`, generated `resValue`
  support is explicit, and the app uses the optimized default ProGuard file.
- Host unit tests now declare their JVM `org.json` and hidden-API test
  classpath explicitly, with small runtime hardening for MIUI op names and log
  setup under non-Android JVM tests.

### Added — Split APK signing mismatch dialog (2026-05-18)

- Split bundle installs now preflight selected split signing certificates against
  the base APK before the package installer session is launched.
- A Material mismatch dialog lists each bad split's name, version, signing cert
  SHA-256, and mismatch reason.
- Users can remove checked optional bad splits, keep required splits blocked
  from removal, or explicitly continue anyway for verification-disabled
  workflows.

### Added — Installer privilege cascade (2026-05-18)

- The installer confirmation dialog now previews the detected install route with
  chips for the primary path, fallback providers, Dhizuku detection, and MIUI /
  HyperOS optimization checks.
- Install attempts temporarily try ADB, then Shizuku, then root package-session
  privileges when the active mode cannot install directly, then restore the
  user's configured mode after the attempt.
- Installer progress notifications now report the provider being tried before
  falling back to Android's normal confirmation, with focused route-order tests.

### Added — Audio-volume AppOps preset (2026-05-18)

- AppOps now exposes the Android audio-volume op family as named constants,
  including media, ring, alarm, notification, Bluetooth, voice, master, and
  accessibility volume.
- App Details -> App ops has a dedicated action to apply one mode to all
  supported audio-volume ops for the selected package, using the existing
  UID-scoped `IAppOpsService` mode path.
- Added focused coverage for supported audio-volume op enumeration, fallback
  labels, and UID-mode routing.

### Added — Root-only Android system data backups (2026-05-18)

- Root/system mode now exposes a **System data** backup category for the
  Android System package.
- System-data backups include supported Wi-Fi, Bluetooth, and account database
  roots while reusing the existing tar, encryption, checksum, metadata, and
  retention pipeline.
- System-data restores skip Android framework package data clearing and restore
  only the captured system roots.

### Added — Profile blocklist backup-root enumeration (2026-05-18)

- The Profiles app/blocklist picker now merges installed packages with validated
  latest backup metadata, so packages represented only by an existing backup
  root can be selected again after uninstall.
- Backup-only picker rows are explicitly labelled and avoid duplicating live
  installed apps that also have backups.
- Existing profile rows with no live app metadata now render a fallback icon and
  keep the delete affordance available, so stale blocklist entries remain
  manageable.

### Changed — CIFS/SMB backup streaming hardening (2026-05-18)

- Backup archive creation now writes split tar streams in bounded 256 KiB
  provider chunks instead of handing large compressed buffers directly to SAF
  destinations.
- APK, data, and keystore backup archives now use a durable split-stream path
  that opens a file descriptor when available, flushes and fsyncs each chunk,
  and verifies the final split-file byte count on close.
- Split stream tests now preserve the original large APK split-hash fixture and
  add a boundary regression that writes one oversized provider buffer, re-reads
  every split, and verifies byte-for-byte reconstruction.

### Changed — scheduled backup freshness gate (2026-05-18)

- Scheduled Auto-Backup now skips only packages whose newest valid backup is
  still inside the configured freshness window, rather than treating backup
  existence as enough.
- Settings -> Backup now exposes a "Skip if backup is newer than" control with
  a one-day default and an "Always back up" opt-out.
- Scheduled-backup result notifications now report how many recent backups were
  skipped alongside normal success or partial-failure counts.

### Added — Default-app role restore rebinds (2026-05-18)

- Backup metadata now records whether a package held the Phone, SMS, Home, or
  Browser default-app role at backup time.
- Restore now attempts to rebind those roles to the restored package/user via
  Android's privileged `cmd role add-role-holder` path after package restore.
- Batch restore completion now exposes a **Review defaults** notification action
  when Android rejects an automatic rebind, opening the system Default apps
  settings with the affected apps and roles listed.

### Changed — Material You widget theming (2026-05-18)

- Existing home-screen widgets now resolve a shared dynamic AppWidget palette
  from the themed Material context and apply RemoteViews tints at update time.
- Screen-time usage bubbles now use primary, secondary, and tertiary container
  roles instead of hard-coded colors.
- Data Usage, Clear Cache, and Log Recording widgets now tint their surfaces,
  icons, and text from the same widget palette, with Android 12+ XML preview
  fallbacks backed by system Monet color resources.

### Added — App Info cleartext deprecation badge (2026-05-18)

- App Info now shows a caution tag when a package opts into manifest-wide
  cleartext traffic without declaring a Network Security Config.
- The tag explains Android 17's Android-18 deprecation warning and recommends
  scoping any required HTTP traffic to explicit domains.
- Added hidden-API access for `ApplicationInfo.networkSecurityConfigRes` and a
  focused unit test for the cleartext/no-config warning predicate.

### Compliance — Android 17 ML-DSA certificate names (2026-05-18)

- Closed the Android 17 ML-DSA certificate OID roadmap row as already
  implemented: certificate display already maps ML-DSA-65 and ML-DSA-87 OIDs to
  readable names through `Utils.getCertificateSignatureAlgorithmName()`.
- Confirmed Package Info and Scanner both consume the shared certificate
  algorithm helper while still displaying the canonical signature OID.
- Removed an unrelated `Utils` class-load dependency on `OsEnvironment` so the
  ML-DSA mapping regression test runs in the JVM unit-test runner.

### Compliance — Android 17 targetSdk 37 audit batch (2026-05-18)

- Declared the Android 17 `ACCESS_LOCAL_NETWORK` permission and added a
  targetSdk/runtime gate before Wireless ADB mDNS discovery tries to locate
  pairing or connect services.
- Added a local-network permission blocker dialog for startup, Settings,
  onboarding, and background Wireless ADB reconnect paths.
- Legacy-gated the remaining `Resources.mSystem` static-final reflection
  workaround to pre-API 37 devices, preserving the old LG ROM fix while avoiding
  Android 17's static-final write ban.
- Documented the targetSdk=37 audit batch for local-network access,
  static-final reflection, Android Keystore entry caps, MemoryLimiter exit
  reasons, native DCL, and `IntentSender` BAL hardening.

### Documentation — F-Droid ROM preseed templates (2026-05-18)

- Added a ROM-builder guide for F-Droid 2.0 repository pre-seeding, including
  the new JSON placement paths and legacy XML transition guidance.
- Added checked-in JSON and XML templates for AppManagerNG-compatible F-Droid
  repository preseeding, with explicit placeholder warnings for the future
  production repo URL and repo signing certificate.
- Linked the ROM preseed guide from the README install/distribution section.

### Added — Sensitive action authentication gate (2026-05-18)

- Settings -> Privacy now has an optional "Require authentication for app
  changes" toggle for package/data-changing operations.
- When enabled, installer commits, direct uninstall/update-removal, clear-data,
  main-list batch uninstall/clear-data, quick uninstall, and one-click orphan
  data cleanup require Android screen lock through `BiometricPrompt`.
- The action gate is independent from the existing app/session lock, so users
  can protect destructive operations without requiring authentication on every
  AppManagerNG launch.

### Added — Per-app language picker (2026-05-18)

- App Info now shows the selected package/user's Android 13+ per-app language,
  including a System default state when no override is set.
- Privileged sessions can change that language from an inline searchable picker
  backed by the hidden `ILocaleManager` service path.
- The hidden API baseline now tracks `android.app.ILocaleManager`, and focused
  tests cover locale option de-duplication plus display summaries.

### Added — App Info hide/unhide action (2026-05-18)

- App Info now exposes a dedicated Hide/Unhide quick action for `pm hide`
  semantics when the active privilege path has `MANAGE_USERS`.
- The action calls AppManagerNG's existing hidden-package helper directly,
  refreshes App Info after success, and confirms before hiding AppManagerNG
  itself.
- The existing Hidden status badge remains the state indicator, keeping hide
  separate from disable, suspend, and saved freeze-method choices.

### Added — Quick assist action sheet (2026-05-18)

- Registered a dedicated `android.intent.action.ASSIST` activity so the
  Digital Assistant gesture can open AppManagerNG's foreground-app quick sheet.
- The assist flow resolves the target package from platform assist extras first,
  then falls back to recent usage events when Usage Access is available.
- The quick sheet offers Force Stop, Freeze/Unfreeze, and Open app details
  actions according to the active privilege path, with a Running Apps fallback
  when no foreground target can be identified.

### Changed — Roadmap triage (2026-05-18)

- Parked the Squashfs writer header-validation row after confirming the live
  backup engine has no SquashFS writer or dependency; NG currently writes
  tar-family archives through `TarUtils`, so a magic-byte regression test is
  reserved for a future backend that actually emits SquashFS images.
- Parked the two JADX 1.5.5 viewer-follow-up rows until NG has the planned
  T12 JADX viewer or external JADX handoff surface to attach `.apks` ingestion,
  UI zoom, and FlatLaf CJK-font behavior to.

### Added — App Info SELinux context display (2026-05-18)

- App Info now surfaces SELinux policy info, file contexts for the app's data
  and source paths, and live process contexts for running package processes.
- Process contexts are gathered from the running-process list and
  `/proc/<pid>/attr/current`; blank or unreadable contexts are omitted.
- Added focused coverage for package/process matching, normalization, and
  missing process-name fallback.

### Added — File Manager batch APK installer (2026-05-18)

- File Manager multi-selection now offers **Install selected APKs** for readable
  `.apk`, `.apks`, `.apkm`, and `.xapk` files.
- The action launches AppManagerNG's installer with a batch `ACTION_SEND_MULTIPLE`
  intent, content-URI grants, and the existing installer queue/service path.
- Batch install auto-selects required plus best-matching supported split APK
  entries, while existing destructive reinstall/signature safety prompts remain
  in place.

### Added — KernelSU app profile visibility (2026-05-18)

- Settings -> Privileges now shows the active KernelSU `su` App Profile shape:
  UID, GID, groups, SELinux context, and effective capabilities.
- KernelSU diagnostics warn when the profile has reduced AppManagerNG's
  expected root capability set, including DAC, owner, setuid/setgid, and
  `CAP_SYS_ADMIN` capabilities.
- The KernelSU details dialog includes raw `id` output and missing-capability
  context next to the existing seccomp and sulog diagnostics.

### Added — smali decode options (2026-05-18)

- Settings -> File Manager now exposes smali comment-level options for decoded
  APK/dex classes: `none`, `basic`, and `verbose`.
- The default `basic` level keeps casual smali output quieter while preserving
  the repo's existing debug-build line-info behavior.
- Added an opt-in smali post-processor that strips common `@Nullable`,
  `@NotNull`, and `@RequiresApi` annotation blocks from decoded output.

### Added — Android full-SDK version helpers (2026-05-18)

- Added `AndroidUtils.sdkAtLeast(int major, int minor)` plus full-SDK
  encoding helpers so Android 16.1+ gates can use `SDK_INT_FULL` semantics
  from one call site.
- Migrated the current Android 16 scheduled-backup ProgressStyle and
  JobScheduler diagnostics gates from raw `SDK_INT >= 36` checks to the helper.
- Existing older major-version guards remain unchanged because they do not
  distinguish minor Android releases.

### Added — KernelSU sulog/seccomp diagnostics (2026-05-18)

- Settings -> Privileges now shows a KernelSU status row when KernelSU is the
  active root provider.
- The diagnostic reads AppManagerNG's current seccomp mode with
  `prctl(PR_GET_SECCOMP)` and tails `/data/adb/ksu/log/sulog` for recent
  denial-style entries.
- The details dialog is copyable and offers an Open action for KernelSU Manager
  or KernelSU Next when a manager launcher is visible.

### Added — Magisk drop-cap diagnostics (2026-05-18)

- Settings -> Privileges -> Capability dropping now includes Magisk version and
  version-code context when Magisk is available to the active privileged shell.
- The diagnostic now samples a bounded `magiskpolicy --live --print-rules`
  output and reports whether relevant Magisk/capability policy rules were
  visible.
- Magisk v30.7+ is labeled with its explicit `su --drop-cap` opt-in capability
  behavior while the existing UID/CapEff probe remains the source-of-truth
  runtime state.

### Added — File Manager whole-volume scan warning (2026-05-18)

- File Manager now warns before a recursive search starts from a storage-volume
  root such as `/storage/emulated/0`, removable `/storage/XXXX-XXXX`, or
  `/mnt/media_rw/*`.
- The warning estimates scan duration from platform-reported volume size when
  available and requires explicit confirmation before the whole-volume walk
  begins.

### Added — File Manager recursive search (2026-05-18)

- File Manager now has a toolbar search action for filtering the current
  folder recursively.
- Active file searches show a clearable chip below the path bar, and empty
  search results offer a clear-search recovery action.
- Recursive results include containing-folder context in row subtitles and
  respect the existing hidden dot-file display option.

### Added — install-date filtering and active filter indicators (2026-05-18)

- Finder now exposes an `install_date` filter with date-picker backed
  `before` and `after` predicates.
- Main list filters now support a persisted install-date range from the quick
  filter strip and list-options sheet.
- Main list and Finder now show a clearable active-filter count chip whenever
  non-default filters are hiding apps.

### Changed — settings snapshot portability (2026-05-18)

- Snapshot exports now include blocking/freeze rule TSV files from
  AppManagerNG's rule configuration directory.
- Snapshot imports now merge SharedPreferences keys and rule rows into the
  current install instead of blindly replacing those local stores.
- Snapshot UI copy and completion toasts now account for rule files and clearly
  distinguish merged preferences/rules from overwritten profile/tag files.

### Added — per-app rollback from operation history (2026-05-18)

- App Details now exposes "Revert AppManager changes" for the selected package
  and user.
- The rollback planner builds newest-first inverse batch operations from
  successful operation-history rows for freeze, tracker/component toggles,
  explicit permission changes, AppOps changes, background-disable app-ops, and
  network-policy changes.
- Confirmation copy states that app data restore, reinstalling removed apps,
  backup deletion, and history rows without enough previous-state data remain
  manual review items.

### Added — OEM debloat uninstall fallbacks (2026-05-18)

- Debloater now detects OEM-protected removal targets for Samsung
  SmartSuggestions on One UI 8.5, MIUI core, and known OPlus/ColorOS/Realme
  uninstall-guarded package IDs.
- Protected selections are labeled in the list/details UI and the batch remove
  confirmation defaults to "Disable instead", routing those targets through the
  existing freeze batch path while leaving explicit removal available as an
  override.
- Added focused JVM coverage for Samsung, MIUI, and OPlus fallback policy
  matching.

### Added — Hidden-Shizuku fork detection (2026-05-18)

- Shizuku manager package discovery now resolves through the package declaring
  `moe.shizuku.manager.permission.API_V23`, with a legacy service-permission
  fallback before the canonical `moe.shizuku.privileged.api` package.
- Version checks, trusted-WLAN auto-start/app-info fallbacks, and clear-data
  authorization warnings now use the resolved manager package, so renamed
  Hidden-Shizuku-style forks work without weakening the binder trust model.

### Added — Tasker parameterized automation intents (2026-05-18)

- Public `am://` operation URIs now cover freeze, unfreeze, force-stop,
  clear-cache, clear-data, uninstall, backup, restore, component
  enable/disable, tracker scan, profile run, and installer handoff.
- Tasker/MacroDroid can start an AppManagerNG Activity with the existing
  `io.github.sysadmindoc.AppManagerNG.action.*` action constants and extras;
  the public path requires AppManagerNG authentication plus an explicit
  confirmation dialog before work starts.
- Profile automation supports one-run JSON overrides through
  `EXTRA_PROFILE_OVERRIDES` / `profile_overrides`, including package targets
  and backup flags, while preserving the saved profile identity.

### Added — backup path exclusions (2026-05-18)

- Backup data archives now support glob-style path exclusions such as
  `**/cache/**`, `**/.thumbnails/**`, and `databases/*.db-journal`.
- Default throwaway-folder exclusions are applied during data tar creation;
  nested cache defaults are active only when the existing Backup cache option is
  off.
- Settings -> Backup/Restore can store global custom exclusions, the manual
  backup dialog can add one-off exclusions for the current run, and profile
  backup configs persist `exclusion_globs` for routine/profile backups.

### Changed — WebDAV certificate-trust posture (2026-05-18)

- Closed the Material Files self-signed WebDAV certificate-trust roadmap row as
  covered by the provider-backed network destination path.
- SMB/WebDAV/SFTP/cloud backup destinations now rely on the selected
  DocumentsProvider app for TLS handshakes and user-installed CA trust.
- Native `KeyChain.getCertificateChain()` handling remains attached to future
  first-party WebDAV/provider work rather than the current SAF-backed backup
  engine.

### Added — provider-backed network backup destination (2026-05-18)

- Settings -> Backup/Restore now has a Network backup destination action for
  SMB, WebDAV, SFTP, and cloud folders exposed by user-installed
  DocumentsProvider apps.
- The selected tree is persisted with read/write SAF permission, normalized,
  and immediately stored as the active backup volume used by the existing
  backup engine.
- `StorageUtils.getFixedTreeUri` is now public and covered by focused JVM tests
  so callers can safely store tree/document URIs selected by Android's folder
  picker.

### Added — app-list import/export workflow (2026-05-18)

- Main-list overflow can export the current visible/filtered app list through
  the existing CSV, JSON, XML, and Markdown exporters.
- Selection mode continues to export only the selected apps, using the same
  format picker and SAF document flow.
- JSON app-list imports accept AppManagerNG export arrays plus wrapped
  `packages` / `apps` arrays, de-dupe valid package names, select matching
  installed apps, and surface the existing batch-operation toolbar.

### Added — scheduled backup diagnostics (2026-05-18)

- Settings -> Backup now shows WorkManager diagnostics for scheduled and manual
  auto-backup requests: state, run attempt, next run time, and stop reason.
- Android 16+ devices also show JobScheduler pending reasons and the most
  recent pending-reason history snapshot for the matching WorkManager job.
- API 37 `JobDebugInfo` pending-reason stats remain parked until the project
  raises compile SDK beyond 36.

### Changed — scheduled backup progress notifications (2026-05-18)

- Scheduled and manual auto-backup runs now update their WorkManager foreground
  notification with app-count progress and the current app being backed up.
- Progress text includes an ETA once enough runtime exists to estimate the
  remaining work.
- Android 16+ devices use platform `Notification.ProgressStyle` segments and
  current-progress point markers; older devices continue to use the standard
  progress-bar notification fallback.

### Added — scheduled backup launcher shortcuts (2026-05-18)

- Added a Settings -> Backup action to pin a "Run scheduled backup" home-screen
  shortcut.
- Added a static launcher shortcut for the same scheduled-backup run-now action
  in AppManagerNG's long-press launcher menu.
- Shortcut launches route through an authenticated no-UI activity before
  queuing the existing scheduled-backup manual WorkManager request.

### Changed — scheduled backup battery optimization guardrail (2026-05-18)

- Enabling Scheduled Auto-Backup now checks whether AppManagerNG is exempt from
  Android battery optimization.
- If the active privilege path can grant `DEVICE_POWER`, the schedule setup
  reuses `SelfBatteryOptimization.autoFixIfPossible()` to exempt the app
  without opening Android settings.
- If the privileged fix is unavailable or refused, Settings -> Backup shows a
  scheduled-backup-specific prompt that opens Android's exemption request.
- The schedule status row now shows the current battery state so users can see
  whether Android may pause scheduled backups while idle.

### Added — Scheduled auto-backup core (2026-05-18)

- Added a WorkManager-backed scheduled backup engine with a daily time-of-day
  trigger plus charging and network constraints.
- Settings -> Backup now exposes scheduled auto-backup enablement, time,
  require-charging, network condition, run-now, and last-run/result status
  controls.
- Scheduled runs use the existing backup engine over installed packages with
  the current backup options plus multi-backup naming, then record and notify
  success, partial success, or failure.
- Pinned WorkManager at 2.10.5 and compile against the existing Guava runtime
  for WorkManager's exposed `ListenableFuture` API while keeping AppManagerNG's
  API-21 floor.
- Added focused JVM coverage for daily delay rollover, schedule value clamping,
  and WorkManager network constraint mapping.

### Added — Dhizuku provider diagnostics (2026-05-18)

- Added a no-dependency Dhizuku provider probe that detects the installed
  manager version, official DeviceOwner/ProfileOwner component, API provider
  visibility, and AppManagerNG's Dhizuku API permission state.
- Settings -> Privileges and Mode Doctor now surface Dhizuku status without
  linking the Dhizuku-API AAR, preserving AppManagerNG's API-21 floor while the
  upstream API remains minSdk 26.
- The onboarding capability sheet now shows a Dhizuku status signal when a
  Dhizuku install or active owner is present.
- Added focused JVM coverage for the Dhizuku package/provider/action constants
  and Android version guardrails.

### Added — File Manager ZIP archive actions (2026-05-18)

- Added File Manager actions to create a ZIP archive from the current selection
  and extract a selected ZIP archive to a folder.
- Archive operations use the existing File Manager progress dialog pattern with
  cancellation, success/error feedback, and destination reload.
- ZIP extraction now prompts on file conflicts with replace, keep-both, skip, or
  stop choices, and rejects unsafe archive entries such as `../` zip-slip paths.
- Added focused JVM coverage for ZIP round-trip extraction, keep-both naming,
  and malicious-entry rejection.
- Restored local app JVM-test execution by mirroring the Shizuku provider
  `tools:overrideLibrary` allowance into the test manifest and fixing three
  stale Java compile blockers found during verification.

### Added — Restricted Settings unlock walkthrough (2026-05-17)

- Added a Settings -> Privileges row for Android 13+ Restricted Settings,
  classifying AppManagerNG's install source as trusted-store, likely sideloaded,
  unknown, or review-recommended.
- The row opens a walkthrough with App info and Accessibility deep-links plus
  copy for the Allow restricted settings / long-press disabled-toggle unlock
  path when Android blocks sensitive service toggles.
- Mode Doctor now includes the same install-source probe and support-ready fix
  hint. The detector is phrased as likely/recommended because Android exposes
  install-source metadata, not a public per-app restricted-settings-blocked bit.
- Added unit coverage for the install-source classification rules.

### Added — Achno Samsung debloat cross-check audit (2026-05-17)

- Added [`docs/audits/2026-05-17-achno-samsung-debloat-cross-check.md`](docs/audits/2026-05-17-achno-samsung-debloat-cross-check.md).
- Compared 82 package-like tokens from `Achno/debloat-samsung-ADB-shizuku`
  against the combined local debloat lists; 76 were already covered.
- Rejected the six exact misses as apparent typos, non-package activity names,
  or uncorroborated single-source IDs, so no debloat dataset mutation shipped.

### Changed — Doze allowlist revert diagnostics (2026-05-17)

- Doze allowlist revert checks now wait 60 seconds and include a one-line
  `device_idle_constants` diff in the "OS reverted your change" detail dialog.
- The same Doze diagnostic also snapshots the newer `DeviceConfig device_idle`
  namespace when available, so Android builds that moved device-idle tuning out
  of the legacy global setting still get useful config context.
- Added policy hints for user-app allowlist rejection, Samsung Knox / Device
  Care / enterprise policy, system-app reverts, and unknown-package cases.
- Added unit coverage for Doze config parsing and one-line diff generation.

### Added — OS-revert detection banner (2026-05-17)

- Added `OsRevertMonitor`, a generic 30-second post-write re-poll layer for
  state mutations that Android or OEM policy may silently undo.
- Doze allowlist changes, freeze/unfreeze, component enabled-state writes, and
  AppOps mode writes now schedule expected-vs-current verification through their
  central compat/helper paths.
- Active AppManagerNG screens now show an "OS reverted your change - see why"
  Snackbar when a re-poll finds a mismatch; the action opens target, operation,
  expected state, current state, and context-hint details.
- Added unit coverage for the monitor's state-match predicates.

### Changed — Shizuku root-backed avoidance (2026-05-17)

- Auto mode now skips root-backed Shizuku when local ADB is available, avoiding a
  KernelSU/root-backed Shizuku service becoming AppManagerNG's default privilege
  path on devices where banking or Play Integrity-strict apps react to that root
  surface.
- Settings -> Mode of Operation now labels Shizuku as root-backed, explains the
  banking-app side effect in the existing Shizuku hint row, and offers a one-tap
  switch into Wireless ADB or ADB-over-TCP.
- Onboarding, Settings -> Privileges, and Mode Doctor now surface the same
  root-backed Shizuku warning so users do not mistake it for a fully rootless
  Shizuku session.
- Added unit coverage for Shizuku uid classification and the Auto-mode avoidance
  gate.

### Added — Shizuku 13.6.0 OEM compatibility warning (2026-05-17)

- Added Shizuku 13.6.0 known-bad device detection for Transsion/Infinix/Tecno/Itel
  Android 15 ROMs, Mediatek platform tags, and Pixel 9 Android 16 QPR1-class
  builds.
- The detector compares installed Shizuku Manager version first, then falls back
  to the live Shizuku API version when package metadata is unavailable.
- Onboarding, Settings -> Operating Mode, Settings -> Privileges, and Mode Doctor
  now tell affected users to pin Shizuku 13.5.4 and link to the archived F-Droid
  repo APK.
- Added unit coverage for the version gate and each known-bad OEM/SoC family.

### Compliance — Android Developer Verification guardrails (2026-05-17)

- Added `DeveloperVerificationCompat` for `developer_verifier` service detection
  and Android 36.1 `PackageInstaller` developer-verification failure reasons.
- App Details now shows a verifier-status chip when Android exposes the verifier
  service; current public APIs report this as unknown until install result time.
- Installer confirmation warns before commit when the verifier service is active,
  including split-APK chooser flows that do not use the stock confirmation body.
- Install result dialogs and copied diagnostic transcripts now append Android's
  developer-verification failure reason when `PackageInstaller` returns one.
- Updated `docs/sideload-verification.md` to document the implemented behavior
  and the public API limit around verified/unverified preflight checks.

### Compliance — Android 17 16 KB native page-size compatibility (2026-05-17)

- Native `libam.so` builds now pass both `-Wl,-z,max-page-size=16384`
  and `-Wl,-z,common-page-size=16384` through CMake.
- App Details / scanner native-library rows now parse ELF program headers and
  show whether each `.so` has 16 KB-compatible `PT_LOAD.p_align` values.
- Reproducible release verification now runs
  `scripts/verify-native-page-alignment.py` against every publish APK and fails
  on misaligned ELF load segments, unverifiable `.so` entries, or uncompressed
  native ZIP entries that are not 16 KB data-offset aligned.
- Recorded the remediation and remaining device-test gap in
  `docs/audits/2026-05-17-android17-16kb-native-page-size.md`.

### Changed — operation history access and recovery guidance (2026-05-17)

- Added Settings -> Privacy -> History as a direct entry point into the existing
  operation-history screen.
- Reversible history rows now expose "Recovery guidance" from the row actions
  menu, using the saved rollback guidance already recorded in operation metadata.
- Confirmed the freeze/audit-log roadmap row is covered by the existing
  Room-backed history UI, filters, export/share, rerun preflight, and
  batch/profile/installer journals; automatic inverse replay remains tracked by
  the separate Per-App Rollback row.

### Added — Shizuku clear-data revoke warnings (2026-05-17)

- App Info clear-data confirmations now warn when clearing AppManagerNG,
  Shizuku Manager, or an installed app that declares the Shizuku provider.
- Main-list batch clear-data confirmations add the same warning when any
  selected package may lose or store Shizuku authorization state.
- Direct privileged clear-data now captures AppManagerNG's Shizuku permission
  before the operation, re-checks after success, and deep-links to Settings ->
  Mode of operation if the grant was revoked.
- Batch/profile clear-data logs the same post-action revocation signal for
  result review when no foreground re-authorization dialog is available.

### Added — hidden-API compatibility harness (2026-05-17)

- Added a checked-in androidTest hidden-API baseline at
  `app/src/androidTest/assets/api/api-versions-appmanagerng-hiddenapi.json`
  covering the current `hiddenapi/src/main/java` stub tree.
- Added `scripts/generate-hidden-api-baseline.ps1` so future AOSP stub refreshes
  can regenerate the baseline deterministically.
- Added JVM coverage to ensure the baseline covers every hiddenapi source file,
  plus an instrumented test that applies HiddenApiBypass exemptions, probes
  hidden classes/methods/fields on the active SDK, emits a JSON diff report, and
  fails on required missing APIs while treating deprecated removals as warnings.

### Added — Quick Settings freeze profile tile (2026-05-17)

- Added `QuickFreezeTileService`, a platform Quick Settings tile that runs the
  selected freeze-enabled profile through the existing `ProfileApplierService`.
- Profiles with the Freeze action enabled now expose "Use for Quick Settings
  freeze tile" from the profile-list popup; selected profiles can be cleared
  from the same menu.
- The tile is unavailable until configured, requires device unlock when tapped
  from the lock screen, and opens the Profiles screen when the selected profile
  is missing or no longer freeze-capable.
- Added JVM coverage for the freeze-profile eligibility gate.

### Changed — Apktool migration audit (2026-05-17)

- Re-audited the Gradle graph and source tree and confirmed AppManagerNG has no
  Apktool 2.x dependency, `org.apktool` dependency, or `brut.apktool` call site
  to migrate.
- Parked the Apktool 3.0.2 roadmap row behind a future T12 Apktool-backed
  decode/rebuild backend instead of adding an unused app dependency.
- Recorded the Maven Central risk: `org.apktool:apktool-lib:3.0.2` brings a
  second smali fork plus runtime dependencies that must be classpath-tested
  against NG's current Google smali/baksmali dependency before adoption.

### Changed — JobScheduler quota stop-reason audit (2026-05-17)

- Re-audited the source and Gradle configuration and confirmed AppManagerNG still
  has no `androidx.work`, WorkManager, JobScheduler, JobService, or Schedules
  screen implementation to instrument.
- Parked the Android 16 JobScheduler quota stop-reason row as a Scheduled
  Auto-Backup acceptance criterion instead of a standalone code item.
- Expanded the Scheduled Auto-Backup roadmap row to require WorkManager /
  JobScheduler stop and pending-reason output in schedule history and result
  notifications when the scheduler is implemented.

### Added — Shizuku trusted-WLAN auto-start action (2026-05-17)

- Operating Mode and the replayable onboarding Shizuku card now show a
  "Configure auto-start in Shizuku" action when Android 13+ has Shizuku Manager
  13.6.0+ installed but the Shizuku binder is stopped.
- `ShizukuBridge` centralizes the auto-start support/offer check and builds a
  best-effort launch intent: roadmap component first, Shizuku launcher fallback,
  Android app-info fallback.
- Added unit coverage for the Android-version, Shizuku-version, and stopped-binder
  gating rules.

### Added — mode doctor active probes (2026-05-17)

- Settings -> Privileges now includes a "Mode doctor" action distinct from the
  passive health summary rows.
- The doctor runs active probes for configured/inferred mode, root grant/root
  manager/Sui, Shizuku binder/UserService/permission, ADB USB/wireless/pairing,
  LocalServer `id -u`, SELinux domain, and ABI state.
- Results are shown as a copyable PASS/WARN/FAIL/SKIP report with fix guidance
  for the failing or incomplete provider path.

### Added — privileged batch journal and recovery (2026-05-17)

- Added `BatchOpsJournal`, a persistent intent/executing journal around
  `BatchOpsService` so interrupted batch operations survive process death until
  the app can reattach.
- While a Shizuku/Sui-backed batch is active, AppManagerNG registers a Shizuku
  binder-dead listener and marks the active journal interrupted if the binder
  dies.
- Main screen startup now checks for an unfinished batch journal when no batch
  service is currently working and shows a recovery dialog with retry, not-now,
  and clear actions.

### Added — privileged operation audit log closure (2026-05-17)

- Audited the existing Room-backed operation history as AppManagerNG's
  privileged operation audit log: it already has a viewer, search/filter/sort,
  rerun/share/delete flows, JSON/CSV/text export, and privacy retention controls.
- New operation-history metadata now records normalized `exit_code` values and
  the remembered LocalServer bootstrap signature when available.
- Operation details and JSON/CSV exports now include those fields so support
  reports can correlate a privileged action with the most recent LocalServer
  bootstrap context.

### Added — support-info bundle composer (2026-05-17)

- Settings -> Troubleshooting now has a "Share support info" action that writes
  a zero-network `support-info-<device>-<timestamp>.txt` file and shares it via
  the system chooser.
- The support file captures app/build version, Android/ROM details,
  Root/Shizuku/Sui/provider status, configured/inferred AppManagerNG mode,
  feature flags, and the last recorded LocalServer bootstrap signature.
- The logcat tail is limited to the latest 120 lines and scrubbed for package-like
  tokens, file/content/http URIs, storage paths, email addresses, UIDs, and large
  numeric identifiers before sharing.

### Added — LocalServer bootstrap smoke test (2026-05-17)

- Settings -> Privileges now includes a "LocalServer bootstrap smoke test" row
  that runs the LocalServer privileged-shell handshake plus an `id -u` probe for
  the current Root/Shizuku/ADB mode.
- The result is shown as a copyable single-line diagnostic signature containing
  device/build/mode/UID/LineageOS/probe fields; failures include the exception
  class, message, and direct cause.
- The existing LocalServer bootstrap-failure log now uses the same
  `LocalServer.buildBootstrapSignature(...)` formatter as the success-path smoke
  test, keeping issue reports consistent.

### Added — distribution build flavors (2026-05-17)

- Added default `floss` and optional `full` product flavors in the
  `distribution` dimension. `floss` compiles optional online surfaces off while
  preserving local ADB / localhost networking; `full` keeps the existing opt-in
  network features available.
- Wired the flavor flag through `FeatureController`, Settings -> Privacy,
  VirusTotal settings, Pithus lookups, and debloat-definition auto-updates.
- Updated release reproducibility scripts, CI artifact globs, Obtainium config,
  README install guidance, and the new
  [`docs/distribution/build-flavors.md`](docs/distribution/build-flavors.md)
  contract so F-Droid metadata targets `flossRelease` and GitHub/Obtainium
  users receive `full` assets.

### Added — per-app launcher action shortcuts (2026-05-17)

- Added dynamic launcher shortcuts for recent installed apps, exposing freeze,
  force-stop, and clear-cache actions only when the current Root/Shizuku/ADB
  privilege path supports the underlying operation.
- Added `AppActionShortcutActivity`, a non-exported authenticated shortcut
  handler that dispatches through the existing `FreezeUtils` and
  `PackageManagerCompat` operation helpers.
- App Details action chips can now pin force-stop and clear-cache shortcuts by
  long-pressing those actions; freeze/unfreeze shortcut defaults now use explicit
  `Freeze <app>` / `Unfreeze <app>` labels.

### Changed — APK share-target roadmap audit (2026-05-17)

- Closed the stale `APK Share-Target Receiver` roadmap row after verifying the
  installer already accepts shared APK/APKM/XAPK payloads via `ACTION_SEND`,
  `ACTION_SEND_MULTIPLE`, `ACTION_VIEW`, and `ACTION_INSTALL_PACKAGE`.
- The existing installer queue and confirmation flow already preserve shared URI
  access, parse the payload, and surface tracker, dependency, checksum, and
  signature-mismatch context in the install path.

### Added — signature-gated automation broadcast API (2026-05-17)

- Added `AutomationReceiver` behind the new signature permission
  `io.github.sysadmindoc.AppManagerNG.permission.AUTOMATION`.
- Added documented broadcast actions for freeze, unfreeze, force-stop,
  clear-cache, clear-data, uninstall, backup, restore, component enable/disable,
  profile execution, install-from-URI handoff, and tracker-scan handoff.
- Broadcast package operations route through the existing `BatchOpsService`;
  profile execution routes through `ProfileApplierService`; install and tracker
  actions reuse the existing installer and App Details tracker view.

### Added — Finder relevance scoring (2026-05-17)

- Finder now sorts filtered package results through `FinderRelevanceScorer` when
  literal package-name, component-name, or tracker-name search predicates are
  active.
- Ranking uses Levenshtein distance with package/simple-name/token/window
  scoring so close package-name and component-name hits surface before broader
  substring matches, while unrelated filter results keep their original scan
  order.
- New `FinderRelevanceScorerTest` covers edit distance, exact-token preference,
  longer-token demotion, and case-insensitive scoring.

### Added — permission-state Finder filters (2026-05-17)

- `PermissionsOption` now filters requested permissions by `granted`, `denied`,
  `custom`, `fixed`, `with_flags`, and `without_flags`.
- New `FilterablePermissionInfo` captures requested-permission grant state,
  source package, declaration/protection metadata, and runtime permission flags
  for both Finder/filter rows and main-list `ApplicationItem` consumers.

### Added — Finder backup-only app results (2026-05-17)

- Finder now opts into validated backup-only rows for AppManagerNG backup
  metadata whose package/user pair is not already returned by PackageManager,
  making filters able to match archived apps that are no longer installed.
- Backup-only rows use synthetic package metadata from the latest archive per
  package/user while keeping existing backup filters wired to the validated
  backup metadata DB.

### Added — Finder debloat-description predicates (2026-05-17)

- Finder's `bloatware` filter now searches debloat-list human descriptions via
  `description_eq`, `description_contains`, `description_starts_with`,
  `description_ends_with`, and `description_regex`.
- Plain contains/prefix/suffix description predicates are case-insensitive so a
  query such as `nfc tags` can match prose from `debloat.json` without requiring
  exact capitalization.

### Added — capability-dropping diagnostic (2026-05-17)

- Settings -> Privileges now includes a "Capability dropping (--drop-cap)" row
  that probes the active privileged shell with `id -u` plus `CapEff` from
  `/proc/$$/status`.
- The row reports whether the current shell is root, a non-root UID with an
  empty effective-capability set, a non-root UID that still has capabilities, or
  unavailable/unknown. Tapping the row reruns the probe.
- New `RootCapabilityDiagnosticsTest` covers dropped, present, root, and malformed
  probe-output parsing.

### Added — privilege health-check screen (2026-05-17)

- New Settings -> Privileges page consolidates the mode diagnostics that were
  split across onboarding, Mode of operation, and Troubleshooting.
- The page shows configured/inferred mode, working UID, a mode self-test result,
  root manager detection (Magisk / KernelSU / APatch with ZygiskNext and Sui
  markers when visible), Shizuku binding status with API-version/minimum checks,
  USB/Wireless ADB state, remote server/service state, and AppManagerNG battery
  optimization state.
- The battery row reuses `SelfBatteryOptimization`: if a privileged root/ADB
  path can whitelist AppManagerNG, tapping the row attempts that fix before
  falling back to the Android system exemption prompt.

### Added — opt-in debloat definition auto-updates (2026-05-17)

- New
  [`DebloatDefinitionsUpdater`](app/src/main/java/io/github/muntashirakon/AppManager/debloat/DebloatDefinitionsUpdater.java)
  fetches AppManagerNG's pinned raw-GitHub debloat definition manifest at app
  launch only when the user enables the new Settings -> Privacy opt-in and the
  existing "Use the Internet" gate is enabled.
- Downloaded `debloat.json` and `suggestions.json` snapshots are length-checked,
  SHA-256 verified against
  [`docs/debloat-definitions/manifest.json`](docs/debloat-definitions/manifest.json),
  schema-smoke-tested, then cached in app-private storage. If the network fetch
  fails or the cache is missing, `StaticDataset` continues using the bundled
  assets.
- The updater uses a generic User-Agent and standard HTTPS GET requests only; no
  package list or device identifier is attached by AppManagerNG. New
  [`DebloatDefinitionsUpdaterTest`](app/src/test/java/io/github/muntashirakon/AppManager/debloat/DebloatDefinitionsUpdaterTest.java)
  covers SHA-256 formatting, approved raw-GitHub URL guardrails, and dataset
  validation.

### Added — cross-user package state surfacing (2026-05-17)

- Main-list `ApplicationItem` rows now preserve per-user package state buckets
  (`enabledUserIds`, `disabledUserIds`, `uninstalledUserIds`) instead of only
  collapsing to a single installed/disabled flag.
- The main app list shows compact per-user state text such as `u0 enabled,
  u10 disabled`, and the multi-user selection dialog appends the state next to
  each user.
- Finder now loads packages for every selected user via `Users.getUsersIds()`
  instead of only the current user, and each Finder result shows the user id and
  enabled/disabled/not-installed state.

### Changed — privileged battery-optimization auto-fix for routines (2026-05-17)

- New
  [`SelfBatteryOptimization`](app/src/main/java/io/github/muntashirakon/AppManager/self/SelfBatteryOptimization.java)
  centralizes AppManagerNG's own Doze exemption state and privileged
  `DEVICE_POWER` auto-fix path.
- Profile routine execution and long-running backup/import/restore batch
  operations now try to whitelist AppManagerNG through the existing root/ADB
  `deviceidle` binder path before work begins, reducing schedule/routine misses
  caused by Doze.
- The Settings → Troubleshooting battery-optimization entry now reuses the same
  helper while preserving the manual system-settings fallback for unprivileged
  devices.

### Added — install-session SHA-256 confirmation (2026-05-17)

- `PackageInstallerCompat` now computes SHA-256 over the exact bytes copied into
  each `PackageInstaller.Session.openWrite()` stream, including split installs,
  before calling `commit()`.
- `PackageInstallerBroadcastReceiver` carries that digest through the pending
  user-action handoff, and `PackageInstallerActivity` shows a checksum dialog
  before launching Android's system install confirmation prompt.
- New [`InstallChecksumDisplayTest`](app/src/test/java/io/github/muntashirakon/AppManager/apk/installer/InstallChecksumDisplayTest.java)
  covers lowercase hex encoding and readable 8-character checksum grouping.

### Changed — USB debugging preflight for Wireless ADB / Shizuku setup (2026-05-17)

- [`OnboardingFragment`](app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java)
  now checks the Developer Options `adb_enabled` flag before starting Wireless
  ADB pairing from onboarding. If USB debugging is off, users get an explicit
  prompt to enable both USB debugging and Wireless debugging before continuing,
  with a deliberate "Continue" escape hatch for devices that pair anyway.
- The Wireless ADB setup dialog, Shizuku explainer, Wireless ADB explainer, and
  ADB pairing instruction now all name the two required Developer Options toggles.
  This closes the T5 Canta-model follow-up where `adb pair` / `adb connect` could
  fail silently because the user enabled only Wireless debugging.

### Compliance — Shizuku Android-17 detection + onboarding fallback (2026-05-17)

- [`ShizukuBridge`](app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java)
  now exposes `hasAndroid17CompatibilityRisk(Context)`, combining
  `Build.VERSION.SDK_INT >= 37` with an installed Shizuku version check against
  `MIN_ANDROID_17_COMPATIBLE_VERSION`. The constant intentionally stays `null`
  until Shizuku publishes a verified Android 17 fix, so Android 17 devices warn
  conservatively.
- [`OnboardingFragment`](app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java)
  now shows an Android-17-only Shizuku compatibility warning inside the Shizuku
  card and starts the existing Wireless ADB setup flow when the warning is tapped.
  The copy is deliberately framed as a risk because Shizuku #1965 reports a blank
  manager page while apps may still function, while #1967 reports broader failure.
- New
  [`ShizukuBridgeTest`](app/src/test/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridgeTest.java)
  covers API-floor behavior, the unknown-fixed-version state, future fixed-version
  comparison, and `v13.6.0.r...` suffix parsing.

### Added — Shizuku release watcher CI workflow (2026-05-17)

- New [`.github/workflows/shizuku-release-watch.yml`](.github/workflows/shizuku-release-watch.yml)
  runs weekly Thursday 09:27 UTC plus `workflow_dispatch`. It scans recent official
  `RikkaApps/Shizuku` releases and opens a maintainer issue when a 13.6.x / 13.7.x
  release note mentions Android 17 or Shizuku #1965 / #1967, with a checklist to
  verify the release and populate `MIN_ANDROID_17_COMPATIBLE_VERSION`.

### Changed — ML-DSA certificate algorithm display names (2026-05-17)

- `Utils.getCertificateSignatureAlgorithmName(X509Certificate)` now maps Android
  17's ML-DSA OIDs (`1.3.6.1.4.1.2.267.12.6.5`, `1.3.6.1.4.1.2.267.12.8.7`) to
  `ML-DSA-65 (Dilithium)` and `ML-DSA-87 (Dilithium)` instead of showing a raw OID
  when the platform/JCA provider does not provide a friendly name.
- The helper is used by the Package Info certificate dialog, Scanner certificate
  panel, and `Utils.getIssuerAndAlg()`. New
  [`UtilsCertificateAlgorithmTest`](app/src/test/java/io/github/muntashirakon/AppManager/utils/UtilsCertificateAlgorithmTest.java)
  covers both mapped OIDs and fallback behavior for known/unknown algorithms.

### Compliance — Android 17 targetSdk=37 audit batch closed clean (2026-05-17)

The five open sub-audits in the Engineering Debt Register's Android 17 targetSdk=37 compliance batch were executed during the iter-26 walk-away research follow-through. All five return clean — NG ships **zero source-side compliance work** for the targetSdk=37 bump.

- **`usesCleartextTraffic` enforcement** ✅ clean — `network_security_config.xml` declares `base-config cleartextTrafficPermitted="false"`; manifest has no `usesCleartextTraffic` attribute; pinned domains (VirusTotal, Pithus) are HTTPS-only with cert-pin sets; loopback (`127.0.0.1` / `localhost`) is explicitly opted in for libadb-android pairing. [`docs/audits/2026-05-17-android17-cleartext-traffic-enforcement.md`](docs/audits/2026-05-17-android17-cleartext-traffic-enforcement.md).
- **`ACCESS_LOCAL_NETWORK` runtime permission** ✅ clean — zero `NsdManager` / `MulticastSocket` / mDNS / `NetworkInterface.getNetworkInterfaces()` in production. Wireless ADB pairing is input-driven (user types host+port from device Wireless Debugging panel); no LAN discovery surface. [`docs/audits/2026-05-17-android17-access-local-network.md`](docs/audits/2026-05-17-android17-access-local-network.md).
- **BAL hardening + `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` migration** ✅ clean — zero matches across `app/src/main/java/` for the deprecated BAL flag or `setPendingIntentBackgroundActivityStartMode()`. NG launches activities from foreground UI contexts; the `am://` URI scheme and the planned Tasker-parameterized broadcast API are receiver-driven and unaffected. [`docs/audits/2026-05-17-android17-bal-intentsender.md`](docs/audits/2026-05-17-android17-bal-intentsender.md).
- **ECH default-on for TLS** ✅ clean — NG's three network destinations all handle ECH default-on without renegotiation. No `<domainEncryption>` opt-out needed in `network_security_config.xml`. [`docs/audits/2026-05-17-android17-ech-default-on.md`](docs/audits/2026-05-17-android17-ech-default-on.md).
- **ML-DSA Keystore OID recognition** ✅ clean (audit) — NG's APK cert display surfaces both `getSigAlgName()` AND `getSigAlgOID()`; no algorithm-name string-comparison branches downstream; forward-compatible with Android 17's new `1.3.6.1.4.1.2.267.12.6.5` (ML-DSA-65) and `.8.7` (ML-DSA-87) OIDs. Polish opportunity (not compliance-blocking): a small OID→display-name map. [`docs/audits/2026-05-17-android17-ml-dsa-keystore-oid.md`](docs/audits/2026-05-17-android17-ml-dsa-keystore-oid.md).

Engineering Debt Register's "Android 17 targetSdk=37 compliance" row is now **closed**. The remaining blockers to the `targetSdk = 37` bump are external: (a) the 1 deferred finding from the iter-20 static-final-reflection audit; (b) the Shizuku 13.6.0 / Android 17 regression (Shizuku #1965 / #1967) — see "Audit — Shizuku Android-17 compatibility" below. Android 17 stable lands June 2026 ([S324]).

### Audit — Shizuku Android-17 compatibility (mitigated, needs device verification) (2026-05-17)

- New [`docs/audits/2026-05-17-shizuku-android17-compat.md`](docs/audits/2026-05-17-shizuku-android17-compat.md) — verdict on NG's iter-23 Shizuku integration against the Android 17 Beta 3 regressions reported in Shizuku #1965 / #1967.
- **Verdict updated after iter-27: `mitigated, needs device verification + fixed-version floor`**. NG's [`ShizukuBridge.java`](app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java) probes are all `Throwable`-caught and **will not crash** on Android 17. Mixed evidence from Shizuku #1965 / #1967 / #1988 means NG should warn and offer Wireless ADB fallback without disabling Shizuku outright.
- **Design implemented** in iter-27: the non-destructive `hasAndroid17CompatibilityRisk(Context)` probe landed with `MIN_ANDROID_17_COMPATIBLE_VERSION = null`, and onboarding now shows a fallback banner that launches Wireless ADB setup.
- Remaining work is (a) device verification against an Android 17 Beta/stable image, and (b) populating `MIN_ANDROID_17_COMPATIBLE_VERSION` after Shizuku or a vetted fork ships a confirmed fix.

### Added — minSdk-21 cascade analysis (2026-05-17)

- [`docs/policy/minsdk-21-ceiling.md`](docs/policy/minsdk-21-ceiling.md) gained a new "Cascade analysis: what `minSdk = 23` would unlock" sub-section. Maps the Material Components 1.14 ceiling pressure to the four other pinned-cluster lines (`activity` 1.11 → 1.12, `biometric` 1.4.0-alpha04 → alpha05+, `room` 2.7.2 → 2.8, `webkit` 1.14 → 1.15) and documents the decision pressure: Material 1.14.0 is still alpha (alpha06/07/08 — [S325]), so the floor decision can stay deferred. Bumping the cluster one-at-a-time is explicitly discouraged — the floor lift lands as a single `min_sdk = 23` PR that also bumps the five pinned-cluster deps in lockstep when the decision is forced.
- Closes iter-24 backlog row F-NEW-09.

### Added — Docs Markdown link checker CI workflow (2026-05-17)

- New [`.github/workflows/docs-link-check.yml`](.github/workflows/docs-link-check.yml) — `lycheeverse/lychee-action@v2` runs on push, PR, and weekly Tuesday 11:11 UTC (staggered off existing CI cadences). Scope: all `*.md` at repo root, `docs/**/*.md`, `.ai/**/*.md`, plus `ROADMAP.md` / `CHANGELOG.md` / `PROJECT_CONTEXT.md`. Cache-backed (7-day TTL) to avoid hammering external URLs on every push; uploads a `lychee-out.md` report as a CI artifact for inspection.
- Closes iter-24 backlog row F-NEW-13 — link-rot insurance for the new `PROJECT_CONTEXT.md` + `docs/architecture/` tree.

### Added — JaCoCo coverage rollout plan (2026-05-17)

- New [`docs/policy/jacoco-coverage-rollout.md`](docs/policy/jacoco-coverage-rollout.md) — implementation plan for JaCoCo coverage reporting (ROADMAP iter-24 backlog F-NEW-11; T11 row "Unit Test Coverage Expansion"). Five concrete steps: (1) apply the `jacoco` Gradle plugin in `app/build.gradle`, (2) pin `jacoco_version = "0.8.13"` in `versions.gradle`, (3) wire the `jacoco` block + `jacocoTestReport` task with the standard Android exclusion set, (4) update `.github/workflows/tests.yml` to generate the report and upload the HTML artifact (optional Codecov step), (5) optional README badge.
- The autonomous-research session that drafted this plan deliberately did **not** modify `app/build.gradle` — JaCoCo wire-in benefits from local-build verification on a Windows / macOS / Linux build host (which the session does not have). The doc is detailed enough that the maintainer can land it as a single copy-paste-and-verify commit.

### Fixed — Finder regex predicates compiled as regex, not literals (2026-05-17)

- [`FilterOption.setKeyValue()`](app/src/main/java/io/github/muntashirakon/AppManager/filters/options/FilterOption.java) was wrapping every user-supplied regex value in `Pattern.quote()` before `Pattern.compile()`, so a pattern like `".*facebook.*"` only matched the literal 9-character string and never anything *containing* "facebook". The iter-23 work that added `name_regex` to [`TrackersOption`](app/src/main/java/io/github/muntashirakon/AppManager/filters/options/TrackersOption.java) and `regex` to `ComponentsOption` would have failed silently in production. The fix drops `Pattern.quote()`, wraps the compile in `try/catch (PatternSyntaxException)` so an invalid pattern surfaces with the key/type/message instead of crashing the filter pass, and adds the missing `break` between the `TYPE_REGEX` and `TYPE_STR_MULTIPLE` switch cases (the fall-through was overwriting `stringValues` after a regex predicate compile).
- Defensive null-safety alongside: [`AppOpsOption.matchesAny()`](app/src/main/java/io/github/muntashirakon/AppManager/filters/options/AppOpsOption.java) drops the unused `ArrayList` allocation and skips null op names; [`TrackersOption.matchesAny()`](app/src/main/java/io/github/muntashirakon/AppManager/filters/options/TrackersOption.java) skips null `ComponentInfo.name` so a malformed APK that omits the field doesn't NPE the filter pass.
- New [`FilterOptionTest`](app/src/test/java/io/github/muntashirakon/AppManager/filters/options/FilterOptionTest.java) (Robolectric) covers the regex-not-literal behaviour, the `PatternSyntaxException` surfacing, and the `TYPE_STR_MULTIPLE` fall-through. Commit [`73387cd`](https://github.com/SysAdminDoc/AppManagerNG/commit/73387cd).

### Security — Install transcript URI redactor closes userinfo / query / fragment leak (2026-05-17)

- [`InstallTranscript.redactSourceUri()`](app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/InstallTranscript.java) previously only stripped after the first `/` of the path. A URI shaped `https://host?token=secret` (no `/` path) was returned verbatim including the query; the same hole existed for `https://user:pass@host/...` (userinfo not stripped) and `#`-appended fragments. The redacted transcript is what the user pastes into a public issue tracker via "Copy diagnostic info", so a leak there equals an unintentional credential disclosure.
- The fix parses the authority per RFC 3986 §3.2 — authority ends at the first `/`, `?`, or `#`. Strips any `user[:password]@` userinfo prefix; drops query and fragment entirely (download providers append document IDs and signed tokens there). New [`InstallTranscriptTest`](app/src/test/java/io/github/muntashirakon/AppManager/apk/installer/InstallTranscriptTest.java) cases cover query-string redaction on path-less URI, userinfo redaction, fragment redaction, and the no-truncation path. Commit [`bcb2874`](https://github.com/SysAdminDoc/AppManagerNG/commit/bcb2874).

### Fixed — Onboarding root-manager probe survives fragment-detach (2026-05-17)

- [`OnboardingFragment.refreshRootManagerStatus()`](app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java) posted root-manager detection to a background pool, then called `requireContext().getApplicationContext()` on the worker thread. If the fragment detached between the post and the run, `requireContext()` threw `IllegalStateException` from the worker and the throw was uncaught/silent — leaving the user-visible root-manager suffix (" · KernelSU", " · APatch", etc.) un-populated without explanation. The fix captures the `Application` context on the main thread before posting, so the worker carries a non-null context regardless of fragment lifecycle. Commit [`25c629a`](https://github.com/SysAdminDoc/AppManagerNG/commit/25c629a).

### Added — Project-context consolidation (2026-05-17)

- New [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) canonical entry-point index, designed so new contributors / AI sessions can orient against the project surface in <5 minutes instead of re-reading every scattered planning artifact. Links into [`ROADMAP.md`](ROADMAP.md), [`CHANGELOG.md`](CHANGELOG.md), the audit / research dirs, [`design/`](design/), [`docs/policy/minsdk-21-ceiling.md`](docs/policy/minsdk-21-ceiling.md), and the new `.ai/` research run.
- New [`.ai/research/2026-05-17/`](.ai/research/2026-05-17/) directory holds the 2026-05-17 autonomous research-run audit trail: `STATE_OF_REPO.md`, `MEMORY_CONSOLIDATION.md`, `SOURCE_REGISTER.md`, `RESEARCH_LOG.md`, `COMPETITOR_MATRIX.md`, `FEATURE_BACKLOG.md`, `PRIORITIZATION_MATRIX.md`, `SECURITY_AND_DEPENDENCY_REVIEW.md`, `DATASET_MODEL_INTEGRATION_REVIEW.md`, `CHANGESET_SUMMARY.md`, `CONTINUE_FROM_HERE.md`. The pass surfaced an iter-24 backlog of three commit-ready in-progress fixes (Finder regex predicate quoting, install transcript URI redactor, onboarding root-probe race) plus governance items, all documented in `FEATURE_BACKLOG.md`.
- New [`docs/audits/README.md`](docs/audits/README.md) documents the audit-doc doctrine: when to write an audit, filename convention (`<YYYY-MM-DD>-<topic>.md`), document-shape template, verdict vocabulary (`clean` / `clean (audit)` / `remediated` / `confirmed, needs-design` / `deferred` / `n/a`), and cross-reference scheme. Lowers the cost of standing up the next behaviour-change audit; addresses the long-standing gap that the 14 existing audit files followed a consistent shape with no documented template.
- [`AGENTS.md`](AGENTS.md) gained a `## Canonical Project Context` block pointing at `PROJECT_CONTEXT.md` and the new `.ai/research/2026-05-17/` directory so any tool that loads `AGENTS.md` (Codex, et al.) routes to the consolidation index. `CLAUDE.md` (gitignored per `.gitignore:32`, intentional working-notes file) received the same pointer locally for the Claude-side path.
- [`ROADMAP.md`](ROADMAP.md) Source Appendix gained **S316 → S320**: F-Droid 2.0-alpha9 release-feed (2026-05-08), F-Droid Basic 2.0-alpha announcement (2026-01-24), Shizuku Manager 13.6.0 APKMirror metadata, the 2026-05-17 research-run self-reference, and the AOSP Android-16 release-notes hub. Header pointer line added linking to `PROJECT_CONTEXT.md` and the iter-24 backlog.
- [`README.md`](README.md) §"Roadmap" version-target preview refreshed to reflect ground truth: v0.2.0 / v0.3.0 / v0.4.0 marked ✅ (v0.4.0 shipped both Permission Inspector + Onboarding capability wizard via the 2026-05-14 `feat: refine capability onboarding` work); v0.5.0 retargeted to "Settings reorganization, global in-app search, contextual help tooltips, in-app changelog viewer" matching ROADMAP §"Committed Version Targets"; new v0.6.0 row "Rootless Power: Shizuku integration polish, wireless ADB auto-pairing, rootless debloat".
- [`versions.gradle`](versions.gradle) `material_version` comment extended to flag the cascade implication: Material Components 1.14.0-rc01+ raises minSdkVersion to 23 and is the single ceiling dep whose bump unblocks the activity/biometric/room/webkit pinned-line cluster. Cross-references [`docs/policy/minsdk-21-ceiling.md`](docs/policy/minsdk-21-ceiling.md).

### Changed — Roadmap hygiene (2026-05-16)

- Closed eight long-standing roadmap rows that turned out to already be implemented in the codebase or shipped under a different name: Backup Tag Autocomplete (covered by iter-21 "Existing-Tag Suggestions"), Force Stop via Shizuku Rootless (already in `AppInfoFragment` via `SelfPermissions.checkSelfOrRemotePermission(FORCE_STOP_PACKAGES)`), Backup Integrity Verification (`BackupOp` + `RestoreOp` + `BackupItems.Checksum`), AES-256 Backup Encryption (`AESCrypto` with Android Keystore-backed keys, metadata v6 per-file IV), PGP Backup Encryption (`OpenPGPCrypto` via OpenIntents OpenPGP API), Finder: Components (`ComponentsOption` with class-name + type-flag predicates), Finder: Permissions (`PermissionsOption` with declared-permission-name predicates), and Biometric App Lock (`BaseActivity.BiometricPrompt` gates `onAuthenticated()` for every authenticated screen). Each row now documents the existing implementation surface for future contributors.

### Added — Finder filter extensions (2026-05-16)

- New [`AppOpsOption`](app/src/main/java/io/github/muntashirakon/AppManager/filters/options/AppOpsOption.java) under the `app_ops` filter key with both name predicates (`eq` / `contains` / `starts_with` / `ends_with` / `regex` over the op name) and a `with_mode` bitfield (`MODE_FLAG_ALLOWED` / `_IGNORED` / `_ERRORED` / `_DEFAULT` / `_FOREGROUND`) so users can filter by op state.
- [`TrackersOption`](app/src/main/java/io/github/muntashirakon/AppManager/filters/options/TrackersOption.java) gains class-name predicates (`name_eq` / `name_contains` / `name_starts_with` / `name_ends_with` / `name_regex`) alongside the existing count predicates. Matched tracker subsets are threaded through `TestResult.setMatchedTrackers` so downstream filters can compose with the narrowed set. Closes the "Finder: Tracker Name Search" and "Finder: Regex Support" rows from the early roadmap.
- [`DataUsageOption`](app/src/main/java/io/github/muntashirakon/AppManager/filters/options/DataUsageOption.java) gains `mobile_le` / `mobile_ge` / `wifi_le` / `wifi_ge` predicates so users can filter by cellular-only or Wi-Fi-only consumption instead of just the combined total. `IFilterableAppInfo` gained `getMobileDataUsage()` and `getWifiDataUsage()` accessors (implemented in both `FilterableAppInfo` and `ApplicationItem` from the existing `PackageUsageInfo.mobileData` / `wifiData` fields).

### Added — Backup sharing (2026-05-16)

- Restore-Single → popup menu now carries a "Share backup" action when exactly one backup is selected. The chooser opens with every file under the backup directory as an `ACTION_SEND_MULTIPLE` payload via `FmProvider`, so users can pipe a backup into another file manager, messaging app, or cloud SAF provider without first zipping it. Encrypted backups stay encrypted on the way out — recipients still need the user's AppManagerNG key to restore. Closes the iter-18, iter-21, and v0.x roadmap rows for "Backup Sharing Button" / "Neo-Backup-Style Backup Sharing Button" in one pass.

### Added — Install diagnostics (2026-05-16)

- Install-failure dialog now exposes a "Copy diagnostic info" button that copies a paste-friendly install transcript (timestamp, AppManagerNG version, device, Android version + security patch, ABI, active mode, package, status code + name, status message, redacted source URI). Source URIs are redacted by default — `file:///` paths drop to `file://<redacted>`, `content://` and `http(s)://` keep scheme + authority but drop the path / document id — so the transcript can be pasted into a public issue safely.
- Install confirmation dialog now warns when the APK's `minSdkVersion` exceeds the device's API level, before the user taps Install. New [`InstallDependencyChecker`](app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/InstallDependencyChecker.java) is structured so future versions can add ABI / split-APK base-missing checks without changing the call site.
- Install confirmation dialog now also warns when the APK declares `<uses-library>` entries the device does not advertise — `INSTALL_FAILED_MISSING_SHARED_LIBRARY` would have been the post-commit failure. The required library names are read from `applicationInfo.sharedLibraryFiles` and compared against `PackageManager.getSystemSharedLibraryNames()`; only the missing names are surfaced in the warning text.

### Added — Backup destination guidance (2026-05-16)

- New [`docs/distribution/backup-destinations.md`](docs/distribution/backup-destinations.md) — canonical inventory of supported backup destinations (native + indirect-via-SAF + rejected), reliability watch-outs per DocumentsProvider class, the FOSS rationale for not shipping direct Google Drive / Dropbox / OneDrive OAuth, and recommended user setups per use case (local, Syncthing, Nextcloud, NAS, removable, cold archive).

### Added — Distribution-channel posture (2026-05-16)

- New [`docs/distribution/package-visibility.md`](docs/distribution/package-visibility.md) — canonical `QUERY_ALL_PACKAGES` justification for F-Droid / IzzyOnDroid / Accrescent / Obtainium review, with per-surface impact table and a maintainer gate for future `AndroidManifest.xml` permission changes.
- New [`docs/policy/minsdk-21-ceiling.md`](docs/policy/minsdk-21-ceiling.md) — running ledger of every dependency that has already dropped (or imminently drops) API 21-22 support, plus the decision tree for raising the minSdk floor vs. raising a single dependency. `versions.gradle` now carries an inline pointer above `min_sdk = 21` so contributors hit the ledger before bumping.

### Changed — Triage intake (2026-05-16)

- All five `.github/ISSUE_TEMPLATE/*` files now point at the NG repo, `CONTRIBUTING.md`, and `ROADMAP.md` instead of upstream `muntashir.dev` / `MuntashirAkon/AppManager`.
- The bug-report template now collects NG-specific support-bundle fields: active mode (Auto/Root/Shizuku/ADB-WiFi/ADB-USB/No-root), privilege provider (Magisk/KernelSU/APatch/Shizuku/Sui/ZygiskNext/None), LocalServer bootstrap signature, install source, security patch level, ROM/build, architecture, and the affected operation — meshes with the upcoming Support Info Bundle Composer.
- The PR template now references the NG `CONTRIBUTING.md` and replaces the upstream blanket AI/LLM ban with the tool-assisted-but-reviewed contract from `CONTRIBUTING.md`; the assignment-required checkbox is dropped.
- The issue-template contact links point first at the AppManagerNG roadmap and Discussions, with upstream user-guide / ADL / android-libraries listed as supporting datasets rather than primary routing.

### Changed — Premium UX polish (2026-05-16)

- Refined onboarding warning and capability status treatments with semantic color, icon, and accessible state handling.
- Stabilized onboarding mode-card highlighting so replayed capability checks reset inactive cards and do not stack active-state accessibility text.
- Normalized dual-pane Settings toolbars to the V2 shell height and surface treatment for better large-screen consistency.
- Tightened main-list badge typography and empty/status copy for clearer scanning in high-density app lists.

### Changed — Release hardening and backup crypto (2026-05-16)

- New encrypted backup metadata now uses backup metadata version 6, enabling per-file AES-GCM IV derivation while keeping older encrypted backups readable with their legacy IV behavior.
- Package install and uninstall waits now fail with a bounded timeout and diagnostic status instead of hanging indefinitely when PackageInstaller callbacks are lost.
- Onboarding now warns when the device security patch is older than the recommended patch level for ADB workflows.
- Backup and Wireless ADB setup diagnostics now log structured warnings instead of dumping stack traces to stderr.
- Gradle build-time derivation now falls back cleanly when the git timestamp command returns an empty value.
- Reproducible-release verification now tracks every generated release APK and checksum sidecar instead of assuming a single universal APK output.
- CI tests/lint now run on the `main` branch, matching the repository's active default branch.

### Changed — Capability onboarding (2026-05-14)

- Added a Shizuku manager version check to the onboarding capability wizard so v13.6.0+ guidance is visible before users rely on Shizuku for Android 16 QPR1-era rootless flows.
- Added the Android 13+ trusted-WLAN auto-start tip to the Shizuku onboarding card, keeping the rootless recovery guidance visible during first-run and replayed onboarding.

### Added — Package URI install intake (2026-05-14)

- Added external installer handling for `package:package-name` view/install intents so already-present packages can be installed for the current user through the installer surface.
- Improved mixed install batches by reading package/file/content URIs from both `EXTRA_STREAM` and `ClipData` on `SEND_MULTIPLE`, de-duplicating repeated entries before they enter the queue.

### Changed — Installer source streaming (2026-05-14)

- Installer queue items now preserve direct file/content URI sources instead of first staging every APK into the app cache.
- PackageInstaller session writes prefer the original readable source stream when possible, while retaining cache-backed fallbacks for preview parsing, signing, and APKM conversion.
- Tightened APK cleanup so closing an `ApkFile` no longer attempts to delete user-provided source files.

### Added — Debloat presets (2026-05-14)

- Added Privacy, Gaming, and Minimal OEM presets to Debloater so installed recommendations can be selected as curated batches with a recommended freeze or removal path.
- Presets now preview installed match counts, explain their selection logic, and route through the existing reviewed freeze/remove confirmations before any changes run.

### Changed — Factory-reset before system app uninstall (2026-05-14)

- Updated Debloater uninstall flows to reset updated system apps back to their factory version before user-scope removal, avoiding stale updated-system stubs on ROMs that otherwise leave packages in a stalled state.
- Added confirmation and row-level copy for updated system apps so the reset step is explicit before the batch starts.

### Added — Rootless Debloat via Shizuku/ADB shell (2026-05-14)

- Added a Debloater uninstall path that uses `pm uninstall --user <id>` through the active Shizuku/ADB shell service, avoiding the accessibility-driven uninstall flow when a rootless privileged shell is available.
- Expanded Debloater safety feedback so selected removals show safe/replace/caution/unsafe counts, dependency or required-by warning counts, and high-risk examples before the batch starts.
- Debloater list rows now surface dependency and required-by counts from the bundled android-debloat-list metadata, making risky removals visible before opening the details sheet.

### Added — Guided Wireless ADB setup (2026-05-14)

- Added a first-run Wireless ADB setup affordance in onboarding, including Android 11+ fallback guidance, direct pairing/connect recovery, and remembered paired-device status.
- Persisted successful Wireless ADB pairing metadata so onboarding and mode settings can distinguish a never-paired device from a paired device that simply needs Wireless debugging enabled again.

### Added — Shizuku privilege provider (2026-05-14)

- Added Shizuku/Sui UserService as a first-class privileged binder path alongside root and ADB, including automatic mode detection, permission recovery, and shell/root/system uid status handling.
- Added Shizuku onboarding and mode-selection status copy so users can see whether Shizuku is running, authorized, missing, or below the supported Android 7.0+ runtime boundary.

### Changed — Adaptive large-screen workflows (2026-05-14)

- Added AndroidX Activity Embedding for the high-traffic Main → AppDetails flow so ≥900dp displays can keep the app catalog and details open side by side.
- Added a calm split-placeholder panel for wide screens before an app is selected, and pinned WindowManager to the latest stable line that preserves the repo's API 21 floor.
- Added a ≥900dp OneClickOps layout that separates review actions from backup and maintenance actions, while preserving the phone layout and smoothing busy-state transitions.
- Added ≥900dp backup/restore review layouts that keep the summary card beside the package or backup-version list, reducing vertical scanning and making the final action state easier to compare before committing changes.

### Changed — Reproducible release verification (2026-05-14)

- Added a two-clean-build release gate that compares signed APK SHA-256 hashes before publishing, plus a release checksum sidecar and local Windows/Linux reproducibility verification scripts.
- Normalized Gradle archive ordering/timestamps and server-jar D8 input ordering so release artifacts are stable across clean builds.

## v0.4.2 — 2026-05-13

### Changed — Contextual notification permission for installer progress (2026-05-13)

- Added a just-in-time Android 13+ notification rationale before the installer foreground service starts, so install progress, completion, and failure feedback remain visible when the user sends an install to the background.

### Changed — Contextual notification permission for batch operations (2026-05-13)

- Added a just-in-time Android 13+ notification rationale before long-running batch operations so progress, completion, and failure feedback are not silently hidden.
- Corrected the persistent session notification text to describe that tapping opens notification settings.

### Changed — Contextual notification permission for wireless pairing (2026-05-13)

- Moved the Android 13+ notification ask into the wireless ADB pairing flow, with a clear rationale that pairing status and pairing-code entry use the ongoing notification.

### Changed — First-run prompt sequencing and recovery-password handoff (2026-05-13)

- Deferred optional notification permission checks from cold startup so first-run opens with AppManagerNG-owned security context instead of an Android permission sheet.
- Clarified the pre-auth KeyStore handoff as a recovery password, with calmer generated/input copy and a denser readable password field.

### Changed — Premium polish: shape system, first-run trust, and warning tone (2026-05-13)

- Normalized the Material shape language away from capsule-style surfaces: chips, popup menus, bottom sheets, onboarding/icon frames, screen-time widget markers, app-info headers, dashed panels, badges, status bars, and shared card/list shapes now use bounded 8–12dp radii while true icon-only controls remain circular.
- Reworked the first-run disclaimer into a clearer trust panel with a stronger hierarchy, visible privileged-operations callout, separated external-project disclosure, and a calmer **I understand** confirmation label.
- Softened warning alert treatments so debug-expiry and privileged-risk notices read as elevated guidance instead of hard error blocks.
- Updated build-expiry copy and update links to point to AppManagerNG release/actions channels instead of the upstream App Manager project.

### Security — Deep-link parser hardening + CSV-injection defuse (2026-05-13)

Audit pass on NG-authored surfaces that handle attacker-influenced strings. Two real bugs fixed:

- **`SelfUriManager.getUserPackagePairFromUri()` crash + validation-bypass** ([`self/SelfUriManager.java`](app/src/main/java/io/github/muntashirakon/AppManager/self/SelfUriManager.java)). The deep-link parser routing both `app-manager://details?id=…&user=…` and `am://app/<pkg>?user=…` through `AppDetailsActivity` had two issues that any installed app could trigger by firing a crafted `VIEW` intent at the exported intent-filter:
    1. `TextUtils.isDigitsOnly("99999999999999")` returns `true`, but `Integer.parseInt` on a string ≥ 2³¹ throws `NumberFormatException`. The exception bubbled out of `onCreate()` and crashed the activity. Now wrapped in `try { … } catch (NumberFormatException)` with a fall-through to `myUserId()`.
    2. The package-name validation was applied to `pkg.trim()` but the un-trimmed `pkg` was passed into `UserPackagePair`. A URL-encoded leading/trailing space (`?id=%20com.foo`) would pass `PackageUtils.validateName()` yet land a whitespace-padded package name in the activity's `mPackageName` field, breaking every downstream `PackageManager` lookup. Now trims **before** validation and uses the trimmed value end-to-end.
    Reference: surfaced during the iter-22 `am://` short-alias audit.

- **`OperationHistoryExporter.toCsv()` CSV / formula injection** ([`history/ops/OperationHistoryExporter.java`](app/src/main/java/io/github/muntashirakon/AppManager/history/ops/OperationHistoryExporter.java)). Operation-history CSV exports include attacker-influenced fields — app labels come from `PackageManager.loadLabel()` (fully controlled by the installed app), and installer failure messages may include vendor-provided text. A hostile package installed with a label like `=HYPERLINK("http://evil/","click")` would land that string verbatim in an exported CSV cell; Excel and LibreOffice Calc evaluate any cell whose first character is `= + - @ \t \r` as a formula, opening data-exfiltration and (on unpatched Excel + legacy DDE) code-execution windows when the user opens the export. New `escapeCsvField()` prepends the OWASP-standard apostrophe defuse to any value beginning with a trigger character; embedded double-quote escaping is unchanged. Regression test landed in [`OperationHistoryExporterTest.exportCsvDefusesFormulaInjection`](app/src/test/java/io/github/muntashirakon/AppManager/history/ops/OperationHistoryExporterTest.java).

### Security — Hardening pass on iter-22 changes (2026-05-09)

Defense-in-depth follow-up to the iter-22 work that landed earlier today. Three findings, all addressed:

- **`upstream-rename-watch.yml` GitHub-Actions script injection** ([`.github/workflows/upstream-rename-watch.yml`](.github/workflows/upstream-rename-watch.yml)). The original `actions/github-script@v7` step interpolated `${{ steps.probe.outputs.actual }}` directly into the JS body. Even though `actual` is sourced from a curl-parsed `full_name` string and shouldn't contain shell-injecting characters, this is the exact pattern that GitHub's own security documentation flags as a supply-chain risk: a value containing `'`, `\\`, `` ` ``, or a newline could break out of the string literal and execute arbitrary JS in the runner. Fixed by passing the value via a step-level `env: ACTUAL_SLUG: ${{ steps.probe.outputs.actual }}` and reading `process.env.ACTUAL_SLUG` inside the script. The probe step also now (a) uses heredoc-style `{ ... } >> "$GITHUB_OUTPUT"` to atomically write the multi-key block (instead of separate `echo X >> $GITHUB_OUTPUT` lines that leak partial state if the script bails) and (b) regex-validates the slug `^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$` before writing it, so a malformed API response never makes it into the output channel.

- **AppDetailsActivity intent-filter split** ([`AndroidManifest.xml`](app/src/main/AndroidManifest.xml)). The original iter-22 `am://app/<pkg>` alias was added as a second `<data>` element inside the existing `app-manager://details` intent-filter. While the parser ([`SelfUriManager.getUserPackagePairFromUri()`](app/src/main/java/io/github/muntashirakon/AppManager/self/SelfUriManager.java)) explicitly enforces the `(scheme, host)` pair in code so cross-matched URIs like `app-manager://app/...` are rejected, the loose filter would still match them at OS resolution time — a footgun against any future regression in the parser. Split into two distinct `<intent-filter>` blocks so the resolver only routes URIs that match an exact `(scheme, host)` pair to us. End-to-end verified on Samsung S25 Ultra: both `am://app/<pkg>` and `app-manager://details?id=<pkg>` correctly open `AppInfoActivity`.

- **`ShortcutDispatchActivity` hardening** ([`shortcut/ShortcutDispatchActivity.java`](app/src/main/java/io/github/muntashirakon/AppManager/shortcut/ShortcutDispatchActivity.java)). The trampoline now: (a) wraps the dispatch logic in `try { … } finally { finish(); }` so the `Theme.NoDisplay` activity contract is honoured even if something unexpected throws (Theme.NoDisplay requires `finish()` before `onCreate` returns; failing to do so leaves a phantom no-display task on the recents stack); (b) catches `ActivityNotFoundException` from `startActivity()` so a disabled-by-PackageManager target component or a removed-by-upgrade-migration shortcut doesn't crash the trampoline; (c) truncates the unknown-action log entry to 80 chars so a hostile caller can't pollute the device log with arbitrary-length entries. End-to-end re-verified on-device: known actions still dispatch correctly, unknown actions silently no-op.

### Security — Static-shortcut export regression closed; trampoline-based dispatch (2026-05-09)

Hardening pass on the iter-22 static launcher shortcuts that landed earlier the same day. The original implementation flipped `OneClickOpsActivity` and `FinderActivity` to `android:exported="true"` so the launcher could resolve the shortcut intents. **`OneClickOpsActivity` accepts an `EXTRA_OP` intent extra that triggers a destructive batch operation (clear cache for all installed apps) without confirmation when set to `OP_CLEAR_CACHE` — this path is intended for the trusted in-process clear-cache home-screen widget (`ClearCacheAppWidget`), not for arbitrary callers.** Combined with the export, any installed app could fire the activity with the destructive extra after the user was process-authenticated, silently clearing the cache of every app on the device. **`FinderActivity` was reverted to `exported=false` for symmetry / minimum exposure.**

Fix:

- **`OneClickOpsActivity` and `FinderActivity` reverted to `android:exported="false"`** in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml). Verified post-fix: `am start -n io.github.sysadmindoc.AppManagerNG.debug/io.github.muntashirakon.AppManager.oneclickops.OneClickOpsActivity --ei op 16` is rejected with `Permission Denial: ... not exported`.
- **New trampoline activity** [`shortcut/ShortcutDispatchActivity.java`](app/src/main/java/io/github/muntashirakon/AppManager/shortcut/ShortcutDispatchActivity.java) — the only exported component the launcher resolves to for shortcuts. Hard-whitelists two action constants (`OPEN_ONE_CLICK_OPS`, `OPEN_FINDER`), constructs a fresh `Intent` for the unexported target, and **does not forward intent extras**. Untrusted callers cannot smuggle in `EXTRA_OP` or any other destructive extra.
- **`shortcuts.xml` updated** to target the trampoline via the explicit action constants instead of the underlying activities.
- The widget / pinned-shortcut consent flow (`ClearCacheAppWidget` → `PendingIntent.getActivity(OneClickOpsActivity.class, EXTRA_OP=OP_CLEAR_CACHE)`) is unaffected — that path is in-process and the destructive extra is the user's explicit consent at widget tap time.
- End-to-end verification on Samsung S25 Ultra (`SM-S938B`): trampoline dispatches `OPEN_FINDER` and `OPEN_ONE_CLICK_OPS` correctly; activity stack shows the unexported target as the foreground activity; passing `--ei op 16` to the trampoline action does not trigger the destructive shortcut path because `OneClickOpsActivity` opens with no extras.

### Fixed — Per-app locale store-of-truth reconciliation at startup (2026-05-09)

Companion fix to the iter-22 Per-App Locale Picker that landed earlier today. The original wiring made `Prefs.Appearance.setLanguage()` mirror to `AppCompatDelegate.setApplicationLocales()` so the OS-side per-app locale (Settings → Apps → AppManagerNG → Language on Android 13+) stays in sync when the user changes language **in-app**. It did not handle the inverse: when the user changes the language **in the OS surface**, `AppCompatDelegate` updated but `Prefs` was stale; `LangUtils.getFromPreference()` (the in-app source-of-truth read by `AppearanceUtils.applyOnlyLocale()` on every activity recreate) then overrode the OS choice with the stale Prefs value on the next configuration change.

Fix: new [`AppearanceUtils.reconcileLocalePreference()`](app/src/main/java/io/github/muntashirakon/AppManager/utils/appearance/AppearanceUtils.java) runs on `Application.onCreate()` (via `AppearanceUtils.init()`) **before** any locale is applied:

- If `AppCompatDelegate.getApplicationLocales()` is non-empty and disagrees with `Prefs.Appearance.getLanguage()` → persist the OS-side value into `Prefs` (OS is the most recent authority).
- If `Prefs` has a non-`AUTO` value but `AppCompatDelegate` is empty (first launch after the iter-22 wiring landed, or a user with a long-standing in-app language preference) → push `Prefs` into `AppCompatDelegate` so the OS-side picker reflects reality.
- Wrapped in `try/catch` so a binder failure during `LocaleManager` reconciliation can never kill app startup.

### Compliance — Predictive-Back WebView Freeze (Obtainium #2911) audit (clean) (2026-05-09)
- **Audit clean — no remediation required.** The iter-20 roadmap row's premise that NG ships WebView surfaces in `RulesActivity` and an APK-info preview pane is stale; neither activity exists in NG. Component Rules surfaces are `RulesFragment` RecyclerView UIs, not WebView.
- The single WebView surface in NG is [`HelpActivity`](app/src/main/java/io/github/muntashirakon/AppManager/misc/HelpActivity.java) and it already uses the correct predictive-back propagation pattern: `android:enableOnBackInvokedCallback="true"` declared in the manifest, `OnBackPressedCallback` registered via `getOnBackPressedDispatcher().addCallback(...)`, and the WebView's `canGoBack()` state tracked on `doUpdateVisitedHistory()` so predictive-back animation only previews when there's a back-stack entry.
- The Obtainium #2911 regression class only affects activities that bypass the dispatcher or register a raw `OnBackInvokedCallback` without integrating with the WebView's back-stack — neither pattern is present in NG.
- Audit at [`docs/audits/2026-05-09-predictive-back-webview.md`](docs/audits/2026-05-09-predictive-back-webview.md). Establishes the canonical pattern for any future WebView-hosting activity (in-app changelog viewer planned for v0.5.0, JADX decompile pane in T12). Reference: [S200].

### Added — Upstream repo-rename watcher CI workflow (2026-05-09)
- New [`.github/workflows/upstream-rename-watch.yml`](.github/workflows/upstream-rename-watch.yml) hits the GitHub API on a weekly cadence (Wednesday 09:27 UTC, staggered off CodeQL Thursday 14:43 + dependency-scan Sunday 04:13) plus `workflow_dispatch`. Asserts that `MuntashirAkon/AppManager` still resolves to the same `full_name`; on drift, auto-opens an `upstream-sync`/`eng-debt`-labelled issue containing a 7-step rename audit checklist (workflow `EXPECTED_SLUG`, README baseline + Credits, ROADMAP baseline + research-source citations, CLAUDE.md Origin section, CHANGELOG historical refs, submodule URLs, Obtainium config, Sphinx docs).
- Idempotent — never opens a duplicate issue for the same drift in a single window. Uses unauthenticated GitHub API (full-name lookup needs no auth) so it does not consume `GITHUB_TOKEN` rate limits for the third-party probe; `GITHUB_TOKEN` is used only for the issue creation.
- Closes ROADMAP iter-18 row "Repo-Rename Detection for Upstream Pin" — Eng-Debt Next (Effort 1/5). Reference: [S121].

### Added — Pseudolocale resources on debug builds (2026-05-09)
- `pseudoLocalesEnabled true` set on the `debug` build type in [`app/build.gradle`](app/build.gradle); release builds stay clean.
- Debug AM-NG now ships `en-XA` (accented + bracketed pseudolocale that catches truncation and untranslatable string regressions) and `en-XB` (RTL mirror of English that catches mirroring/layout breakage). Activate via `adb shell setprop persist.sys.locale en-XA` or **Settings → Developer options → Pseudolocale** on Android 13+.
- The CI screenshot-diff portion of the iter-22 T10 row stays open — it gates on the upcoming **Espresso + UI Automator Smoke Pack** providing the headless instrumentation pipe the screenshot capture needs.
- Closes the build-side half of ROADMAP iter-22 T10 row "Pseudolocale Build Variants + RTL CI Pass" (Effort 2/5, [S268]).

### Added — CI Dependency CVE Scan (PR review + weekly OWASP) (2026-05-09)
- New [`.github/workflows/dependency-scan.yml`](.github/workflows/dependency-scan.yml) ships two layers:
    - **PR Dependency Review** (`actions/dependency-review-action@v4`) on every pull request: fails the PR on HIGH/CRITICAL CVEs introduced by a dependency change. Also denies CC-BY-NC* / CC-BY-ND* / AGPL-1.0 license bumps up-front (GPL-3.0-or-later redistribution compatibility — see ROADMAP iter-19 DDG Tracker Radar reject [S69]).
    - **Weekly OWASP Dependency Check** (Sunday 04:13 UTC, staggered off the existing CodeQL Thursday 14:43 cadence) plus `workflow_dispatch`: runs `./gradlew dependencyCheckAggregate` and uploads HTML + SARIF reports as artifacts (30-day retention). Catches CVEs disclosed *after* a dependency landed.
- `org.owasp:dependency-check-gradle:10.0.3` plugin wired into [`build.gradle`](build.gradle) at the root; `dependency_check_version = '10.0.3'` declared in [`versions.gradle`](versions.gradle).
- Local runs default to `failBuildOnCVSS = 11.0` (effectively never fail) so the report is purely informational on developer machines; CI uses `continue-on-error: true` and surfaces the report as an artifact rather than killing the weekly cadence on a single new CVE. NVD API rate limit honored via optional `NVD_API_KEY` secret with anonymous fallback.
- Suppression file path is wired but optional (`config/owasp-suppressions.xml`) — populate on first weekly audit to silence vendored-AAR false positives without losing the failing-on-real-CVE behavior.
- Closes ROADMAP iter-22 row "CI Dependency CVE Scan" — T4 Now (Effort 2/5). Reference: [S274].

### Added — `am://app/<pkg>` short-alias deep link + intent-API documentation (2026-05-09)
- New `am://app/<pkg>?user=<uid>` URI scheme as a short alias for the canonical `app-manager://details?id=<pkg>&user=<uid>`. Parses through the existing [`SelfUriManager.getUserPackagePairFromUri()`](app/src/main/java/io/github/muntashirakon/AppManager/self/SelfUriManager.java) — both schemes share the code path so consumers downstream don't change.
- Intent-filter `<data android:host="app" android:scheme="am"/>` added to the existing `details.AppInfoActivity` activity-alias in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml). Mirrors `hail://`'s shape.
- New [`docs/intent-api.md`](docs/intent-api.md) documents the full URI / broadcast-intent surface: shipped App Info alias, reserved-but-not-yet-wired shapes (`am://freeze/<pkg>`, `am://profile/<id>/run`, `am://install?source=<url>`), and the then-roadmapped signature-permission-gated broadcast schema. The broadcast surface later shipped on 2026-05-17 under `io.github.sysadmindoc.AppManagerNG.action.*`; Tasker / MacroDroid integration still needs the planned plugin broker.
- The freeze / profile / install URI shapes are deliberately not wired yet — they need user-confirmed dialogs on top of the broadcast-intent automation surface (iter-22 T8 [S247]) before becoming public URL actions. Reserved here so a future implementation doesn't churn the schema.
- Closes ROADMAP iter-22 T8 row "`am://` URI Scheme — Concrete Schema" (Effort 1/5, [S246]) for the App Info alias slice; remaining shapes carried forward.

### Added — Static launcher shortcuts for power-user entry points (2026-05-09)
- Long-pressing the AppManagerNG icon on the launcher now surfaces three core entry points: **1-Click Ops** (batch operations), **Running Apps** (process inspector), and **Finder** (cross-app search). Shortcuts shipped at [`app/src/main/res/xml/shortcuts.xml`](app/src/main/res/xml/shortcuts.xml) and registered on `SplashActivity` via `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/>`.
- `FinderActivity` and `OneClickOpsActivity` flipped to `exported="true"` in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) so the launcher can dispatch the shortcut intents. `RunningAppsActivity` was already exported.
- Pinned per-app shortcuts (Freeze / Force-Stop / Clear Cache) continue to flow through `ShortcutManagerCompat` in `CreateShortcutDialogFragment` and the existing FreezeUnfreeze service path; this commit is the static-launcher anchor the upcoming dynamic top-N pinned-app set will extend.
- Closes ROADMAP T8 row "Launcher Shortcuts for AM Features" (Issue #660 [S32]). Iter-22 [S252] dynamic per-app shortcut work remains.

### Added — Per-app locale picker now syncs with OS Settings (2026-05-09)
- The in-app **Settings → Appearance → Language** picker now mirrors its selection to `AppCompatDelegate.setApplicationLocales(...)` after persisting the in-app preference. On Android 13+ (API 33+) AppManagerNG appears under **Settings → Apps → AppManagerNG → Language** and the OS-side picker stays in sync with the in-app picker bidirectionally.
- New `AppLocalesMetadataHolderService` registration in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) (with `autoStoreLocales=true`, `enabled=false`) provides the SharedPreferences-backed back-port so per-app locale selection survives process death on API 26-32 devices via androidx.appcompat 1.7.1.
- The `LANG_AUTO` setting maps to `LocaleListCompat.getEmptyLocaleList()` so "Auto" tracks the system locale through the platform mechanism instead of the legacy NG-only override pipeline.
- Existing `AppearanceUtils.applyConfigurationChangesToActivities()` activity-recreate path is unchanged — in-app re-render after a language change is still immediate.
- Closes ROADMAP iter-22 row "Per-App Locale Picker (`AppCompatDelegate.setApplicationLocales`)" — T10 Now (Effort 1/5). Reference: [S269].

## v0.4.1 — 2026-05-08

Maintenance release. Concentrates 19 closed Now/Eng-Debt rows from the iter-19/iter-20 ROADMAP drains plus one CONFIRMED audit finding flagged for design (GCM cipher reuse). All changes ship as user-visible polish + compliance + diagnostics; no breaking format changes. The GCM cipher-reuse bug in `AESCrypto.handleFiles()` is documented but **not yet fixed** — multi-file AES-encrypted backups produced by v0.4.0 and v0.4.1 cannot be trusted to restore. OpenPGP / RSA / ECC backup modes are unaffected; single-file AES backups are unaffected. See the audit at `docs/audits/2026-05-08-gcm-cipher-reuse-large-backup.md` for remediation options. The next release will pick a fix path and ship it behind a backup metadata version flag.

### Audit — GCM cipher reuse in `AESCrypto.handleFiles()` (CONFIRMED BUG, needs-design) (2026-05-08)
- ⚠️ **Confirmed:** [`AESCrypto.handleFiles()`](app/src/main/java/io/github/muntashirakon/AppManager/crypto/AESCrypto.java) instantiates a single `GCMBlockCipher` once before the per-file for-loop and reuses it across every file with the same `mIv`. After file 0's `doFinal()`, the cipher is in finalized state; iteration 1 wraps the same cipher in a fresh `CipherOutputStream`, with behavior that's either fail-fast or silent nonce-reuse depending on BouncyCastle's internals. This matches upstream AM issue #1958.
- GCM mode has a hard cryptographic invariant: `(key, IV)` must NEVER encrypt more than one distinct plaintext. Reuse silently breaks confidentiality and breaks the auth tag. The single-file `encrypt(InputStream, OutputStream)` path creates its own cipher and isn't affected; only the multi-file `handleFiles` path triggers the bug. OpenPGP / RSA / ECC modes are unaffected.
- **Remediation requires backup format planning**, not a one-line cipher re-init (re-init with the same IV is still nonce reuse). Three options documented at [`docs/audits/2026-05-08-gcm-cipher-reuse-large-backup.md`](docs/audits/2026-05-08-gcm-cipher-reuse-large-backup.md): (A) HKDF-Expand-derive per-file IV (no format change, old backups stay broken — they're already corrupt); (B) per-file IV stored alongside ciphertext (clean, requires metadata version bump); (C) fresh Crypto instance per file.
- **No code change shipped** in this commit — the audit is the deliverable. The next pass picks an option, ships the fix behind a metadata-version flag, and adds a synthetic-4-GB-blob round-trip regression test. Reference: AM #1958 / [S138].

### Compliance — Zip-slip protection audit (clean) (2026-05-08)
- **Audit clean — every disk-writing extraction path canonicalizes the output path and rejects traversal entries before any bytes are written.**
- `TarUtils.extract` and `AndroidBackupExtractor.extract` both carry the canonical "double-check" guard from upstream AM v4.0.0-alpha02: pre-write `Paths.normalize(entry.getName())` + `startsWith("../")` rejection, plus post-create `realFilePath.startsWith(realDestPath)` containment verification. Both raise `IOException("Zip slip vulnerability detected!")` with diff-able expected/actual paths on the (extremely unlikely) malicious-archive case.
- Archive-to-archive converters (`SBConverter`, `OABConverter`, `TBConverter`) cache entries by extension to `FileCache.createCachedFile()` or do tar-to-tar metadata copying — they never use the source entry name as a disk path, so they're inherently safe; any malicious entry name is re-encoded into the output archive and rejected at the eventual extraction step. `ApkUtils.getManifestFromApk` is in-memory only.
- Audit at [`docs/audits/2026-05-08-zip-slip-protection.md`](docs/audits/2026-05-08-zip-slip-protection.md). Closes the iter-20 Engineering Debt Register row "Zip-slip protection in APK/backup extraction".

### Compliance — libsu 6.0.0 `Shell.cmd` migration audit (clean) (2026-05-08)
- **Audit clean — zero matches.** Recursive sweep across `app/`, `libcore/`, `libserver/`, `libopenpgp/`, `hiddenapi/`, `server/` returned 0 `Shell.sh(` / `Shell.su(` / `FLAG_REDIRECT_STDERR` references.
- The single `Shell.cmd(` call site in [`RemoteShellImpl.java:25`](app/src/main/java/io/github/muntashirakon/AppManager/ipc/RemoteShellImpl.java#L25) implements the 6.0.0 idiom; all other privileged shell invocations route through NG's `Runner.runCommand` abstraction on top of it.
- Audit at [`docs/audits/2026-05-08-libsu-shell-cmd-migration.md`](docs/audits/2026-05-08-libsu-shell-cmd-migration.md). Closes the iter-20 Engineering Debt Register row "libsu `6.0.0`".

### Added — LocalServer bootstrap-failure signature line (2026-05-08)
- New `logBootstrapFailureSignature()` helper in [`LocalServer.checkConnect()`](app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServer.java) emits a single-line failure signature whenever the privileged-shell handshake throws (`IOException` / `AdbPairingRequiredException`). The signature captures `Build.MANUFACTURER/PRODUCT/DEVICE`, `SDK_INT`, `Build.ID`, `ro.lineage.version` (when present), the exception class + message, and the cause chain.
- Bug reporters can copy this one `Log.e("IPC", …)` line into an issue instead of a full audit log. Targets in particular the LineageOS 23.2 / Android 16 root-binder regression (AM #1962 / [S185]) where the SELinux denial in `system_server` kills the handshake silently. The actual SELinux denial line still has to come from `dmesg` / `logcat` separately, but the device + exception fingerprint is now structured and trivially diff-able across reports.
- Diagnostic logging is wrapped in a try/catch so it can never mask the original failure. Closes the iter-20 Now/T2 row.

### Fixed — A16 QPR2 silent `clearApplicationUserData` failure (2026-05-08)
- [`PackageManagerCompat.clearApplicationUserData()`](app/src/main/java/io/github/muntashirakon/AppManager/compat/PackageManagerCompat.java) now snapshots `IStorageStatsManager.queryStatsForPackage()` `dataBytes + cacheBytes` pre-clear, calls the hidden-API IPC path, re-snapshots post-clear, and when the post-clear size hasn't dropped below the pre-clear baseline (with a 64 KiB tolerance for the user-data dir's skeleton state), runs `pm clear --user N <pkg>` as a shell fallback.
- The IPC path is also fallen-back-to-shell when it throws, so true IPC failures plus the QPR2 silent-success class of bug both route to the same shell remediation. The 64 KiB tolerance avoids false positives on the small placeholder state the OS retains even after a clean wipe; full-MB-or-GB silent-failure cases (Poco F3 / Infinity-X 3.9 / Root mode on QPR2) are caught and recovered.
- New helpers: `clearApplicationUserDataViaIpc()`, `clearApplicationUserDataViaShell()`, `queryAppDataBytesQuietly()`. Reference: AM #1965 / [S184]. Closes the iter-20 Now/T2 row.

### Added — `Ops.isAdbShellRoot()` detection helper (2026-05-08)
- New static helper [`Ops.isAdbShellRoot()`](app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java) returns true when the configured mode is ADB but the working shell's uid is 0 — the "ADB Root" surface KernelSU v3.2.3+ added in 2026 (also reachable via APatch's adb-root toggle and Magisk's kang mode).
- Cheap, all-thread-safe, no shell round-trip. The detection layer intentionally doesn't gate on KernelSU specifically because APatch / Magisk-kang reach the same privilege state; callers wanting a stricter "root manager confirmed" gate pair this with `RootManagerInfo.detect()`.
- The javadoc carries the trust caveat ("anyone can plug in via USB"): a device left unattended with USB debugging enabled grants any laptop the same uid-0 surface, so consumers (the still-pending Privilege Health-Check screen and onboarding wizard) MUST gate elevated trust on explicit user confirmation before treating this as full-root for sensitive ops. Reference: [S166]. Closes the iter-20 Now/T9 row at the detection-foundation layer; UX confirmation flow lands with the Privilege Health-Check screen.

### Changed — Backup-name dialog now autocompletes from prior backup names (2026-05-08)
- The "Multiple backup" name dialog in [`BackupFragment.handleBackup()`](app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupFragment.java) is now backed by `TextInputDropdownDialogBuilder` instead of `TextInputDialogBuilder`. Users tagging a fresh backup get an autocomplete dropdown of every prior backup label across the apps in scope, so re-using the same tag as last time is one tap instead of a full retype.
- New `collectExistingBackupNames()` walks `viewModel.getBackupInfoList()` → per-backup `BackupMetadataV5.metadata.backupName` and feeds the de-duplicated `LinkedHashSet` into `setDropdownItems(items, -1, true)` (filterable), so typing narrows the suggestion list as the user goes. Empty/null names are skipped.
- Re-scoped from the iter-20 row's original wording: NG's multi-tag dao hasn't shipped yet, so the *applicable* user-facing surface today is the backup-name dialog. When the multi-tag dao lands, the same `setDropdownItems` adapter pattern can be reused for the tag-add dialog with zero code change. Reference: Neo-Backup 8.3.17 / [S135]. Closes the iter-20 Now/T8 row.

### Added — Per-OEM Debloat Risk Ribbon (Samsung One UI 8.5) (2026-05-08)
- New [`OemBloatRiskTable`](app/src/main/java/io/github/muntashirakon/AppManager/debloat/OemBloatRiskTable.java) helper resolves vendor-aware known-bad debloat warnings from a `(package, Build.MANUFACTURER, vendor-OS-version)` triple, where vendor-OS-version comes from the platform's vendor-specific system property (`ro.build.version.oneui` for Samsung One UI, `ro.mi.os.version.name` / `ro.miui.ui.version.code` for Xiaomi HyperOS / MIUI).
- First entry: `com.samsung.android.smartsuggestions` on Samsung One UI 8.5 (`ro.build.version.oneui == 80500`) — UAD-NG #1394 documented Settings → Mobile-Networks crash-loop on Galaxy A57. The new warning string `oem_bloat_risk_samsung_smartsuggestions_oneui85` ships with localizable copy directing users to disable/freeze the package instead of removing it.
- Wired into [`BloatwareDetailsDialog.bind()`](app/src/main/java/io/github/muntashirakon/AppManager/debloat/BloatwareDetailsDialog.java) via a new `composeWarning()` helper: vendor-known-bad ribbon leads, the upstream debloat-list warning trails for additional context, and the alert chip is forced to `ALERT_TYPE_WARN` regardless of the upstream removal rating (a system-surface crash loop is not "info").
- Resolution order is exact match → wildcard match (`*` handles devices where the vendor-OS-version property is unreadable). Generic "this looks Samsung-y" warnings stay on the upstream string; this surface is reserved for verified field reports keyed to a specific OEM/version combo. Reference: [S188]. Closes the iter-20 Now/T7 row.

### Added — Cert dialog now shows Subject + Issuer (2026-05-08)
- The "Sign · SHA-256" tag chip's dialog in App Info now exposes the X.509 **Subject** and **Issuer** distinguished names alongside the SHA-256 fingerprint, so users vetting an APK can see who the certificate claims to be issued *to* without dropping to `apksigner verify --print-certs`.
- New `AppInfoViewModel.populateSigningCertInfo()` (replaces `computeSigningCertSha256`) writes `signingCertSha256` / `signingCertSubject` / `signingCertIssuer` together off the same `X509Certificate` instance — Subject/Issuer come from `getSubjectX500Principal().getName()` / `getIssuerX500Principal().getName()` (RFC 2253 form). All three stay `null` for unparseable / multi-signer / unsigned packages.
- [`AppInfoFragment.showCertFingerprintDialog()`](app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java) renders the trio as labelled sections; new strings `cert_fingerprint_dialog_{sha256,subject,issuer}_header`. Copy button still copies fingerprint-only to keep AppVerifier / `apksigner` paste-compatibility.
- The iter-20 row's other layout-density bullets (SDK-row reorder, two-column trackers|SDK, popup `maxHeightPercent`) target an upstream `app_info_card.xml` that doesn't exist in NG — App Info is a full pager fragment, not a bottom-sheet popup, so those don't map. Reference: AM #1966 / [S187]. Closes the iter-20 Now/T21 row (Subject + Issuer scope).

### Changed — AppOps row-tap cycles ALLOWED → IGNORED → ERRORED (2026-05-08)
- Row-tap on an AppOps entry in App Details (`AppDetailsPermissionsFragment`) now cycles **ALLOWED → IGNORED → ERRORED → ALLOWED** instead of binary toggling between ALLOWED and a derived deny.
- The IGNORE (`MODE_IGNORED`) state silently no-ops the op without throwing `SecurityException`, matching platform behavior. It's the correct option for ops that misbehaving apps would otherwise crash on if DENY (`MODE_ERRORED`) is set — Inure build106.5.0 model.
- A short Toast names the new mode after each tap (`AppOpsManagerCompat.modeToName(mode)`); long-press still opens the full single-choice mode picker (FOREGROUND/DEFAULT/etc.) for advanced users.
- New `nextAppOpModeInCycle()` helper in [`AppDetailsPermissionsFragment.java`](app/src/main/java/io/github/muntashirakon/AppManager/details/AppDetailsPermissionsFragment.java). Reference: [S131]. Closes the iter-20 Now/T9 row.

### Added — Sui (Magisk-module Shizuku) detection in onboarding (2026-05-08)
- New `checkSuiViaShell()` probe in [`runner/RootManagerInfo`](app/src/main/java/io/github/muntashirakon/AppManager/runner/RootManagerInfo.java) reads `/data/adb/modules/sui/` whenever the privileged shell already returned a non-NONE root manager (Magisk / KernelSU / APatch). New `RootManagerInfo.suiPresent` boolean carries the result through to consumers.
- [`OnboardingFragment.buildRootManagerSuffix()`](app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java) appends a " + Sui" suffix on the Root status line alongside the existing ZygiskNext suffix; combined cases render as e.g. "Detected · Magisk + Sui" or "Detected · KernelSU + Sui + ZygiskNext".
- Sui has no `moe.shizuku.privileged.api` package install, so the Magisk-module marker is the only authoritative signal — the iter-20 `PackageManager` enumeration approach the row originally proposed is unnecessary once the marker is read directly. The "prefer Sui over Shizuku" routing decision is deferred to the still-pending Privilege Health-Check screen (T5); `info.suiPresent` is the wire for it. Reference: [S178]. Closes the iter-20 Now/T5 row.

### Docs — GrapheneOS A16 background-install fix patch reference (2026-05-08)
- New [`docs/patch-references/2026-05-08-grapheneos-a16-background-install.md`](docs/patch-references/2026-05-08-grapheneos-a16-background-install.md) captures both fixes from GrapheneOS AppStore Release 36: (a) wrap user-confirmation `startActivity()` in an `isResumed` check + defer to `onPostResume()` when paused (Android 16 `IllegalStateException: Can not perform this action after onSaveInstanceState`), and (b) audit `getCallingPackage()` + `getReferrer()` and drop queued `PendingActions` when an external untrusted caller re-targets the activity.
- Port deferred until an Android 16 test device is available; doc lists the exact NG site ([`PackageInstallerActivity.java`](app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/PackageInstallerActivity.java)) and validation steps. Closes the iter-20 Now/T11 row in patch-reference form.

### Fixed — Debloater shortcut crash on pre-A13 / Unisoc devices (2026-05-08)
- Added `.debloat.DebloaterActivityAlias` (`android:exported="true"`, `targetActivity=".debloat.DebloaterActivity"`) in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) so external launcher pins / Tasker shortcuts / third-party app shortcuts resolve to a stable component name on platforms where pinning the underlying activity directly fails with `ActivityNotFoundException`. Reproduces upstream AM #1963 (Moto g22 / Unisoc T606 / Android 12).
- The alias has no `CATEGORY_LAUNCHER` filter — it does not appear as a separate launcher icon. Closes the iter-20 Now/T2 row.

### Compliance — Android 17 static-final reflection audit (1 fix, 1 deferred) (2026-05-08)
- Audited 20 `setAccessible(true)` call sites across `app/`, `libcore/`, `server/` for the Android 17 ban on `Field.set()` against `static final` fields with `setAccessible(true)`.
- 17 sites safe (10 `Method`/`Constructor`, 7 read-only `Field.get`).
- 1 fixed: [`TypefaceUtil.restoreFonts()`](app/src/main/java/io/github/muntashirakon/AppManager/utils/appearance/TypefaceUtil.java) wrote a same-reference back to `Typeface.sSystemFontMap` (static-final). Removed the redundant `Field.set()` call — the map's contents are mutated in place via `remove()` / `put()` so the write-back was a no-op. Behavior preserved.
- 1 deferred: [`RootServiceMain.startService()`](server/src/main/java/io/github/muntashirakon/AppManager/server/RootServiceMain.java) writes to `Resources.mSystem` (static-final). Currently `targetSdk=36` so the site is not yet broken; flagged for the targetSdk=37 bump task with three remediation options documented in the audit. Audit at [`docs/audits/2026-05-08-android17-static-final-reflection.md`](docs/audits/2026-05-08-android17-static-final-reflection.md). Closes the iter-20 Now/Eng-Debt audit row.

### Compliance — Google Play Contacts / Location-Button Policy audit (clean) (2026-05-08)
- Audit clean — the policy does not apply. NG's manifest declares only `READ_PHONE_STATE` (used for the telephony-side mobile/Wi-Fi data-usage split). NG does **not** declare `READ_CONTACTS`, `WRITE_CONTACTS`, `GET_ACCOUNTS`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `READ_PHONE_NUMBERS`, or any call-log / SMS permission.
- The contact and location permission strings that appear in [`PermissionGroupCatalog.java`](app/src/main/java/io/github/muntashirakon/AppManager/permissions/PermissionGroupCatalog.java) are **label constants** for the Permission Inspector UI to render groups when inspecting *other* installed apps; AppManagerNG itself never requests them at runtime. The iter-19 ROADMAP row's claim to the contrary was incorrect; the audit corrects the record.
- No NG UI button reveals contact info or precise location; no remediation required before the 2026-05-15 Google Play Console enforcement window. Audit at [`docs/audits/2026-05-08-google-play-contacts-location-policy.md`](docs/audits/2026-05-08-google-play-contacts-location-policy.md). Closes the iter-19 Now/Eng-Debt row.

### Security — CVE-2026-0073 disclosure for ADB mode (2026-05-08)
- New [`docs/security-advisories/2026-05-08-cve-2026-0073-adb-mode.md`](docs/security-advisories/2026-05-08-cve-2026-0073-adb-mode.md) discloses the Critical zero-click proximal RCE in `adbd` patched in the May 2026 Android Security Bulletin. AppManagerNG's ADB mode and Shizuku-via-wireless-debug provisioning talk to the same daemon, so devices below patch level `2026-05-01` carry residual risk.
- AppManagerNG itself is **not vulnerable** — the bug is in the platform `adbd` binary, not in any code we ship. Advisory documents impact split (USB-ADB on trusted network = moderate, wireless-debug = high), recommended actions for end users + downstream packagers, and the cross-reference to the sideload-verification doc (BR/ID/SG/TH overlap). Closes the iter-20 Now/T5 row; the companion in-app patch-level banner is deferred to the upcoming Onboarding Capability Wizard.

### Compliance — Android 17 `System.load()` read-only native audit (2026-05-08)
- **Audit clean — zero matches.** Recursive sweep across all source roots; AppManagerNG does not extract native libraries to disk via any of its own code paths and does not use `System.load(absolutePath)` anywhere.
- Two `System.loadLibrary("am")` call sites (`AhoCorasick.java:7`, `CpuUtils.java:13`) use the canonical AOSP path; the platform installer handles the read-only flag for bundled `jniLibs/`.
- Forty-plus `IoUtils.copy` call sites — none of them write `.so` files. Audit at [`docs/audits/2026-05-08-android17-system-load-readonly.md`](docs/audits/2026-05-08-android17-system-load-readonly.md). Closes the iter-20 Now/Eng-Debt row.

### Docs — AOSP source-pull retarget to `android-latest-release` (2026-05-08)
- AOSP moved to a trunk-stable publishing cadence in 2026: public source publishing now happens on a Q2 + Q4 schedule rather than continuous; `master` reflects a transient mid-quarter snapshot whose private-API surface may not survive to a published Android release.
- Pinned the **`android-latest-release`** branch as the only safe target for `hiddenapi/` stub harvesting in two places:
  - new "Pulling AOSP source for `hiddenapi/`" section in [`CONTRIBUTING.md`](CONTRIBUTING.md);
  - new [`hiddenapi/README.md`](hiddenapi/README.md) for in-module visibility.
- Both forbid `master` / `main` / `android-mainline` / date-stamped tags and point version-specific backports at version-tagged branches (`android-15.0.0_r1`, `android-16.0.0_r1`, etc.).
- The iter-52 Hidden-API Compatibility Harness now inherits this pinning through `scripts/generate-hidden-api-baseline.ps1`, which should be rerun after future `hiddenapi/` stub refreshes. Closes the iter-20 Now/Eng-Debt row.

### Build — Gson 2.13.2 → 2.14.0 (2026-05-08)
- `gson_version` bumped 2.13.2 → 2.14.0 in [`versions.gradle:26`](versions.gradle).
- Built-in `java.time` adapters drop the `--add-opens` requirement on JDK17 CI runners.
- Strict duplicate-JSON-key handling: malformed `{"foo": null, "foo": …}` now throws `JsonSyntaxException` instead of silently overwriting. Audited every Gson call-site (7 files); zero `setLenient(true)` opt-outs in the codebase, so all parse paths benefit. Audit + verification plan at [`docs/audits/2026-05-08-gson-2-14-0-bump.md`](docs/audits/2026-05-08-gson-2-14-0-bump.md). Closes the iter-20 Now/Eng-Debt row; supersedes the Engineering Debt Register entry that pinned 2.13.2.

### Security — BouncyCastle 1.83 → 1.84 (CVE-2026-3505 / 5588 / 5598) (2026-05-08)
- `bouncycastle_version` bumped 1.83 → 1.84 in [`versions.gradle:21`](versions.gradle); CVE list inlined as a trailing comment so the rationale lives at the dependency line.
- Closes **CVE-2026-3505** (PGP AEAD chunk-size DoS — directly relevant since `libopenpgp` powers OpenPGP-encrypted backup archives), **CVE-2026-5588**, and **CVE-2026-5598** (FrodoKEM non-constant-time compare; pre-emptive against future ML-DSA / PQ adoption).
- Audit at [`docs/audits/2026-05-08-bouncycastle-1-84-cve-bump.md`](docs/audits/2026-05-08-bouncycastle-1-84-cve-bump.md). Closes the iter-20 Now/Eng-Debt row; supersedes the long-standing low-urgency Engineering Debt Register entry that pinned 1.83.

### Docs — Sideloading Verification position document (2026-05-08)
- New [`docs/sideload-verification.md`](docs/sideload-verification.md) explaining what AppManagerNG does and does not do regarding Google's [Android Developer Verification](https://developers.google.com/android/play-protect/developer-verification) program — preempts the user-confusion wave when the 2026-09-30 enforcement starts hitting BR/ID/SG/TH users on certified devices.
- README "Install" section gains an `IMPORTANT` callout linking the document for users in the four enforcement regions.
- Closes the Iter-20 Now/T1/Docs row "Sideloading-Verification Position Document" (companion to "Android Developer Verification — BR/ID/SG/TH Enforcement"; that row remains in flight as a code-bearing task tracked separately).

### Docs — ROADMAP iter-20 research delta (2026-05-08)
- Appended "Iter-20 Research Additions" table to `ROADMAP.md` (38 rows: 19 Now / 16 Next / 1 Later / 1 Under Consideration / 1 Watch; 40 new sources S172–S211). Two-day delta from iter-19 (closing 2026-05-06): GitHub-issue mining of MuntashirAkon/AppManager (#1956–#1968), Canta, Hail (#387–#391), Neo-Backup (#1029–#1034), sdmaid-se (#2410–#2413), UAD-NG (#1386–#1394), Hamza417/Inure (#480), Obtainium (#2908–#2911 + discussion #2846), RikkaApps/Shizuku (#2036–#2052); Android 17 / QPR1 Beta 2 / Android Security Bulletin May-2026; Google Play Developer Verification rollout (BR/ID/SG/TH, enforcement 2026-09-30); 7-day GitHub-releases sweep (Neo-Backup 8.3.18, sdmaid-se v1.7.2-rc0 cert publish, Material Components 1.14.0-rc01, Gson 2.14.0, BouncyCastle 1.84 with three CVE fixes, hddq/restoid v0.5.0 restic-backed backup engine, wxxsfxyzm/InstallerX-Revived 26.05); new-competitor harvest the iter-19 list missed (Sui Magisk-module Shizuku, sameerasw/essentials, yume-chan/VolumeManager, pass-with-high-score/universal-installer, Hjsosn/FireWall-Blocks, kerneldroid/Shizuku-modern, BugeStudioTeam/Buge-App-Manager); GrapheneOS forum + XDA Shizuku/QPR1 threads.
- New themes: Android Developer Verification (single biggest sideload-tooling regulatory event of 2026), CVE-2026-0073 adbd zero-click RCE min-patch-level disclosure, BouncyCastle 1.83 → 1.84 PGP-AEAD DoS fix, Sui Magisk-module Shizuku detection, Shizuku 13.6.0 OEM allowlist (Transsion NPE / Mediatek / Pixel-9 QPR1), Shizuku root-backed avoidance for banking apps, OS-revert detection banner (novel — no competitor surfaces this), A16 QPR2 `clearApplicationUserData` fallback shell path with disk-usage-delta verification, LineageOS 23.2 root binder regression probe, Debloater activity-alias for pre-A13 Unisoc devices, App Info popup density refactor, per-OEM debloat risk ribbon (One UI 8.5 SmartSuggestions known-bad), default-app role re-binding after restore, restic-style backup engine (Under Consideration leapfrog), backup scheduler newest-age gate, CIFS/SMB streaming hardening, Wi-Fi configurations backup (root), squashfs writer header validation, FileManager recursive in-folder search, per-app volume via AppOps `OP_AUDIO_VOLUME` (closes upstream #1863), InstallerX-Revived privilege-elevation cascade, GrapheneOS A16 background-install-confirmation fix, split-APK cert-mismatch dialog, predictive-back WebView freeze fix, Material Components 1.14 FocusRingDrawable + SplitButton, AGP 8.13 → 9.2 migration ahead of AGP-10 cliff, AOSP source-pull retarget to `android-latest-release` (trunk-stable cadence), ML-DSA Keystore `KeyPairGenerator` recognition, HKDF-from-master backup key derivation (50K key cap mitigation), `System.load()` read-only native audit, Android 17 static-final reflection severity-promotion, persistent ADB tcpip 5555 detection in Shizuku setup, Doze allowlist diff banner, sideloading-verification position document.
- Iter-19 row promotions: Android Developer Verification rolled into a top-level T1 row; static-final reflection audit promoted to **Now** (severity revision); BouncyCastle bump promoted to **CVE-driven** Now; AGP migration promoted to **Next** with AGP-10 cliff dependency.

### Docs — ROADMAP iter-19 research delta (2026-05-06)
- Appended "Iter-19 Research Additions" table to `ROADMAP.md` covering 30 new items mined from a three-day GitHub-issue / community-pain-point sweep, a Shizuku-era competitor harvest (`timschneeb/awesome-shizuku`), and Android 17 Beta 4 + F-Droid 2.0 platform deltas. New themes: Hidden-API compatibility harness, GCM-cipher reuse on large OBB backup (#1958), Shizuku-permission auto-revoke warning on data-clear (Canta #359), Hidden-Shizuku fork detection, OEM debloat-blocker bypass (OPlus / Samsung / MIUI), per-app rollback / undo, Tasker parameterized intent API, freeze / operation audit-log UI, settings import/export portability, install-date filter, Android 17 16 KB page-size fix, Google Play Contacts/Location-button policy enforcement, KernelSU ADB-Root privilege enum, Blocker-style IFW rule editor, Amarok-Hider `pm hide` toggle, Language-Selector per-app locale via Shizuku, InstallerX-style biometric install gate, debuggable-app rootless backup, F-Droid 2.0 ROM JSON pre-seeding format, F-Droid 2.0 protobuf index v2, Android 17 ACCESS_LOCAL_NETWORK + static-final reflection ban + 50K Keystore cap + ML-DSA cert OIDs + cleartext deprecation, OwnDroid Dhizuku DPM mode, FireOS SYSTEM USER privilege backend, PI install-interception, UpgradeAll getter-plugin API, Material You / Monet widget theming. Two explicit rejects (Shizuku-iptables firewall, Thanox-style Accessibility-Service auto-freeze) per NG philosophy.
- Source Appendix extended S137–S171 (35 new sources). All iter-19 rows cite `[S###]` references.

## v0.4.0 — 2026-05-02

### Fixed — Permission Inspector: recovery action for previously revoked critical packages
- New "Restore system app permissions" action on the Permission Inspector home screen. Re-grants every dangerous permission to a fixed set of OS- and vendor-critical packages (Phone, system UI, Settings, telephony/contacts/media providers, fused location, Google Play services / GSF, Samsung location & IMS, etc.) and clears any persisted ComponentsBlocker permission rules for those packages so a bad state from a pre-guard build cannot survive reboot or reinstall.
- Required because earlier builds without the bulk-revoke guard could leave Phone, voicemail, location services, and other system functions broken via REVOKED_COMPAT appop flags. The recovery action makes that recoverable from inside the app instead of via adb.

### Added — Permission Inspector: master grant + info dialog
- New "Grant for all apps" toolbar action mirrors the existing "Revoke for all" — mass-grants the permission group to every modifiable app on the device. Useful when you've over-revoked and want to start fresh.
- New info icon on the toolbar opens a dialog explaining what the screen does and, importantly, **why some apps are skipped during a bulk revoke** (OS- and vendor-critical packages — GMS, GSF, system UI, telephony/media providers, fused location, Samsung location/IMS, etc. — are excluded from the bulk action because revoking from them can crash system_server). Per-app toggles remain unrestricted.
- The same explanation dialog auto-pops after a bulk revoke whenever any app was skipped, so users see the reason in context.

### Fixed — Permission Inspector: bulk-revoke could reboot device
- The master "Revoke for all apps" action now skips a denylist of critical system packages (`android`, `com.google.android.gms`/`gsf`, `com.android.systemui`, `com.android.settings`, `com.android.phone`, telephony/media/contacts providers, `com.android.location.fused`, Samsung location/IMS/phone services, etc.) and any `com.android.server.*` / `com.google.android.gms.*` subpackage. Revoking `ACCESS_FINE_LOCATION` / `ACCESS_BACKGROUND_LOCATION` from these crashed `system_server` and rebooted the device on Samsung One UI. Per-app toggles remain unrestricted — the guard only applies to the bulk action. A toast now reports both how many apps were revoked and how many were skipped.

### Added — Permission Inspector: review and bulk-revoke permissions across apps

- New top-level screen accessible from the main overflow menu (shield-key icon) that inverts the standard "app -> permissions" view. Catalog lists 12 curated dangerous permission groups (Camera, Microphone, Location, Contacts, SMS, Phone, Files & media, Calendar, Body sensors, Physical activity, Nearby devices, Notifications) each with a "X of Y apps granted" count. Tap a group to drill into the per-permission list of every app that requested it; toggle individual apps with a Material switch, or use the master "Revoke for all apps" toolbar action to mass-revoke in one shot. Persists changes through `ComponentsBlocker` so they survive reinstalls, same as the existing per-app permissions tab. SDK-version-gated for permission groups added in API 29/31/33/34.

### Added — Main List & Item Layout: v2 Design System Integration (v0.5.x surface migration phase 1)
- New `activity_main_v2.xml` and `item_main_v2.xml` wired behind the `PREF_PREMIUM_PREVIEW_BOOL` 
  toggle. When enabled, MainActivity and MainRecyclerAdapter load v2 layouts with refined v2 token 
  palette (calmer surfaces, tighter typography, pill-shaped search, outlined card variants). 
  Layout switching is conditional per-view, allowing zero-impact on classic theme users. 
  Completes v0.5.x phase 1 (top-5 surface migration). Next phases: AppDetails, AppUsage, Settings.

### Added — Troubleshooting: auto-fix battery optimization via root/ADB (SD-Maid parity, ROADMAP iter-18 T20)
- Settings → Troubleshooting → "Battery optimization" now auto-applies the
  exemption when NG has root or ADB privileges (checks `DEVICE_POWER`). If
  permitted: silently grants the whitelist in background, updates summary,
  and shows a confirmation toast. If not: falls back to the system dialog.
  Matches SD-Maid's "auto-fix" UX pattern. No user setup needed.

### Added — App Details: copyable error dialog helper (UAD parity, ROADMAP iter-18 T4)
- New `UIUtils.displayCopyableErrorDialog(context, title, message)` shows a
  Material alert with OK + **Copy** buttons. Copy invokes `ClipboardUtils`
  (which already handles >1MB error blobs via FileProvider URI fallback)
  so users can paste failure detail straight into a bug report instead
  of screenshotting + transcribing. Foundation only; high-traffic toast
  failure sites migrate in a follow-up commit.

### Changed — App Info / AppOps / Permissions: descriptions now selectable (UAD parity, ROADMAP iter-18 T10)
- `item_app_details_appop.xml` and `item_app_details_perm.xml` now mark
  `perm_description`, `perm_protection_level`, `op_mode_running_duration`
  and `op_accept_reject_time` as `textIsSelectable="true"`. Long-press to
  copy permission/op descriptions and runtime metadata directly from the
  list — matches Universal Android Debloater's selectable-description
  affordance.

### Added — Appearance: Preview new design (BETA) toggle (premium polish v0.4.x foundation)
- New Settings → Appearance → "Preview new design (BETA)" switch
  (default OFF, key `PREF_PREMIUM_PREVIEW_BOOL`). When enabled the
  app inflates the v2 design system: a refined teal-leaning palette
  with crisper contrast tiers, tightened typography (no letter-
  spacing hangs on titles), pill-shaped FABs and search surfaces,
  and outlined card variants that respect the layered surface
  hierarchy. Pure-black mode routes to `AppTheme.V2.Amoled` so the
  premium look composes with the existing AMOLED preference.
  Layouts and widget IDs are intentionally untouched in this
  release; only the theme/token plane changes. Restart applies.
  Resources copied verbatim from `design/impl/values/{themes,colors,
  dimens}-v2.xml`; rollout plan: `design/plan/3-rollout.md`.

### Added — Backup: Android 14+ "Keep device awake" warning toast (ROADMAP iter-18 item closed)
- When a backup operation begins on Android 14+ (`SDK_INT >=
  UPSIDE_DOWN_CAKE`), NG now displays a long Toast asking the user
  to keep the device awake and AppManager open until the backup
  finishes. Mitigates Android 14's tightened JobScheduler quotas
  and aggressive Doze kills of long-running foreground services.
  Mirrors Neo Backup 8.3.17 behavior. Source: ROADMAP S135.

### Audit — App list / Finder search history (ROADMAP iter-18 item closed)
- Audited persistent search-term storage per Inure build107.0.1
  privacy posture. NG's `SearchView` usage is already session-only
  in memory — `recent_search`, `searchHistory`,
  `SearchRecentSuggestionsProvider` grep all return zero hits.
  No persistent storage exists; no remediation needed. Source:
  ROADMAP S131.

### Added — App Info: Device page size row (ROADMAP iter-18 item closed)
- New "Device page size" row in App Info under Primary ABI for any
  app with native code. Populated via `Os.sysconf(_SC_PAGESIZE)` and
  rendered as "4 KB", "16 KB (page-size compatibility required)",
  raw bytes for any other value, or "Unknown" if the syscall throws.
  Pairs with the per-lib 16KB-alignment indicator (iter-11) so
  16k-incompatible libs visibly explain the warning instead of
  looking spurious on 4k devices. Source: Termux v0.118.3
  page-size detection (ROADMAP S126).

### Docs — ROADMAP iter-18 research (no code change)
- Research-only iteration. 29 new candidate items added under a new
  "Iter-18 Research Additions" section in `ROADMAP.md`, drawn from
  Shizuku v13.6.0, Magisk v30.7, KernelSU v3.2, Termux v0.118.3,
  Apktool v3.0.2, JADX v1.5.5, APKEditor v1.4.7-8, Hail v1.10.0,
  Inure build107, Material Files v1.7.4, SD Maid SE v1.7.2-rc0,
  UAD-NG v1.2.0, Neo Backup 8.3.17, androidx.glance, and Android
  16/17 platform docs. Highlights: Shizuku trusted-WLAN auto-start
  banner, Magisk `--drop-cap` opt-in semantics surface, KernelSU
  sulog/seccomp parity, Android 16 `SDK_INT_FULL` plumbing audit,
  JobScheduler quota stop-reason surfacing, APKEditor smali
  comment-level "basic", Hail-style auto-freeze QS tile,
  Inure-style AppOps IGNORE flag, UAD-style cross-user package
  state detection, Neo-Backup-style backup sharing button.
  Sources S121–S136 appended to the appendix; baseline line bumped
  with iter-18 summary.

### Added — Settings: Battery optimization entry (ROADMAP Trivial closed)
- New "Battery optimization" preference under Settings → Troubleshooting.
  Summary reflects the current `PowerManager.isIgnoringBatteryOptimizations()`
  state and refreshes on resume. Tap routes to the per-app request prompt
  (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) when optimized, or to the
  system-wide list (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) when
  already exempt so the user can revoke. Pre-M devices see a disabled
  entry with explanatory copy. Manifest now declares
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

### Added — AppType filter: Play App Signing + Overlay flags (eng-debt TODO partially closed)
- The AppType filter (used by Saved Filters and Finder) gains two
  previously-stubbed flags: **Uses Play App Signing** (APK signed by
  Google rather than the developer's release key) and **Overlay app**
  (Resource Runtime Overlay declaring an `<overlay>` manifest tag).
  Both flags work in `with_flags` and `without_flags` modes.
- New `IFilterableAppInfo.usesPlayAppSigning()` / `isOverlay()` methods
  implemented on both `FilterableAppInfo` (eager via
  `PackageUtils.usesPlayAppSigning` and `PackageInfoCompat2.getOverlayTarget`)
  and `ApplicationItem` (lazy via `fetchPackageInfo()`). PWA and
  short-code remain on the TODO list pending a stable detection signal —
  TWA detection requires manifest service-tag sniffing and short-code
  isn't exposed by `PackageManager`.

### Added — Code Editor: language / tab-size / go-to-line pickers (ROADMAP T14 ×3 closed)
- **Language toolbar button** now opens a popup listing all seven
  tmLanguage-backed languages bundled in `assets/languages/`
  (java / json / kotlin / properties / sh / smali / xml). Picking switches
  the syntax highlighter (`mEditor.setEditorLanguage`) and persists via
  the new `CodeEditorViewModel.setLanguage()` setter; the indent-mode
  label re-renders against the chosen language's `useTab/useSpace`
  default.
- **Tab-size toolbar button** opens a 2 / 4 / 8 popup wired to
  `mEditor.setTabWidth(n)` so the user can override the language default
  for files like Makefiles that need real tabs.
- **Position toolbar button** now opens a "Go to line" dialog
  (`TextInputDialogBuilder` numeric input) and moves the cursor via
  `mEditor.setSelection(line - 1, 0)`; out-of-range input clamps to
  `[1, lineCount]`.
- The hardcoded "tabs"/"spaces" suffix on the indent label now reads
  from `R.plurals.editor_tab_size_option_{tabs,spaces}` so non-English
  locales render the correct plural form. Closes the four `CodeEditorFragment.java`
  TODOs (`13/9/22 Display all the supported languages`, `13/9/22 Enable
  setting custom tab size`, `13/9/22 Enable going to custom places`,
  `13/9/22 Use localization`).

### Changed — Plural string audit (ROADMAP T10 closed)
- Three remaining pluralizable count strings converted to `<plurals>`:
  `main_status_showing_apps` ("Showing N of M apps"),
  `main_status_all_apps` ("Showing N apps"), and
  `bar_chart_content_description` ("Bar chart with N data points").
  Callers in `MainActivity.updateListStatus` and
  `BarChartView.updateContentDescription` now use
  `getQuantityString()` so locales whose plural form differs by count
  (Russian / Polish / Arabic / etc.) render correctly. Orphan
  `selected_items_accessibility_description` (no callers) removed.
- The remaining `%d`-using strings in `values/strings.xml` describe
  IDs, positions, range bounds, or "X of Y" composites — none are
  pluralizable, so the audit is closed.

### Added — Share profile as JSON (ROADMAP T8 closed)
- New **Share as JSON** popup action on each profile in the Profiles
  list (`action_share` between Export and Shortcut). Sends the profile's
  pretty-printed JSON via `Intent.ACTION_SEND` — Telegram / KDE Connect
  / email / Gmail draft / Slack pick it up directly, no SAF round-trip
  required. The wire format is identical to what `Export` writes, so
  the receiving NG instance can re-import via the existing Import
  action verbatim. File-export remains for share targets that need an
  attachment.
- The companion file-roundtrip Import + Export paths were already wired
  in `ProfilesActivity` (`ActivityResultContracts.GetContent` /
  `CreateDocument("application/json")`); ROADMAP row was stale, now
  closed.

### Added — Signing-cert SHA-256 chip in App Info (ROADMAP T18 closed)
- New "Sign · SHA-256 21:5F…38:6C" chip in the App Info tag cloud
  surfaces the colon-separated, upper-case SHA-256 fingerprint of the
  current signing certificate. Tap opens a Material dialog showing the
  full digest with a one-tap **Copy** button so users can paste the
  fingerprint directly into AppVerifier or compare against
  `apksigner verify --print-certs` output without leaving NG. Single-
  signer APKs only — multi-signer cases stay routed through the existing
  icon-tap verify-from-clipboard flow.
- Backed by `AppInfoViewModel.computeSigningCertSha256()` (worker-side
  via `PackageUtils.getSignerInfo` + `DigestUtils.SHA_256`); result is
  cached on `TagCloud.signingCertSha256`.

### Changed — VirusTotal poll-wait scales with upload size (engineering-debt TODO closed)
- `VirusTotal.fetchFileReportOrScan` now scales the *first* poll wait
  by file size via the new `computeInitialPollWait(fileSize)` helper —
  roughly +1 s per MB above a 10 MB threshold, clamped to [60 s, 240 s].
  Avoids burning the 4 req/min free-API rate-limit quota on a large
  upload that hasn't finished engine processing yet. Subsequent polls
  remain at the 30 s rate-limit floor. Closes the inline TODO at
  `VirusTotal.java` (originally filed 2022-05-23) and the matching
  Engineering Debt Register row.

### Added — Root manager detection: Magisk / KernelSU / APatch / ZygiskNext (ROADMAP T5 ×3 closed)
- New `runner/RootManagerInfo` helper probes `/data/adb/{magisk,ksu,ap}` via
  the privileged shell when root is granted, and falls back to a
  `PackageManager` lookup of the manager apps
  (`com.topjohnwu.magisk`, `me.weishu.kernelsu`, `com.rifsxd.ksunext`,
  `me.bmax.apatch`) when it isn't. Whenever a non-NONE manager is detected
  through the shell, a follow-up `[ -d /data/adb/modules/zygisksu ]`
  identifies the ZygiskNext layer.
- Onboarding sheet (`OnboardingFragment.refreshCapabilityStatuses`) now
  appends the resolved manager name (and " + ZygiskNext" if applicable)
  to the Root status line — e.g. "Detected · KernelSU + ZygiskNext". The
  probe runs on a background thread (one shell round-trip), result is
  posted back to the main thread, and the suffix update is idempotent so
  the Re-check button can be tapped repeatedly without stacking suffixes.
- Closes ROADMAP T5 rows: KernelSU Detection, APatch Detection,
  ZygiskNext Detection. SuperKey / per-module-count surfacing for APatch
  and ZygiskNext error-count surfacing remain on the Privilege
  Health-Check Screen row.

### Added — Stable signing-cert fingerprint URL (ROADMAP T1 closed)
- New [`docs/fingerprints.txt`](docs/fingerprints.txt) publishes the SHA-256
  signing-cert fingerprint in a comment-tolerant `package:` / `sha256:`
  record format (SD Maid SE precedent), served via the stable
  `raw.githubusercontent.com/.../docs/fingerprints.txt` URL — AppVerifier
  and similar tooling can fetch it programmatically without scraping the
  README. README "Verifying releases" section now points users at the URL.

### Added — Android 17 ProfilingManager OOM/anomaly triggers (ROADMAP T4 closed)
- New `misc/ProfilingTriggerHelper.registerTriggersIfSupported(Context)`
  registers `TRIGGER_TYPE_OOM` and `TRIGGER_TYPE_ANOMALY` via reflection on
  API 37+ devices, so the system auto-captures heap profiles when
  AppManagerNG hits low-memory or anomaly conditions during JADX decompile
  or APK parsing. Silent no-op on anything below API 37 and on any
  reflective lookup failure (compileSdk is still 36 so the profiling
  classes are not present at build time).
- Wired from `AppManager.onCreate()` once per process. The harvest +
  diagnostic-ZIP-attach side of the workflow is deferred until API 37 is
  available on a real device for end-to-end test.

### Compliance — Android 17 per-app Keystore key-cap audit (clean; ROADMAP T2 closed)
- Audit confirms NG can never exceed Android 17's 50,000-key per-app
  `AndroidKeyStore` cap: it generates at most **two** static, idempotently
  guarded aliases (`aes_local_protection` on API ≥ M, plus a legacy
  `rsa_wrap_local_protection` on pre-M devices) — both in
  `CompatUtil.getAesGcmLocalProtectionKey()` behind `containsAlias`
  checks. All backup-crypto paths route through a file-backed BKS
  keystore (`am_keystore.bks` via `KeyStoreManager`) which is outside
  the platform-managed Keystore. No remediation needed; roadmap row
  closed. Audit at
  [docs/audits/2026-05-02-android17-keystore-key-cap.md](docs/audits/2026-05-02-android17-keystore-key-cap.md).

### Changed — Pre-emptive Android 18 share-intent compliance (ROADMAP T3 closed)
- All seven outgoing `ACTION_SEND` / `ACTION_SEND_MULTIPLE` paths that carry
  a content URI (App Info APK share, log viewer attachment chooser, code
  editor share, single + multi file-manager share, diagnostic export, crash
  report) now set both `FLAG_GRANT_READ_URI_PERMISSION` and an explicit
  `ClipData` so the chooser target keeps receiving read access once
  Android 18 removes the implicit auto-grant for SEND/SEND_MULTIPLE/
  IMAGE_CAPTURE. Multi-URI shares from the file manager now build a
  multi-item `ClipData` rather than relying on the EXTRA_STREAM list alone.
- Audit and full inventory at
  [docs/audits/2026-05-02-android18-implicit-uri-grant.md](docs/audits/2026-05-02-android18-implicit-uri-grant.md).
  No `IMAGE_CAPTURE` callers in source; `PackageInstaller` install path
  streams via `openWrite()` and is unaffected.

### Changed — Utils.java flag-string i18n (ROADMAP T3 closed)
- `Utils.getSoftInputString`, `getServiceFlagsString`,
  `getActivitiesFlagsString`, and `getInputFeaturesString` now read their
  flag labels from `strings.xml` (`soft_input_flag_*`, `service_flag_*`,
  `activity_flag_*`, `input_feature_*`) via `ContextUtils.getContext()`,
  so the App Details Activities / Services / Other tabs respect the
  device locale instead of hardcoded English.
- `Utils.getProtectionLevelString` keeps Android's canonical manifest
  `android:protectionLevel="..."` tokens (`dangerous`, `signature`,
  `signature|privileged`, etc.) untranslated by design — they are
  technical identifiers, and `AppDetailsPermissionsFragment` does a
  `protectionLevel.contains("dangerous")` check that must keep working.
  Replaced the stale `FIXME` with a comment documenting the rationale.

### Added — Settings: Mode-of-Ops live capability refresh
- Capability badges (Root / Wireless ADB / USB ADB) now refresh every time
  the Mode-of-Ops settings screen resumes. Toggling Wireless debugging in
  Quick Settings or granting root in another app while Settings is in the
  background now reflects on return — no need to leave the screen entirely.

### Added — Android TV launcher parity (audit)
- Confirmed `SplashActivity` already declares `LEANBACK_LAUNCHER`, the
  manifest declares leanback `uses-feature` with `required="false"` and
  optional touchscreen, and the `ic_banner` mipmap is wired. AppManagerNG
  appears on Android TV / Google TV launchers with no additional work.
  ROADMAP item closed.

### Added — App Info bloatware safety rating
- **Bloatware tag now surfaces the safety call directly** — App Info →
  tag cloud previously showed a generic "Bloatware" chip coloured by
  removal type. Tag text now reads "Bloatware · Safe", "Bloatware ·
  Replace", "Bloatware · Caution", or "Bloatware · Unsafe", so users can
  read the recommendation without tapping into the details dialog.
  Colour is preserved (`ColorCodes.getBloatwareIndicatorColor`).

### Added — Onboarding replay (v0.4.0)
- **Replay welcome wizard** action in Settings → Troubleshooting; clears
  `PREF_ONBOARDING_SHOWN_BOOL` and immediately surfaces the privilege-mode
  picker (Auto / Root / Wireless ADB / ADB-TCP / No-root) so power users
  and testers can revisit the explainers without a fresh install. The
  picker writes the flag back on pick/cancel, so the flow self-heals.
- **Replay quick tour** action in Settings → Troubleshooting; clears
  `PREF_MAIN_TOUR_SHOWN_BOOL` so the main-list tour re-arms on the next
  launch. Toast confirms the reset.
- **Active-mode highlight in onboarding** — when the wizard opens (first
  run or replay), the card matching the currently saved mode is ringed
  with a 2dp `colorPrimary` stroke, so users replaying see at a glance
  which mode is in effect. A11y description is prefixed with "Currently
  active." for screen-reader parity.
- **Pick-Root-without-detection guardrail** — when a user taps the Root
  card and `Ops.hasRoot()` returns false, a confirmation dialog explains
  the situation (root managers can hide su until first request, but most
  ops will fail until granted) and lets them cancel without burning the
  onboarding-shown flag.
- **Re-check capabilities button** in the onboarding sheet — refreshes
  the Root / Wireless-ADB / USB-ADB badges and the active-mode highlight
  in place without dismissing the sheet, so users who toggle Wireless
  debugging from quick-settings or grant root from another app can see
  the new state immediately. Snackbar confirms the refresh.

### Added — Premium facelift design system (foundation)
- **`design/` deliverable folder** (audit, spec, impl, plan, README) — full
  v2 design system reference: palette, typography, 4dp spacing ladder,
  elevation tokens, motion vocabulary, iconography choice, and 5 drop-in
  reference XML files (themes-v2, colors-v2, dimens-v2, item_main_v2,
  activity_main_v2). Read order: [design/README.md](design/README.md).
- **Pain-point inventory** ([design/audit/4-painpoints.md](design/audit/4-painpoints.md))
  catalogues 16 dated surfaces with concrete fix proposals and benchmark
  references (Linear, Arc, Things 3, 1Password 8, Obsidian).
- **4-release rollout plan** ([design/plan/3-rollout.md](design/plan/3-rollout.md))
  for shipping the facelift behind a Pro Mode "Preview new design" toggle
  (v0.4.x foundation → v0.5.x top-5 migration → v0.6.x long tail → v0.7.x
  toggle removal).

### Added — Main list polish (preview)
- **Semantic threshold tinting** for the tracker indicator (green ≤4,
  amber 5-19, red ≥20) and the dangerous-permission badge (success when
  zero granted, warning below 50%, danger 50%+).
- **Risk-tinted package name** on app rows when a heuristic combination
  of granted-perms + tracker count crosses a danger threshold.
- **Restyled main status banner** with stronger metric typography,
  clearer "filtered N of M" affordance, and tonal background.
- **Refined empty state** (`view_main_empty_state.xml`) with explicit
  active-filter description and reset-filter affordance.
- New `ColorCodes` semantic colors and `Widget.AppTheme.Chip.MainFilter` /
  `Widget.AppTheme.Chip.MainSuggestion` chip styles.

### Added — Settings & onboarding polish
- Tighter `m3_preference*.xml` row treatments (preference category
  indicator, dual-pane divider, focused-pane state).
- Onboarding fragment redesign: tonally-tinted mode-of-operation cards,
  status background drawables, plain-language privilege explainer.
- Reorganised `preferences_main.xml` to match the new tier hierarchy.

### Added — Operation Activity Log (ROADMAP T8 closed)
- Persistent journal of every operation AppManagerNG performs (freeze,
  backup, batch, install, profile execution). Per-entry metadata: target
  app(s), operation type, timestamp, mode (root/ADB/Shizuku/no-root),
  risk tier, success/failure, scope.
- `OpHistoryActivity` reachable from main overflow → "History" with
  aggregate summary, package/operation/mode/target search, success/risk
  filter chips, risk-tinted card borders, FAB.
- New helpers: `OperationJournalMetadata`, `OperationPreflight`,
  `OperationHistoryExporter` (with Robolectric test). Per-entry copy/
  delete actions; rerun preflight gates dangerous reruns.
- Debug-only "Add sample entries" menu action under `BuildConfig.DEBUG`
  for development verification — never visible in release builds.

### ROADMAP additions (research-driven)
- New T19 tier: **Package-Aware Storage Analysis** (App Details Storage
  Panel, Leftover Detection After Uninstall, APK Duplicate Finder,
  Backup Duplicate Cleaner — SD Maid SE / UAD-NG models).
- T2 row: **Android 17 Keystore Per-App Key Cap** (50,000-key audit
  before targetSdk=37 bump).
- T3 row: **Android 18 Implicit URI Grant Removal** (preemptive
  `grantUriPermission()` audit before Android 18 ships).
- T7 rows: **Finder Relevance-Based Search Scoring** (Levenshtein) and
  **Finder Description-Field Search** (debloat-list metadata).
- T8 row: **Multi-Tag per App** (Hail v1.10.0 model, many-to-many join
  table on the existing Room schema).
- T9 rows: **Permission Policy Flags Display** + **MiUI-Specific AppOps
  Mapping** (Inure 106.5.0 model).
- T12 rows: **Native Library Sizes in App Details** + **Batch APK
  Installer from File Manager** (Inure 107.0.0/.1 model).
- New sources S65–S68 logged. Full research at
  [docs/research/2026-05-02-android-power-tools.md](docs/research/2026-05-02-android-power-tools.md).

### Compliance
- **Android 17 `MessageQueue` audit (clean)**: lock-free `MessageQueue`
  shipping in Android 17 / targetSdk=37 crashes apps that reach into
  private fields via reflection. Recursive sweep across all source roots
  returned zero matches; root-shell IPC routes through libsu shell
  processes, not `MessageQueue` reflection. Audit at
  [docs/audits/2026-05-02-android17-messagequeue.md](docs/audits/2026-05-02-android17-messagequeue.md).
- **Adaptive Layout for Large Screens audit (clean)**: Android 16 /
  targetSdk=36 ignores `screenOrientation`, `resizeableActivity=false`,
  and aspect-ratio limits on ≥ 600dp displays. Manifest sweep across 43
  activities returned zero fixed-orientation declarations, zero resize
  blockers, zero aspect-ratio limits. Audit at
  [docs/audits/2026-05-02-adaptive-layout.md](docs/audits/2026-05-02-adaptive-layout.md).

### Added
- **Tablet density overrides** (`app/src/main/res/values-w600dp/dimens.xml`,
  `libcore/ui/src/main/res/values-w600dp/dimens.xml`): bumps icon sizes,
  list-row min-height, font sizes, and medium/large/very-large padding
  tiers when the available width is ≥ 600dp (tablets, foldables in
  landscape, Chromebooks, free-form windowed mode). Phone-sized devices
  read the existing `values/dimens.xml` unchanged. No per-layout edits
  required — the new values propagate to every layout already consuming
  these tokens.
- **Sort by Dangerous Permissions**: new `SORT_BY_DANGEROUS_PERMS` option in
  the main app list (Sort menu). Mirrors the `SORT_BY_TRACKERS` shape —
  primary key is granted dangerous perms (most-privileged-by-actual-grant
  apps surface first); secondary key is total declared dangerous perms.
  Wires `dangerous_perm_total` / `dangerous_perm_granted` (Room schema v9)
  into the user-facing UI.
- **Obtainium config** (`docs/distribution/obtainium-config.json`):
  ready-to-import Obtainium AppConfig pointing at GitHub Releases with
  artifact regex for the signed `AppManagerNG-<version>-{arm64-v8a,universal}.apk`
  files (auto-ABI selection enabled). README "Install" section adds an
  "Install via Obtainium" subsection with paste-and-go instructions plus
  an AppVerifier pairing tip.

### Compliance
- **`elegantTextHeight` audit (clean)**: Android 16 / targetSdk=36 silently
  ignores `android:elegantTextHeight`; affects Arabic/Thai/Indic text
  rendering. Recursive sweep across all source roots returned zero
  matches — no remediation required. Audit recorded at
  [docs/audits/2026-05-01-elegant-text-height.md](docs/audits/2026-05-01-elegant-text-height.md).

## v0.3.0 — 2026-06-05

Platform compliance, bug fixes, and observability hardening.

### Fixed
- **BarChartView accessibility** (`usage/BarChartView.java`): replaced deprecated
  `announceForAccessibility()` (ignored by TalkBack on Android 16+) with
  `ViewCompat.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE)`. The 3
  redundant announcement calls alongside virtual-view events were removed; 1 was replaced
  with `updateOverallContentDescription()` so the live region fires on data change.
- **KeyStoreUtils secure memory** (`crypto/ks/KeyStoreUtils.java`): `StringBuilder`
  (non-zeroable) replaced with `CharArrayWriter`; key material byte arrays explicitly zeroed
  after `generatePrivate()` to reduce key-in-memory exposure window.
- **ABX editor** (`editor/CodeEditorViewModel.java`): Android Binary XML files can now be
  opened for inspection in the code editor. Write-back is blocked via `canWrite() = false`
  when `mXmlType == XML_TYPE_ABX` to prevent lossy typed-value → string round-trip.
- **ActivityInterceptor `ACTION_OPEN_DOCUMENT`** (`intercept/ActivityInterceptor.java`):
  `FLAG_ACTIVITY_NEW_TASK` was being added to the document-picker intent, which broke result
  delivery (Android bug: new-task flag + `startActivityForResult` never delivers result).
  Flag is now stripped with `removeFlags()` before launching the picker.

### Added
- **Crash log persistence** (`misc/AMExceptionHandler.java`): crashes are written to
  `getFilesDir()/crashes/crash_TIMESTAMP.log` (capped at 10 files). The crash share
  notification now attaches the log file as a `content://` URI via FmProvider. Upstream
  hardcoded email removed; subject updated to "AppManager NG: Crash Report".
- **Diagnostic export** (`misc/DiagnosticUtils.java` + Settings → About): new "Export
  Diagnostic Report" preference bundles device info, all crash logs, and the last 2 000
  logcat lines (main/system/crash buffers) into a ZIP file and opens the share chooser.
- **CodeQL on main** (`.github/workflows/codeql.yml`): analysis now triggers on pushes to
  `main` (was limited to `master`); `workflow_dispatch` added for on-demand scans.

### Deferred
- `Utils.java` flag-string i18n (5 methods × ~50 string resources) — deferred to v0.4.0.
  Caller Context injection required before extracting strings.

Identity milestone: AppManagerNG now has its own install identity, signing key, and release
pipeline, fully separated from the upstream package.

### Added
- **`applicationId` rename**: install identity changed from `io.github.muntashirakon.AppManager`
  to `io.github.sysadmindoc.AppManagerNG`. Source namespace kept at
  `io.github.muntashirakon.AppManager` (full namespace rename is future work).
- **New release keystore**: `AppManagerNG-release.jks` — 4096-bit RSA, 10,000-day validity.
  SHA-256: `21:5F:B4:70:63:2E:A6:CD:59:A4:BA:AB:35:0A:9E:0B:99:AD:11:0F:DD:FA:F5:A9:EA:64:61:E5:D0:C2:38:6C`
- **GitHub Actions release pipeline** (`.github/workflows/release.yml`): tag push → build →
  sign → upload arm64-v8a + universal APKs to GitHub Releases.
- **CONTRIBUTING.md**: NG-specific contribution guidelines (replaces upstream CONTRIBUTING.rst
  reference); covers AI code policy, commit format, upstream sync protocol, translation note.
- **ROADMAP.md**: comprehensive prioritized roadmap through v0.6.0+ (17 themes, 37 sources).
- **AppVerifier fingerprint** in README for release verification.
- **16KB page size compliance**: `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` CMake argument
  added to `app/build.gradle` for Android 15+ physical device compatibility.

### Fixed
- `LocalFileOverlay.java`: hardcoded application ID fallback now uses `BuildConfig.APPLICATION_ID`
  instead of a literal string, so it tracks applicationId changes automatically.
- `settings.gradle`: `rootProject.name` updated to `AppManagerNG`.

## v0.1.0 — 2026-04-30

Initial AppManagerNG release. Repo bootstrap from upstream
[App Manager](https://github.com/MuntashirAkon/AppManager) commit
[`3d11bcb`](https://github.com/MuntashirAkon/AppManager/commit/3d11bcbc399d3a4f995b544e26d86bd80487fd32)
(2026-04-16, upstream tag context: post-v4.0.5).

### Added
- AppManagerNG-branded README.md with shields.io badges, GPL-3.0-or-later notice, and upstream credit
- CHANGELOG.md (this file)
- Branding/logo prompts directory (`branding/logo-prompts.md`)

### Changed
- App display name (`app_name` resValue): `App Manager` → `AppManagerNG` (release), `AM Debug` → `AM-NG Debug` (debug)
- Android `versionName`: `4.0.5` → `0.1.0`; `versionCode`: `445` → `1`

### Preserved (unchanged from upstream)
- All Java/Kotlin/Native sources
- Package name (`io.github.muntashirakon.AppManager`) and namespace — rebrand deferred to v0.2.0
- License files: `COPYING`, `LICENSES/` directory (REUSE-compliant), per-file SPDX headers
- Build configuration (Gradle, AGP version, dependencies, signing config)
- Documentation: `BUILDING.rst`, `CONTRIBUTING.rst`, `PRIVACY_POLICY.rst`, `docs/`
- F-Droid metadata (`fastlane/`)
- Submodule pointers (`scripts/android-libraries`, `scripts/android-debloat-list`)

### Roadmap
- **v0.2.0** — applicationId + namespace rename to `io.github.sysadmindoc.AppManagerNG`; fresh keystore
- **v0.3.0** — Material 3 dashboard refresh + Pro-mode toggle for advanced features
- **v0.4.0** — Onboarding flow (root/ADB capability detection + plain-language explainer)
- **v0.5.0** — Settings reorganization + in-app search and help
