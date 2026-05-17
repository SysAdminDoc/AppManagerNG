<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# PROJECT_CONTEXT — AppManagerNG canonical project memory

> **Read me first.** This file is the canonical "where do I look?" index for AppManagerNG.
> It links to the load-bearing artifacts rather than duplicating them, because the
> primary documents (ROADMAP.md, CHANGELOG.md, CLAUDE.md, the audit/research dirs) are
> the source of truth and they update faster than this index does.
>
> Last consolidated: **2026-05-17 pass 21**. The 2026-05-17 walk-away sequence now has
> twenty-one local passes: foundation, source-fix/architecture follow-through, Android-17 audit
> follow-through, Shizuku/ML-DSA implementation follow-through, and USB-debugging
> preflight follow-through for Wireless ADB / Shizuku setup, installer checksum
> confirmation, privileged battery-optimization auto-fix for routines/backups,
> cross-user package-state/Finder follow-through, and opt-in debloat-definition
> auto-update follow-through, Privileges health-check follow-through,
> capability-dropping diagnostics follow-through, and Finder debloat-description
> search follow-through, Finder backup-only app results, permission-state filters,
> Finder relevance scoring, the signature-gated automation broadcast API, the
> stale APK share-target receiver audit closure, per-app launcher action
> shortcuts, the `floss` / `full` distribution flavor split, the
> LocalServer bootstrap smoke test in Settings -> Privileges, and the
> scrubbed support-info bundle composer in Settings -> Troubleshooting. Run `git status --short --branch`
> for the exact current branch/ahead state before starting new code work.

---

## 1. What this project is, in one paragraph

