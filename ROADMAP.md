<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# ROADMAP

Live checklist of incomplete work. Historical surfaces are archived under
`docs/roadmap/archive/`. Research backing the items below: `RESEARCH.md`.

If a live copy of this file exists on another machine, merge these additions
into it — existing items take precedence over duplicates.

All remaining items are in `Roadmap_Blocked.md` — gated on device access,
visual verification, privileged-mode testing, or external dependencies.

## Research-Driven Additions (2026-06-20)

### P2

- [ ] P2 — Main-thread blocking operations audit
  Why: Upstream #1987/#1988 (opened 2026-06-18) flag main-thread keystore reads and time-consuming PackageManager queries. Given shared codebase origin, the same patterns likely exist in NG. ANR risk on low-end devices.
  Evidence: upstream MuntashirAkon/AppManager#1987, #1988; StrictMode ThreadPolicy violations
  Touches: app/src/main/java/ (keystore operations, PackageManager queries, file I/O on UI thread)
  Acceptance: StrictMode ThreadPolicy set to detectAll()+penaltyLog() in debug builds; zero violations logged during main list load, app details open, and settings navigation; offending operations moved to background threads or ViewModel coroutines
  Complexity: M

### P3

- [ ] P3 — UAD-ng debloat safety cross-reference
  Why: Canta's killer feature is showing Recommended/Advanced/Expert/Unsafe safety badges per OEM package from the UAD-ng community database. NG's debloater has OemBloatRiskTable and debloat presets but doesn't cross-reference the UAD-ng dataset (15k+ packages with safety ratings).
  Evidence: Canta (5k stars) — UAD-ng integration is its primary differentiator; Universal-Debloater-Alliance/universal-android-debloater-next-generation
  Touches: debloat/ (DebloaterViewModel, DebloaterRecyclerViewAdapter, OemBloatRiskTable), scripts/android-debloat-list submodule or new data source
  Acceptance: debloater list shows UAD-ng safety badge (Recommended/Advanced/Expert/Unsafe) alongside existing bloat risk when available; badge absent for packages not in the UAD-ng database; data refreshed via the existing debloat-definition updater in full flavor
  Complexity: M

- [ ] P3 — DDG Tracker Radar as supplementary tracker source
  Why: TrackerControl demonstrates that layering DuckDuckGo's mobile-specific tracker database on top of Exodus catches mobile-specific trackers that Exodus misses. DDG Tracker Radar is MIT-licensed, JSON-formatted, and maintained by DuckDuckGo.
  Evidence: TrackerControl multi-source approach (Disconnect + DDG + in-house); DDG Tracker Radar mobile TDS at staticcdn.duckduckgo.com
  Touches: app/src/main/assets/ (tracker data), scanner/ (signature matching), full-flavor debloat-definition updater
  Acceptance: tracker scanner reports trackers found via DDG Tracker Radar alongside Exodus signatures; source attribution visible in tracker detail view; full flavor auto-updates the DDG list; floss flavor ships a bundled snapshot
  Complexity: M

- [ ] P3 — MyAndroidTools rule import
  Why: Many power users migrating from MyAndroidTools or Blocker have existing component-blocking rule sets. Blocker already imports MAT backups and converts to IFW rules. NG should support the same import path.
  Evidence: Blocker MAT import feature (PR history); MyAndroidTools user base; existing IFW import infrastructure in rules/compontents/
  Touches: rules/ (new MAT format parser), settings/ (import UI entry point), debloat/ or details/ (import action)
  Acceptance: Settings → Rules → Import accepts a MyAndroidTools backup file; rules are converted to NG's IFW format and applied; import report shows converted/skipped/failed counts; round-trip test with a sample MAT export file
  Complexity: S

- [ ] P3 — APK Signature Scheme v3.2 display
  Why: Android 17 ships hybrid PQC signing (ML-DSA + classical). When apksig-android adds isVerifiedUsingV32Scheme(), NG should display the PQC indicator in the signing cert info chip. Current code shows v3.2-signed APKs as v3 (no crash, but incomplete info).
  Evidence: PackageUtils.java:882 TODO comment; Android 17 PQC upgrade docs; apksig-android v3.2 version notes
  Touches: utils/PackageUtils.java (getSignerInfo), details/info/ (signing cert display), apksig dependency
  Acceptance: when apksig-android supports v3.2 detection, the signing cert chip shows "v3.2 (PQC)" for hybrid-signed APKs; no change needed until upstream adds the API — gate behind version check
  Complexity: S

- [ ] P3 — Transparent launch-through frozen apps
  Why: Hail's killer UX pattern: tap a frozen app's launcher icon → auto-unfreeze → launch → auto-refreeze when the app closes or screen locks. NG has freeze/unfreeze plumbing and auto-freeze-on-screen-lock, but no transparent launch-through.
  Evidence: Hail (6k stars) — transparent launch is its most-cited feature; NG freeze data layer + screen-lock receiver already landed
  Touches: main/ (launcher shortcut handling), freeze/ (unfreeze-then-launch flow), auto-freeze receiver (refreeze-on-close trigger)
  Acceptance: user can mark apps for "auto-freeze"; tapping their launcher icon unfreezes, launches, and refreezes when the app leaves foreground or screen locks; works in root, ADB, and Shizuku modes
  Complexity: M

- [ ] P3 — Samsung "Clear Compiler Artifacts" batch operation
  Why: Upstream #1989 (opened 2026-06-19) requests "Clear Compiler Artifacts" / "Reset Dexopt" in batch ops for Samsung devices. Samsung One UI's per-app storage screen shows this option but no other package manager exposes it as a batch operation.
  Evidence: upstream MuntashirAkon/AppManager#1989; Samsung One UI storage management; SD Maid SE Samsung-specific workarounds
  Touches: batchops/BatchOpsManager.java, compat/ (Samsung dexopt clearing API), main/ (batch ops menu)
  Acceptance: batch operations menu includes "Clear Compiler Artifacts" on Samsung devices; operation clears dexopt/ART profiles for selected apps; hidden on non-Samsung devices; works via root or ADB
  Complexity: S

All remaining blocked items are in `Roadmap_Blocked.md`.
