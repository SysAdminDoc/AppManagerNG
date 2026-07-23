<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Research — AppManagerNG
Date: 2026-07-22 — replaces all prior research.

Confidence labels: [Verified] checked in the local tree or an authoritative source;
[Likely] strong indirect evidence; [Assumption] reasoned but unproven;
[Needs live validation] requires a device/emulator or an unreleased dependency.

## Executive Summary

AppManagerNG is a mature, local-first, GPL-3.0-or-later Android package manager for power
users (Java + Android Views, minSdk 21, `floss`/`full` flavors), forked from upstream App
Manager at post-v4.0.5 and kept unusually current. This pass confirms the fork has already
absorbed every host-verifiable finding from the 2026-07-14 research (log-scrubber embedded
PII, `IntentCompat` BadParcelable guard, VirusTotal timeouts, cumulative decompression
budgets, CRC32/GCM restore integrity, latest-wins App Usage) and the headline upstream
v4.1.0 features (Android 17 `IPackageManagerV37` enumeration, filter-by-installer, cached
`hasActivities`). Upstream has shipped **no new tag since v4.1.0 (2026-06-29)** — only six
release-day commits, all already-ported or cosmetic — so the remaining opportunity is narrow
and comes from **competitor feature harvest**, not upstream catch-up. The codebase is already
ahead of the field on data-layer correctness; the highest-value direction is a small set of
transparency/inspection features that ride data NG already computes, plus two concrete
security-hardening passes.

