<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Research — AppManagerNG

Date: 2026-07-14 — replaces all prior research.

## Executive Summary

[Verified] AppManagerNG is a mature, local-first Android package manager for power users, spanning inspection, installation, backup/restore, rules, profiles, debloat, file management, diagnostics, and privileged operation without making accounts or telemetry part of the core product. Its strongest current shape is breadth plus unusually strong recovery and offline controls; the highest-value direction is to harden the remaining trust boundaries and asynchronous workflows before adding more surface area.

Top opportunities, in priority order:

1. Isolate device-local secrets and transient state from Android cloud/device transfer.
2. Impose cumulative decompression budgets on every importer and converter.
3. Make VirusTotal I/O finite and validate delegated upload origins.
4. Make App Usage refresh latest-request-wins with an explicit failure state.
5. Derive distribution packets and verification from published release artifacts.
6. Restore a blocking maintainer-local dependency-CVE release gate.
7. Fuzz untrusted import formats with persistent regression corpora.
8. Publish a concrete coordinated-disclosure policy.
9. Repair canonical documentation drift with link and truth tests.
10. Add ordered per-tag backup policies as a local-first automation differentiator.

## Product Map

- [Verified] **Core workflows:** inventory/search/filter installed and external apps; inspect manifests, permissions, AppOps, signing, trackers, libraries, usage, and changes; install/export/verify APK sets; back up/restore/convert app state; manage rules, profiles, routines, files, logs, and debloat operations.
- [Likely] **User personas:** rooted power users; rootless Shizuku/ADB/Dhizuku operators; ROM maintainers; privacy/security auditors; APK developers; users who require offline FLOSS behavior.
- [Verified] **Platforms and distribution:** Android API 21+, Java/Views/XML with Material Components, Gradle 9.6.1/AGP 9.2.1, native and privileged-server modules, `floss` and opt-in-network `full` flavors, GitHub Releases/Obtainium plus F-Droid, IzzyOnDroid, and Accrescent preparation.
- [Verified] **Key integrations and data flows:** Android PackageManager/PackageInstaller and UsageStats; libsu, Shizuku/Sui, ADB, and Dhizuku; SAF-backed local/network destinations; encrypted snapshots and backup converters; bundled/updateable debloat and tracker data; VirusTotal only in the opt-in `full` flavor.

## Competitive Landscape

- [Verified] **Upstream App Manager:** sets the parity baseline and exposes current user pain such as the 100%-reproducible App Usage interval ANR in issue #1994. Learn from narrow, test-backed fixes; avoid wholesale ports that overwrite NG identity, release policy, or API-21 support.
- [Verified] **Hail and Blocker:** excel at explicit privilege modes, freeze recovery, state-driven progress, and advanced component rules. Learn their recovery semantics; avoid raw IFW expression editing until validation, rollback, and a concrete user case exist.
- [Verified] **LibChecker:** presents dense static package/signing/library evidence clearly. Learn its confidence-aware presentation; avoid claiming exact third-party-library versions when obfuscation makes that inference unreliable.
- [Verified] **Canta and UAD-ng:** make debloat intent and recovery legible. NG already verifies postconditions and has rescue artifacts, so learn their guided recovery/bisect flow rather than adding another removal engine.
- [Verified] **InstallerX-Revived:** provides strong split-selection explanations, install-origin controls, and mismatch warnings. NG already covers most installer parity; retain the transparent preflight model without becoming an app store.
- [Verified] **Neo Backup:** demonstrates explicit backup-part selection, retention, and migration handling. Learn fail-closed restore and format compatibility; avoid ROM-integrated assumptions that a normal app cannot satisfy.
- [Likely] **AppDash and Swift Backup:** commercial value concentrates in automation, tags, versioned recovery, and policy reuse. The fitting lesson is ordered, local tag-to-backup policy; avoid mandatory accounts, proprietary cloud SDKs, price tracking, and subscription-only recovery.

## Security, Privacy, and Reliability