AppManagerNG is a **continuation/fork of [MuntashirAkon/AppManager](https://github.com/MuntashirAkon/AppManager)**
— a full-featured root/ADB/Shizuku-aware Android package manager — bootstrapped on
2026-04-30 from upstream commit
[`3d11bcb`](https://github.com/MuntashirAkon/AppManager/commit/3d11bcbc399d3a4f995b544e26d86bd80487fd32)
(post-v4.0.5). The NG fork's product thesis is **"all the power, half the friction"**:
keep every power-user capability (component blocking, tracker scanning, backup/restore,
app-ops editing, hidden-API reach), but rebrand under `io.github.sysadmindoc.AppManagerNG`,
ship a Material 3 + onboarding-led UX, and treat **Shizuku as a first-class privilege
path** alongside root and ADB.

Current release: **v0.4.2** (2026-05-13). Code 6.

License: **GPL-3.0-or-later**, REUSE-compliant per-file SPDX headers; vendored deps keep
their own licenses (Apache-2.0, BSD-2/3, CC-BY-SA-4.0, GPL-2.0, ISC, MIT, WTFPL).

---

## 2. Where things live — entry points for a new AI session

Read these in order. Do **not** rewrite them as a drive-by; they are mature.

| Document | Lines | What it gives you |
|----------|------:|-------------------|
| [`CLAUDE.md`](CLAUDE.md) | 129 | Stack, build commands, origin, gotchas, version status. Tool-specific working notes. |
| [`AGENTS.md`](AGENTS.md) | 9 | Pointer to `CLAUDE.md` + shared codex memory dir. |
| [`README.md`](README.md) | 185 | Public user-facing surface — features, install, signing fingerprint. |
| [`ROADMAP.md`](ROADMAP.md) | large | The plan. Tier-organised (Now / Next / Later / Under Consideration / Rejected) with an Engineering Debt Register, Upstream Sync Strategy, and iter-18 → iter-31 research deltas inline. Cites **329 numbered external sources** in a Source Appendix at the bottom. |
| [`CHANGELOG.md`](CHANGELOG.md) | large | Per-release notes back to v0.1.0; "Unreleased" section currently holds 2026-05-14 → 2026-05-17 shipped work. |
| [`docs/research/`](docs/research/) | 4 files | `2026-05-02-android-power-tools.md`, `2026-05-09-capability-extension.md`, `2026-05-09-observability-testing-audit.md`, `2026-05-09-roadmap-extension-phase-2.md`. Plus `iter-6-delta.md`. |
| [`docs/audits/`](docs/audits/) | 20 files + README | Per-audit verdicts for Android 16/17/18 platform changes, crypto/dependency bumps, predictive back, Play policy, and Shizuku Android-17 compatibility. Read `docs/audits/README.md` first for verdict vocabulary. |
| [`research/iter-20-delta.md`](research/iter-20-delta.md) | — | Free-form 2026-05-08 issue-mining notes from the iter-20 sweep. Subsequent iters live inline in ROADMAP. |
| [`design/`](design/) | 7 files | Premium-facelift design system (`spec/1-design-system.md`, `impl/values/*-v2.xml`, `impl/layout/*_v2.xml`, `plan/3-rollout.md`, `audit/0-recon.md`, `audit/4-painpoints.md`). Driven by [`codexprompt.md`](codexprompt.md) at repo root. |
| [`docs/distribution/`](docs/distribution/) | 6 files | Obtainium config (`obtainium-config.json` + `.license`), reproducible-builds doc, backup-destinations matrix, package-visibility dossier, build-flavor contract. |
| [`docs/policy/minsdk-21-ceiling.md`](docs/policy/minsdk-21-ceiling.md) | — | Running ledger of every dep that has dropped (or imminently drops) API 21-22 support. Read this **before** bumping `min_sdk` in [`versions.gradle`](versions.gradle). |
| [`docs/security-advisories/`](docs/security-advisories/) | 1 file | CVE-2026-0073 ADB-mode advisory. |
| [`docs/intent-api.md`](docs/intent-api.md) | — | `app-manager://` + `am://` deep-link contract. |
| [`docs/sideload-verification.md`](docs/sideload-verification.md) | — | Position document for Google's 2026-09-30 Brazil/Indonesia/Singapore/Thailand developer-verification enforcement. |
| [`.ai/research/2026-05-17/`](.ai/research/2026-05-17/) | pass 1 | Foundation research-run audit; STATE_OF_REPO, MEMORY_CONSOLIDATION, SOURCE_REGISTER, FEATURE_BACKLOG, etc. |
| [`.ai/research/2026-05-17-pass-2/`](.ai/research/2026-05-17-pass-2/) | pass 2 | Source fixes + architecture docs follow-through. |
| [`.ai/research/2026-05-17-pass-3/`](.ai/research/2026-05-17-pass-3/) | pass 3 | Android 17 targetSdk=37 audit batch + CI/docs hygiene follow-through. |
| [`.ai/research/2026-05-17-pass-4/`](.ai/research/2026-05-17-pass-4/) | pass 4 | Shizuku Android-17 runtime warning, release watcher, ML-DSA display-name map, and updated source register. |
| [`.ai/research/2026-05-17-pass-5/`](.ai/research/2026-05-17-pass-5/) | pass 5 | T5 USB-debugging preflight for Wireless ADB / Shizuku setup and next-run handoff. |
| [`.ai/research/2026-05-17-pass-6/`](.ai/research/2026-05-17-pass-6/) | pass 6 | Installer session SHA-256 confirmation and Dhizuku minSdk integration constraint. |
| [`.ai/research/2026-05-17-pass-7/`](.ai/research/2026-05-17-pass-7/) | pass 7 | Root/ADB battery-optimization auto-fix helper wired into profile routines and long-running backup batch operations. |
| [`.ai/research/2026-05-17-pass-8/`](.ai/research/2026-05-17-pass-8/) | pass 8 | Cross-user package-state buckets in the main list and Finder multi-user scope. |
| [`.ai/research/2026-05-17-pass-9/`](.ai/research/2026-05-17-pass-9/) | pass 9 | Opt-in debloat-definition auto-update cache, manifest/checksum verifier, and app-private fallback path. |
| [`.ai/research/2026-05-17-pass-10/`](.ai/research/2026-05-17-pass-10/) | pass 10 | Settings → Privileges health-check screen for mode, root/Shizuku/ADB, remote services, and battery optimization. |
| [`.ai/research/2026-05-17-pass-11/`](.ai/research/2026-05-17-pass-11/) | pass 11 | Android 16 capability-dropping diagnostic in Settings -> Privileges, backed by active-shell UID + `CapEff` parsing. |
| [`.ai/research/2026-05-17-pass-12/`](.ai/research/2026-05-17-pass-12/) | pass 12 | VPN plugin flag blocker audit and Finder debloat-description search predicates. |
| [`.ai/research/2026-05-17-pass-13/`](.ai/research/2026-05-17-pass-13/) | pass 13 | Finder backup-only result rows for validated uninstalled app backups. |
| [`.ai/research/2026-05-17-pass-14/`](.ai/research/2026-05-17-pass-14/) | pass 14 | Permission-state filter predicates backed by `FilterablePermissionInfo`. |
| [`.ai/research/2026-05-17-pass-15/`](.ai/research/2026-05-17-pass-15/) | pass 15 | Finder relevance scoring for literal package/component/tracker search filters. |
| [`.ai/research/2026-05-17-pass-16/`](.ai/research/2026-05-17-pass-16/) | pass 16 | Signature-gated automation broadcast API for existing batch/profile/installer/tracker operations. |
| [`.ai/research/2026-05-17-pass-17/`](.ai/research/2026-05-17-pass-17/) | pass 17 | Stale APK share-target roadmap row closed by manifest/installer audit. |
| [`.ai/research/2026-05-17-pass-18/`](.ai/research/2026-05-17-pass-18/) | pass 18 | Dynamic and pinned per-app launcher shortcuts for freeze, force-stop, and clear-cache actions. |
| [`.ai/research/2026-05-17-pass-19/`](.ai/research/2026-05-17-pass-19/) | pass 19 | `floss` / `full` build flavors, optional-network feature gating, Obtainium full-build targeting, and release/test artifact path updates. |
| [`.ai/research/2026-05-17-pass-20/`](.ai/research/2026-05-17-pass-20/) | pass 20 | LocalServer bootstrap smoke test in Settings -> Privileges plus shared success/failure bootstrap signature formatter. |
| [`.ai/research/2026-05-17-pass-21/`](.ai/research/2026-05-17-pass-21/) | pass 21 | Support-info text bundle composer in Settings -> Troubleshooting with scrubbed logcat tail and LocalServer signature capture. |

**The full external-source corpus the project relies on is in `ROADMAP.md` → "Source Appendix" (S01–S329).** Do not start a new external-research pass without scanning that table first — most modern Android-power-tool ground has been mined.

---

## 3. Stack, build, code stats

- **Language**: Java + Kotlin (Java in core, Kotlin in newer additions). **629 `.java` files** under `app/src/main/java/io/github/muntashirakon/AppManager/`. Kotlin file count is lower.
- **UI**: Android Views + Material Components **1.13.0**. Compose is **out of scope** — see `codexprompt.md` ("DO NOT propose Jetpack Compose. Compose migration is a multi-year project").
- **Build**: Gradle 8.x, AGP `8.13.2`, Java 8 source/target with desugaring, NDK + CMake for native.
- **min/target SDK**: **21 / 36**.
- **Modules** (top-level): `app/`, `libcore/`, `libserver/`, `libopenpgp/`, `hiddenapi/`, `server/`, `libs/`, `scripts/`, `docs/`, `fastlane/`, `LICENSES/`.
- **app package tree** (excerpt — what each folder does is mostly inferrable from the name): `accessibility`, `adb`, `apk`, `app`, `backup`, `batchops`, `changelog`, `compat`, `crypto`, `db`, `debloat`, `details`, `dex`, `editor`, `filters`, `fm` (file manager), `history`, `intercept`, `ipc`, `logcat`, `logs`, `magisk`, `main`, `misc`, `miui`, `onboarding`, `oneclickops`, `permission`, `permissions`, `profiles`, `progress`, `rules`, `runner`, `runningapps`, `scanner`, `self`, `servermanager`, `session`, `settings`, `sharedpref`, `shizuku`, `shortcut`, `ssaid`, `sysconfig`, `terminal`, `types`, `uri`, `usage`, `users`, `utils`, `viewer`.
- **CI workflows** ([`.github/workflows/`](.github/workflows/)): `codeql.yml`, `dependency-scan.yml`, `lint.yml`, `release.yml`, `tests.yml`, `upstream-rename-watch.yml`.

### Key dependency pins (from [`versions.gradle`](versions.gradle))
| Dep | Pinned | Notes |
|-----|--------|-------|
| `compile_sdk` | 36 | Android 16 |
| `agp_version` | `8.13.2` | AGP 9.x cliff acknowledged in roadmap |
| `material_version` | `1.13.0` | **Ceiling** — `1.14.0-rc01` requires minSdk 23 |
| `bouncycastle_version` | `1.84` | CVE-2026-3505 / 5588 / 5598 closed |
| `gson_version` | `2.14.0` | Built-in `java.time` adapters, strict duplicate-JSON-key handling |
| `libsu_version` | `6.0.0` | `Shell.cmd` migration audit clean |
| `shizuku_version` | `13.1.5` | Shizuku-API floor for compile-time; runtime tested against Shizuku Manager 13.6.0+ |
| `jadx_version` | `1.4.7` | **7 releases behind** 1.5.5. Upgrade before T12 APK editing work. |
| `min_sdk` | 21 | Bump gated by [`docs/policy/minsdk-21-ceiling.md`](docs/policy/minsdk-21-ceiling.md). |
| `activity_version` | `1.11.0` | API 21-22 dropped in 1.12.x |
| `biometric_version` | `1.4.0-alpha04` | API 21-22 dropped in 1.4.0-alpha05 |
| `room_version` | `2.7.2` | API 21-22 dropped in 2.8.x |
| `webkit_version` | `1.14.0` | API 21-22 dropped in 1.15.x |

The minSdk-21 floor is a load-bearing decision; the ledger documents which deps it freezes.

---

## 4. Current pass-19 state as of 2026-05-17

The stale pass-1 "uncommitted work" list is resolved. The Finder regex fix, install-transcript
redactor, and onboarding detach fix all landed in local commits (`73387cd`, `bcb2874`,
`25c629a`) and are documented in `CHANGELOG.md`.

Pass 4 added three small implementation follow-ups:

| File / area | Type | Diff highlight |
|-------------|------|----------------|
| [`ShizukuBridge.java`](app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java) + [`OnboardingFragment.java`](app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java) | platform-risk mitigation | Android-17-only `hasAndroid17CompatibilityRisk(Context)` warning while Shizuku's fixed-version floor is unknown; warning launches the existing Wireless ADB setup flow. |
| [`.github/workflows/shizuku-release-watch.yml`](.github/workflows/shizuku-release-watch.yml) | process automation | Weekly official Shizuku release watcher opens a maintainer issue when 13.6.x / 13.7.x release notes mention Android 17 or #1965 / #1967. |
| [`Utils.java`](app/src/main/java/io/github/muntashirakon/AppManager/utils/Utils.java), [`PackageUtils.java`](app/src/main/java/io/github/muntashirakon/AppManager/utils/PackageUtils.java), [`ScannerFragment.java`](app/src/main/java/io/github/muntashirakon/AppManager/scanner/ScannerFragment.java) | Android 17 polish | ML-DSA-65 / ML-DSA-87 certificate OIDs render as readable Dilithium algorithm names instead of raw OIDs where the platform provider lacks a friendly name. |

Pass 5 closed the T5 "USB Debugging Prompt in Shizuku Setup" row: onboarding's
Wireless ADB setup flow now preflights the `adb_enabled` Developer Options flag
and prompts users to enable both USB debugging and Wireless debugging before
pairing. The Shizuku / Wireless ADB / pairing instructions now name both toggles
so the silent `adb pair` / `adb connect` failure path is visible before setup.

Pass 6 closed T5's installer checksum row: `PackageInstallerCompat` hashes bytes
as they stream into `PackageInstaller.Session.openWrite()`, passes the digest
through `PackageInstallerBroadcastReceiver`, and `PackageInstallerActivity`
shows a pre-system-prompt checksum dialog. The same pass left Dhizuku open after
checking primary sources: Dhizuku-API `2.5.4` is current, but its upstream module
declares `MIN_SDK = 26`; NG is still API 21, so a reflection/optional-provider
design or minSdk decision is needed before integration.

Pass 7 closed T5's root/ADB battery-optimization auto-fix row: new
`SelfBatteryOptimization` centralizes NG's own Doze-exemption state and privileged
`DEVICE_POWER` auto-fix, `ProfileApplierService` calls it for routine/profile
execution, `BatchOpsService` calls it for long-running backup/import/restore
operations, and `TroubleshootingPreferences` reuses the same helper for the
manual UI path.

Pass 8 closed cross-user package state and Finder multi-user scope: `ApplicationItem`
now keeps per-user enabled/disabled/uninstalled buckets, the main list and
multi-user picker show explicit user-state labels, and Finder loads all selected
users through `Users.getUsersIds()` while labeling each result with user/state.

Pass 9 closed T5's auto-update debloat-definition row: Settings → Privacy now has a
default-off "Update debloat definitions on launch" opt-in that only runs when the
existing Internet feature gate is enabled. `DebloatDefinitionsUpdater` fetches the
pinned AppManagerNG raw-GitHub manifest, verifies `debloat.json` and
`suggestions.json` by byte length and SHA-256, schema-smoke-tests both payloads,
writes them atomically to app-private storage, and `StaticDataset` falls back to
bundled assets when no valid cache exists.

Pass 10 closed T5's Privilege Health-Check Screen row: Settings now has a
`PrivilegeHealthPreferences` page titled **Privileges** with current-mode diagnostics,
root manager detection, Shizuku API/min-version status, USB/Wireless ADB status,
remote server/service status, a mode self-test row, and the same
`SelfBatteryOptimization` one-tap battery whitelist path used by Troubleshooting.

Pass 11 closed T5's Android 16 capability-dropping UI row: Settings -> Privileges
now has a "Capability dropping (--drop-cap)" row backed by
`RootCapabilityDiagnostics`, which runs through the active `Runner` privilege path,
reads `id -u` plus `CapEff` from `/proc/$$/status`, and reports root, dropped,
present, unavailable, or unknown states. The remaining deeper Magisk-version or
root-manager config parsing belongs to the separate T9 provider-introspection row.

Pass 12 audited T5's VPN plugin flags row and parked it as blocked because the repo
has no `VpnService`, `BIND_VPN_SERVICE`, or Shizuku VPN binding/session surface to
control. The same pass closed T7's Finder description-field search row by extending
`BloatwareOption` with `description_*` predicates over `DebloatObject.getDescription()`,
so Finder can match plain-language debloat prose from the bundled or cached
definition set.

Pass 13 closed T7's Finder uninstalled-app-backups row. `FinderViewModel` now opts
into backup-only rows after its PackageManager pass by reading the AppManagerNG
backup metadata DB, validating each archive's presence, selecting the newest backup
per package/user pair, and skipping pairs already returned by
`MATCH_UNINSTALLED_PACKAGES`.
`BackupFilterableAppInfo` is the synthetic row adapter for archived uninstalled apps;
it preserves label/version/system/has-code/keystore/rule signals while reporting
`isInstalled() == false` so Finder's visible user/state line and existing filters
continue to behave like normal package rows.

Pass 14 closed T7's Filter: Permission Flags row. `FilterablePermissionInfo` is the
shared permission-state model for requested permissions: grant state comes from
`PackageInfo.requestedPermissionsFlags`, runtime permission flags come from
`PermissionCompat.getPermissionFlags()` when the active privilege path can read
them, and custom-source/fixed-state helpers feed `PermissionsOption`. Both
`FilterableAppInfo` and `ApplicationItem` now expose this model through
`IFilterableAppInfo.getAllPermissionDetails()`.

Pass 15 closed T7's Finder relevance-scoring row. `FinderViewModel` still uses the
existing `FilterItem` evaluator for inclusion, then sends results through
`FinderRelevanceScorer` when literal package-name, component-name, or tracker-name
search predicates are present. The scorer ranks by Levenshtein distance against
full package names, simple package names, tokens, sliding windows, and matched
component/tracker class names; regex/negative predicates are skipped and unrelated
filter matches keep their original scan order.

Pass 16 closed the iter-22/T8 Broadcast Intent API row. `AutomationIntents` defines
the stable `io.github.sysadmindoc.AppManagerNG.action.*` action/extra constants,
`AutomationReceiver` is exported behind the signature-only
`io.github.sysadmindoc.AppManagerNG.permission.AUTOMATION` permission, and dispatch
reuses existing execution paths: `BatchOpsService` for package operations,
`ProfileApplierService` for profiles, `PackageInstallerActivity` for install URI
handoff, and `AppDetailsActivity.getIntentForTrackers()` for tracker review.

Pass 17 closed the T11 APK Share-Target Receiver row as stale. The manifest already
exports `PackageInstallerActivity` for shared APK/APKM/XAPK payloads via
`ACTION_SEND`, `ACTION_SEND_MULTIPLE`, `ACTION_VIEW`, and `ACTION_INSTALL_PACKAGE`;
`ApkQueueItem.fromIntent()` already maps those incoming URIs into installer queue
items while preserving URI permission grants; and `PackageInstallerActivity` already
surfaces tracker counts, dependency warnings, session SHA-256 confirmation, and
signature-mismatch handling in the install flow.

Pass 18 closed the T8 App Shortcut: Freeze / Force-Stop / Clear Cache Per-App row.
`AppActionShortcutPublisher` refreshes dynamic launcher shortcuts from the loaded
main-list app snapshot, ranking recent installed apps and only exposing actions
the current privilege path supports. `AppActionShortcutActivity` is non-exported
and authenticated through `BaseActivity` before it dispatches to `FreezeUtils` or
`PackageManagerCompat`. App Details long-press affordances now pin force-stop and
clear-cache shortcuts, and freeze/unfreeze shortcuts use explicit action labels by
default.

Pass 19 closed the T1 `floss` vs `full` Build Flavors row. `app/build.gradle`
now defines a `distribution` flavor dimension with default `floss` and optional
`full`; `BuildConfig.ALLOW_OPTIONAL_NETWORK_FEATURES` gates the optional online
surfaces through `FeatureController`. `floss` keeps ADB / localhost networking
available but disables the user-facing Internet feature switch, VirusTotal,
Pithus lookups, and debloat-definition auto-updates. `full` keeps the existing
opt-in behavior. Release scripts now collect recursive flavor APK outputs, CI
artifact globs handle flavored reports, and Obtainium targets `full` assets.
F-Droid metadata should build `flossRelease`; see
[`docs/distribution/build-flavors.md`](docs/distribution/build-flavors.md).

Pass 20 closed the T4 LocalServer Bootstrap Smoke Test row. `LocalServer` now has
a shared `buildBootstrapSignature(...)` formatter used by the existing failure log
and by the success-path smoke test. Settings -> Privileges adds a "LocalServer
bootstrap smoke test" row that runs the privileged-shell handshake plus `id -u`,
then shows the device/build/mode/UID/LineageOS/probe/exception signature in a
copyable dialog. This intentionally lands in the existing Privileges diagnostics
screen rather than a new Developer page because pass 10 made that screen the
project's mode and remote-service health-check home.

Pass 21 closed the T4 Support Info Bundle Composer row. `SupportInfoBundle`
creates a zero-network `support-info-<device>-<timestamp>.txt` file from Settings
-> Troubleshooting, sharing it through `FmProvider` with explicit ClipData grants.
The bundle records app/build version, Android/ROM fields, configured/inferred
mode, provider status, root-manager/Sui/ZygiskNext markers, feature flags, the
remembered LocalServer bootstrap signature, and a 120-line logcat tail scrubbed
for package-like tokens, URIs, storage paths, emails, UIDs, and large numeric IDs.
`LocalServer` now persists the latest bootstrap signature for this bundle.

Unit-test files from passes 4-21 cover the new helpers, but local Gradle execution is
still blocked on this Windows shell because no JDK is installed / `JAVA_HOME` is unset.

---

## 5. Hard project facts (don't relearn these)

These are decisions, gotchas, and non-obvious facts already documented elsewhere in the
repo. Reading them here saves a fresh AI session a re-discovery pass.

### License & redistribution
- **GPL-3.0-or-later** throughout. New files must carry `// SPDX-License-Identifier: GPL-3.0-or-later` (or `<!-- … -->` for XML/HTML).
- **`COPYING` and `LICENSES/` directory are load-bearing** — never delete or "tidy". F-Droid / IzzyOnDroid review fails without REUSE compliance.
- Vendored dep licenses (Apache-2.0, BSD-2/3, CC-BY-SA-4.0, GPL-2.0, ISC, MIT, WTFPL) live under `LICENSES/`. Each must remain.

### Identity vs source namespace
- **applicationId** = `io.github.sysadmindoc.AppManagerNG` (Android install identity → installs side-by-side with upstream AppManager).
- **Java/Kotlin package namespace** stays `io.github.muntashirakon.AppManager` — do **not** rename as a drive-by; it'd be a high-risk churn commit for no behavior win.
- `app_name` resolves to "AppManagerNG"; many internal dialogs still say "App Manager" (copy debt scheduled for v0.3.0+ sweep).

### Debug keystore is intentionally checked in
- `app/dev_keystore.jks` + plaintext password in `app/build.gradle` is upstream's deliberate shared debug-signing config so devs can install debug builds over each other. **Not a leak.** Do not "fix".
- Release signing uses a separate keystore — released APK SHA-256 fingerprint is `21:5F:B4:70:63:2E:A6:CD:59:A4:BA:AB:35:0A:9E:0B:99:AD:11:0F:DD:FA:F5:A9:EA:64:61:E5:D0:C2:38:6C`. Published at the stable URL [`docs/fingerprints.txt`](docs/fingerprints.txt) for programmatic verification (AppVerifier, etc.).

### Submodules required at build time
- `scripts/android-libraries` and `scripts/android-debloat-list` — fetched from upstream-maintained repos via `git submodule update --init --recursive`. Tracker scanner won't ship its database without them.
- `.gitmodules` currently points at upstream MuntashirAkon repos for these — that is a build-dependency pointer, not a fork relationship.
- A SysAdminDoc fork of `android-debloat-list` (`+112 entries` from S22 Ultra US scrape, then `+562` UAD-NG delta sync) is referenced from commit `c3fb75b` onward.
- Runtime debloat-definition updates are now decoupled from APK releases through [`docs/debloat-definitions/manifest.json`](docs/debloat-definitions/manifest.json). Future list-refresh-only work should regenerate `app/src/main/assets/debloat.json` / `suggestions.json`, update the manifest byte counts + SHA-256s, and let app clients fetch the verified snapshot when the user has opted in.

### Pull policy vs upstream (`MuntashirAkon/AppManager`)
- **Security fixes from upstream**: pull immediately regardless of conflict cost.
- **Bug fixes**: cherry-pick within one upstream release cycle.
- **Upstream "Upcoming Features"** (Finder, APK editing, Routine Ops, Crash Monitor, Database Viewer, Terminal): pull when upstream ships; NG reimplements only if upstream stalls for >12 months.
- A weekly CI workflow [`upstream-rename-watch.yml`](.github/workflows/upstream-rename-watch.yml) opens an auto-issue if `MuntashirAkon/AppManager` changes slug.

### "Pro Mode" / Premium facelift
- The design facelift in [`design/`](design/) ships behind **`PREF_PREMIUM_PREVIEW_BOOL`** (Settings → Appearance → "Preview new design (BETA)", default OFF as of v0.4.x). The v0.5.x phase is in progress — `activity_main_v2.xml` and `item_main_v2.xml` are wired behind the toggle. v0.6.x flips default to ON; v0.7.x removes the toggle and retires classic themes.

### Hardware verification context
- Primary on-device test target: **Samsung S25 Ultra (SM-S938B)** — One UI 8.x / Android 16.
- Landscape rotation triggers the `values-w600dp/dimens.xml` adaptive overrides (833dp).
- Test matrix that bugs are reported against: Pixel 9a (Android 17, 16-KB page size), Poco F3 (Infinity-X 3.9, Android 16 QPR2, Root), LineageOS 23.2 / Android 16, Moto g22 (Unisoc T606 / Android 12, pre-A13 activity-alias bug), Galaxy A57 (One UI 8.5), Redmi Note 13 Pro 5G (crDroid 12.9 + KernelSU). All sourced from upstream issues mined in iter-18 → iter-23.

### Privilege provider matrix (per-user mode)
- **Root** — historical default. libsu 6.0.0 backed.
- **Shizuku / Sui** — first-class as of 2026-05-14 (`feat: add Shizuku privilege provider`). Compile-time pinned at `shizuku_version = 13.1.5`; onboarding wizard checks for manager version ≥13.6.0 (Android 16 QPR1 guidance).
- On Android 17+, onboarding now warns that Shizuku has unresolved compatibility reports
  (#1965 / #1967 / #1988) and offers Wireless ADB setup while
  `MIN_ANDROID_17_COMPATIBLE_VERSION` remains unknown.
- **ADB** — wireless ADB pairing wizard shipped 2026-05-14.
- **KernelSU / APatch / Magisk / ZygiskNext** — detected by `runner/RootManagerInfo`, surfaced as suffix on the onboarding sheet's Root status line.
- **Dhizuku (DPM via Binder proxy)** — open T5 row. Do not add `io.github.iamr0s:Dhizuku-API`
  directly until the API-21 floor conflict is resolved; upstream Dhizuku-API `2.5.4`
  currently declares `MIN_SDK = 26`.
- **Battery optimization auto-fix** — `self/SelfBatteryOptimization.java` is the canonical
  helper for AppManagerNG's own Doze exemption state. Use it instead of adding new
  direct `PowerManager` / `DeviceIdleManagerCompat` checks for NG's package.
- **Privilege health diagnostics** — `settings/PrivilegeHealthPreferences.java` is now the
  persistent Settings surface for mode/provider diagnostics. `runner/RootCapabilityDiagnostics.java`
  is the canonical active-shell capability probe. Add future VPN plugin flag checks
  and provider health probes there rather than rebuilding a new diagnostics page.
- **FireOS SYSTEM USER** — Under Consideration (T11 row; ~1M Fire devices have no AM-class power tool).

### Backup engine
- Crypto modes: AES / RSA / ECC / OpenPGP. AES is Android Keystore-backed (hardware-isolated where TEE available) — **not** the original roadmap's PBKDF2 sketch.
- Metadata v6 (2026-05-16) introduces per-file AES-GCM IV derivation while keeping v5-and-older backups restorable.
- Integrity verification (`BackupItems.Checksum` + per-file SHA-256) was already shipped — closed as "pre-existing" in 2026-05-16 hygiene pass.
- Multi-format ingest: APK / APKS / APKM / XAPK with OBB support.

### Don'ts
- Don't add code via blanket AI generation that bypasses review — NG's `CONTRIBUTING.md` replaces upstream's flat AI ban with a tool-assisted-but-reviewed contract; the rule still constrains how AI work lands.
- Don't bundle CC-BY-NC-SA datasets (e.g. DuckDuckGo Tracker Radar) — incompatible with GPL-3.0 redistribution. Roadmap UC row documents this.
- Don't introduce a new third-party dep without checking F-Droid Anti-Features rules (`Tracking` / `NonFreeNet` / `NonFreeAdd` / `NonFreeAssets`) — see ROADMAP source [S244].

---

## 6. The next things on the runway

(See [`ROADMAP.md`](ROADMAP.md) for the full picture — these are just the headline targets.)

- **v0.5.0** — Settings reorganization by task; global in-app search; contextual help tooltips; **in-app changelog viewer** (replaces bundled upstream v4.0.5 changelog).
- **v0.6.0** — Rootless Power: Shizuku integration polish + wireless ADB auto-pairing polish + rootless debloat. (Most of the engine work shipped 2026-05-14 — v0.6.0 is the user-visible roll-up.)
- **Now / Eng-Debt** — Hidden-API Compatibility Harness (iter-19 [S137] — the highest-leverage eng-debt item; mitigates the "Android version migration takes 80 hours" cliff). Android 17 device verification for Shizuku's fixed-version floor. Freeze / Operation Audit Log ([S144]). Shizuku permission auto-revoke warning on data-clear ([S139]).
- **Distribution next** — IzzyOnDroid listing, F-Droid listing, Accrescent listing. All gated on the rename being public + reproducible builds (both done).

---

## 7. How to keep this file useful

- This is an **index**, not a memory dump. If you have a new fact to record, ask first whether it belongs in `ROADMAP.md` (planned work), `CHANGELOG.md` (shipped work), `docs/audits/<date>-<topic>.md` (audit verdict), `docs/research/<date>-<topic>.md` (research delta), or `CLAUDE.md` (tool gotcha). Only update this file when the **entry point** changes — e.g. a new top-level directory, a new mandatory read order, a load-bearing convention flip.
- Tool-specific instruction files (`CLAUDE.md`, `AGENTS.md`) **must not be merged away**. They remain the tool entry points; this file is the project-state consolidation they both point at.
- Source citations live in the `ROADMAP.md` Source Appendix (S01–S329). Add new sources there, then reference by `[Sxxx]` in roadmap rows / changelog entries / audit docs.

---

**Maintainer note**: this file was reconciled by autonomous deep-research passes on
2026-05-17. The audit artifacts are split across [`.ai/research/2026-05-17/`](.ai/research/2026-05-17/),
[`.ai/research/2026-05-17-pass-2/`](.ai/research/2026-05-17-pass-2/),
[`.ai/research/2026-05-17-pass-3/`](.ai/research/2026-05-17-pass-3/),
[`.ai/research/2026-05-17-pass-4/`](.ai/research/2026-05-17-pass-4/),
[`.ai/research/2026-05-17-pass-5/`](.ai/research/2026-05-17-pass-5/),
[`.ai/research/2026-05-17-pass-6/`](.ai/research/2026-05-17-pass-6/),
[`.ai/research/2026-05-17-pass-7/`](.ai/research/2026-05-17-pass-7/),
[`.ai/research/2026-05-17-pass-8/`](.ai/research/2026-05-17-pass-8/),
[`.ai/research/2026-05-17-pass-9/`](.ai/research/2026-05-17-pass-9/),
[`.ai/research/2026-05-17-pass-10/`](.ai/research/2026-05-17-pass-10/),
[`.ai/research/2026-05-17-pass-11/`](.ai/research/2026-05-17-pass-11/),
[`.ai/research/2026-05-17-pass-12/`](.ai/research/2026-05-17-pass-12/),
[`.ai/research/2026-05-17-pass-13/`](.ai/research/2026-05-17-pass-13/),
[`.ai/research/2026-05-17-pass-14/`](.ai/research/2026-05-17-pass-14/),
[`.ai/research/2026-05-17-pass-15/`](.ai/research/2026-05-17-pass-15/), and
[`.ai/research/2026-05-17-pass-16/`](.ai/research/2026-05-17-pass-16/).
