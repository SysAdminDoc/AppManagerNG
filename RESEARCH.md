<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Research — AppManagerNG
Date: 2026-07-29 — replaces all prior research.

Confidence labels: [Verified] checked in the 2026-07-29 tree or an authoritative source;
[Likely] supported by multiple indirect sources; [Assumption] reasoned but unproven;
[Needs live validation] requires a final APK or device/emulator.

## Executive Summary

AppManagerNG is a mature, local-first, GPL-3.0-or-later Android package-management suite
for ordinary, privacy-conscious, and privileged power users. Its strongest shape is the
combination of deep inspection and automation with guided capability recovery, reversible
actions, `floss`/`full` network boundaries, and reproducible local releases. The 2026-07-29
audit found that another feature expansion would be lower value than closing a few concrete
trust boundaries: OBB data is mutated before APK success, remotely fetched debloat safety
data is unsigned and activated as two independent files, snapshot restore is memory-heavy
and non-transactional, and several “atomic” metadata writers can delete the last good copy.
Priorities therefore map to **Now = P0**, **Next = P1**, **Later = P2**, and **Under
Consideration = P3**.

Top opportunities, in priority order:

1. [Verified] Stage OBB payloads and preserve the prior generation until APK success.
2. [Verified] Sign, rollback-protect, and atomically activate debloat-definition generations.
3. [Verified] Make local audit/identity metadata writes genuinely durable and recoverable.
4. [Verified] Stream snapshot bundles and apply every selected section as one recoverable unit.
5. [Verified] Extend App Change Auditor to exported/guard/custom-permission security deltas.
6. [Verified] Preserve and salvage profile triggers when their JSON is partially corrupt.
7. [Verified] Correct the overbroad `2026-09-30` developer-verification statement.
8. [Verified; Needs live validation] Resolve affected Guava/protobuf runtime pins after final-APK
   reachability inspection.
9. [Verified] Preflight installer storage, set the session size, and expose recovery.
10. [Verified] Bind local release evidence, lint/translation ratchets, and artifact identity in
    one fail-closed command.

## Product Map

- **Core workflows**
  - [Verified] Inventory, search, Finder predicates, sort, tags, and batch package actions.
  - [Verified] App/component/permission/AppOps/signature/tracker/native-library inspection.
  - [Verified] APK/split install, export, archive, backup/restore, and portable app snapshots.
  - [Verified] Freeze, debloat, profiles, rules, automation, action history, and rollback.
  - [Verified] File manager, log viewer, scanner, terminal, running-app, and support diagnostics.
- **User personas:** ordinary no-root users; offline/privacy users; root/Shizuku/ADB power
  users; multi-profile/device administrators; developers and mobile-security analysts.
- **Platforms and distribution:** Android 5.0+ (`minSdk 21`), target 36/compile 37; Java/XML
  Material 3 Views; locally orchestrated releases with a published certificate fingerprint;
  GitHub, F-Droid, IzzyOnDroid, Accrescent, and ROM-preseed documentation; `floss` compiles
  optional Internet features out.
- **Key integrations and data flows:** Android package/usage/AppOps APIs and hidden-API
  compatibility; root/Shizuku/ADB/privileged AIDL server; local Room/files/keystore; APK,
  archive, OpenPGP, and snapshot import/export; opt-in VirusTotal, Pithus, and definition
  updates in the `full` flavor with redacted diagnostics.

## Competitive Landscape

- **Upstream App Manager** — Does well: broad inspection, ADB backup, filter expressions, and
  usage analysis. Learn: selectively port proven diagnostics through NG's guided/reversible
  model. Avoid: inheriting upstream behavior without rechecking NG's privacy and recovery
  contracts.
- **Universal Installer** — Does well: preinstall SDK/ABI/language/permission/split/OBB review,
  storage visibility, and install history. Learn: size-aware preflight and precise package
  review. Avoid: presenting advanced flags without capability and consequence explanations.
- **Thor** — Does well: root/Shizuku/Dhizuku coverage, centralized UAD safety, and signed
  extension catalogs. Learn: put every GUI/intent/automation entry point behind one safety
  boundary and authenticate remote catalogs. Avoid: a public extension ecosystem without a
  compatibility, signing, and maintenance owner.
- **Hail** — Does well: an explicit privilege-mode capability matrix and multi-profile freeze
  semantics. Learn: source-aware recovery and honest per-mode limitations. Avoid: implying
  that one privilege path has identical durability across OEMs and profiles.
