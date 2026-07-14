<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Research — AppManagerNG

## Executive Summary

[Verified] AppManagerNG is a local-first, GPL-3.0-or-later Android package manager for power users, with unusually broad install, inspection, backup/restore, debloat, rule, profile, file-management, and diagnostics workflows across unprivileged, root, ADB, Shizuku, and Dhizuku modes. Since the last research pass the highest-value user-state findings (snapshot secret boundary, encrypted bundles, snapshot portability, release/screenshot truth, Room destructive-migration ladder) have shipped or moved to `Roadmap_Blocked.md`, and a fresh sweep confirms the fork is **unusually current with upstream**: the headline v4.1.0 features (Android 17 `IPackageManagerV37` enumeration, filter-based profiles, ADB `.ab` backup, App Usage bar chart, Bouncy Castle 1.84) are already ported, and every stale dependency is a deliberate, annotated consequence of the minSdk-21 ceiling. The remaining opportunity is therefore narrow and concrete: a small set of host-verifiable correctness/privacy bugs, a few selective upstream ports upstream shipped after our base commit, and power-user static-inspection signals that differentiate NG without breaking its offline/no-account posture. Priority order: (1) fix the log scrubber so it actually redacts embedded IPs/phone numbers, (2) guard intent-extra inspection against `BadParcelableException`, (3) bounds-fix the signature-checksum filter, (4) port upstream's post-4.0.5 host-verifiable fixes (TarUtils overflow, A12+ keystore-backup gate, link-interception intent resolution, `EXTRA_RETURN_RESULT`), (5) lock in GCM-reuse immunity with a regression test, and (6) add low-cost inspection signals (weak-signature flag, health-permission mapping, LibChecker-class signals).

## Product Map

- **Core workflows:** inventory/search/filter apps; inspect manifests, permissions, AppOps, trackers, libraries, usage, and changes; install/export/verify APK sets; back up, restore, convert, archive, freeze, debloat, and roll back; edit rules, profiles, routines, and files.
- **Users:** rooted power users, rootless Shizuku/ADB/Dhizuku operators, ROM maintainers, privacy auditors, APK developers, and offline FLOSS users.
- **Platforms and distribution:** Android API 21+, Java Android Views with Material Components 1.13.x, Gradle 9.6 / AGP 9.2 with native and privileged-server modules, `full` and `floss` flavors, GitHub Releases plus prepared F-Droid/IzzyOnDroid/Accrescent packets.
- **Integrations:** PackageManager/PackageInstaller, libsu, Shizuku/Sui, Wireless ADB, Dhizuku, Room, SAF, Android backup/transfer rules, OpenPGP/Bouncy Castle, VirusTotal in `full`, and bundled/updateable tracker and debloat datasets.
- **Data flows:** device/package state feeds inventory and scanners; privileged actions produce operation history and rollback data; backups and rules leave through SAF; settings/profiles/rules/history/tags/filters/favorites/freeze-methods leave through the encrypted `SnapshotBundle`.

## Competitive Landscape

- **Upstream App Manager (v4.1.0, 2026-06-29):** The fork already tracks it closely. Learn through narrow, test-backed ports of the post-base host-verifiable fixes (see Architecture Assessment). Avoid wholesale re-imports that overwrite NG identity, trust copy, release policy, or the deliberate minSdk-21/Views architecture. The one un-ported security item is the HMAC mutual challenge-response ADB handshake (already parked in `Roadmap_Blocked.md`).
- **Hail:** Strong at multi-tag freeze policy, automation, and conditional-freeze guards (skip-while-charging / skip-foreground / skip-active-notifications). NG has multi-tag and a QS freeze tile already; learn its conditional-freeze predicates for routine ops. Avoid making core workflows depend on Xposed or one root implementation.
- **LibChecker:** Excellent at cheap static-inspection signals (modern-vs-legacy Xposed API detection, live-update-notification capability, themed-icon/alias detection, overlay-target pages). NG already parses most of this data; learn to surface it as new App Details / Finder signals. Avoid remote metadata fetches by default.
- **Canta / UAD-ng:** Evidence-backed debloat descriptions, cross-user state verification, persistent "what I removed" ledgers, and immutable/verifiable releases. Learn the removal audit trail and an in-app "verify my own APK against the published fingerprint" loop. Avoid presenting a package as universally safe to remove across OEMs/users.
- **InstallerX-Revived:** Rich installer preflight (select-all splits, install-initiator display, nested APK-in-ZIP, batch queue). Learn the preflight transparency (which app requested the install). Avoid turning the installer into a store.
- **SD Maid SE:** CorpseFinder's event-driven post-uninstall leftover sweep and per-OEM cache-clear quirk handling. NG has a manual `LeftoverScanner`; learn the event-driven trigger. Avoid scope creep into general storage cleaning.
- **AppDash / Swift Backup (commercial):** Paywall watchlist intelligence ("app removed from Play", "update available"), auto-tagging, and per-app remembered backup configs. The offline-safe subset (auto-tag suggestions, per-app backup profiles) fits; real-time price/sale alerts require network and conflict with the local-first creed — keep any network feature opt-in, account-free, cache-first, and `full`-flavor only.

