<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Observability, Telemetry & Testing Audit

Date: 2026-05-09

Companion research for [Iter-22 Research Additions](../../ROADMAP.md#iter-22-research-additions-2026-05-09).

Comparative survey of how power-user Android package managers and adjacent OSS projects handle diagnostics, crash reporting, telemetry, perf tracing, smoke tests, OEM quirks, CI, and audit logs. Goal: import what fits AM-class apps without compromising the privacy posture.

## Per-tool audit

| Project | Diag screen | Crash reporting | Telemetry | Perf tracing | Self-test | OEM fixtures | CI | Audit log |
|---|---|---|---|---|---|---|---|---|
| **SD Maid SE** | Manual debug-log toggle, exports verbose file user-shareable [1] | "Automatic error reports" *opt-in*, no Bugsnag/Sentry/Firebase per privacy policy [1] | None; opt-in MOTD + update check only | Espresso + Macrobenchmark modules in tree [2] | Accessibility-service smoke prompts | None public | `code-checks`, `gradle-wrapper-validation`, `test-reports`, `release-prepare`, `release-tag` workflows [2] | Operations log per-task |
| **Canta** | None visible | None | None (F-Droid clean) | None | Shizuku presence check | Per-OEM bloat list (oem_*.json) [3] | Single Gradle build workflow | Reversible "uninstalled by you" history backed by Shizuku |
| **Inure** | Built-in "Logs" screen + bug-report exporter (writes to /Documents/Inure) [4] | None (paid app, no third-party SDK) | None | None | Root/Shizuku state pill in dashboard | None | None public (closed CI) | Per-action history page |
| **Hail** | None | None (F-Droid clean, GPL-3) | None | None | Owner/Shizuku/root mode self-detect on launch [5] | None | Single release.yml | Frozen-app list = implicit audit |
| **Neo Backup** | "Support info" composer dumps device + ROM + perms + Shizuku/root state into shareable text [6] | None; CHANGELOG references ACRA removed years ago | None | None | Schedules report dry-run failures | Tested matrix listed in README | Forgejo + GitHub workflows, KtLint + tests + signed release | Per-package backup/restore log w/ timestamps |
| **UAD-NG** | Rust `tracing` log viewer baked into TUI/GUI | None | None | None | `adb devices` probe + Android API gate | Per-OEM CSV recommendation lists (Samsung/Xiaomi/Huawei/Oppo etc.) [7] | `cargo fmt`, `clippy`, `cargo test`, multi-OS release build | Per-uninstall CSV export ("Selection" log) |
| **Shizuku** | Manager exposes ADB/root mode, server PID, API version, Sui detection [8] | None | None | None | Self-binds to its own service to verify | Detects Sui/Magisk variants | Build + release workflows | Per-app permission grant timestamps |
| **KernelSU Mgr** | "About" page: kernel version, KSU API, SELinux, hook mode | None | None | None | Boot-time kernel-compat probe [9] | GKI vs non-GKI branch detection | Multi-arch build matrix | superuser.db audit of grants |
| **Magisk Mgr** | Boot-install log viewer + `magisk --boot-complete` log capture | None | None | None | Zygisk + module compat probe at install [10] | A/B slot + dynamic-partition detect | Per-arch build, GitHub Releases | install/module enable/disable log |
| **F-Droid client** | None app-side | None | None (project-policy, hard rule) | None | Repo signature verification on every sync | None | Reproducible-build verification CI [11] | Install/update log via PrivilegedExtension |
| **Aurora Store** | "Self-update" + token-dispenser status panel | None (F-Droid clean) | None | None | Anonymous/Google login probe | None | F-Droid build infra | Download history |
| **LibChecker** | Detail screen w/ ABI / target SDK / signature blocks | Firebase Crashlytics + AppCenter in Play flavor; F-Droid flavor strips them, gets `Tracking` antifeature flag [12] | Firebase Analytics (Play flavor) | None | None | None | Gradle CI + Crowdin sync | None |
| **PCAPdroid** | In-app pcap export, mitm log, "App info" dump for support tickets [13] | None (F-Droid clean) | None | None | VPN/root mode self-test on start | None | Build matrix, native libs, F-Droid metadata audit | Per-connection log = audit by design |
| **NetGuard** | "Send logs to developer" composer + traffic log export [14] | None (Bugsnag dropped years ago, replaced by user-initiated log mail) | Pro features local-only | None | VPN-service self-test on enable | None | None public | Per-app allow/deny history |

Sources: [1] github.com/d4rken-org/sdmaid-se PRIVACY_POLICY.md · [2] sdmaid-se/.github/workflows · [3] github.com/samolego/Canta tree · [4] inure.app docs · [5] github.com/aistra0528/Hail README · [6] github.com/NeoApplications/Neo-Backup README · [7] UAD-NG resources/assets/uad_lists.json · [8] shizuku.rikka.app · [9] kernelsu.org docs · [10] github.com/topjohnwu/Magisk · [11] f-droid.org/docs/Reproducible_Builds · [12] github.com/LibChecker/LibChecker (build flavors) · [13] emanuele-f.github.io/PCAPdroid · [14] netguard.me FAQ · [15] f-droid.org/docs/Anti-Features (Tracking flag rules) · [16] developer.android.com/topic/performance/benchmarking/macrobenchmark-overview · [17] docs.sentry.io/platforms/android/data-management/sensitive-data.

## Industry references for what a privacy-respecting AM-class app adopts in 2026

- **Android Vitals** (Play Console only — not applicable to F-Droid/sideload distribution).
- **Firebase Performance / Crashlytics** — adds `Tracking` + `NonFreeNet` antifeatures on F-Droid; **hard reject**.
- **Sentry Android SDK 8.x** — supports `sendDefaultPii=false`, `beforeSend` scrubber, `enableAutoSessionTracking=false`, **self-hosted DSN**, opt-in flow. Acceptable only if (a) opt-in default-off, (b) self-hosted endpoint, (c) PII scrubber. Even then, F-Droid still flags `Tracking` unless disabled by default. [17]
- **ACRA** (Apache, sends to your own Forgejo issue / email / HTTP backend) — historically the F-Droid-friendly choice; opt-in-only flavor avoids `Tracking` flag. [15]
- **OpenTelemetry mobile** — still alpha for Android, no proven OSS sideload ingestion path; **defer**.
- **Macrobenchmark + Baseline Profiles** — purely local, no privacy cost; high ROI for AM since cold-start is dominated by package-info enumeration. [16]

## Net-new rows for AppManagerNG ROADMAP

| # | Name | Description | Why it fits AM | Effort | Source |
|---|---|---|---|---|---|
| O-01 | **Support Info Bundle** | One-tap composer that snapshots Android version, ROM build, root/Shizuku/Sui mode, AM mode (root/ADB/no-root), enabled features, last LocalServer bootstrap signature, scrubbed logcat tail. Outputs `support-info-<device>-<ts>.txt` to share via system intent. PHI-scrubbed (UIDs masked, no package data unless user opts in). | Mirrors Neo Backup [6] + NetGuard [14]; user-pasteable issue replies; zero network. | 2 | github.com/NeoApplications/Neo-Backup |
| O-02 | **Privileged Op Audit Log** | SQLite-backed append-only log: `(ts, mode, op, target, exit_code, signature)` for freeze/unfreeze/uninstall/permission-grant/component-toggle. Viewer screen + JSON export. Retention slider (7/30/90 days/forever). | UAD-NG ships Selection CSV [7]; Magisk + Shizuku log grants [8][10]; AM upstream lacks this — pure differentiator. | 3 | github.com/Universal-Debloater-Alliance/uad-ng |
| O-03 | **Mode Self-Test ("Doctor")** | Diagnostics screen runs ordered probes: root binary, su grant, Shizuku binder ping, Sui detection, ADB-pairing state, ABI/SELinux, KernelSU API. Each probe shows pass/fail/cause, with a "fix it" deeplink (e.g. open Shizuku settings). | Shizuku troubleshoot pattern [8] + KSU compat probe [9]; addresses root-op-X-fails-on-Sui class of bugs. | 3 | shizuku.rikka.app |
| O-04 | **OEM Quirk Panel** | Detect Samsung Knox, MIUI, EMUI, OxygenOS, ColorOS, OneUI, HyperOS at runtime. Surface known limitations (Knox container packages can't be frozen, MIUI auto-restarts disabled apps, EMUI revokes notification access). Link to per-OEM workaround page in app. | UAD-NG carries per-OEM lists [7]; AM users hit these constantly. | 3 | UAD-NG resources/assets |
| O-05 | **Opt-In Local Crash Sink (ACRA-style)** | ACRA configured to write crash reports to `Documents/AppManagerNG/crashes/*.json`, NOT a network endpoint. User shares via O-01. Default OFF. F-Droid `Tracking` antifeature avoided because no network and disabled by default. [15] | SD Maid SE pattern of "manual log → user shares" [1]; no third-party SDK; GDPR-trivial. | 2 | github.com/ACRA/acra |
| O-06 | **LocalServer Bootstrap Smoke Test** | On Settings > Developer, button runs the LocalServer bootstrap end-to-end against current mode and prints the same signature line that the new failure-signature feature emits — except on success path too. Catches "works on Magisk, fails on KSU 1.0.4" before users hit it. | Extends the bootstrap-failure-signature feature already shipped; analog to Magisk install-log [10]. | 2 | (internal LocalServer module) |
| O-07 | **Macrobenchmark module** | New `:benchmark` module measuring cold-start, package-list scroll jank, Backups screen TTI. Run on physical device in CI nightly; results posted as PR comment. Generate Baseline Profile for the app-list path. | Pure local, no privacy cost; AM cold-start dominated by `PackageManager` enumeration — high ROI. [16] | 4 | developer.android.com/topic/performance/benchmarking |
| O-08 | **Espresso + UI Automator smoke pack** | Headless device-side suite that exercises: open app-list, freeze/unfreeze test package, view component blocker, run one-shot rule. Runs in `connectedCheck` on emulator matrix (API 26/30/34/35). | SD Maid SE ships these [2]; AM has zero. Catches regressions in privileged ops without user telemetry. | 4 | github.com/d4rken-org/sdmaid-se |
| O-09 | **CI dependency CVE scan** | Add `dependency-check-gradle` + GitHub `dependency-review-action` on PR. Surface CVEs in transitive deps (we inherit a lot from upstream AM). Fail PR on HIGH/CRITICAL. | Standard 2026 OSS hygiene; F-Droid reproducible-build pipeline already does sig checks [11], we add CVE check. | 2 | github.com/jeremylong/DependencyCheck |
| O-10 | **Feature flag → "F-Droid clean" build flavor** | Build flavors: `floss` (no ACRA, no anything optional that touches network) vs `full` (ACRA file sink, update check, MOTD). F-Droid metadata pinned to `floss`. Mirrors LibChecker pattern but inverted: floss is the default, network features are the add-on. [12] | Keeps F-Droid antifeature-flag-free, lets adventurous users opt in via Releases page APK. | 3 | github.com/LibChecker/LibChecker |
| O-11 | **In-app log viewer w/ severity filter + redaction** | Replace raw logcat surface with structured viewer: filter by tag/severity, one-tap "redact UIDs/paths" for screenshot sharing, "copy line" button. | Inure ships this [4]; protects users when pasting into issues. | 2 | inure.app |
| O-12 | **"Was it me?" reverse audit** | Given a package, show every privileged op AM performed against it (from O-02 audit log) — useful when something broke and user wonders if AM did it. Exports to clipboard. | Power-user accountability; nothing comparable in upstream AM or peers. | 2 | (composes with O-02) |

## Hard rejects

- **Firebase Analytics / Crashlytics / Performance.** Triggers F-Droid `Tracking` + `NonFreeNet` antifeatures, requires Google Play Services, defeats the whole point of an AM-class power-user tool whose users explicitly disable GMS. LibChecker's split-flavor approach proves the cost. [12][15]
- **Always-on Sentry (cloud).** Even with `sendDefaultPii=false`, an AM app's stack traces leak target package names — that's user behavior. F-Droid would still flag it. Acceptable only as **opt-in + self-hosted + scrubber** per O-05's local-file alternative. [17]
- **Anonymous feature-usage counters of any kind, even "minimal".** F-Droid's Tracking rule is bright-line: opt-in default-off only. Counters that send "user opened Permissions tab" violate the project's privacy posture. [15]
- **Play Console Android Vitals.** Distribution is F-Droid + GitHub Releases + IzzyOnDroid; no Play Console signal exists.
- **Play Integrity / SafetyNet attestation telemetry.** Antithetical to a tool whose users root their devices.
- **Google Play In-App Update API.** GMS dependency; we ship update checks via GitHub Releases JSON.