- **Canta + Universal Android Debloater NG** — Do well: approachable safety tiers, independent
  definitions, state rechecks, and reversible debloat. Learn: versioned, signed data and
  post-action verification. Avoid: crowdsourced safety votes without poisoning resistance,
  OEM/version context, moderation, and a privacy model.
- **Inure + LibChecker** — Do well: task-oriented app details, recent-exit diagnostics, and
  library/native metadata. Learn: expose provenance and uncertainty with each scanner result.
  Avoid: treating a missing signature match as proof that an app is tracker-free.
- **Neo Backup + SD Maid SE** — Do well: restore taxonomy, cancellation discipline, schedules,
  and OEM-specific recovery. Learn: staged all-or-nothing state changes and failure-injection
  tests. Avoid: destructive operations whose recovery exists only inside a still-bootable app.
- **AppDash + Swift Backup** — Do well: monetize change history, insight cards, schedules, and
  versioned local/remote backup. Learn: users value actionable “what changed and what can I
  recover?” answers. Avoid: cloud destinations, Play-market tracking, or subscription scope
  that conflicts with AppManagerNG's local-first package-management purpose.

## Security, Privacy, and Reliability

- [Verified] **APK/OBB installation is not rollback-safe.**
  `app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/PackageInstallerCompat.java:664-700`
  copies OBBs before opening/committing the package session; `:1115-1141` deletes old OBBs
  before extraction and logs extraction failure without aborting APK installation. Stage each
  user's payload, preserve old files until package success, and surface an explicit recoverable
  partial result if activation fails.
- [Verified] **Debloat-definition integrity has no trusted root or atomic generation.**
  `debloat/DebloatDefinitionsUpdater.java:39,103-141` accepts a mutable `main`-branch manifest
  whose own hashes authenticate the payloads; `:263-283` deletes then renames two files
  independently. `debloat/DebloatPreset.java:15-55,97-100` converts those classifications into
  recommended removals. Use an app-pinned signing key, expiry/rollback checks, one versioned
  generation pointer, and a retained last-known-good generation.
- [Verified] **Portable snapshot restore can exhaust heap and leave mixed state.**
  `snapshot/SnapshotBundle.java:332-345,445-466` materializes up to 256 MiB and
  `snapshot/SnapshotCrypto.java:77-121` duplicates plaintext/ciphertext; restore then applies
  files and database sections sequentially at `SnapshotBundle.java:567-619,733-999,1233-1240`
  without a single transaction or atomic file replacement. Authenticate into bounded private
  staging, transact database sections, atomically replace files, and retain rollback material.
- [Verified] **Several atomic-write contracts fail open.**
  `permission/monitor/{PermissionSnapshotStore,ComponentSnapshotStore,SigningCertSnapshotStore,AppChangeFeedStore}.java`
  delete the live target after the first rename failure, while
  `libcore/io/.../AtomicExtendedFile.java:214-220` reports success after `fsync()` throws.
  Consolidate these writers around backup/new-file recovery and fault-injection tests.
- [Verified] **The App Change Auditor omits material manifest changes.**
  `permission/monitor/ComponentSnapshot.java` records only version and class-name sets;
  `PermissionSnapshot.java` records only dangerous permission names. Component type, effective
  exported/enabled state, guard permission, declared custom permissions, protection levels,
  and owner signer are discarded despite Android's documented exported-component and orphaned
  custom-permission risks. Version the schema and detect weakened guards, new requests, and
  same-name permissions owned by unrelated signers.
- [Verified] **Profile-trigger corruption is converted into silent deletion.**
  `profiles/trigger/ProfileTriggerStore.java:175-194` returns an empty set for malformed
  document JSON and skips malformed entries; the next mutation can overwrite the only copy.
  Retain raw/last-known-good data, salvage valid siblings, and expose export/reset recovery.
- [Verified] **Affected libraries are present on release runtime classpaths.**
  `app/gradle.lockfile:209,223` resolves Guava `31.1-android` and protobuf-java `3.22.3`;
  public advisories fix these lines at `32.0.0-android` and `3.25.5`. The local dependency gate
  blocks only CVSS `9.0+` (`scripts/run_dependency_cve_gate.py:17`), so protobuf's `8.7` issue
  does not fail release. [Needs live validation] R8 may remove protobuf; inspect the final APK
  before choosing a constraint or exclusion.
