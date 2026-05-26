<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# AppManagerNG — Research and Feature Plan (2026-05-25, pass 2)

> **Scope.** Follow-on research pass after the 2026-05-25 plan (NF-01..NF-18 +
> EI-01..EI-10) shipped in full across iter-141 → iter-146. Every row in that
> plan is now ticked or explicitly parked with on-disk rationale. This file
> finds **new** opportunities by digging into surfaces the earlier plan
> under-mined (Activity Interceptor, Running Apps, One-Click Ops, Scanner,
> Logcat, Code Editor, App Details Components tab, File Manager non-favorites).
> See [`RESEARCH_FEATURE_PLAN_2026-05-25.md`](RESEARCH_FEATURE_PLAN_2026-05-25.md)
> for the prior plan and its closed rows.
>
> Authored 2026-05-25 against working tree at `9e7e693` (`origin/main`).

---

## Executive Summary

AppManagerNG is shipping faster than its planning corpus can re-mine — six
research passes in 24 hours closed every actionable P0-P3 row and tagged v0.5.0
(versionCode 7). The strongest current shape is the **privilege story**
(root / Shizuku / Sui / ADB / Dhizuku detection + diagnostics) and the
**backup engine** (metadata v7, HKDF-derived per-archive keys, scheduled work,
provider-backed destinations). The weakest current shape is the **App Details
Components tabs** (Activities / Services / Receivers / Providers all read-
only despite the data being right there) and a cluster of stale-TODO surfaces
that block real workflows: privileged-launch result loss in Activity
Interceptor, Running-Apps rules not persisted, One-Click Ops clear-cache
single-volume only. The highest-value direction for the next quarter is
**closing the four-year TODO cliff** under `intercept/`, `runningapps/`, and
`oneclickops/`, then **making the App Details Components tabs actionable** so
the surface that currently *shows* every activity / service / receiver /
provider can also *exercise* them.

### Top opportunities, prioritised

