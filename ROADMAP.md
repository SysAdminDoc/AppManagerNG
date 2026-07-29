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

### P1

### P2

### P3

## Deep Audit Follow-ups (2026-07-14)

Findings from the 2026-07-14 deep audit that were not fixed in place because they
need a versioned data migration, a design decision, a broad multi-site refactor,
or on-device verification. High-confidence host-fixable bugs from the same audit
were fixed directly (see git history / CHANGELOG).

### P3

## Research-Driven Additions (2026-07-14)

Backing research: `RESEARCH.md` (2026-07-14). Fresh host-verifiable code audit plus an
upstream/ecosystem sweep (App Manager v4.1.0, LibChecker/Hail/Canta/InstallerX/SD Maid SE,
dependency CVEs, Android 16/17 APIs). All items below are host-verifiable and unit-testable
offline. Device-gated feature ideas from this pass are in `Roadmap_Blocked.md`.

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions (2026-07-22)

Backing research: `RESEARCH.md` (2026-07-22). Competitor harvest (InstallerX-Revived,
LibChecker, SD Maid, Hail) + a fresh host-verifiable audit and dependency/CVE sweep.
Upstream shipped no new tag since v4.1.0 (2026-06-29); all prior host findings are already
fixed, so these are net-new. Every item below is host-implementable and host-testable
(Robolectric/JUnit/Jazzer) except where a final on-device check is noted.

### P2

- [ ] P2 — Fuzz the archive extraction paths against path-traversal, symlink, and TOCTOU payloads
  Why: extraction is guarded (`ArchiveExtractionGuard` is wired across every path) but the
  `archive` Jazzer target does not explicitly exercise zip-slip/symlink/TOCTOU — the CVE
  class with the most 2025-26 activity for archive-handling apps.
  Evidence: `utils/ArchiveExtractionGuard.java`; `fm/FmArchiveUtils.java` (normalizeZipEntryName);
  `app/build.gradle:272-277` (`archive` fuzz target); CVE-2026-27800, CVE-2026-37531;
  developer.android.com/privacy-and-security/risks/zip-path-traversal.
  2026-07-29 evidence update: `ArchiveImportFuzzTarget` already exercises synthetic symlink
  containment and the corpus has traversal/relative-path seeds; remaining scope is real ZIP/TAR
  extraction, real symlink entries, destination replacement, and TOCTOU fault simulation.
  Touches: `app/src/test/.../*ArchiveFuzz*` or the existing archive fuzz harness, tracked
  fuzz corpus under `app/src/test/resources/fuzz-corpus/`, `ArchiveExtractionGuardTest.java`.
  Acceptance: real ZIP/TAR extraction ingests archive fixtures containing traversal, absolute
  and drive-letter paths plus actual symlink entries; destination replacement and an injected
  validation-to-write race cannot escape the extraction root; fixtures also run as regression
  tests and stay green.
  Complexity: S

- [ ] P2 — Audit exported components for intent redirection and PendingIntent provenance
  Why: a root-capable app is a prime target for BadParcelable/lazy-Bundle and PendingIntent
  provenance-confusion; the interceptor re-dispatches received intents and no systematic
  audit of exported components exists.
  Evidence: `intercept/ActivityInterceptor.java:149` (TODO receiver flags); `AndroidManifest.xml`
  exported components; CVE-2023-45777 (TheLastBundleMismatch); PendingIntent provenance
  paper arxiv 2603.02539.
  Touches: `AndroidManifest.xml` (audit exported activities/services/receivers),
  `intercept/`, any `PendingIntent.getActivity/Broadcast` call sites, `compat/` Parcelable reads.
  Acceptance: every exported component that forwards a received Intent targets an explicit
  component (no implicit re-broadcast of untrusted intents); all `PendingIntent`s are
  `FLAG_IMMUTABLE` unless mutation is required and justified; Parcelable extras are read via
  type-safe `IntentCompat`/`BundleCompat` getters; a host test asserts an unresolved/foreign
  Parcelable extra is rejected rather than re-forwarded.
  Complexity: M

- [ ] P2 — Installer confirmation: show requested permissions and min/target SDK
  Why: the install dialog surfaces version/cert only, though `ApkFile`/`PackageInfo` already
  parse permissions and SDK levels; InstallerX-Revived's permission + SDK + version panel is
  its most-cited transparency feature and is a pure UI/binding change here.
  Evidence: `apk/installer/PackageInstallerActivity.java` (version/downgrade shown at :354-365,
  cert mismatch at :630-710); `apk/installer/PackageInstallerViewModel.java`;
  `res/layout/dialog_installer.xml`; github.com/wxxsfxyzm/InstallerX-Revived.
  Touches: `res/layout/dialog_installer.xml`, `PackageInstallerActivity`/`PackageInstallerViewModel`
  (expose parsed requested-permissions list + `minSdkVersion`/`targetSdkVersion`), strings.
  Complements (does not duplicate) the blocked "installer preflight: initiating package +
  select-all splits" item, which is device-gated.
  Acceptance: the confirmation dialog lists requested permissions (grouped, dangerous flagged)
  and shows target/min SDK alongside the existing version comparison; a Robolectric test binds
  a fixture APK and asserts the permission/SDK rows render.
  Complexity: M