- [Verified] **Installer storage is discovered too late.**
  `PackageInstallerCompat.java:697-715,880-960` creates a session without
  `SessionParams.setSize`; no installer path calls `StorageManager.getAllocatableBytes`.
  Preflight selected APK/split/OBB staging bytes off the main thread and offer the platform
  storage-recovery intent when space is insufficient.
- [Verified] **Optional-network receipts are incomplete.**
  `settings/NetworkTransparencyLedger.java:58-74` hardcodes VirusTotal/Pithus last-use to zero
  and embeds English metadata. Record only redacted success/failure time, endpoint, and purpose;
  never record payloads, package inventories, API keys, or response bodies.

## Architecture Assessment

- [Verified] Keep the existing Java/XML, minSdk-21, local-first architecture. Material Views is
  in maintenance mode and several AndroidX upgrade lines now require minSdk 23, but a Compose
  rewrite has XL cost and no proportionate user outcome.
- [Verified] Introduce narrow trust boundaries instead of generic file-size refactors:
  an installer staging/activation coordinator around `PackageInstallerCompat.java`; one durable
  atomic-state primitive for the four auditor stores and `AtomicExtendedFile`; separate
  snapshot read/authenticate/stage/apply phases in `SnapshotBundle.java`; and versioned
  manifest-security value types under `permission/monitor/`.
- [Verified] `AppInfoFragment.java` (~4.3k lines), `AppDetailsViewModel.java` (~2.6k),
  `FmFragment.java` (~1.9k), `SnapshotBundle.java` (~1.7k), and
  `PackageInstallerCompat.java` (~1.5k) are concentration points. Refactor only while extracting
  the transaction, state, or test seams above.
- [Verified] Test breadth is strong at the JVM layer, but instrumentation remains single-digit
  source files and only four Jazzer targets exist. The existing archive target already covers
  synthetic symlink containment and has traversal/relative-path corpus seeds; its remaining
  gap is real ZIP/TAR extraction, real symlink entries, destination replacement, and TOCTOU.
- [Verified] `app/lint-baseline.xml` masks 4,132 issues. Concrete escaped signal includes
  hard-coded Owner/Group/Others labels in `res/layout/dialog_change_file_mode.xml:30-44`, an
  ACTION_UP path that omits `performClick()` in `usage/BarChartView.java:713-739`, and duplicate
  zero output in `scripts/verify-translation-quality.sh:73`. Ratchet fail-on-new findings and
  bind tests, lint, consistency, APK metadata/certificate, reproducibility, SBOM, and advisory
  disposition into one local release receipt; preserve the deliberate no-hosted-CI policy.
- [Verified] `README.md:146` and `docs/sideload-verification.md:11-21,56-75` overstate
  `2026-09-30` Android developer-verification enforcement, and
  `res/values/strings.xml:2137` omits the participating-store limitation. Google's 2026-07-22
  FAQ limits that phase to named stores and regions; other stores and direct sideloading are
  outside the initial phase, ADB remains exempt, and advanced flow is a one-time account setup,
  not biometric confirmation per install. Fold these exact surfaces into the existing
  `Roadmap_Blocked.md:383-389` documentation-truth item rather than adding a duplicate row.
- **Coverage disposition:** security, offline/resilience, migration, multi-user install,
  observability, testing, i18n, docs, and distribution have actionable additions below.
  Accessibility/theme/device form factors, AppsDb migrations, Android 17, translation hosting,
  privileged-mode matrices, and broader upgrade validation already exist in
  `Roadmap_Blocked.md`. Mobile means Android for this product; desktop and remote fleet
  multi-user control are purpose conflicts. A public plugin ecosystem is rejected below.

## Rejected Ideas

- **Full Compose rewrite** — Material Views maintenance and competitor adoption do not justify
  an XL migration/minSdk conflict; source: Material Components releases/maintenance notice.
- **Public plugin or extension marketplace** — Thor proves feasibility, not demand sufficient
  to fund signing, compatibility, review, and incident response.
- **Crowdsourced debloat safety voting** — Canta's request lacks poisoning resistance,
  OEM/firmware context, moderation, and privacy infrastructure.
- **Cloud AI privacy/security scoring** — no trustworthy ground truth and conflicts with the
  `floss`/local-first boundary; commercial insight cards do not require AI.
- **Play price/watchlists and cloud backup destinations** — AppDash/Swift Backup validate a
  paid market, but the features move AppManagerNG away from on-device management.
