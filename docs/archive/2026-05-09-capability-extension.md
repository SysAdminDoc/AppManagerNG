<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Capability Extension Research — Datasets, Privacy Intelligence, Modern Android APIs

Date: 2026-05-09

Scope: A net-new survey of (a) public datasets we could ingest to make AppManagerNG's debloat, tracker-detection, and replacement-suggestion verdicts smarter, and (b) modern Android 14–16 platform APIs we have not yet exploited. Run as a follow-up to [`2026-05-02-android-power-tools.md`](2026-05-02-android-power-tools.md), so capabilities the prior pass already named (Canta-style debloat UX, Hail tile, Insular cloning, Exodus-based per-app tracker count, SD Maid corpse-finder) are *not* repeated here. Only deltas.

Source device for the contemporaneous gap audit: Samsung Galaxy S25 Ultra (SM-S938B), One UI 8.0, Android 16, API 36 — 424 packages, 112 entries contributed upstream as `SysAdminDoc/android-debloat-list@b53089f`.

## Executive Take

AppManagerNG's existing roadmap already plans the capability surface (T9 tracker blocking, T16 cloning, T19 corpse-finder, T20 perf, iter-20 UAD-style auto-fetch). What it does *not* plan is the **data layer feeding those capabilities**. Today our debloat verdicts come from one upstream source; UAD-NG ships a richer list with bidirectional dependency edges; IzzyOnDroid's apkscanner-data ships SDK signatures Exodus doesn't have; UAD-NG's preinstalled-lists ships the OEM provenance graph.

The other untapped wedge is the Android-14-and-up API surface: SDK Runtime / Privacy Sandbox enumeration, Domain Verification, App Archiving, MTE, Health Connect, Credential Manager, Restricted Settings — each surfaces a privacy or security signal we currently can't show.

## Part A — Datasets to Ingest

### A1. UAD-NG `uad_lists.json` — bidirectional dependency graph

**Source:** [Universal-Debloater-Alliance/universal-android-debloater-next-generation](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation), `resources/assets/uad_lists.json` (~1.54 MB, 5,357 entries).

**License:** GPL-3.0. Compatible downstream of our AGPL-3.0 dataset (one-way per FSF).

**Why it matters:** Our schema declares `dependencies` and `required_by` but the upstream MuntashirAkon list populates them sparsely. UAD-NG's list populates them densely — `dependencies` and `neededBy` arrays for ~80% of system entries. This is what lets a "remove X" UI honestly tell the user "you'll also break Y, Z, and W". Today we can't do that without manually authoring the graph.

**Integration sketch:** Add `tools/import_uad.py` that reads `uad_lists.json`, normalises UAD-NG's `removal` levels (`Recommended` / `Advanced` / `Expert` / `Unsafe`) into our 4-state enum, and emits a sidecar `dependencies.json` keyed by package id with edges only. Don't merge entries 1:1 (overlap is messy and our descriptions are higher-quality on the rows we own); merge edges only. Refresh from upstream weekly via CI.

### A2. UAD-NG `universal-android-preinstalled-lists` — OEM provenance

