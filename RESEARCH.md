<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Research — AppManagerNG

## Executive Summary

[Verified] AppManagerNG is a local-first, GPL-3.0-or-later Android package manager for power users, with unusually broad install, inspection, backup/restore, debloat, rule, profile, file-management, and diagnostics workflows across unprivileged, root, ADB, Shizuku, and Dhizuku modes. Its strongest current shape is depth plus explicit trust controls; the highest-value direction is therefore to protect the app's own user state and make releases truthful rather than add another large subsystem. Priority order: (1) stop snapshot ZIPs from exporting or importing credential keys, (2) eliminate Room's silent destructive-migration path, (3) include all portable DB-backed user state in snapshots, (4) offer authenticated encrypted snapshots, (5) make release/distribution documentation mechanically consistent, and (6) replace legacy storefront screenshots with current NG captures.

## Product Map

- **Core workflows:** inventory/search/filter apps; inspect manifests, permissions, AppOps, trackers, libraries, usage, and changes; install/export/verify APK sets; back up, restore, convert, archive, freeze, debloat, and roll back; edit rules, profiles, routines, and files.
- **Users:** rooted power users, rootless Shizuku/ADB/Dhizuku operators, ROM maintainers, privacy auditors, APK developers, and offline FLOSS users.
- **Platforms and distribution:** Android API 21+, Java Android Views with Material Components, Gradle/AGP plus native and privileged-server modules, `full` and `floss` flavors, GitHub Releases and prepared F-Droid/IzzyOnDroid/Accrescent packets (`app/build.gradle`, `versions.gradle`, `docs/distribution/`).
- **Integrations:** PackageManager/PackageInstaller, libsu, Shizuku/Sui, Wireless ADB, Dhizuku, Room, SAF, Android backup/transfer rules, OpenPGP/Bouncy Castle, VirusTotal in `full`, and bundled/updateable tracker and debloat datasets.
- **Data flows:** device/package state enters app inventory and scanners; privileged actions produce operation history and rollback data; backup archives and rules leave through SAF; AppManagerNG settings/profiles/rules/history leave through `SnapshotBundle`.

## Competitive Landscape

- **Upstream App Manager:** Does breadth, Android-version adaptation, scanner performance, language controls, and accessibility fixes well. Learn through narrow, test-backed upstream ports. Avoid wholesale re-imports that overwrite NG package identity, trust copy, release policy, or deliberately divergent architecture.
- **AppDash:** Does tags, notes, history, storage analysis, versioned backup destinations, and polished information hierarchy well. Learn its user-state clarity and current storefront presentation. Avoid subscriptions, account dependence, and cloud-first defaults that contradict this fork's local-first posture.
- **Swift Backup:** Does scheduled backup lists, per-app configuration, restore guidance, and multiple storage targets well. Learn explicit capability/preflight messaging and recovery guidance. Avoid provider sprawl and implying that Shizuku can restore private app data without root.
- **Neo Backup:** Does FLOSS scheduled encrypted backups, batch lists, onboarding, and translation infrastructure well. Learn visible job outcomes and recoverable scheduling. Avoid silently treating a completed worker as proof that every app restored correctly.
- **Hail:** Does multi-tag freeze policy, automation, quick settings, and privilege-mode integrations well. Learn compact policy organization. Avoid making core workflows depend on Xposed or one root implementation.
- **Canta and UAD-ng:** Do evidence-backed debloat descriptions, restoration, cross-user checks, and safe defaults well. Learn from their conservative classification and current definitions. Avoid presenting a package as universally safe to remove across OEMs and users.
- **Blocker:** Does detailed component/IFW rule editing and rule backup well. Learn precise rule review and restore semantics. Avoid exposing raw privileged changes without AppManagerNG's preview, confirmation, history, and rollback layers.
- **LibChecker and InstallerX-Revived:** Do change snapshots, export, package-format coverage, install profiles, and privilege fallbacks well. Learn focused comparison and preflight surfaces. Avoid expanding AppManagerNG into a remote app catalog or update service.