- **Private Space control** — `LauncherApps` visibility is role/permission constrained and is
  not a general package-manager capability.
- **Destructive APEX management or 32-bit translation** — SD Maid/InstallWithOptions demand
  does not create a safe stable write API; both are OS/runtime scope.
- **Universal non-root ADB backup claims** — upstream v4.1.0 does not remove manifest,
  platform, or private-data restrictions; eligibility must remain explicit.
- **“No tracker found” means clean** — detector studies show signature/version accuracy
  degrades under obfuscation; use “no known matches” plus provenance instead.
- **Restore `_data` path fallback** — deprecated/scoped-storage paths would weaken the current
  fail-closed SAF result; source: `libcore/io/.../MediaDocumentFile.java:47-50`.
- **Duplicate roadmap work** — Android 17, patch-aware network ADB, recent process exits,
  installer history, backup round-trip, secure privileged transport, visual accessibility,
  multi-user capability, and broad docs cleanup are already shipped or represented in
  `ROADMAP.md`/`Roadmap_Blocked.md`.

## Sources

### Direct and adjacent OSS

- https://github.com/MuntashirAkon/AppManager
- https://github.com/MuntashirAkon/AppManager/releases/tag/v4.1.0
- https://github.com/pass-with-high-score/universal-installer
- https://github.com/trinadhthatakula/Thor
- https://github.com/trinadhthatakula/Thor/releases/tag/v1.93.0
- https://github.com/Hamza417/Inure
- https://github.com/LibChecker/LibChecker
- https://github.com/lihenggui/blocker
- https://github.com/aistra0528/Hail
- https://github.com/samolego/Canta
- https://github.com/samolego/Canta/discussions/364
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- https://github.com/d4rken-org/sdmaid-se
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/zacharee/InstallWithOptions
- https://github.com/timschneeb/awesome-shizuku
- https://github.com/awesome-android-root/awesome-android-root

### Commercial and community

- https://appdash.app/
- https://appdash.app/faq/
- https://www.swiftbackup.app/
- https://www.swiftbackup.app/faq
- https://adbappcontrol.ru/en/
- https://www.reddit.com/r/androidapps/comments/17fnler/
- https://news.ycombinator.com/item?id=47173783
- https://xdaforums.com/t/script-disable-f-k-services-trackers-on-all-apps-1-5-04-15.4074427/

### Platform and standards

- https://developer.android.com/developer-verification/guides/faq
- https://developer.android.com/blog/posts/android-developer-verification-rolling-out-to-all-developers-on-play-console-and-android-developer-console
- https://developer.android.com/reference/android/content/pm/PackageInstaller.html
- https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams
- https://developer.android.com/reference/android/content/pm/LauncherApps
- https://developer.android.com/reference/android/os/storage/StorageManager
- https://developer.android.com/guide/topics/manifest/application-element.html
- https://developer.android.com/privacy-and-security/risks/android-exported
- https://developer.android.com/privacy-and-security/risks/access-control-to-exported-components
- https://developer.android.com/privacy-and-security/risks/custom-permissions
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://source.android.com/docs/security/bulletin/2026/2026-05-01
- https://theupdateframework.github.io/specification/latest/

### Research and advisories

- https://arxiv.org/abs/2605.27667
- https://arxiv.org/abs/2508.02008
- https://arxiv.org/abs/2504.13547
- https://conf.researchr.org/details/icse-2026/icse-2026-research-track/189/An-Empirical-Study-on-the-Robustness-of-Android-Third-Party-Library-Detection-Tools-A
- https://github.com/advisories/GHSA-5mg8-w23w-74h3
- https://github.com/advisories/GHSA-7g45-4rm6-3mm3
- https://github.com/advisories/GHSA-735f-pc8j-v9w8

### Core dependency direction

- https://developer.android.com/jetpack/androidx/releases/work
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/activity
- https://developer.android.com/jetpack/androidx/releases/window
- https://github.com/material-components/material-components-android/releases
- https://github.com/material-components/material-components-android

## Open Questions

- [Needs live validation] Does each minified `flossRelease`/`fullRelease` APK retain or invoke
  the affected Guava/protobuf classes, or can protobuf be excluded from the jadx path entirely?
- [Needs live validation] Which supported OEM/profile OBB targets permit a same-volume atomic
  activation, and which require verified copy plus retained-backup rollback?