**Source:** [Universal-Debloater-Alliance/universal-android-preinstalled-lists](https://github.com/Universal-Debloater-Alliance/universal-android-preinstalled-lists). GPL-3.0.

**Why it matters:** A separate corpus of "package X is known preinstalled on OEM Y, model Z, region R" claims. Lets us draw a "preinstalled on Samsung / Pixel / OnePlus / Xiaomi / Motorola" filter chip in Finder *without* re-deriving from per-device audits like the one that produced our 112 S25 Ultra entries.

**Integration sketch:** Bundle as a sidecar `preinstalled.json`; expose via a new `IFilterableAppInfo.getKnownPreinstallOems()` method consumed by Finder filter chips and the App Info "Provenance" row.

### A3. IzzyOnDroid `apkscanner-data` — SDK / anti-feature signatures

**Source:** [codeberg.org/Katastima/apkscanner-data](https://codeberg.org/Katastima/apkscanner-data) (definitions) + [`apkscanner`](https://codeberg.org/Katastima/apkscanner) (scanner). Migrated from GitLab in early 2026.

**License:** GPL-2.0; mirror-friendly. Compatible with AppManagerNG's GPL-3.0-or-later (GPL-2.0+ → GPL-3.0+ is permitted).

**Why it matters:** Complementary to Exodus. Where Exodus focuses on tracker SDKs, apkscanner-data covers a broader set of libraries with anti-feature classification (NLP / ad / payment / analytics / utility). Powering F-Droid IzzyOnDroid antifeature flags in production. Catches signatures Exodus doesn't (~30–40% additional library coverage on a typical Play Store APK).

**Integration sketch:** Bundle alongside Exodus DB; the existing tracker-scan engine already does regex-match against class-signature lists. Adding a second source is one new asset file + one merged scan pass.

### A4. Achno/debloat-samsung-ADB-shizuku — Samsung CSC/region annotations

**Source:** [Achno/debloat-samsung-ADB-shizuku](https://github.com/Achno/debloat-samsung-ADB-shizuku).

**Why it matters:** Samsung-specific, with CSC (Country Sales Code) / region notes in prose. Tiny corpus, but high-value cross-check for the kind of S25 Ultra additions we just contributed. Useful as a one-time validation pass against `oem.json`'s Samsung block, not as ongoing ingest.

### A5. Datasets we are *not* ingesting (decision log)

- **DDG Tracker Radar** — already documented in iter-18 as license-blocked (CC-BY-NC-SA). Re-confirmed.
- **AppCensus** — research-paper artifacts, not a maintained dataset.
- **GrapheneOS / DivestOS / CalyxOS app catalogs** — editorial commentary on dozens of apps; useful as hand-curated suggestion hints but not as bulk dataset.
- **TrackerControl bundled DB** — derived from Exodus + DDG Tracker Radar; the DDG portion is licence-tainted at the bundle level even if ingested at runtime.
- **Original UAD `0x192/universal-android-debloater`** — archived 2024-08; UAD-NG fork supersedes.

## Part B — Modern Android API Exposures

### B1. Privacy Sandbox / SDK Runtime enumeration

**API:** `SdkSandboxManager.getSandboxedSdks()` (Android 14+, API 34+).

**Why it matters:** Apps targeting the Privacy Sandbox load advertising and analytics SDKs into a separate process with restricted permissions. Today we can detect SDKs at static-analysis time (Exodus-style); the SDK Runtime surface tells us which sandboxed SDKs are *actually loaded right now* per app. Adds a runtime-truth column to the tracker panel.

**Integration sketch:** New "SDK Sandbox" row in App Info → Trackers tab. Falls back to "Not supported on this device" below API 34.

### B2. Domain Verification audit

**API:** `DomainVerificationManager.getDomainVerificationUserState(packageName)` (API 31+, dramatically expanded behaviour in 34).

**Why it matters:** Apps claim deep links via `<intent-filter android:autoVerify="true">`. Today, two apps can both claim `https://example.com/share/*` and the user has no visibility into which one wins. A "Domain Verification" tab in App Info would surface (a) every domain the app claims, (b) the verification state per domain (verified / declined / unverified), and (c) which other installed apps also claim the same domain — i.e. a deep-link hijack inspector.

**Integration sketch:** New tab under App Info; a system-wide "Deep Link Conflicts" finder that scans all apps for overlapping claims is a follow-up.

### B3. App Archiving

**API:** `PackageInstaller.requestArchive()` and `PackageInstaller.requestUnarchive()` (Android 15+, API 35+).

**Why it matters:** Archive removes the APK while preserving user data. Hail-adjacent feature, but with zero privilege requirement (the platform handles it). Pairs with our Freeze infrastructure as a third state — Active / Frozen / Archived — and unlocks storage reclaim that Freeze can't deliver.

**Integration sketch:** New "Archive" action in App Info next to Freeze and Force Stop; a Batch Operations entry "Archive selected (preserves data)".

### B4. Memory Tagging Extension (MTE) status

**API:** Per-app manifest property `android:allowNativeHeapPointerTagging` (effective on Android 11+) plus the runtime MTE state (Android 14+ Pixel 8+).

**Why it matters:** MTE catches memory-safety bugs at runtime. Apps that opt in get hardened heap protection; the user has no current way to see who opted in. A simple "MTE: enabled / disabled / not supported" chip in the App Info security row makes the security posture visible alongside the existing signing-cert chip.

**Integration sketch:** New chip in the App Info tag cloud, parsing `applicationInfo.flags` and the MTE manifest property; below API 30 displays "MTE: not supported on this device".

### B5. Health Connect dashboard

**API:** `HealthConnectClient` + `getGrantedPermissions()` (Android 14 mainline / API 34).

**Why it matters:** Health Connect is the privacy-sensitive shared store for fitness, sleep, and clinical data. We currently audit standard permissions and AppOps but treat Health Connect as opaque. A dedicated dashboard answering "which apps read my heart rate / sleep / step count?" is a differentiator vs. every other app manager.

**Integration sketch:** New "Privacy Dashboard" entry under Settings (or under T19 Storage Analysis as a sibling tab) that enumerates Health Connect permission grants per app.

### B6. Credential Manager audit

**API:** `CredentialManager` + `Settings.ACTION_CREDENTIAL_PROVIDER` (Android 14+).

**Why it matters:** Apps register passkey and password providers. Today the user can see them in system Settings but not who's registered, when, or which credentials are stored. Pairs with the Health Connect dashboard as a "Privacy Dashboard" tab — "which apps registered passkeys / passwords / federated identities".

### B7. Restricted Settings unlock walkthrough

**API:** Restricted Settings (Android 13+) — sideloaded apps cannot be granted Accessibility / NotificationListener / DevicePolicy without a manual unlock step (long-press the disabled toggle in Settings).

**Why it matters:** This is the #1 support question for sideloaded power-user apps (KeepAndroidOpen, Tasker, our own AppManagerNG when granting accessibility for component blocking). A first-run wizard that detects the restriction, explains why it exists, and deep-links to the right Settings screen would eliminate a recurring user pain.

**Integration sketch:** Add to the existing T5 Privilege Health-Check screen; detect `ApplicationInfo.FLAG_INSTALLED` + install source, show a graduated walkthrough.

### B8. MMRL + LSPosed module browser

**API:** Filesystem access to `/data/adb/modules/` (Magisk + KernelSU + APatch) and `/data/adb/lspd/` (LSPosed). Read `module.prop` for metadata.

**Why it matters:** Power users on rooted devices manage modules in a separate app (MMRL, LSPosed Manager). We already detect the active root provider (iter-7); reading the module list and exposing toggle / scope-edit is one tab away.

**Integration sketch:** New "Modules" entry under Privilege Health-Check; gated on root detection. Read-only in v1, toggle in v2, install/upload in v3.

## Verdict / Roadmap Wiring

Twelve net-new rows ready to migrate into Iter-21 Research Additions. Five are dataset rows under T7 (Finder + Debloater); seven are capability rows split across T9 Privacy & Security (B1, B2, B5, B6, B7), T9/T19 (B3 archiving, B4 MTE), and T5 Rootless (B8 modules).

License posture is uniformly safe — every dataset is GPL-2.0+, GPL-3.0, or CC-BY-SA, all compatible with our AGPL-3.0 dataset and GPL-3.0-or-later codebase. No NC-licensed sources slipped through.

The biggest single capability we are *not* recommending is a full local-VPN per-app firewall (NetGuard / TrackerControl-style). Effort is 5/5 (own VpnService + DNS proxy + UI), and the existing T9 AppOps-based tracker blocking already addresses the Day-1 user need at fraction of the implementation cost. Park it under "Under Consideration" until the AppOps blocker has shipped and we have user feedback on whether enforcement at the AppOp layer is sufficient.