## Security, Privacy, and Reliability

- [Verified] `logcat/reader/ScrubberUtils.java:10,12` anchor `PHONE_NUMBER_PATTERN` and `IP_ADDRESS_PATTERN` with `^...$` but apply them with `matcher(line).replaceAll(...)` at `:28,:30`. Because of the anchors they only match a line that is *entirely* an IP or phone number, so the common case (an IP/phone embedded in a longer log line, e.g. `Connected to 8.8.8.8:443`) is never scrubbed. PII leaks into displayed/saved/shared logs. The sibling EMAIL/WEB_URL patterns in the same file are correctly un-anchored. Fix: drop the anchors (or use `\b`).
- [Verified] `intercept/IntentCompat.java:520-534` (`getUnsupportedExtras`) calls `intent.getExtras()` and `extras.get(key)` with no try/catch, and is reached by the public `describeUnsupportedExtras(Intent, String)` at `:537-540`. An intent carrying a Parcelable extra whose class is not loadable in-process throws `BadParcelableException` during lazy unparceling. Sibling methods (`copyUriSafeExtras`, `ActivityInterceptor.storeOriginalIntent`) already guard exactly this. Fix: wrap the unparcel loop and return an `<unreadable extras>` marker.
- [Verified] `filters/options/SignatureOption.java:126-134` (case `sha256`) iterates `i` over `getSignatureSha256Checksums()` but on a match reads `getSignatureSubjectLines()[i]`. These are independently produced arrays; when a signer yields a checksum but a shorter subject-line array (one cert parse fault), this throws `ArrayIndexOutOfBoundsException` and aborts the whole filter run. Fix: bounds-check before indexing.
- [Likely] `dex/DexUtils.java:77` (`isDex`) discards the `is.read(headerBytes)` return; a short read (SAF/remote/`ProxyInputStream`) leaves trailing zeros and misidentifies a real `.dex`. Relatedly, `DexUtils.java:216-230` retries the odex parser on the *same* already-consumed `InputStream` (buffered without `mark`/`reset`), so a valid `.odex` always fails the fallback. Fix: fully-read the header / buffer into a re-readable stream.
- [Likely] Small correctness cluster: `profiles/ProfileManager.java:174-176` can pass a `@Nullable` resolved path into a `@NonNull` `BaseProfile.fromPath`, NPE'ing outside the `IOException/JSONException` catch; `fm/FmItem.java:160-166` `compareTo` returns 0 for unequal items (breaks the `TreeSet`/`TreeMap` contract, can silently drop items); `misc/AdvancedSearchView.java:439` string-concatenates a nullable `mQueryHint`, rendering a literal `"null (Contains)"` hint.
- [Verified] Bouncy Castle is on 1.84 — CVE-2026-3505 / -5588 / -5598 are all fixed; the app's AES backup path uses streaming `GCMBlockCipher.newInstance()` + `CipherInputStream`, so it is structurally immune to upstream #1958 ("GCM cipher cannot be reused"). This immunity is currently unguarded by any test.

## Architecture Assessment

- **Selective upstream ports (post-base, host-verifiable):** upstream shipped these after our base commit `3d11bcb`; each is unit-testable offline. `TarUtils` integer-overflow guard (`3899dca0a`); A12+ keystore-entry backup gate — keystore cannot be backed up on Android 12+, so the backup path must version-gate it (`bdb293626`); `am start -d <link>` / link-interception intent resolution fix, matching open issue #2001 (`4a25c3f0f`); report install result via `EXTRA_RETURN_RESULT` to match the stock-installer contract (#2003). The HMAC mutual-auth ADB handshake (`88eb453d0`) is device-gated and already parked in `Roadmap_Blocked.md`.
- **Dependency ceiling is correctly managed:** `versions.gradle` annotates every hold — Material 1.13→1.14 (minSdk 23), Room 2.7→2.8 (drops API 21-22), WorkManager 2.10→2.11 (minSdk 23), AndroidX core 1.17→1.19 (minSdk 23). Do not raise minSdk or rewrite in Compose without active-install evidence. One low-risk bump is available: ARSCLib tagged `V1.4.0` (2026-07-01) supersedes the current commit pin.
- **Cheap power-user inspection signals:** NG already computes the underlying data. Surface (a) a weak-signature flag for v1-only / v1-only-scheme APKs as an at-a-glance security signal; (b) the Android 16 `BODY_SENSORS → android.permissions.health` granular-permission mapping in the permission catalog; (c) LibChecker-class signals — modern-vs-legacy Xposed API, live-update-notification capability, themed-icon/alias detection — as App Details / Finder rows.
- **Test gaps:** add a GCM-reuse immunity regression test; log-scrubber substring-redaction tests; a `SignatureOption` short-subject-array test; `IntentCompat` foreign-Parcelable extra tests; dex header short-read/odex-fallback tests. Existing support bundles, local crash capture, operation history, and diagnostics cover observability sufficiently; no new telemetry service is justified.