- [ ] P2 — Non-blocking progress during full-list cache invalidation
  Why: upstream #2000's loudest complaint is a perceived freeze while the app list cache
  invalidates on launch, blocking Backup/Restore; NG's cached `hasActivities` avoids the
  filter-hang half, but the invalidation still has no progress signal.
  Evidence: upstream #2000 (maintainer-confirmed ~12s block, 2026-07-03); `main/MainViewModel.java`
  (list load/refresh); `db/entity/App.java` (cached `hasActivities`).
  Touches: `main/MainViewModel.java`, `main/MainActivity.java` (progress/indeterminate state),
  batch/backup entry points that wait on list readiness.
  Acceptance: while the list cache invalidates, the UI shows determinate/indeterminate progress
  instead of an unresponsive surface, and Backup/Restore actions are either enabled
  incrementally or clearly gated with a reason; a host test asserts the loading state is
  emitted before results. Confirm the real-device block first (see RESEARCH Open Questions) —
  if the cached model already mitigates it, downgrade to progress-polish only.
  Complexity: M

### P3

- [ ] P3 — Native-lib readiness Finder filter + App Details chip (16 KB / 32-bit-only / compressed)
  Why: NG already detects 16 KB load-segment alignment but only as a scanner string; making it
  a Finder predicate + at-a-glance chip lets users sweep the whole device for apps that will
  break or bloat on Android 15/16 (LibChecker parity).
  Evidence: `scanner/NativeLibraries.java:256` (`has16KbLoadSegmentAlignment`), :339-341
  (`native_lib_16kb_aligned` string); `filters/options/` (no native-lib predicate);
  github.com/LibChecker/LibChecker.
  Touches: `filters/options/` (new `NativeLibOption`), the `IFilterableAppInfo` seam (expose
  ELF-alignment / 32-bit-only / compressed-native-libs flags), `details/` + main-list row chip.
  Cross-ref: shares the `IFilterableAppInfo` extension with the blocked "weak-signature Finder
  filter" item — do both once the seam is extended.
  Acceptance: a Finder filter lists apps that are not 16 KB-ready / 32-bit-only / ship
  compressed native libs; App Details and the main row show a readiness chip; a host test
  covers the predicate over fixture ELF data. (Chip visual check needs on-device theme pass.)
  Complexity: M

- [ ] P3 — Restricted-settings detector for sideloaded apps
  Why: Android 14+ gates accessibility, notification-listener, and health access behind the
  "allow restricted settings" prompt for sideloaded apps; surfacing which installed apps are
  currently blocked is a genuine inspector signal no NG screen provides.
  Evidence: developer.android.com/about/versions/16/behavior-changes-16 (restricted settings);
  `permission/` and `details/` permission views (no restricted-settings indicator);
  `compat/AppOpsManagerCompat.java`.
  Touches: `compat/AppOpsManagerCompat.java` (query the restricted-settings op),
  `details/`/`permissions/` (indicator row), strings.
  Acceptance: apps blocked by the restricted-settings gate show a labelled indicator in the
  permission view; a host test covers the mapping. (The exact AppOps op / API across API 34-36
  needs on-device confirmation — see RESEARCH Open Questions.)
  Complexity: M

## Research-Driven Additions (2026-07-29)

### P0

### P1

- [ ] P1 — Make snapshot import/export bounded-memory and all-or-nothing
  Why: export/decrypt duplicates up to 256 MiB in heap and restore can commit a mixture of old
  files and newly inserted database rows after a late failure.
  Evidence: `snapshot/SnapshotBundle.java:332-345,445-466,567-619,733-999,1233-1240`;
  `snapshot/SnapshotCrypto.java:77-121`.
  Touches: `snapshot/SnapshotBundle.java`, `SnapshotCrypto.java`, Room transaction boundary,
  private staging/rollback cleanup.
  Acceptance: encryption/decryption streams through bounded private staging; authentication
  completes before apply; selected DB sections use one transaction and files use atomic
  replacement; injected failure after every section leaves a complete old or new state; a
  >=128 MiB fixture completes within a documented heap bound with no orphaned temporary files.
  Complexity: L