- [Verified] **Android backup has a weaker secret boundary than SnapshotBundle.** `app/src/main/res/xml/backup_rules.xml:7-13,20-26` and `full_backup_rules.xml:6-12` include all internal files and shared preferences except a few named paths. That transfers the authorization key, Tasker HMAC secret, and VirusTotal API key from `utils/AppPref.java:278-292,357-360`, despite the source declaring that they must not leave the device. It also transfers device-bound `keystore.xml`, retryable `${applicationId}.batch_ops_journal.xml`, and crash-only `installer_state.xml`; `crypto/ks/KeyStoreManager.java:507-523`, `batchops/BatchOpsJournal.java:110-125`, and `main/MainActivity.java:431-469` show why restored state can be undecryptable or stale. Android Auto Backup explicitly includes preferences/files and restores them before first launch.
- [Verified] **Compressed-input budgets are incomplete.** `snapshot/SnapshotBundle.java:456-509` caps each entry and the count but buffers all accepted entries without a cumulative expanded-byte ceiling; 10,000 individually valid entries can exceed memory far beyond the 256 MiB input cap. `backup/convert/SBConverter.java:223-258,382-397`, `OABConverter.java:339-375`, and `TBConverter.java:288-362` copy third-party ZIP/TAR content without the tested `utils/ArchiveExtractionGuard.java` used elsewhere.
- [Verified] **VirusTotal calls can hang or consume unbounded memory.** `scanner/vt/VirusTotal.java:237,266,312` opens connections without connect/read timeouts or an explicit redirect policy, accepts a server-supplied upload URL without scheme/host validation, and accumulates success/error bodies without a cap at `:386-426`. Other NG clients already use finite timeouts and disabled redirects, so a shared policy is available without adding a dependency.
- [Verified] **App Usage refresh is race-prone and failures look empty.** `usage/AppUsageViewModel.java:75-87,161-199` queues every date/interval change on a global executor, reads mutable request fields inside the worker, has no cancellation/generation token, and ignores per-user exceptions. Rapid changes can overlap and stale results can win; `usage/AppUsageActivity.java:108-178,256` cannot distinguish all-query failure from no usage. Upstream issue #1994 reports a 100%-reproducible ANR on this exact interval switch.
- [Verified] **The 17 existing 2026-07-14 deep-audit rows remain valid and are not duplicated.** They cover backup checksum/versioning and restore integrity (`BackupOp`, `RestoreOp`, `BackupMetadataV5`, `VerifyOp`); snapshot allowlisting/merge divergence (`SnapshotBundle`); rule/URI/automation parsing (`RuleEntry`, `PathReader`, `RulesImporter`, `UriManager`, `AutomationReceiver`); split export and regex safety (`SplitApkExporter`, `LogLine`); and small concurrency/state contracts (`BatchQueueItem`, `LogViewerViewModel`, `ApkWhatsNewFinder`). Their inline paths remain the implementation evidence.
- [Verified] **No currently pinned core dependency was found vulnerable in the advisories checked.** Bouncy Castle 1.84 is above the relevant fix; the process gap is that `build.gradle:58-65` defaults OWASP Dependency Check to nonblocking CVSS 11 after hosted workflows were intentionally removed, while the local reproducible-release scripts generate an SBOM but do not run the CVSS-9 gate.
- [Verified] **Coordinated disclosure is underspecified.** `.github/SECURITY.md` is absent and GitHub private vulnerability reporting is disabled; `CONTRIBUTING.md:55-63` only points reporters to an unspecified profile contact. GitHub recommends a repository security policy with supported versions and a private reporting route.

## Architecture Assessment

- [Verified] **Prefer targeted policy/request boundaries, not a rewrite.** The largest risk-heavy classes include `details/info/AppInfoFragment.java`, `details/AppDetailsViewModel.java`, `fm/FmFragment.java`, `main/MainActivity.java`, and `snapshot/SnapshotBundle.java`. When touched, extract pure backup classification, archive-budget, HTTP-policy, and App Usage request/result objects so host tests can exercise them while preserving Views/XML and API 21.
- [Verified] **Release truth is not mechanically enforced.** Published `v0.6.5` is code 13 at tag commit `fc03e0332`; the GitHub floss asset is 18,948,513 bytes with SHA-256 `986da6fc19e325c5fe35d03523021fa30dd2b483466c5f98a3a8c7c64d6a5fa0`. `docs/distribution/accrescent-listing.md:22` and `fdroid-listing.md:20` still say code 7, F-Droid points to a different commit, and `izzyondroid-listing.md:18-19,78-79` carries an older size/hash. `scripts/verify-release-consistency.sh:163-174` misses the actual `Version code:` spelling yet prints success.
- [Verified] **Canonical documentation has measurable drift.** `CLAUDE.md` refers to removed `PROJECT_CONTEXT.md` and a deleted scheduled CVE workflow; `docs/architecture/README.md` omits tracked topics; `launcher-icon-aliases.md` and `04-filter-finder.md` describe shipped work as future; `docs/roadmap/COMPLETED.md` calls re-enabled tests ignored; `docs/policy/2026-05-26-minsdk-23-decision.md` links a deleted workflow; and `design/README.md` links ignored files unavailable in a clean clone. `DocumentationArchiveContractTest.java` checks only a subset of these contracts.
- [Verified] **Untrusted parser testing is example-based, not generative.** Snapshot, rule, list, backup, and archive fixtures are extensive, but no coverage-guided fuzz target exists for `SnapshotBundle`, `RulesImporter`, `PathReader`, `ListImporter`, `VerifyOp`, or `TarUtils`. Jazzer supports JUnit/Gradle and persistent regression corpora; make the archive-budget item its prerequisite.
- [Verified] **Gson is a contained upgrade risk, not a rewrite mandate.** Gson now documents maintenance mode and warns about Android/R8 reflection; NG minifies releases, but the owned debloat DTO fields are already explicitly named. Keep Gson for API-21/Java compatibility, centralize strict construction as those formats change, and retain legacy/minified compatibility fixtures rather than introducing Moshi/Kotlin solely for serialization.
- [Verified] **Category coverage:** accessibility, large-screen/mobile, i18n/l10n, multi-user/profile, migration, and device-only upgrade validation already have shipped infrastructure or explicit entries in `Roadmap_Blocked.md`; adding duplicates would reduce actionability. Observability is addressed by explicit App Usage failures plus existing support bundles/crash capture, not telemetry. Distribution, testing, docs, security, and offline resilience have net-new items here. A general plugin ecosystem and multi-user collaboration are intentionally rejected below.

