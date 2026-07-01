<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Research - AppManagerNG

## Executive Summary

AppManagerNG is a GPL Android package-manager fork for power users who inspect, install, freeze, archive, back up, restore, and repair apps through normal, root, ADB, Shizuku, and Dhizuku paths. Verified: the project is strongest where it keeps privileged Android work offline-first, testable, and reversible; recent code already covers native 16 KB library alignment, Android 17 local-network permission handling, installer transcripts, backup archive guards, unsupported-extra reporting, and multiple release gates. Highest-value direction: drain the remaining trust gaps around Android backup/restore, privileged-server state, intent parsing recovery, and host-test/build truth before adding new surfaces. Top opportunities: 1. exclude or rotate local privileged-server secrets from Android cloud/D2D backup; 2. add bounded component-rule reset progress and partial-failure recovery already tracked in `ROADMAP.md`; 3. reconcile local-server port changes with live server/session state; 4. preserve Activity Interceptor output when unknown Parcelables are present; 5. re-enable ignored Robolectric fixture tests already tracked in `ROADMAP.md`; 6. finish docs/archive truth already tracked in `ROADMAP.md`; 7. codify the Robolectric SDK36/JDK matrix; 8. complete runtime feature truth in System Config.

## Product Map

- Core workflows: app inventory/search/filter/sort, app details, APK install/export/verify, Activity Interceptor command generation, backup/restore/conversion, archive/freeze/unfreeze, component/app-op/permission rules, debloat guidance, file management, local docs, and privilege health diagnostics.
- User personas: rooted Android power users, Shizuku/ADB users without root, ROM/device maintainers, privacy auditors, APK/app developers, and offline FLOSS users who cannot rely on hosted services.
- Platforms and distribution: Android minSdk 21, targetSdk 36, compileSdk 37, FLOSS/FULL flavors, Java/Kotlin Android Views, Material Components 1.13.0, Gradle 9.6.1, AGP 9.2.1, native/server helper modules, GPL-3.0-or-later.
- Key integrations and data flows: PackageManager/PackageInstaller, root/libsu, Shizuku, ADB pairing/local server, Dhizuku, Android app archiving, Room-backed metadata, backup archives/manifests, OpenPGP/Bouncy Castle, tracker/library scanners, optional FULL network sources, and local documentation generation.

## Competitive Landscape

- Upstream App Manager: breadth benchmark for app ops, backups, system config, ADB backups, filter profiles, installer options, and accessibility; learn API coverage and bug signals; avoid blind upstream ports without NG privacy, minSdk, and source-contract review.
- Canta and Thor: strongest debloat lesson is safety labeling plus reversible `pm uninstall --user`/`install-existing` flows; learn explicit risk chips and privilege-mode clarity; avoid downloading or trusting mutable safety data in FLOSS paths.
- Neo Backup, Swift Backup, and Titanium Backup: backup users expect clear versioning, schedulers, batch restore, and special-data transparency; learn state visibility and restore caveats; avoid cloud/sync expansion until local Android backup and archived-app semantics are unambiguous.
- Hail, Blocker, and PermissionManagerX: focused controls make permission/app-op/component changes understandable; learn idempotent "already applied" feedback and backup/restore of reference states; avoid collapsing privileged partial failures into generic booleans.
- LibChecker and Inure: dense inspection surfaces can stay usable when evidence is grouped and source is explicit; learn scannable technical detail and reproducible-build trust cues; avoid extra dashboards where AppManagerNG already has scanner coverage.
- SD Maid SE and adjacent file managers: mature file tools emphasize SAF setup, cancellation, and recoverable cleanup; learn operation lifecycle guards; avoid a file-manager rewrite when targeted cancellation/logging items remain.
- AppDash: commercial signal for organization, notes/tags, watchlists, and operation history; learn compact inventory ergonomics; avoid Play/network-backed intelligence in FLOSS builds.

## Security, Privacy, and Reliability

- Verified: `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/full_backup_rules.xml` include all shared preferences except Chromium prefs, while `ServerConfig` stores a local privileged-server token in `server_config` shared preferences (`app/src/main/java/io/github/muntashirakon/AppManager/servermanager/ServerConfig.java`). Android Auto Backup includes shared preferences by default and restores before first launch, so volatile privileged-server secrets need exclusion or rotation.
- Verified: local ADB server port changes only save the new preference and show a restart notice (`AdvancedPreferences.java`), while `LocalServer`/`LocalServerManager` bind and handshake using `ServerConfig.getLocalServerPort()`/`getLocalToken()`. `ServerStatusChangeReceiver` also has a TODO to broadcast started-state updates, leaving settings and live privilege state weakly coupled.
- Verified: Activity Interceptor catches `BadParcelableException` in `getUri()` and returns `null` (`ActivityInterceptor.java`), losing the whole generated intent URI when one unknown extra cannot be unmarshalled. Android documents this exception as typical when a custom Parcelable crosses into a process without its class.
- Verified: `SystemConfig.java` still comments out runtime feature additions for file-based encryption, adoptable storage, incremental delivery, and app enumeration. Upstream App Manager exposes system configuration as a root-only feature, so unknown/stale feature truth should be explicit.
- Verified existing roadmap coverage remains valid: component-rule reset needs bounded progress/partial-failure reporting; ignored Robolectric fixtures hide ZIP/VFS/TAR/OAB/settings-search regressions; production `printStackTrace()` cleanup, offline manual truth, archive-link truth, and crash/replay ignore rules are still active in `ROADMAP.md`.
- Likely: dependency posture is current but brittle at edges. Bouncy Castle 1.84 fixes recent OpenPGP/CVE issues and is already adopted; Robolectric 4.16 supports SDK36 but documents a JDK21 requirement for SDK36-target tests, while `BUILDING.rst` still says JDK 17+.