- [ ] P1 — Expand App Change Auditor to security-relevant manifest deltas
  Why: current snapshots discard exported/enabled/guard and custom-permission ownership data,
  missing update-time privilege expansion and confused-deputy signals.
  Evidence: `permission/monitor/ComponentSnapshot.java`, `PermissionSnapshot.java`,
  `ComponentChangeMonitor.java`; Android exported-component/custom-permission guidance;
  arxiv:2605.27667 and arxiv:2508.02008.
  Touches: `permission/monitor/` snapshot schemas, package parsing, diff/feed UI and migration.
  Acceptance: versioned snapshots record component type, effective exported/enabled state and
  guard permission plus requested/declared custom permissions, protection level, and owner
  signer; alerts cover false-to-true export, removed/weakened guards, new requests, orphaned
  permissions, and same-name ownership by unrelated signers; v1 data reprimes without false
  alerts; host fixtures cover each transition.
  Complexity: L

### P2

- [ ] P2 — Disposition affected Guava/protobuf release-runtime dependencies
  Why: affected versions resolve on both release runtime classpaths but fall below the local
  CVSS 9.0 blocking threshold; actual packaged reachability is not yet proven.
  Evidence: `app/gradle.lockfile:209,223`; `scripts/run_dependency_cve_gate.py:17`;
  GHSA-5mg8-w23w-74h3, GHSA-7g45-4rm6-3mm3, GHSA-735f-pc8j-v9w8.
  Touches: `app/build.gradle`, dependency constraints/exclusions, lockfiles/checksums,
  final-APK inspection and dependency report.
  Acceptance: minified `flossRelease` and `fullRelease` APKs are inspected for reachable
  affected code; Guava is constrained to >=32.1.3-android and protobuf to >=3.25.5 or excluded
  when proven unnecessary; smali and Smali-to-Java regressions pass; every affected
  below-threshold runtime dependency receives an explicit release disposition.
  Complexity: S

- [ ] P2 — Add installer storage preflight and recovery
  Why: install sessions omit their APK byte size and selected APK/OBB staging can start before
  the user knows storage is insufficient.
  Evidence: `apk/installer/PackageInstallerCompat.java:697-715,880-960`;
  Android `SessionParams.setSize` and `StorageManager.getAllocatableBytes`.
  Touches: installer selection/view model/UI, `PackageInstallerCompat.openSession`, storage
  compatibility helper and tests.
  Acceptance: selected split and per-user OBB bytes are summed with overflow/unknown handling
  before mutation; the session receives APK size; API 26+ compares allocatable bytes off-main
  and offers `ACTION_MANAGE_STORAGE` with required/free values; older APIs use a documented
  fallback; host tests cover insufficient, unknown, overflow, and multi-user cases.
  Complexity: M

- [ ] P2 — Create one fail-closed local release/quality orchestrator and receipt
  Why: reproducibility, consistency, tests, lint, artifact identity, certificate, SBOM, and CVE
  checks are split, so a release can omit a gate or produce evidence for a different artifact.
  Evidence: `verify_reproducible_release.{ps1,sh}`, `scripts/verify-release-consistency.sh`,
  `docs/distribution/release-receipt.json`, `app/lint-baseline.xml` (4,132 issues);
  commit `4ebc3f9ec` intentionally removed hosted workflows.
  Touches: local release scripts, lint baseline/config, translation-quality script, receipt
  schema and release documentation; no hosted CI.
  Acceptance: one command aborts on dirty/non-exact source, test failure, consistency drift,
  new lint issues, malformed translation counts, wrong APK package/version/SDK/certificate,
  non-reproducibility, or CVE failure; it prunes stale baseline entries and emits a receipt
  binding HEAD/tag, APK/SBOM/report hashes, signing fingerprint, and tool versions.
  Complexity: M

- [ ] P2 — Complete privacy-safe optional-network receipts
  Why: the transparency ledger promises last-use evidence but VirusTotal and Pithus always
  display “never,” preventing users and support bundles from auditing optional egress.
  Evidence: `settings/NetworkTransparencyLedger.java:58-74`.
  Touches: optional-network clients, ledger persistence/model/UI, localized strings and tests.
  Acceptance: every optional client records redacted success/failure time, destination, and
  purpose without payloads, package inventories, response bodies, or secrets; localized
  never/success/failure states and support export are covered by tests.
  Complexity: M

- [ ] P2 — Calibrate tracker/library scanner certainty and provenance
  Why: class-signature detectors degrade under obfuscation, but the UI/export can imply that no
  match proves no tracker.
  Evidence: `scanner/ScannerViewModel.java:249-312,583-614`; `StaticDataset.java`;
  `res/values/strings.xml` (`no_tracker_found`); arxiv:2504.13547 and ICSE 2026 detector study.
  Touches: scanner result model/UI/export, dataset metadata, strings and fixture tests.
  Acceptance: absence reads “no known matches”; every match/export includes dataset version,
  rule/signature provenance, tentative/ETIP status, supported confidence, and an obfuscation
  limitation; tests cover exact, ambiguous, and no-match output.
  Complexity: M