## Rejected Ideas

- [Verified] **Cloud-first backup, mandatory accounts, proprietary destinations, or price tracking** — AppDash/Swift Backup monetization conflicts with NG's offline FLOSS core; SAF already delegates provider choice.
- [Verified] **Compose rewrite or minSdk-23 bump solely for current Material/Room/WorkManager** — their release notes confirm the new floor, but this would abandon the explicit API-21/Views contract without install-share evidence.
- [Verified] **Hosted GitHub Actions revival** — commit `4ebc3f9ec` intentionally removed workflows; restore the missing CVE protection in the maintainer-local release path instead.
- [Likely] **Arbitrary privileged plugin SDK** — awesome-Shizuku/root ecosystems show demand, but third-party privileged code would create a disproportionate security, hidden-API, and compatibility burden.
- [Verified] **Exact third-party-library version claims from static signatures** — LibChecker-adjacent research shows poor neighboring-version discrimination under R8/obfuscation; keep confidence visible instead.
- [Verified] **Advanced raw IFW condition editor** — Blocker's releases show the capability, but NG lacks a concrete demand signal and safe validation/recovery contract.
- [Verified] **Broad Gson-to-Moshi/Kotlin rewrite** — Gson's warning is real, but current owned DTOs are Java and explicitly named; compatibility fixtures and constrained factories are lower-risk.
- [Verified] **A new split-details expander** — `AppInfoFragment.java:1685-1704` already opens every `ApkFile.Entry`, whose `toLocalizedString` includes split type/name, size, required/isolated state, and compatibility; the older audit request is stale.
- [Needs live validation] **MTE scanner gate in the active roadmap** — useful for native diagnostics, but it requires an MTE-capable device and belongs with device-gated validation rather than host-actionable work.
- [Verified] **Desktop fleet management, work-profile cloning/DPC ownership, or multi-user collaboration** — these are different product/ownership models, not extensions of an on-device package manager.

## Sources

### OSS and commercial products

- https://github.com/MuntashirAkon/AppManager
- https://github.com/MuntashirAkon/AppManager/issues/1994
- https://github.com/MuntashirAkon/AppManager/issues/2000
- https://github.com/aistra0528/Hail
- https://github.com/lihenggui/blocker/releases
- https://github.com/LibChecker/LibChecker
- https://github.com/samolego/Canta
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/wxxsfxyzm/InstallerX-Revived
- https://appdash.app/
- https://www.swiftbackup.app/configs

### Standards, security, and testing

- https://developer.android.com/identity/data/autobackup
- https://developer.android.com/privacy-and-security/risks/zip-path-traversal
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://source.android.com/docs/security/features/apksigning
- https://github.com/CodeIntelligenceTesting/jazzer
- https://google.github.io/oss-fuzz/getting-started/new-project-guide/jvm-lang/
- https://github.com/google/gson
- https://github.com/google/gson/blob/main/Troubleshooting.md
- https://docs.github.com/en/code-security/getting-started/quickstart-for-securing-your-repository
- https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/configure-vulnerability-reporting/configure-for-a-repository
- https://docs.virustotal.com/reference/files-upload-url
- https://github.com/SysAdminDoc/AppManagerNG/releases/tag/v0.6.5

### Dependencies, research, and community

- https://github.com/material-components/material-components-android/releases
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/work
- https://github.com/advisories/GHSA-4cx2-fc23-5wg6
- https://arxiv.org/abs/2509.04091
- https://stackoverflow.com/questions/61596881/usagestatsmanager-not-returning-correct-weekly-or-monthly-results

## Open Questions

- [Needs product decision] Should Android device transfer retain `files/am_keystore.bks` while excluding device-bound `keystore.xml` and require the recovery password, or exclude both and require explicit key export/import? The secret and transient-state exclusions do not depend on this choice.
- [Needs maintainer data] What proportion of active installs still use API 21–22? Public dependency release notes prove the ceiling, but only private install data can decide when raising minSdk becomes justified.