## Architecture Assessment

- Boundary improvements: privileged-server lifecycle should own config-change reconciliation instead of leaving settings toasts, receiver TODOs, and `LocalServer` liveness checks as separate behavior. Candidate files: `AdvancedPreferences.java`, `LocalServer.java`, `LocalServerManager.java`, `ServerStatusChangeReceiver.java`, `Ops.java`.
- Refactor candidates: Android backup eligibility needs a source-contract test around `backup_rules.xml`, `full_backup_rules.xml`, and secret-bearing preference names; the implementation can either move local tokens to `noBackupFilesDir`, exclude `server_config.xml`, or rotate token on restore.
- Refactor candidates: Activity Interceptor should serialize safe base fields even when extras contain unknown Parcelables, then report skipped keys/types through the existing unsupported-extra warning surface. Candidate files: `ActivityInterceptor.java`, `IntentCompat.java`, related interceptor tests.
- Refactor candidates: System Config runtime features should use public/compat APIs where available and mark unavailable/unknown runtime-only features deliberately instead of leaving commented AOSP code.
- Test gaps: ignored Robolectric fixture tests, no backup-rule secret contract, no local-server port-change unit contract, no BadParcelable interceptor fallback test, and no JDK/toolchain guard for SDK36 Robolectric execution.
- Documentation gaps: existing `ROADMAP.md` already tracks manual source truth and archive-link truth; add only the JVM/toolchain matrix if tests require JDK21.

## Rejected Ideas

- Compose rewrite or dependency-led Material migration: rejected because `CONTRIBUTING.md` says no Compose and the repo still targets minSdk 21 with Material Components pinned in `versions.gradle`.
- Native 16 KB library alignment item: rejected as duplicate; `NativeLibraries.java`, strings, changelog, and release verifier scripts already expose/check this.
- Android 17 local-network permission item: rejected as duplicate; manifest, strings, startup recovery, `Ops`, and `WifiWaitService` already handle `ACCESS_LOCAL_NETWORK`.
- Installer transcript/gentle-update surface: rejected for now; installer status transcripts, update ownership, background install behavior, and session failure mapping already exist, while `InstallConstraints.GENTLE_UPDATE` is more relevant to app-store auto-update flows than AppManagerNG's explicit installs.
- Emergency jadx CVE item: rejected after source review; the cited jadx package-name shell injection affects `jadx-gui` ADB launch before 1.5.0, while this project uses jadx core/dex input paths and already has dependency-check gates.
- Cloud-backup/sync provider expansion: rejected because Neo Backup/Swift Backup make it attractive, but AppManagerNG's FLOSS/offline privacy model and current trust gaps make local correctness higher value.
- Plugin ecosystem/marketplace: rejected because no plugin boundary exists, package-manager operations are privilege-sensitive, and current architecture favors pinned local data over runtime extension loading.
- Broad UAD model ingestion: rejected until a pinned data contract exists; Canta/Thor show the value, but mutable OEM safety data can turn destructive if stale.
- Accessibility/i18n mega-pass: rejected as a new roadmap item because recent code already added pseudolocales, Android 13 language support, and multiple accessibility passes; keep targeted fixes tied to concrete UI bugs.

## Sources

OSS and upstream:

- https://github.com/MuntashirAkon/AppManager
- https://github.com/MuntashirAkon/AppManager/releases
- https://github.com/samolego/Canta
- https://github.com/trinadhthatakula/Thor
- https://github.com/NeoApplications/Neo-Backup
- https://github.com/aistra0528/Hail/blob/master/README_EN.md
- https://github.com/aistra0528/Hail/issues/398
- https://github.com/lihenggui/blocker
- https://github.com/mirfatif/PermissionManagerX
- https://github.com/LibChecker/LibChecker
- https://github.com/Hamza417/Inure
- https://github.com/d4rken-org/sdmaid-se
- https://github.com/timschneeb/awesome-shizuku
- https://github.com/awesome-android-root/awesome-android-root

Commercial and community:

- https://appdash.app/
- https://play.google.com/store/apps/details?hl=en_US&id=org.swiftapps.swiftbackup
- https://www.titaniumtrack.com/titanium-backup.html
- https://www.reddit.com/r/fossdroid/comments/1syj7lt/best_app_to_freezedisable_with_shizuku/

Platform, dependency, and security:

- https://developer.android.com/identity/data/autobackup
- https://developer.android.com/reference/android/os/BadParcelableException
- https://developer.android.com/guide/components/activities/parcelables-and-bundles
- https://developer.android.com/reference/android/content/pm/PackageManager
- https://developer.android.com/reference/android/content/pm/ArchivedPackageInfo
- https://developer.android.com/reference/android/content/pm/PackageInstaller.InstallConstraints
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://support.google.com/android-developer-console/answer/16650243?hl=en
- https://github.com/robolectric/robolectric/releases/
- https://docs.gradle.org/current/release-notes.html
- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://www.bouncycastle.org/resources/new-releases-bouncy-castle-java-1-84-and-bouncy-castle-java-lts-2-73-11/

## Open Questions

- Should the local privileged-server token be excluded entirely from backup or rotated after restore while preserving non-secret server preferences?
- Should a local-server port change automatically restart the server in every privilege mode, or mark the current server stale when a safe restart is not possible?
