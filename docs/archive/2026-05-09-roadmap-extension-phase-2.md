<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Roadmap Extension — Phase 2

Date: 2026-05-09

Companion to [Iter-22 Research Additions](../../ROADMAP.md#iter-22-research-additions-2026-05-09). Documents the four research streams that fed iter-22, after [iter-21's data + API extension](2026-05-09-capability-extension.md). Where iter-21 covered datasets and modern Android APIs, iter-22 covers the **operational layer** AppManagerNG hasn't yet built out: privileged-action accountability, recoverability when shells die, automation surfaces (Tasker / QS / Wear), non-phone form factors, localization tooling, and migration paths in and out of the app.

## Stream 1 — Observability + Testing

Companion deep-dive: [Observability, Telemetry & Testing Audit](2026-05-09-observability-testing-audit.md).

The single bright-line finding: of 13 peer projects audited, only LibChecker ships Firebase, and they pay for it with an F-Droid `Tracking` antifeature flag and a parallel FLOSS flavour. Every other AM-class peer (SD Maid SE, Neo Backup, Hail, Canta, PCAPdroid, NetGuard, F-Droid client, Aurora, KernelSU/Magisk/Shizuku managers, Inure) ships zero remote telemetry. The dominant pattern is **user-initiated support bundle**, not automated crash reporters: Neo Backup's support-info composer, NetGuard's "send logs to dev" intent, Inure's bug-report exporter, SD Maid SE's manual debug-log toggle.

Net deltas worth picking up:
- **Audit-log accountability** is the unclaimed differentiator. UAD-NG ships a Selection log; Magisk + Shizuku log grants; AM upstream logs *nothing*. A SQLite append-only `(ts, mode, op, target, exit_code, signature)` table closes the gap.
- **Smoke-test the privileged shell, not the UI.** A "Doctor" diagnostics screen running probes (root binary → su grant → Shizuku binder ping → Sui detection → ADB pairing → KSU API → SELinux → ABI) catches the "works on Magisk, fails on KernelSU 1.0.4" class of bug before users hit it. Distinct from the existing T5 Privilege Health-Check, which is *display-only*; Doctor is the *active probe*.
- **Macrobenchmark + Baseline Profiles** are pure-local, privacy-cost-zero, and AM cold-start is dominated by `PackageManager` enumeration — exactly the kind of workload where Baseline Profiles deliver outsized win.
- **F-Droid clean build flavour** — a `floss` flavour that strips every optional network call (update check, MOTD, debloat-defs auto-fetch) is the bulletproof way to keep the F-Droid listing antifeature-flag-free while letting adventurous users opt in to the `full` flavour via GitHub Releases.

Hard rejects with reasoning:
- **Firebase Analytics / Crashlytics / Performance** — triggers F-Droid `Tracking` + `NonFreeNet`, requires GMS, defeats the AM-class user demographic.
- **Always-on cloud Sentry** — even with `sendDefaultPii=false`, AM stack traces leak target package names = user behaviour. F-Droid still flags. Acceptable only as **opt-in + self-hosted + scrubber**, which makes it parallel to the local-file ACRA sink we already have planned.
- **Anonymous feature-usage counters** — F-Droid's Tracking rule is bright-line: opt-in default-off only. A "user opened Permissions tab" counter violates project privacy posture.
- **Play Console Android Vitals / Play Integrity / Play In-App Update** — distribution is F-Droid + GitHub Releases + IzzyOnDroid; no Play Console signal exists; users explicitly disable GMS.

## Stream 2 — Plugin Ecosystem + Inter-App Integration

Today AppManagerNG exposes power actions through its own UI only. There is no public broadcast intent API, no `am://` URI scheme implementation (only T8 placeholder), no Tasker plugin, no Quick Settings tile, no Wear OS companion, no DocumentsProvider. The space around us has matured: **Hail v1.10.0 ships a 10-action intent surface + URI scheme + Assist intent**, **Neo Backup ships a documented `CommandReceiver`**, **Termux:API ships a Unix-socket dispatcher**.

The under-claimed differentiator: **no app in the AM space ships a Tasker plugin, a Quick Settings tile suite, or a Wear OS companion.** SD Maid has rejected/deferred all three for ~7 years (issue [#83 from 2015](https://github.com/d4rken-org/sdmaid/issues/83), [#385](https://github.com/d4rken-org/sdmaid/issues/385), [SE #1439 from 2024](https://github.com/d4rken-org/sdmaid-se/issues/1439)). AM upstream has [#1219](https://github.com/MuntashirAkon/AppManager/issues/1219) (clear-cache via Tasker) closed as duplicate, unresolved. AppManagerNG can leapfrog the entire space.

Tasker plugin specifically: the spec is proprietary but free, the [`com.joaomgcd:taskerpluginlibrary`](https://tasker.joaoapps.com/pluginslibrary.html) is GPL-compatible, and **a Tasker plugin can live inside the host app via one extra `Activity` and one `BroadcastReceiver` — no separate APK.** Adds ~120 KB. Effort 2/5. Novelty 5/5 in the AM/Canta/Hail/SD Maid/Neo Backup space.

Quick Settings tiles likewise — Android 13's `TileService.requestAddTileService()` is a one-tap install flow at onboarding. Two tiles to ship in v1: "Run Freeze Profile" (executes user-chosen profile) and "Force-Stop Pinned App" (per-app pinned tile for repeat force-stop on a misbehaving app).

DocumentsProvider exposing `am://backups` and `am://profiles` makes our backup archives + profile JSONs first-class file-picker citizens — AppManagerNG-managed data shows up in Material Files, Files by Google, and any SAF picker without us shipping our own browser.

## Stream 3 — Non-Phone Form Factors

The killer addition: **Wear OS phone-side package manager.** No FOSS app does this. GeminiMan WearOS Manager exists but is closed-source, ad-supported, Play-only. The pattern is well-trodden — ADB-over-WiFi to the watch, `WearableListenerService` + `MessageClient` for live state, a small companion APK on the watch (~200 KB) for query/operation handlers. Galaxy Watch 7+ runs One UI Watch 6 + Wear OS 5; bootloader is locked but `pm` works fine. The **debloat-watch-from-phone** workflow is a power-user dream and a banner feature for AppManagerNG. Effort 4/5; differentiation maximum.

Floor table-stakes: `NavigationSuiteScaffold` (auto-switches bottom-bar / nav-rail / nav-drawer by WindowSizeClass), `ListDetailPaneScaffold` (master/detail at Expanded+), `SupportingPaneScaffold` (terminal/log alongside main package view), 5-breakpoint WSC including XL (TriFold / Tab S11 Ultra / external display), `FoldingFeature` posture awareness for Z Fold 7 / Pixel Fold 2 tabletop mode. Without these, AppManagerNG looks dated on a Tab S11 vs Inure / Material Files.

ChromeOS ARCVM detection is a 2-effort polish item (`hasSystemFeature("org.chromium.arc.device_management")` → hide phone-only ops, surface ChromeOS-broker constraints). Samsung DeX multi-instance + pop-up resizeable adds 2-effort manifest work for power-users running NG side-by-side to compare two apps' manifests. Galaxy XR launches Q4 2026 with **the most open sideload story of any XR platform** — a 2-effort manifest variant lands AppManagerNG as plausibly the first FOSS app manager on Android XR.

Hard rejects:
- **Android Automotive OS** — apps restricted to media / nav / messaging / parking / charging / IoT / weather templates. Power-user package management is not a sanctioned category. Skip.
- **Android Auto projection** — no package management surface. Car App Library is template-only. Skip.

## Stream 4 — Localization, Migration, Offline Resilience

### Localization

Upstream MuntashirAkon/AppManager uses **hosted.weblate.org/projects/app-manager/** for translations. AppManagerNG inherits ~50 locales from upstream but is not connected to a translation backend — every cherry-pick from upstream pulls translation drift it can't push back. Solution: bridge to upstream's existing hosted-Weblate as a **separate component** sharing upstream's glossary + TM. Cost ~1 day setup, free hosting. Win: every locale upstream gains flows downstream, NG-specific strings (Pro mode, M3 dashboard labels) translate independently. Self-hosted Weblate is overkill at this scale; Crowdin OSS is plausible but reintroduces a proprietary dependency a GPL fork shouldn't accept when Weblate Libre exists.

Pseudolocale build variants `en_XA` (accented + bracketed text — catches truncation) and `en_XB` (right-to-left mirror of English — catches RTL layout breaks) are standard Android i18n CI hygiene. Per-app locale picker via `AppCompatDelegate.setApplicationLocales()` (Android 13+, backports to API 26+ via `AppLocalesMetadataHolderService`) lets users override the system locale per-app.

### Migration paths

**Critical pre-rename gap**: AppManagerNG keeps upstream's `applicationId` (`io.github.muntashirakon.AppManager`) until v0.2.0. Once renamed to `io.github.sysadmindoc.AppManagerNG`, NG installs *next to* upstream and inherits no data. A snapshot bundle export/import flow (`{prefs/, profiles/, tags/, history.db}` ZIP w/ schema-version header) must ship **before** the applicationId rename to prevent data loss on flip. AnkiDroid's `.colpkg` is the precedent.

Importers cheap to ship and high-differentiation:
- **Canta presets** — exports JSON `{packages: [...]}`; importer is ~30 LoC.
- **UAD-NG selection** — `uad_settings.json` per device serial; well-documented schema.
- **Hail freeze tags** — newline-separated package lists per tag; trivial.

### Offline resilience

The biggest reliability gap today: **what happens when the privileged shell dies mid-batch-op?** Today the user gets a half-applied batch with no recovery hint. Pattern to import: wrap every batched op in a journal (write intent → execute → mark done). On reattach, replay unfinished entries or surface "12 ops interrupted, retry?". Pairs with Shizuku binder `DeathRecipient` and libsu `Shell.isAlive()`.

Other patterns worth importing:
- **Bundled-data freshness banner** at >30d, blocking warn at >180d. Tells users "your debloat list is stale" before they trust a stale verdict.
- **Air-gap toggle** that short-circuits every `OkHttpClient.newCall()` with `IOException("airgap")`. Pairs with bundled-only debloat defs for users who refuse network calls.
- **Multi-mirror debloat-defs fetcher** (GitHub raw → Codeberg raw → IPFS gateway list) with signed manifest. F-Droid's mirror pattern.
- **Battery-aware JobScheduler** (`setRequiresBatteryNotLow(true)` + `setRequiresDeviceIdle(true)`) for non-urgent batch freezes — defers to charging-idle windows, respects Doze + App Standby Buckets.
- **Atomic-write profile dir + Syncthing-friendly mtime semantics + conflict-file picker UI** — zero-code P2P profile sync, conflict files (`<name>.sync-conflict-<date>-<peer>.json`) handled by AM-NG conflict-picker UI.

## Verdict

35 net-new rows ready to migrate into Iter-22. License posture is uniformly safe — every dataset, library, or pattern is GPL-2.0+, GPL-3.0, Apache-2.0, BSD, or AGPL. No CC-BY-NC or proprietary deps slipped through.

**Top-5 highest-leverage rows for v0.5–v0.7:**
1. Privileged-Shell Journal + DeathRecipient Replay — the recoverability story is the #1 reliability gap.
2. Privileged Op Audit Log — pure differentiator, AM upstream has nothing like it.
3. Snapshot Bundle Export/Import — must ship before the v0.2.0 applicationId rename.
4. Tasker Plugin (in-app) — first in the AM space, ~120 KB, effort 2.
5. Wear OS Phone-Side Companion — banner feature for v0.7.0+ form-factor expansion.