Top opportunities, priority order:
1. Install-confirmation dialog: show requested permissions + min/target SDK (InstallerX parity).
2. Extend the `archive` fuzz target with path-traversal / symlink / TOCTOU payloads.
3. Intent-redirection & PendingIntent-provenance audit of exported components.
4. Native-lib readiness (16 KB-alignment / 32-bit-only / compressed-libs) as a Finder filter + chip.
5. Non-blocking progress during full-list cache invalidation (turn upstream #2000 pain into a NG win).
6. Restricted-settings detector for sideloaded apps (accessibility/notification-listener/health).

## Product Map

- [Verified] **Core workflows:** inventory/search/filter (37 Finder predicates incl. installer,
  compileSdk, min/target SDK, signature, intent-action, domain-links); inspect
  manifests/permissions/AppOps/trackers/native libs/usage/signing; install/export/verify APK
  sets; back up/restore/convert; freeze/debloat/rules/profiles/routines; file manager, logcat,
  terminal, interceptor.
- [Likely] **Personas:** rooted power users; rootless Shizuku/ADB/Dhizuku operators; ROM
  maintainers; privacy/security auditors; APK developers; offline-FLOSS users.
- [Verified] **Platforms/distribution:** API 21–37 (compileSdk 37, targetSdk 36), AGP 9.2.1 /
  Gradle 9.6.1, NDK; `floss` (F-Droid, IzzyOnDroid) and opt-in-network `full` (GitHub
  Releases, Obtainium); Accrescent prepared; reproducible-build + SBOM + CVSS-9 release gate.
- [Verified] **Integrations/data flows:** PackageManager/PackageInstaller/UsageStats; libsu
  6.0.0, Shizuku API 13.1.5/Sui, libadb-android 3.1.1, Dhizuku; Room 2.7.2; SAF; BouncyCastle
  1.84; ARSCLib V1.4.0; apksig-android 4.4.0; VirusTotal (`full` only). 417 unit tests +
  4 Jazzer fuzz targets (`appList`, `rules`, `snapshot`, `archive`); Robolectric 4.16.1.

## Competitive Landscape

- **InstallerX-Revived** (26.05.01, 5.7k★): install dialog shows requested permissions +
  target/min SDK + version comparison and per-install `InstallFlags` inheriting global
  profiles. NG already shows version/upgrade/downgrade + split-cert mismatch
  (`PackageInstallerActivity.java:354-365,630-710`) but **not** the permission/SDK panel —
  learn that. Avoid becoming a store or a silent-install engine.
- **LibChecker** (2.5.4): per-app 16 KB page-size + ABI + stripped-symbol readiness and rich
  static signals. NG **already detects 16 KB alignment** (`scanner/NativeLibraries.java:256`,
  `has16KbLoadSegmentAlignment`) — learn to make it *filterable and badged*, not just a
  detail string. Avoid remote metadata fetches and Compose-era chart UIs that fight the Views
  ceiling.
- **Hail** (v1.10.0): URI-scheme automation API to trigger freeze from Tasker; auto-unfreeze
  on launch; multi-tag. NG has `automation/` + QS freeze tile; learn the documented intent
  contract. Avoid Xposed dependence. (Execution is privilege-gated — largely already in
  `Roadmap_Blocked.md`.)
- **SD Maid SE** (v1.7.5-rc0): CorpseFinder traces file ownership across *all* storage;
  fixed Samsung One UI force-stop. NG's `LeftoverScanner` is manual — the event-driven,
  cross-storage sweep is the lesson (already a blocked item). Avoid general storage-cleaner
  scope creep.
- **UAD-ng / Canta** (v1.2.0 / active): curated risk-tiered debloat DB with one-tap restore
  and a durable "what I removed" ledger. NG has `debloat/` + `OemBloatRiskTable`; learn the
  persistent removal ledger (blocked P3). Avoid presenting any package as universally safe.
- **Obtainium / Neo Store** (v1.6.10 / 1.2.6): per-item update checkboxes and default-handler
  warnings. NG is not an updater (version-watch panel is a blocked full-flavor item); the
  transferable lesson is per-item (not all-or-nothing) batch selection UX.
- **AppDash / Swift Backup** (commercial): paywall tag-*group* organization, SMB/Drive backup
  targets, and backup-on-update triggers. Signal: tag-group batch org and non-local backup
  destinations are the undervalued OSS gap — but non-local targets conflict with the
  local-first creed and belong in `full` only, if at all.

## Security, Privacy, and Reliability

- [Verified] **Archive extraction is well-guarded but under-fuzzed for traversal.**
  `utils/ArchiveExtractionGuard.java` is wired across every extraction path (converters
  `SBConverter`/`OABConverter`/`TBConverter`, `fm/FmArchiveUtils.java`,
  `snapshot/SnapshotBundle.java`, `utils/TarUtils.java`), and `FmArchiveUtils.normalizeZipEntryName`
  rejects `..`, absolute, and drive-letter entries. The gap is coverage: the `archive` Jazzer
  target (`app/build.gradle:272-277`) should carry explicit zip-slip / symlink-entry / TOCTOU
  payloads, given 2025-26 CVE activity in this class (CVE-2026-27800, CVE-2026-37531).
- [Verified] **PendingIntent / intent-redirection surface is unaudited.** A privileged app is
  a prime target for the BadParcelable/lazy-Bundle (CVE-2023-45777) and PendingIntent-
  provenance-confusion classes. `intercept/ActivityInterceptor.java` re-dispatches received
  intents and has an open TODO for receiver flags (`:149`); exported components that forward
  intents need explicit component targets, `FLAG_IMMUTABLE` PendingIntents, and type-safe
  `getParcelableExtra` calls. No systematic audit exists.
- [Verified] **Prior-pass host findings are all fixed** — `logcat/reader/ScrubberUtils.java`
  patterns are now explicitly un-anchored with `\b`; `intercept/IntentCompat.java:520-543`
  wraps unparceling in try/catch returning `<unreadable extras>`. Do not re-open these.
- [Verified] **No dependency CVE forces action.** BouncyCastle 1.84 already fixes
  CVE-2026-3505/-5588/-5598 for the `jdk15to18` artifacts in use; 1.85 has a duplicate-class
  regression (bc-java #2356) and no `bcpkix-jdk15to18:1.85.1` — hold at 1.84. Every
  AndroidX/Material pin is the last API-21-safe release; the whole cluster is gated behind the
  one-way minSdk 21→23 door (`docs/policy/minsdk-21-ceiling.md`).
- [Needs live validation] **Perceived-freeze on full-list refresh (upstream #2000).** NG's
  cached `hasActivities` Room column (`db/entity/App.java`) avoids upstream's "with activities"
  filter hang, but startup cache invalidation can still block Backup/Restore for seconds with
  no progress signal. Confirm on-device, then add non-blocking progress + async guard.

## Architecture Assessment

- [Verified] **`IFilterableAppInfo` does not expose static-inspection flags.** Two desirable
  Finder predicates — native-lib readiness (16 KB / 32-bit-only / compressed) and weak
  (v1-only) signing — are blocked on the same seam: the filter model exposes subjects/sha256
  and SDK/size fields but not ELF-alignment or signature-scheme flags. Extending
  `IFilterableAppInfo` once unblocks both (the weak-signature filter is already a
  `Roadmap_Blocked.md` P3). `filters/options/` is the extension point; `NativeLibraries`
  already computes the data.
- [Verified] **Installer dialog is data-rich but transparency-poor.** `PackageInstallerActivity`/
  `PackageInstallerViewModel` parse full `ApkFile`/`PackageInfo` (permissions, SDK levels are
  available) but the confirmation dialog (`res/layout/dialog_installer.xml`) surfaces version/
  cert only. Rendering the already-parsed permissions + SDK is a pure UI/binding change.
- [Verified] **Test gaps concentrate in UI/Fragment layers.** Core subsystems (backup/restore,
  filters, profiles, permissions, debloat, signing) are well covered; Activities/Fragments,
  live logcat, and interactive terminal rely on device smoke. New host items should ship with
  Robolectric/JUnit coverage (installer-dialog binding, native-lib option predicate,
  fuzz-corpus regressions) to stay in the host-verifiable lane.
- [Verified] **~47 TODOs are aged deferrals, not blockers** (mostly 2020-2023): e.g.
  `LocalServerManager.java:417` (per-session SSL — already a blocked security item),
  `PermUtils.java:368` (AOSP policy-fixed flags), `dex/DexClasses.java:33-37` (lower-SDK
  Smali). None gate core function.

## Rejected Ideas

- **Re-port Android 17 list fix / App Usage ANR fix** — already present
  (`PackageManagerCompat.java:179`; `AppUsageViewModel` latest-wins). Source: upstream
  #2000/#1994.
- **Filter-by-installer-source, compileSdk/min/target-SDK/intent-action/domain-links filters**
  — already shipped (`filters/options/InstallerOption.java` et al.). Source: upstream #2008.
- **Bump Material 1.14 / Room 2.8 / WorkManager 2.11 / core 1.19 / activity 1.12** — all
  require minSdk 23; single one-way door. Source: Google Maven metadata + minsdk-21-ceiling.md.
- **BouncyCastle 1.85 bump** — packaging regression (bc-java #2355/#2356), no security force.
- **SMB / Google Drive backup targets, cloud sync** — contradicts local-first creed; `full`-only
  at best, high maintenance. Source: AppDash/Swift Backup paywall.
- **apksig v3.2 / PQC signature display** — apksig-android exposes no `isVerifiedUsingV32Scheme()`;
  Android 17 PQC is ML-DSA via Keystore, not a new APK scheme (top is v3.1). Already blocked.
  Source: source.android.com/docs/security/features/apksigning/v3-1.
- **Compose migration / chart-heavy analytics dashboards** — permanent Views ceiling; UI-heavy
  work fights the toolkit. Data-layer wins preferred.

## Sources

- https://github.com/MuntashirAkon/AppManager/commits/master
- https://github.com/MuntashirAkon/AppManager/releases
- https://github.com/MuntashirAkon/AppManager/commit/836c7248eafe5d7b73e21d919eb31c34dd06a348
- https://github.com/MuntashirAkon/AppManager/issues/2000
- https://github.com/MuntashirAkon/AppManager/issues/1994
- https://github.com/MuntashirAkon/AppManager/issues/2008
- https://github.com/MuntashirAkon/AppManager/issues/2003
- https://github.com/MuntashirAkon/AppManager/issues/1986
- https://github.com/wxxsfxyzm/InstallerX-Revived
- https://github.com/LibChecker/LibChecker
- https://github.com/aistra0528/Hail/releases/tag/v1.10.0
- https://github.com/d4rken-org/sdmaid-se/releases/tag/v1.7.5-rc0
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/releases
- https://github.com/samolego/Canta
- https://github.com/ImranR98/Obtainium/releases/tag/v1.6.10
- https://github.com/RikkaApps/Shizuku/releases/tag/v13.6.0
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/about/versions/16/features
- https://developer.android.com/privacy-and-security/risks/zip-path-traversal
- https://source.android.com/docs/security/features/apksigning/v3-1
- https://github.com/advisories/GHSA-cj8j-37rh-8475
- https://github.com/bcgit/bc-java/issues/2356
- https://github.com/material-components/material-components-android
- https://developer.android.com/jetpack/androidx/versions
- https://github.com/michalbednarski/TheLastBundleMismatch
- https://arxiv.org/pdf/2603.02539
- https://github.com/zed-industries/zed/security/advisories/GHSA-v385-xh3h-rrfr
- https://www.thehackerwire.com/agl-app-framework-main-critical-zip-slip-toctou-path-traversal/
- https://play.google.com/store/apps/details?id=flar2.appdashboard
- https://levelup.gitconnected.com/root-detection-is-dead-what-actually-works-in-android-2026-b7f801e50531

## Open Questions

- Does the startup cache-invalidation actually block Backup/Restore on-device in NG (as it
  does upstream #2000), or does the cached-column model already fully mitigate it? Determines
  whether the progress-state item is a real fix or a no-op polish. [Needs live validation]
- What is the correct AppOps op / API to detect that an app is blocked by the Android 14+
  "restricted settings" gate across API 34-36? Determines feasibility of the detector item.
  [Needs live validation]