## Security, Privacy, and Reliability

- [Verified] `snapshot/SnapshotBundle.java` copies every non-excluded shared-preference XML file byte-for-byte into an ordinary ZIP. Its file-level exclusion list contains only `keystore` and `server_secrets`, while `utils/AppPref.java` stores `authorization_key`, `tasker_plugin_signing_secret`, and `virus_total_api_key` in `preferences.xml`. Export therefore discloses live credentials, and import can overwrite them. A typed sensitive-key denylist must apply symmetrically on export and import.
- [Verified] The same ZIP contains preferences, app notes, profiles, rules, tags, and operation history without authenticated encryption. `settings/PrivacyPreferences.java` launches the SAF export immediately and only says keystore secrets are excluded (`res/values/strings.xml`); it does not warn that the remaining bundle is plaintext. Secret minimization is P0; a passphrase-derived AEAD envelope is the follow-up.
- [Verified] `db/AppsDb.java` declares schema 10 and migrations 2→3 through 9→10, but no 1→2 migration, then enables both `fallbackToDestructiveMigration()` and downgrade destruction. Room explicitly defines this as permanent table deletion. The claim that `apps.db` is rebuildable is false for `log_filter`, `op_history`, `fm_favorite`, `freeze_type`, and user-relevant backup rows.
- [Verified] `res/xml/backup_rules.xml` and `full_backup_rules.xml` exclude the entire database from cloud backup and device transfer. Manual `SnapshotBundle` preserves operation history but not log filters, file-manager favorites, or per-package freeze methods. Cached inventory and scan results should remain excluded; portable user-authored tables need stable, typed snapshot sections.
- [Verified] Recovery coverage is incomplete: `androidTest/.../db/MigrationTest.java` covers 7→8, 8→9, and 7→9, not 1→current, 9→10, every start schema, downgrade behavior, or preservation of user-owned rows. Migration failure should preserve the original DB and fail closed, never recreate it silently.

## Architecture Assessment

- **Separate cache from durable state:** `AppsDb` mixes rebuildable package/scan caches with filters, favorites, history, freeze choices, and backup metadata. Short term, classify every table and remove destructive fallback. Next, either split durable state into its own database or enforce per-table migration and snapshot contracts.
- **Replace opaque preference-file copying:** `SnapshotBundle` needs a portable-state codec with an explicit schema, typed sections, a centralized nonportable/sensitive-key policy, deterministic merge rules, and per-section counts. Legacy ZIP import can remain read-only compatibility after the new format lands.
- **Use a versioned encrypted envelope:** wrap the snapshot ZIP in an authenticated header plus Argon2id-derived AES-256-GCM payload (minimum Argon2id `m=19456`, `t=2`, `p=1`; random 16-byte salt, 12-byte nonce, 128-bit tag; header authenticated as AAD). The passphrase stays in a clearable `char[]`; wrong passwords or tampering must be detected before any import write.
- **Make release truth single-sourced:** `scripts/verify-release-consistency.sh` checks build pins, README badges, changelog presence, and selected `CLAUDE.md` strings, but misses the three distribution packets, removed-workflow claims, missing local links, and storefront images. Consequently, `docs/distribution/{fdroid,izzyondroid,accrescent}-listing.md` still describe v0.5.0/versionCode 7 while `app/build.gradle` and the latest tag are v0.6.5/versionCode 13; `CLAUDE.md` and `CONTRIBUTING.md` link to absent `PROJECT_CONTEXT.md`.
- **Refresh external UI evidence:** all nine `fastlane/metadata/android/en-US/images/phoneScreenshots/*.png` files predate the NG v0.6.x UI and visibly show legacy upstream-era identity/data. Capture deterministic, privacy-safe current screens for onboarding, app list, app details, permissions/AppOps, scanner, backup/restore, installer preflight, file manager, and settings/search across light and dark themes.
- **Test gaps:** add exhaustive Room migration fixtures, sensitive-key snapshot tests, encrypted-format wrong-password/tamper tests, portable-section merge tests, and release-doc contract tests. Existing support bundles, local crash capture, operation history, and diagnostics cover observability sufficiently; no new telemetry service is justified.
- **Dependency ceiling:** API 21 keeps Room at 2.7.x, WorkManager at 2.10.x, and Material at 1.13.x; current AndroidX defaults and Material 1.14 require API 23, and Material Views is in maintenance mode. Do not raise minSdk or rewrite in Compose without active-install evidence. Accessibility, i18n service integration, live-device UI validation, Android 17, TV, and broader multi-user validation already exist in `Roadmap_Blocked.md` and are intentionally not duplicated.