1. **NF-19 — Activity Interceptor result-bridge for privileged launches.**
   Three TODOs (2022-02 + 2024-06) leave `startActivityForResult` returning
   nothing when the user requested a root / Shizuku launch. Verified at
   [`ActivityInterceptor.java:954,961,972`](app/src/main/java/io/github/muntashirakon/AppManager/intercept/ActivityInterceptor.java#L954).
2. **NF-20 — Running Apps "Prevent background run" rule persistence.** Toggle
   currently writes `OP_RUN_ANY_IN_BACKGROUND` to AppOps but **not** to the
   component-rules DB; survives the session but not export/import or device
   reboot. Verified at
   [`RunningAppsViewModel.java:275`](app/src/main/java/io/github/muntashirakon/AppManager/runningapps/RunningAppsViewModel.java#L275).
3. **NF-21 — One-Click Ops multi-volume cache trim.** Clears internal storage
   only; the 2021-08-30 TODO asks for all volumes. Verified at
   [`OneClickOpsViewModel.java:257`](app/src/main/java/io/github/muntashirakon/AppManager/oneclickops/OneClickOpsViewModel.java#L257).
4. **NF-22 — App Details Activities tab → intent-launch builder.** Power
   users (devs, testers) cannot pass extras or flags to a non-default
   activity entry point. Today the launch is package + class + user + root
   only.
5. **NF-23 — Logcat in-viewer search + structured (CSV/JSON) export.** The
   `SearchCriteria` struct is wired but no toolbar `SearchView` ever
   surfaces it; saved logs export only as plain text.
6. **NF-24 — Tracker scanner: per-organisation summary + filter chips.** The
   εxodus dataset already classifies trackers; the scanner list flattens
   them with no rollup or filter.
7. **NF-25 — File Manager bulk rename (regex template).** Single-file
   `RenameDialogFragment` only; multi-select exists for delete / copy /
   move / archive but rename is excluded.
8. **EI-11 — Code Editor: search widget needs a close button.** 2023-04-21
   FIXME. Search currently dismisses by tapping the search icon a second
   time.
9. **EI-12 — Code Editor: line-separator change doesn't apply
   retroactively.** 2022-09-18 TODO. CRLF/LF/CR toggle updates the chip
   text but not the buffer contents.
10. **EI-13 — Main list: "select all matching this chip"**. Filter chips
    exist but multi-select still requires per-app long-press.

---

## Evidence Reviewed

### Files / directories inspected this pass

- **TODO/FIXME corpus** under `app/src/main/java/io/github/muntashirakon/AppManager/`
  — directly verified the three load-bearing TODO claims this plan builds on
  (Activity Interceptor result-loss; Running Apps rule persistence;
  One-Click Ops single-volume).
- `app/src/main/java/.../intercept/ActivityInterceptor.java` (1357 lines, 6
  TODOs).
- `app/src/main/java/.../runningapps/RunningAppsViewModel.java` (~340 lines).
- `app/src/main/java/.../oneclickops/OneClickOpsViewModel.java`.
- `app/src/main/java/.../scanner/ScannerFragment.java` +
  `scanner/NativeLibraries.java`.
- `app/src/main/java/.../logcat/{Saved,Live}LogViewerFragment.java`.
- `app/src/main/java/.../editor/CodeEditorFragment.java` (search widget +
  line-separator paths).
- `app/src/main/java/.../sharedpref/SharedPrefsViewModel.java` (the
  AtomicExtendedFile TODO from 2022-02-08).
- `app/src/main/java/.../details/AppDetailsComponentsFragment.java`
  (Activities / Services / Receivers / Providers tabs — verified the
  `ActivityInterceptor.EXTRA_*` plumbing at lines 799-803).
- `app/src/main/java/.../usage/BarChartView.java` (the `mManualMinValue`
  field unused).
- `app/src/main/java/.../fm/FmFavoritesManager.java` (favorites verified;
  bulk rename absence verified).
- `app/src/main/java/.../main/MainListOptions.java` (19 sort orders;
  no select-all-matching-chip affordance).
- `app/src/main/AndroidManifest.xml` (export surface clean).

### Git history reviewed

- 50 commits `v0.4.2..9e7e693` (iter-141 → iter-146, 2026-05-18 →
  2026-05-25). v0.5.0 cut at `e46ea26`. All P0-P3 rows from the prior plan
  closed.

### External sources

- `gh api repos/MuntashirAkon/AppManager/issues?state=open` — top 10 by
  reaction count cross-checked against NG roadmap:
  - #55 Shizuku (31) — shipped iter-N.
  - #61 Routine Ops (21) — data layer iter-145, executor parked.
  - #464 Aurora/F-Droid unify (12) — rejected per ROADMAP.
  - #321 Finder (10) — shipped.
  - #122 Export App List (8) — shipped iter-98.
  - #516 DocumentsProvider (7) — open T13.
  - #138 APK Editor (7) — open T12.
  - #150 Systemless (4) — open T15.
  - #1718 Hide apps in profile (4) — addressed by iter-131.
  - #555 Scheduled backup (4) — shipped.

### Not verified this pass

- A device-level pass on Pixel 9a / S25 Ultra / Galaxy A57 for any of the
  surfaces below — every claim here is code-grounded but on-device
  validation is left to the implementer.
- TalkBack accessibility audit on iter-141 → iter-146 surfaces; T10 row
  remains open.

---

## Current Product Map

See `PROJECT_CONTEXT.md` for the canonical map. **Delta since the 2026-05-25
plan**: v0.5.0 (versionCode 7) tagged; in-app changelog viewer, global
Settings search, Glossary (14 topics), tracker-blocking intensity, multi-tag
store, scheduled-backup tappable diagnostics, package-visibility chip,
debloat impact preview, permission-inspector chip-row filter, privacy-
dashboard deep link, runtime-activity chip, ProfileTrigger data layer,
FilterPresetStore, keep-app-open hint, three new architecture docs
(filter-finder, routine-scheduler, permissions catalogue), the permissions
catalogue under `docs/policy/permissions.md`, and `docs/architecture/`
README updated. 138 unit tests. The repository is clean of P0-P3 work
modulo the two `parked` rows below.

---

## Feature Inventory — surfaces with new shortfalls

The prior plan's feature inventory covers shipped state. This pass adds rows
**only** for features the agent's deep-dive found rough edges in:

### Activity Interceptor (`intercept/ActivityInterceptor.java`)

- **User value**: inspect/edit/reroute the next intent the user fires.
- **Entry**: from any "share to" or "open with" chooser; from App Details
  → Activities tab → launch.
- **Maturity**: **Partial.** Six TODOs from 2021-08 → 2024-06 — most
  notably three "Support sending activity result back to the original app"
  at lines 88, 954, 961, 972, 1075. The unprivileged launch path works
  end-to-end (Issue #1767 fix at line 989). The privileged launch path
  loses the result silently because `ActivityManagerCompat.startActivity`
  has no callback channel back.
- **Improvement**: NF-19 (result-bridge), plus a minor fix for the line
  88 TODO ("Enable getting activity result for activities launched with
  root") and line 145 ("Add support for receiver flags").

### Running Apps (`runningapps/`)

- **User value**: process inspector; per-process actions (kill, force-stop,
  prevent background run).
- **Maturity**: **Partial.** "Prevent background run" toggle writes the
  AppOp but never persists to the component-rules DB — TODO since 2023-02-14.
  Decision survives the session via the OS but vanishes from
  exports/profiles, and a `pm clear` resets the AppOp without leaving any
  audit trail in NG's history surface.
- **Improvement**: NF-20.

### One-Click Ops (`oneclickops/OneClickOpsViewModel.java`)

- **User value**: bulk cache trim, leftover cleanup, freeze stale apps.
- **Maturity**: **Partial.** `trimCaches()` at line 254 passes
  `null /* internal */` to `freeStorageAndNotify`; 2021-08-30 TODO asks
  for all volumes. Modern devices with SD card + USB OTG see only a
  fraction of their cache reclaimed.
- **Improvement**: NF-21.

### Scanner (`scanner/ScannerFragment.java`)

- **User value**: εxodus tracker scan + ARSCLib library scan + AndroidX
  AndroidHiddenApiBypass detection.
- **Maturity**: **Complete but flat.** The εxodus tracker list and
  `android-libraries` library list render as long flat scrollers. No
  filter chips, no per-org rollup despite `TrackerCategory.categorize()`
  existing since iter-N. The `BloatwareOption.description_*` predicates
  shipped iter-12 but the scanner doesn't surface its own "filter by
  vendor / category / severity" controls.
- **Improvement**: NF-24.

### Logcat (`logcat/`)

- **User value**: in-app logcat viewer + filter + share.
- **Maturity**: **Partial.** A `SearchCriteria` struct exists in
  `LiveLogViewerFragment` but no toolbar `SearchView` ever surfaces it.
  Saved log export from `SavedLogViewerFragment` writes plain text only —
  no JSON / CSV variants. Two stale TODOs from 2022 question whether
  `onResume()` should scroll to bottom (suggesting nobody verified the
  UX).
- **Improvement**: NF-23.

### Code Editor (`editor/`)

- **User value**: edit XML / smali / shell / JSON / Java in-app.
- **Maturity**: **Complete + two known polish bugs.**
  - `CodeEditorFragment.java:447` — line-separator toggle (CRLF / LF / CR)
    updates the chip text but doesn't rewrite existing line endings.
    2022-09-18 TODO.
  - `CodeEditorFragment.java:604` — search widget has no close button;
    user has to tap the search icon a second time to dismiss. 2023-04-21
    FIXME.
- **Improvement**: EI-11, EI-12.

### App Details Components tabs (`details/AppDetailsComponentsFragment.java`)

- **User value**: list every activity / service / receiver / provider an
  app declares.
- **Maturity**: **Complete for *display*; missing every actionable verb.**
  - Activities tab launches with bare `ActivityInterceptor.EXTRA_*` only —
    no extras, no flags. Verified at lines 799-803.
  - Services tab: read-only. No start / stop affordance for users with
    Shizuku / root / ADB.
  - Receivers tab: read-only. No "Send broadcast" dialog.
  - Providers tab: read-only. No URI builder, no `ContentResolver.query`
    inspector.
- **Improvement**: NF-22 (Activities builder), NF-28 (Receivers
  broadcast), NF-29 (Services start/stop), NF-27 (Providers query).

### File Manager (`fm/`)

- **User value**: bundled file browser + archive create/extract + checksum
  + favorites.
- **Maturity**: **Strong for browsing, gaps in bulk + inspection.**
  Favorites are Room-backed (`FmFavoritesManager.java:16-51`); checksums
  ship (iter-109); recursive search ships (iter-108); ZIP create/extract
  ships (iter-90). Bulk rename is missing — `RenameDialogFragment` is
  single-file. Hex / binary viewer is missing — text viewer is the only
  raw inspection surface.
- **Improvement**: NF-25, NF-26.

### Main list (`main/MainActivity.java`, `MainListOptions.java`)

- **User value**: app list, quick filters, batch operations.
- **Maturity**: **Strong, with one bulk-select friction.** 19 sort orders;
  six quick-filter chips (User / System / Frozen / Running / Backed Up /
  Stopped). Long-press selects one app at a time; no "select every app
  matching the active chip" affordance.
- **Improvement**: EI-13.

### App Usage BarChart (`usage/BarChartView.java`)

- **Maturity**: **Partial.** A `mManualMinValue` field is declared but
  ignored in `onDraw` (line 308). Charts always start from 0 even when
  the user-supplied min-value would make small deltas visible.
- **Improvement**: EI-14.

### SharedPreferences editor (`sharedpref/SharedPrefsViewModel.java`)

- **Maturity**: **Partial.** 2022-02-08 TODO: "Use AtomicExtendedFile to
  better handle errors". Today a power loss mid-write can corrupt the
  prefs file.
- **Improvement**: EI-15.

### Dex/Smali viewer (`dex/DexClasses.java`)

- **Maturity**: **Partial.** FIXME on line 33: smali round-trip below
  API 26 unsupported (T12 dependency). Already documented in ROADMAP
  Eng-Debt Register; not a new finding.

---

## Competitive and Ecosystem Research

Limited fresh external mining this pass — the prior plan exhausted the
field. **One new signal**: upstream MuntashirAkon/AppManager open issues
(top 10 by reactions) all map onto already-shipped or parked NG rows.
Nothing new to mine there.

| Source | Signal | What this project should do |
|---|---|---|
| Upstream `MuntashirAkon/AppManager` #1718 "Hide some apps from profile" (4 reactions) | NG already addresses this through profile blocklists (iter-131). | No action; row is satisfied. |
| Upstream #138 "APK Editor (RE-Build APKs and More)" (50 comments, 7 reactions) | Long-running upstream request. NG keeps it in T12 + JADX 1.4.7 ceiling. | Stay parked behind JADX-android fork shipping 1.5.5. |
| **`yume-chan/VolumeManager`** ([S196] in ROADMAP) | Per-app volume control via Shizuku-driven AppOps. NG shipped this in iter-134 (audio-volume preset). | Closed; no action. |
| **Android-13+ Privacy Dashboard** | NG deep-links via `ACTION_REVIEW_PERMISSION_USAGE` (iter-144 NF-12). | The real-data integration (read PermissionControllerManager history in-app) remains unbuilt. Out of scope for this pass. |
| **`d4rken-org/sdmaid-se`** | CorpseFinder, leftover detection, per-app storage panel. ROADMAP T19 lists these as Storage Analysis. | Still parked; no new action this pass. |

---

## Highest-Value New Features

### NF-19 — Activity Interceptor result-bridge for privileged launches

- **User problem**: When the user picks "Resend (root)" from the
  Interceptor, the launched activity's `Activity.setResult` payload is
  dropped on the floor. `ACTION_OPEN_DOCUMENT`, `ACTION_PICK`, and any
  intent that expects a result silently fail with no Uri returned.
- **Evidence**: Verified TODOs at
  [`ActivityInterceptor.java:954,961,972`](app/src/main/java/io/github/muntashirakon/AppManager/intercept/ActivityInterceptor.java#L954),
  dated 2022-02-04 and 2024-06-04. The unprivileged path correctly
  forwards results (line 989 — Issue #1767 fix) so the contrast is
  visible to anyone exercising both code paths.
- **Proposed behavior**: A file-backed result journal under
  `FileCache.getGlobalFileCache().createCachedDir("intercept-results")`.
  The privileged launch path writes the originating activity's
  ComponentName + a journal id into the chained Intent; a tiny
  `InterceptResultReceiver` exported `<receiver>` listens for
  `io.github.sysadmindoc.AppManagerNG.action.INTERCEPT_RESULT`, reads
  the journaled context, then posts `setResult` + `finish` on the
  originating Activity. On API 33+ the same path can use
  `IBinder` handoff instead.
- **Implementation areas**: `intercept/ActivityInterceptor.java`,
  new `intercept/InterceptResultJournal.java`,
  new `intercept/InterceptResultReceiver.java` exported with
  `io.github.sysadmindoc.AppManagerNG.permission.AUTOMATION`,
  `AndroidManifest.xml`.
- **Risks**: TOCTOU on the journal file; mitigate with
  `AtomicExtendedFile` writes. The receiver must reject any caller
  whose UID isn't ourselves.
- **Verification**: Robolectric `InterceptResultJournalTest` for the
  pure-data path. On-device: Resend `ACTION_OPEN_DOCUMENT` as root,
  pick a file, confirm the calling activity receives the Uri.
- **Complexity**: **M**.
- **Priority**: **P1**.

### NF-20 — Running Apps "Prevent background run" persisted to component rules

- **User problem**: Toggling "Prevent background run" on a process in
  Running Apps writes `OP_RUN_ANY_IN_BACKGROUND = MODE_IGNORED` to AppOps
  but doesn't persist it to NG's component-rules DB. A reboot, a
  `pm clear`, or an app update silently resets the AppOp; the user has
  no audit trail in App Details → "App ops" or in profile exports.
- **Evidence**: Verified TODO at
  [`RunningAppsViewModel.java:275`](app/src/main/java/io/github/muntashirakon/AppManager/runningapps/RunningAppsViewModel.java#L275)
  dated 2023-02-14: "Store it to the rules".
- **Proposed behavior**: When the toggle fires, also write a
  `ComponentRule` row of type `RuleType.APP_OP` keyed on the package +
  AppOp number through the existing `ComponentsBlocker` write path. This
  is the same shape iter-N already uses for AppOps modifications from
  App Details and from Profile.apply.
- **Implementation areas**:
  `runningapps/RunningAppsViewModel.java`,
  reuse `rules/struct/AppOpRule.java` + `ComponentsBlocker.addAppOp()`.
- **Risks**: None new; existing AppOp rule path is mature.
- **Verification**: Pure-JVM test on the new write path; on-device
  cross-check that the rule shows up in App Details → AppOps after a
  Running Apps toggle.
- **Complexity**: **S**.
- **Priority**: **P0**.

### NF-21 — One-Click Ops multi-volume cache trim

- **User problem**: "Clear all caches" on a device with internal +
  removable storage only clears internal, silently leaving the SD-card
  / OTG cache. The user thinks they reclaimed space they didn't.
- **Evidence**: Verified TODO at
  [`OneClickOpsViewModel.java:257`](app/src/main/java/io/github/muntashirakon/AppManager/oneclickops/OneClickOpsViewModel.java#L257)
  dated 2021-08-30: "Iterate all volumes?"
- **Proposed behavior**: Enumerate
  `StorageManager.getStorageVolumes()` via the existing
  `StorageUtils.getAllStorageLocations()` (already used by Settings →
  Backup → Network destination), and call
  `PackageManagerCompat.freeStorageAndNotify(volumeUuid, size, flags)`
  per volume. Aggregate the per-volume result counts before posting
  `mTrimCachesResult`.
- **Implementation areas**: `oneclickops/OneClickOpsViewModel.java`,
  `utils/StorageUtils.java`.
- **Risks**: Some emulated SD-card mounts return their UUID as the
  internal-default. Test on emulator + physical SD + OTG.
- **Verification**: Robolectric volume-enumeration test; on-device
  before/after on a multi-volume device.
- **Complexity**: **S**.
- **Priority**: **P0**.

### NF-22 — App Details Activities tab: intent-launch builder

- **User problem**: Devs and testers want to launch a specific activity
  with custom extras and flags, not just the default zero-arg launch.
  Today the only path is to leave AppManagerNG, open adb, and run
  `am start -n pkg/cls --es key value` by hand.
- **Evidence**: `AppDetailsComponentsFragment.java:799-803` shows the
  current launch is package + class + user + root only. There is no
  extras editor.
- **Proposed behavior**: Long-press an activity → "Launch with
  advanced…" → a bottom sheet with:
  - extras key/value rows (type: String / int / boolean / long / String[]),
  - intent-flag multiselect chip group (NEW_TASK, CLEAR_TOP,
    SINGLE_TOP, REORDER_TO_FRONT, NO_HISTORY, EXCLUDE_FROM_RECENTS,
    REQUIRE_NON_BROWSER, FORWARD_RESULT),
  - action / category / mime-type free-text fields,
  - "Launch" routes through Activity Interceptor so it benefits from
    the unprivileged result forwarding (NF-19 makes the privileged
    case work too).
- **Implementation areas**:
  `details/AppDetailsComponentsFragment.java`,
  new `details/components/ComponentLaunchBuilderFragment.java`,
  new `R.layout.dialog_component_launch_builder.xml`.
- **Risks**: Extras-builder UX is fiddly. Borrow Android Studio Logcat
  Filter Builder's chip-row pattern.
- **Verification**: Manual: launch `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`
  with `Uri = package:io.github.sysadmindoc.AppManagerNG` and confirm
  the system settings page opens.
- **Complexity**: **M**.
- **Priority**: **P1**.

### NF-23 — Logcat in-viewer search + structured export

- **User problem**: A saved log of 5000 lines is essentially unreadable
  without a `SearchView`. Export-as-text loses structure that JSON or
  CSV would preserve (timestamp, pid, tid, level, tag, body).
- **Evidence**: `LiveLogViewerFragment.java` references a
  `SearchCriteria` struct but no `SearchView` consumer renders it.
  `SavedLogViewerFragment.java` exports plain text only.
- **Proposed behavior**:
  - Add a SearchView to both viewers' toolbars. Debounced 250 ms;
    matches highlighted in-line, count badge in the bar.
  - Export menu: "Export as text" / "Export as CSV" / "Export as JSON"
    (timestamp, pid, tid, level, tag, body).
- **Implementation areas**:
  `logcat/LiveLogViewerFragment.java`,
  `logcat/SavedLogViewerFragment.java`,
  new `logcat/LogcatExporter.java`,
  `R.menu.fragment_log_viewer_actions.xml`.
- **Risks**: None significant; SearchView debounce pattern is repeated
  elsewhere in the codebase.
- **Verification**: Robolectric round-trip test on the CSV / JSON
  exporter (`LogcatExporterTest`). Manual smoke on a 5000-line saved
  log.
- **Complexity**: **M**.
- **Priority**: **P1**.

### NF-24 — Scanner per-organisation summary + filter chips

- **User problem**: A 47-tracker scanner result is overwhelming. Many
  of the 47 entries are SDKs from the same vendor (Google's AdMob +
  Analytics + Crashlytics + Firebase = 4 entries, 1 organisation).
- **Evidence**: `TrackerCategory.categorize(String)` exists since
  iter-N and is already consumed by NF-07 (Tracker Blocking
  Intensity). The scanner UI never surfaces the categorisation.
- **Proposed behavior**:
  - Add a chip row above the scanner result list with categories:
    All / Ad / Analytics / Crash / Push / Location / Identification /
    Social / Other. Single-select; default All. Filters the list
    in-place.
  - Above the chip row, a one-line metric strip: "47 trackers across
    18 organisations".
- **Implementation areas**: `scanner/ScannerFragment.java`,
  `scanner/ScannerAdapter.java`, layout.
- **Risks**: None; pure visual + filter.
- **Verification**: Manual on a known app (e.g. uninstalled Facebook
  Lite APK).
- **Complexity**: **S**.
- **Priority**: **P2**.

### NF-25 — File Manager bulk rename (regex template)

- **User problem**: Renaming 30 backup files to drop a timestamp
  prefix takes 30 tap-rename cycles today.
- **Evidence**: `fm/dialogs/RenameDialogFragment.java` is single-file.
  Multi-select is supported for copy / move / delete / archive.
- **Proposed behavior**: Multi-select rename → bottom sheet with:
  - "Find" + "Replace with" text fields (default: literal),
  - "Regex" toggle,
  - preview list showing the 10 first targets' before/after,
  - "Apply" runs the rename through the existing
    `Path.renameTo()` per file with collision detection.
- **Implementation areas**: `fm/dialogs/BulkRenameDialogFragment.java`
  (new), `fm/FmActivity.java` action wiring.
- **Risks**: Regex syntax errors must not crash the dialog; catch
  `PatternSyntaxException` and disable Apply until valid.
- **Verification**: Robolectric `BulkRenameLogicTest` over the
  rename-spec function (pure-JVM). Manual: rename 5 files with a
  regex.
- **Complexity**: **M**.
- **Priority**: **P2**.

### NF-26 — File Manager hex / binary viewer

- **User problem**: Inspecting APK metadata, .odex bytecode, config
  blobs forces the user out of AppManagerNG. The bundled text viewer
  crashes or shows mojibake on binary files.
- **Evidence**: `fm/` contains no hex viewer.
  `OpenWithDialogFragment.java` lets users pick external editors.
- **Proposed behavior**: New `HexViewerActivity` that lazy-loads
  4 KB pages of a `Path`, renders offset + 16 bytes hex + ASCII
  sidebar, supports go-to-offset, has a search field for hex strings.
- **Implementation areas**: new `fm/hex/HexViewerActivity.java`,
  new `fm/hex/HexLineAdapter.java`, layout.
- **Risks**: Memory pressure on multi-GB files — paginate strictly.
- **Verification**: Manual on a 50 MB APK.
- **Complexity**: **M-L**.
- **Priority**: **P3** (real value, but other rows ahead).

### NF-27 — App Details Providers tab: ContentResolver query inspector

- **User problem**: Devs cannot validate provider schemas without
  external tools.
- **Evidence**: Providers tab in
  `AppDetailsComponentsFragment.java` is read-only.
- **Proposed behavior**: Long-press a provider → "Query…" → dialog
  with authority/path/query-param builder, projection multiselect,
  selection clause text field. Runs `ContentResolver.query()` on a
  worker thread, renders the resulting cursor as a table.
- **Implementation areas**: new
  `details/components/ProviderQueryDialogFragment.java`,
  new `details/components/CursorTableAdapter.java`.
- **Risks**: `query()` can OOM on huge result sets — cap at 500 rows
  by default with a "load more" button.
- **Verification**: Manual on `content://settings/system`.
- **Complexity**: **L**.
- **Priority**: **P3**.

### NF-28 — App Details Receivers tab: "Send broadcast" dialog

- **User problem**: Testing broadcast receivers (alarm, network,
  battery, custom protected broadcasts) requires adb shell.
- **Evidence**: Receivers tab in
  `AppDetailsComponentsFragment.java` is read-only.
- **Proposed behavior**: Long-press a receiver → "Send broadcast…" →
  build an intent (action / categories / extras), dispatch via
  `Context.sendBroadcast()` (unprivileged) or
  `ActivityManagerCompat.broadcastIntent` (privileged) when the
  action is protected.
- **Implementation areas**: new
  `details/components/BroadcastSendDialogFragment.java`.
- **Risks**: Some protected broadcasts kill the system_server if
  fired from the wrong UID. Refuse to send any action starting with
  `android.intent.action.` from the unprivileged path; require
  Shizuku / root.
- **Verification**: Manual: send `android.intent.action.BATTERY_LOW`
  to a receiver that logs the receipt.
- **Complexity**: **M**.
- **Priority**: **P3**.

### NF-29 — App Details Services tab: start/stop affordance

- **User problem**: A user with Shizuku / root cannot start a stuck
  background service from NG.
- **Evidence**: Services tab in
  `AppDetailsComponentsFragment.java` is read-only.
- **Proposed behavior**: Long-press a service → "Start" / "Stop"
  contextual action. Disabled with an inline reason
  ("Requires Shizuku or root") when the active mode can't start it.
- **Implementation areas**:
  `details/AppDetailsComponentsFragment.java`, reuse the existing
  `ActivityManagerCompat.startService` / `stopService` helpers.
- **Risks**: Starting a foreground service from outside its app's
  process requires `FOREGROUND_SERVICE_DATA_SYNC` permission (already
  declared); test on Android 14+ where foreground-service start
  rules tightened.
- **Verification**: Manual: stop a known sticky service on a test
  app.
- **Complexity**: **M**.
- **Priority**: **P3**.

---

## Existing Feature Improvements

### EI-11 — Code Editor search widget needs a close button

- **Current**: Tap search icon to open search; tap it a second time
  to close. No `X` button on the widget itself.
- **Problem**: Discoverability. Users keep typing in the field
  after a match, then have to remember the close gesture.
- **Recommended change**: Add a trailing IconButton to the search
  widget with `R.drawable.ic_close` content description "Dismiss
  search". Wire to `hideSearchWidget()`.
- **Touches**: `editor/CodeEditorFragment.java`,
  `editor/widget/SearchWidget.java` (or wherever the widget lives),
  `R.layout.code_editor_search.xml`.
- **Backward compat**: None affected.
- **Verification**: Manual on a known file with multiple matches.
- **Complexity**: **S**.
- **Priority**: **P2**.

### EI-12 — Code Editor: line-separator change applies retroactively

- **Current**: CRLF/LF/CR popup toggles the chip text but does not
  rewrite the buffer line endings.
- **Problem**: User expects the toggle to convert the document.
  Today they have to manually find-and-replace.
- **Recommended change**: After `mEditor.setLineSeparator(target)`,
  enumerate every line of the buffer and rewrite the trailing
  separator. Skip the rewrite if the buffer hasn't changed (no
  user-visible state churn).
- **Touches**: `editor/CodeEditorFragment.java:447`,
  `editor/CodeEditorViewModel.java`.
- **Backward compat**: None — silent improvement.
- **Verification**: Manual: open a CRLF file, switch to LF, save,
  re-open and confirm pure LF.
- **Complexity**: **S**.
- **Priority**: **P2**.

### EI-13 — Main list "select all matching this chip"

- **Current**: Tap a chip to filter the list. Long-press an item to
  enter selection mode. Each subsequent app requires its own tap.
- **Problem**: Filter chips exist precisely so users can operate
  on a class of apps at once; the bulk-op step undoes that.
- **Recommended change**: When selection mode is active and a chip
  is also active, add a "Select all visible" action to the
  contextual action bar.
- **Touches**: `main/MainActivity.java` (action mode menu),
  `R.menu.contextual_main_actions.xml`,
  `main/MainRecyclerAdapter.java` (already has selection state).
- **Backward compat**: None.
- **Verification**: Manual: filter to Frozen → enter selection
  mode → tap "Select all visible" → confirm batch op
  count.
- **Complexity**: **S**.
- **Priority**: **P2**.

### EI-14 — App Usage BarChart: implement `mManualMinValue`

- **Current**: The class declares a `mManualMinValue` field but
  `onDraw()` (line 308) ignores it; charts always start from 0.
- **Problem**: For multi-day usage charts where the spread is
  narrow (e.g. 4h, 5h, 4.5h), all bars look identical because the
  axis starts at 0 instead of at the user-set min.
- **Recommended change**: Honour `mManualMinValue` when set;
  surface an "auto/manual min" toggle on the chart's overflow
  menu.
- **Touches**: `usage/BarChartView.java`.
- **Backward compat**: Existing default is auto-min = 0; behaviour
  unchanged unless user opts in.
- **Verification**: Manual on a four-day usage range.
- **Complexity**: **S**.
- **Priority**: **P3**.

### EI-15 — SharedPrefs editor: write atomically through `AtomicExtendedFile`

- **Current**: `SharedPrefsViewModel` writes the prefs file directly;
  a kill / OOM mid-write can corrupt the file.
- **Problem**: 2022-02-08 TODO. Real users of the SharedPrefs editor
  are touching system-prefs files where a corrupt write can brick the
  app being edited.
- **Recommended change**: Wrap the write path in
  `AtomicExtendedFile` (the helper used by `KeyStoreManager` and
  the backup engine — write to `.tmp`, fsync, atomic-rename).
- **Touches**: `sharedpref/SharedPrefsViewModel.java`.
- **Backward compat**: Identical user-visible behaviour; on-disk
  layout unchanged.
- **Verification**: New `SharedPrefsAtomicWriteTest` that simulates
  a mid-write kill.
- **Complexity**: **S**.
- **Priority**: **P2**.

### EI-16 — Dex viewer: surface the API < 26 caveat in the UI

- **Current**: `DexClasses.toJavaCode()` works on API 26+ only.
  Below 26 the viewer fails silently with a generic error.
- **Problem**: Users on API 21-25 see no decompiled Java and don't
  know why.
- **Recommended change**: Add a top-of-viewer info bar:
  "Decompiled Java requires Android 8.0 or newer. Smali view
  remains available."
- **Touches**: `dex/DexClassesActivity.java`,
  `R.layout.activity_dex_classes.xml`.
- **Backward compat**: None.
- **Verification**: Manual on a minSdk-21 emulator image.
- **Complexity**: **S**.
- **Priority**: **P3**.

---

## Reliability, Security, Privacy, and Data Safety

- **No new vulnerabilities** surfaced in the iter-141 → iter-146 surface
  audit. Export attack-surface is clean (40+ exported components, all
  signature-gated or fed through `BaseActivity` auth).
- **NF-19 introduces a result-journal file** under
  `FileCache.getGlobalFileCache().createCachedDir("intercept-results")`.
  Mitigate TOCTOU with `AtomicExtendedFile`; reject any receiver caller
  whose UID isn't ours.
- **NF-21 calls `freeStorageAndNotify`** per volume. Some emulated
  internal-SD mounts can answer to multiple UUIDs; coalesce results
  by physical mount path.
- **NF-22 / NF-28 expand the surface that fires user-crafted intents**.
  Refuse `android.intent.action.*` protected broadcasts from the
  unprivileged path; require Shizuku / root before
  `ActivityManagerCompat.broadcastIntent`. Add an explicit
  one-line "This will execute on the device" warning above the
  Launch / Send button.

---

## UX, Accessibility, and Trust

- **TalkBack pass** (ROADMAP T10 row, still open) is the right next
  cross-cutting effort. Iter-141 → iter-146 surfaces — Glossary, runtime
  activity dialog, debloat impact preview, Mode Doctor share, package-
  visibility chip — all have unverified accessibility labels.
- **High-contrast theme** (ROADMAP T17) is still parked. The v2 token
  plane already separates semantic colors; a high-contrast variant is
  a small token swap.
- **Font scale 200% audit** on iter-141 → iter-146 surfaces — the
  Privileges screen, Mode Doctor report, scheduled-backup status row,
  Glossary, Runtime activity dialog — none have been measured at 200%
  scale.

---

## Architecture and Maintainability

- **`details/AppDetailsComponentsFragment.java` is the next overgrown
  file** (~2000 lines covering all four component tabs). When NF-22 /
  NF-27 / NF-28 / NF-29 land, split each tab into its own fragment
  under `details/components/`.
- **`runningapps/` lacks a clear write-path abstraction**: NF-20's
  "persist the AppOp toggle" naturally lives next to the existing
  `ProfileApplierService` AppOp write path. Consider exposing a
  thin `RulePersistence` helper that `RunningAppsViewModel`,
  `AppDetailsPermissionsFragment`, and `ProfileApplierService`
  all share.
- **Test gap**: every new row in this plan should ship with a
  Robolectric or pure-JVM test; the codebase already has 138 tests
  and the precedent is strong. No new test infra needed.

---

## Prioritized Roadmap

### Phase 1 — Close the old TODO cliff (target this week)

- [ ] P0 - **NF-20**: Running Apps "Prevent background run" persists as a rule
  - Why: 2023-02-14 TODO at `RunningAppsViewModel.java:275`.
  - Evidence: AppOp write present; component-rules DB write absent.
  - Touches: `runningapps/RunningAppsViewModel.java`, `rules/struct/AppOpRule.java`, `rules/compontents/ComponentsBlocker.java`.
  - Acceptance: After toggle, App Details → AppOps shows the new rule; profile export carries it.
  - Verify: Robolectric `RunningAppsRulePersistenceTest`.

- [ ] P0 - **NF-21**: One-Click Ops `trimCaches()` iterates all volumes
  - Why: 2021-08-30 TODO at `OneClickOpsViewModel.java:257`.
  - Evidence: `freeStorageAndNotify(null /* internal */, ...)` ignores SD / OTG.
  - Touches: `oneclickops/OneClickOpsViewModel.java`, `utils/StorageUtils.java`.
  - Acceptance: Multi-volume device sees per-volume reclaim numbers in the result.
  - Verify: Robolectric `TrimCachesVolumeEnumerationTest`; manual on a SD-card device.

### Phase 2 — App Details Components tabs become actionable

- [ ] P1 - **NF-19**: Activity Interceptor result-bridge for privileged launches
  - Why: 3× 2022-02 TODO + 1× 2024-06 TODO in `ActivityInterceptor.java`.
  - Evidence: Lines 88, 954, 961, 972; lines unmodified since.
  - Touches: `intercept/ActivityInterceptor.java`, new `intercept/InterceptResultJournal.java`, new `intercept/InterceptResultReceiver.java`, `AndroidManifest.xml`.
  - Acceptance: `ACTION_OPEN_DOCUMENT` via "Resend (root)" returns a Uri to the originating activity.
  - Verify: Robolectric `InterceptResultJournalTest`; on-device manual.

- [ ] P1 - **NF-22**: Activity-launch builder with extras + flags
  - Why: Devs can't pass extras to non-default entry points without leaving NG.
  - Evidence: `AppDetailsComponentsFragment.java:799-803` only forwards package + class + user + root.
  - Touches: new `details/components/ComponentLaunchBuilderFragment.java`, `R.layout.dialog_component_launch_builder.xml`.
  - Acceptance: Launching `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` with `data=package:io.github.sysadmindoc.AppManagerNG` opens the system app-info page.
  - Verify: Manual flow on five common entry-point activities.

- [ ] P1 - **NF-23**: Logcat in-viewer search + structured (CSV/JSON) export
  - Why: A `SearchCriteria` struct exists with no UI consumer; export is plain text only.
  - Evidence: `LiveLogViewerFragment.java` + `SavedLogViewerFragment.java`.
  - Touches: both viewer fragments, new `logcat/LogcatExporter.java`, menu XML.
  - Acceptance: Search highlights matches with a count badge; CSV export round-trips through a spreadsheet.
  - Verify: Robolectric `LogcatExporterTest`; manual on a 5000-line saved log.

### Phase 3 — Scanner / File Manager / Main-list polish

- [ ] P2 - **NF-24**: Scanner per-organisation summary + category filter chips
  - Why: 47 trackers across 18 orgs is unreadable as a flat list.
  - Evidence: `TrackerCategory.categorize()` already classifies; UI never shows it.
  - Touches: `scanner/ScannerFragment.java`, layout.
  - Acceptance: Chip row above the list; metric strip shows orgs.
  - Verify: Manual on a tracker-dense APK.

- [ ] P2 - **NF-25**: File Manager bulk rename (regex template)
  - Why: Single-file rename only; multi-select supports every other op.
  - Evidence: `fm/dialogs/RenameDialogFragment.java`.
  - Touches: new `fm/dialogs/BulkRenameDialogFragment.java`.
  - Acceptance: Rename 5 files via `(.*)\.txt` → `$1.bak` succeeds atomically.
  - Verify: Robolectric `BulkRenameLogicTest` over the rename-spec function.

- [ ] P2 - **EI-11**: Code Editor search widget close button
  - Why: 2023-04-21 FIXME.
  - Touches: `editor/CodeEditorFragment.java:604`.
  - Acceptance: Trailing `X` button hides the widget.
  - Verify: Manual.

- [ ] P2 - **EI-12**: Code Editor line-separator change rewrites buffer
  - Why: 2022-09-18 TODO.
  - Touches: `editor/CodeEditorFragment.java:447`.
  - Acceptance: Switching CRLF→LF on a CRLF file saves pure-LF bytes.
  - Verify: Manual + binary diff of the saved file.

- [ ] P2 - **EI-13**: Main list "Select all matching active chip"
  - Why: Filter chips imply class-of-apps operations.
  - Touches: `main/MainActivity.java`, action-mode menu XML.
  - Acceptance: One-tap bulk-select inside an active filter.
  - Verify: Manual: Frozen → selection mode → "Select all visible".

- [ ] P2 - **EI-15**: SharedPrefs editor atomic writes
  - Why: 2022-02-08 TODO; a kill mid-write can corrupt user-edited prefs.
  - Touches: `sharedpref/SharedPrefsViewModel.java`.
  - Acceptance: New `SharedPrefsAtomicWriteTest` simulates partial write and confirms the file stays valid.
  - Verify: Pure-JVM test.

### Phase 4 — Components-tab deep-dive

- [ ] P3 - **NF-28**: Receivers tab "Send broadcast"
- [ ] P3 - **NF-29**: Services tab start/stop affordance
- [ ] P3 - **NF-27**: Providers tab ContentResolver query inspector

### Phase 5 — Polish

- [ ] P3 - **NF-26**: File Manager hex / binary viewer
- [ ] P3 - **EI-14**: App Usage BarChart `mManualMinValue`
- [ ] P3 - **EI-16**: Dex viewer surface the API < 26 caveat

### Carry-over from prior plan (still parked)

- [ ] **NF-09 executor (Worker + Settings UI + boot receiver)** — blocked on real-device validation. Architecture doc `05-routine-scheduler.md` is the implementation spec.
- [ ] **NF-10 Premium Polish Phase 2** — blocked on designer-authored v2 layouts for AppDetails / AppUsage / Settings.

---

## Quick Wins

(`S` complexity, ship in a single commit each)

- **NF-20** Running Apps rule persistence — single ViewModel write site.
- **NF-21** One-Click Ops multi-volume cache trim — single ViewModel write site.
- **EI-11** Code Editor search widget close button — layout + listener.
- **EI-12** Code Editor line-separator retroactive apply — single setter
  + buffer rewrite helper.
- **EI-13** Main list select-all-matching-chip — action-mode menu wiring.
- **EI-14** BarChart `mManualMinValue` — three-line `onDraw` change.
- **EI-15** SharedPrefs atomic write — single helper substitution.
- **EI-16** Dex viewer API < 26 banner — info-bar string + visibility gate.
- **NF-24** Scanner per-org summary + filter chips — UI-only delta over
  existing data.

---

## Larger Bets

- **NF-19** Activity Interceptor result-bridge — needs receiver design
  + on-device validation.
- **NF-22** Activity-launch builder — moderate UI design.
- **NF-23** Logcat in-viewer search + structured export — touches both
  viewer fragments + new exporter.
- **NF-25** File Manager bulk rename — preview UI + regex handling.
- **NF-27 / NF-28 / NF-29** Components-tab actions — three related
  surfaces; ship together for consistency.
- **NF-26** File Manager hex viewer — separate activity + paging.

---

## Explicit Non-Goals

- **No new third-party dependency for NF-19's result journal.** Reuse
  `FileCache` + `AtomicExtendedFile`. A Room migration would be
  over-engineered for what's effectively a one-row-per-pending-result
  table.
- **No KeyStore Explorer-style detailed certificate parser.** The
  signing-cert chip + ASN.1-aware Subject/Issuer dialog already shipped.
- **No `BIND_VPN_SERVICE` permission.** ROADMAP says NG audits and
  governs apps, not proxies their traffic. The Receivers tab broadcast
  action explicitly does not enable VPN-bound flows.
- **No Compose migration.** `codexprompt.md` calls it a multi-year
  project; reaffirmed in this pass.
- **No in-app store reimplementation** to satisfy upstream #464.
  Continue deep-linking Obtainium / F-Droid as the rejected row already
  says.
- **No CC-BY-NC tracker dataset bundling** (DuckDuckGo Tracker Radar).
  GPL-3.0 redistribution incompatible.

---

## Open Questions

Three open prioritisation questions:

1. **NF-19 receiver permission**: should the
   `INTERCEPT_RESULT` broadcast be gated by the existing
   `${applicationId}.permission.AUTOMATION` (signature) or a new
   in-process-only permission? The journal-id is opaque so leaking
   the broadcast surface is low-risk, but tightening is free.
2. **NF-22 / NF-28 protected-broadcast policy**: NG should refuse
   to dispatch `android.intent.action.*` from the unprivileged path.
   Should the privileged path still surface a confirmation dialog
   the first time per session, or is the existing
   `BaseActivity` biometric gate enough?
3. **NF-21 emulated-volume coalescing**: on Galaxy devices, the
   "internal SD" returns a UUID distinct from primary but cleans the
   same physical mount. Coalesce by `StorageManager.getStorageVolumes()
   .findUuid()` deduplication, or trust the per-volume call to be
   idempotent? Verify on a Galaxy A57 before shipping NF-21.

---

## Quality bar checklist

- ✓ Specific file paths and line numbers on every claim.
- ✓ Verified the three load-bearing TODOs (`ActivityInterceptor.java:954,961,972`;
  `RunningAppsViewModel.java:275`; `OneClickOpsViewModel.java:257`) by
  direct read.
- ✓ Verified the supporting TODOs (`CodeEditorFragment.java:447,604`;
  `SharedPrefsViewModel.java:32`).
- ✓ Verified `AppDetailsComponentsFragment.java:799-803` is the only
  launch site for component activities.
- ✓ Verified upstream MuntashirAkon/AppManager open issues via
  `gh api`; nothing new mineable since the iter-23 deep dive.
- ✓ No proposal repeats a row from `RESEARCH_FEATURE_PLAN_2026-05-25.md`.
- ✓ Every row has acceptance + verify lines a coding agent can act on.

Date written: **2026-05-25 (pass 2)**. Author: autonomous research pass.
Companion to [`RESEARCH_FEATURE_PLAN_2026-05-25.md`](RESEARCH_FEATURE_PLAN_2026-05-25.md).