## Rejected Ideas

- **Cloud-first backup providers** (AppDash, Swift Backup): conflicts with local-first/offline operation; SAF already permits user-chosen sync folders.
- **Real-time Play-Store price/sale watchlist** (AppDash Pro): requires persistent network and account-like polling; conflicts with the offline/no-account creed. Only the account-free, cache-first "update available / removed from Play" subset is even arguably in-bounds, and only in `full`.
- **Built-in multi-source app updater/catalog** (Obtainium, APKUpdater): turns a package manager into a remote trust/availability service; keep signed-APK inspection and installation focused. (A `full`-flavor version-watch panel already exists in `Roadmap_Blocked.md`.)
- **Always-on VPN traffic monitor** (TrackerControl): duplicates a mature adjacent tool and adds persistent VPN/battery/network complexity outside the static inspection core.
- **Work-profile cloning/DPC ownership** (Shelter, Island): a different onboarding/ownership/recovery/enterprise model; NG should inspect/manage the profiles Android already exposes.
- **Generic privileged plugin SDK / root-module WebUI** (systemapp_nuker, awesome-shizuku ecosystem): expands the attack surface around unstable hidden APIs and shifts supportability onto third-party code; keep explicit, reviewed integrations.
- **Full Compose rewrite** (Material Components maintenance notice): contradicts the deliberate Java/Views/API-21 baseline and offers no near-term safety or recovery value.
- **Xposed-dependent auto-unfreeze / transparent launch-through as a core path** (Hail): the transparent launch-through UX is already parked in `Roadmap_Blocked.md`; do not make the core freeze workflow depend on Xposed.
- **Subscription/pro tier** (AppDash): conflicts with the GPL, no-ads, local-first philosophy.

## Sources

### OSS and commercial products
- https://github.com/MuntashirAkon/AppManager/releases/tag/v4.1.0
- https://github.com/MuntashirAkon/AppManager/compare/v4.0.5...v4.1.0
- https://github.com/MuntashirAkon/AppManager/compare/v4.1.0...master
- https://github.com/aistra0528/Hail/releases
- https://github.com/samolego/Canta
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/releases
- https://github.com/lihenggui/blocker/releases
- https://github.com/LibChecker/LibChecker/releases
- https://github.com/NeoApplications/Neo-Backup/blob/main/CHANGELOG.md
- https://github.com/wxxsfxyzm/InstallerX-Revived
- https://sdmse.darken.eu/changelog
- https://appdash.app/
- https://www.swiftapps.org/
- https://appops.rikka.app/changelog/

### Upstream issues / commits
- https://github.com/MuntashirAkon/AppManager/commit/88eb453d0
- https://github.com/MuntashirAkon/AppManager/commit/3899dca0a
- https://github.com/MuntashirAkon/AppManager/commit/bdb293626
- https://github.com/MuntashirAkon/AppManager/commit/4a25c3f0f
- https://github.com/MuntashirAkon/AppManager/issues/1958
- https://github.com/MuntashirAkon/AppManager/issues/2001
- https://github.com/MuntashirAkon/AppManager/issues/2003
- https://github.com/MuntashirAkon/AppManager/issues/1967

### Platform, dependencies, and security
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://source.android.com/docs/security/features/apksigning/v3
- https://android-developers.googleblog.com/2026/06/Android-17.html
- https://github.com/material-components/material-components-android/releases/tag/1.14.0
- https://github.com/REAndroid/ARSCLib/releases/tag/V1.4.0
- https://developer.android.com/jetpack/androidx/releases/room
- https://github.com/advisories/GHSA-cj8j-37rh-8475
- https://accrescent.app/docs/guide/appendix/requirements.html

## Open Questions

- What proportion of active AppManagerNG installs still run API 21–22? That private distribution signal remains the only evidence that can decide whether preserving Android 5 support outweighs unlocking current Room/WorkManager/Material/AndroidX releases.
- Is an opt-in, account-free, cache-first network check (`full` flavor only) acceptable for "update available / removed from Play" signals, or does the offline creed keep that entire category off the table?