## Rejected Ideas

- **Cloud-first backup providers** (AppDash, Swift Backup): conflicts with local-first/offline operation and adds credentials, SDKs, and provider maintenance; SAF already permits user-chosen sync folders.
- **Built-in multi-source app updater/catalog** (Obtainium): changes a package manager/installer into a remote trust and availability service; keep signed APK inspection and installation focused.
- **Always-on VPN traffic monitor** (TrackerControl): duplicates a mature adjacent tool and adds persistent VPN, battery, and network-policy complexity outside the project's static inspection/control core.
- **Work-profile cloning/DPC ownership** (Shelter, Island): requires a different onboarding, ownership, recovery, and enterprise-policy model; AppManagerNG should inspect/manage profiles Android already exposes.
- **Generic privileged plugin SDK** (awesome-shizuku ecosystem): expands the attack surface around unstable hidden APIs and makes supportability depend on third-party code; keep explicit, reviewed integrations.
- **Full Compose rewrite** (Material Components maintenance notice): contradicts the deliberate Java/Views/API-21 baseline and offers no near-term safety or recovery value. Contain Views maintenance and revisit only with a platform-floor decision.
- **Subscription/pro tier** (AppDash): conflicts with the repository's GPL, no-ads, local-first philosophy and does not solve a verified engineering gap.
- **Cross-platform iOS transfer** (Android 16 QPR2 API): there is no iOS counterpart or cross-platform product goal, so implementing its backup contract would create dead architecture.

## Sources

### OSS and commercial products

- https://github.com/MuntashirAkon/AppManager
- https://github.com/aistra0528/Hail
- https://github.com/samolego/Canta
- https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
- https://github.com/lihenggui/blocker
- https://github.com/LibChecker/LibChecker
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/wxxsfxyzm/InstallerX-Revived
- https://github.com/ImranR98/Obtainium
- https://github.com/TrackerControl/tracker-control-android
- https://github.com/PeterCxy/Shelter
- https://github.com/oasisfeng/island
- https://appdash.app/
- https://www.swiftbackup.app/

### Ecosystem and community

- https://github.com/timschneeb/awesome-shizuku
- https://github.com/awesome-android-root/awesome-android-root
- https://www.reddit.com/r/AndroidQuestions/comments/1ue8zdh/do_android_backups_finally_fully_work_in_2026/

### Platform, distribution, dependencies, and security

- https://developer.android.com/training/data-storage/room/migrating-db-versions
- https://developer.android.com/identity/data/autobackup
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/
- https://f-droid.org/docs/Reproducible_Builds/
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/work
- https://github.com/material-components/material-components-android/releases
- https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html

### Research and update-system design

- https://www.usenix.org/conference/usenixsecurity23/presentation/gilsenan
- https://theupdateframework.io/docs/security/
- https://arxiv.org/abs/1701.05467

## Open Questions

- What proportion of active AppManagerNG installs still run API 21–22? That private distribution signal is the only evidence that can correctly decide whether preserving Android 5 support outweighs access to current Room, WorkManager, Material, and AndroidX releases.
