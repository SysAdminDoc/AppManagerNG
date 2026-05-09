# Changelog

All notable changes to AppManagerNG are documented in this file.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

### Security — Hardening pass on iter-22 changes (2026-05-09)

Defense-in-depth follow-up to the iter-22 work that landed earlier today. Three findings, all addressed:

- **`upstream-rename-watch.yml` GitHub-Actions script injection** ([`.github/workflows/upstream-rename-watch.yml`](.github/workflows/upstream-rename-watch.yml)). The original `actions/github-script@v7` step interpolated `${{ steps.probe.outputs.actual }}` directly into the JS body. Even though `actual` is sourced from a curl-parsed `full_name` string and shouldn't contain shell-injecting characters, this is the exact pattern that GitHub's own security documentation flags as a supply-chain risk: a value containing `'`, `\\`, `` ` ``, or a newline could break out of the string literal and execute arbitrary JS in the runner. Fixed by passing the value via a step-level `env: ACTUAL_SLUG: ${{ steps.probe.outputs.actual }}` and reading `process.env.ACTUAL_SLUG` inside the script. The probe step also now (a) uses heredoc-style `{ ... } >> "$GITHUB_OUTPUT"` to atomically write the multi-key block (instead of separate `echo X >> $GITHUB_OUTPUT` lines that leak partial state if the script bails) and (b) regex-validates the slug `^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$` before writing it, so a malformed API response never makes it into the output channel.

- **AppDetailsActivity intent-filter split** ([`AndroidManifest.xml`](app/src/main/AndroidManifest.xml)). The original iter-22 `am://app/<pkg>` alias was added as a second `<data>` element inside the existing `app-manager://details` intent-filter. While the parser ([`SelfUriManager.getUserPackagePairFromUri()`](app/src/main/java/io/github/muntashirakon/AppManager/self/SelfUriManager.java)) explicitly enforces the `(scheme, host)` pair in code so cross-matched URIs like `app-manager://app/...` are rejected, the loose filter would still match them at OS resolution time — a footgun against any future regression in the parser. Split into two distinct `<intent-filter>` blocks so the resolver only routes URIs that match an exact `(scheme, host)` pair to us. End-to-end verified on Samsung S25 Ultra: both `am://app/<pkg>` and `app-manager://details?id=<pkg>` correctly open `AppInfoActivity`.

- **`ShortcutDispatchActivity` hardening** ([`shortcut/ShortcutDispatchActivity.java`](app/src/main/java/io/github/muntashirakon/AppManager/shortcut/ShortcutDispatchActivity.java)). The trampoline now: (a) wraps the dispatch logic in `try { … } finally { finish(); }` so the `Theme.NoDisplay` activity contract is honoured even if something unexpected throws (Theme.NoDisplay requires `finish()` before `onCreate` returns; failing to do so leaves a phantom no-display task on the recents stack); (b) catches `ActivityNotFoundException` from `startActivity()` so a disabled-by-PackageManager target component or a removed-by-upgrade-migration shortcut doesn't crash the trampoline; (c) truncates the unknown-action log entry to 80 chars so a hostile caller can't pollute the device log with arbitrary-length entries. End-to-end re-verified on-device: known actions still dispatch correctly, unknown actions silently no-op.

### Security — Static-shortcut export regression closed; trampoline-based dispatch (2026-05-09)

Hardening pass on the iter-22 static launcher shortcuts that landed earlier the same day. The original implementation flipped `OneClickOpsActivity` and `FinderActivity` to `android:exported="true"` so the launcher could resolve the shortcut intents. **`OneClickOpsActivity` accepts an `EXTRA_OP` intent extra that triggers a destructive batch operation (clear cache for all installed apps) without confirmation when set to `OP_CLEAR_CACHE` — this path is intended for the trusted in-process clear-cache home-screen widget (`ClearCacheAppWidget`), not for arbitrary callers.** Combined with the export, any installed app could fire the activity with the destructive extra after the user was process-authenticated, silently clearing the cache of every app on the device. **`FinderActivity` was reverted to `exported=false` for symmetry / minimum exposure.**

Fix:

- **`OneClickOpsActivity` and `FinderActivity` reverted to `android:exported="false"`** in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml). Verified post-fix: `am start -n io.github.sysadmindoc.AppManagerNG.debug/io.github.muntashirakon.AppManager.oneclickops.OneClickOpsActivity --ei op 16` is rejected with `Permission Denial: ... not exported`.
- **New trampoline activity** [`shortcut/ShortcutDispatchActivity.java`](app/src/main/java/io/github/muntashirakon/AppManager/shortcut/ShortcutDispatchActivity.java) — the only exported component the launcher resolves to for shortcuts. Hard-whitelists two action constants (`OPEN_ONE_CLICK_OPS`, `OPEN_FINDER`), constructs a fresh `Intent` for the unexported target, and **does not forward intent extras**. Untrusted callers cannot smuggle in `EXTRA_OP` or any other destructive extra.
- **`shortcuts.xml` updated** to target the trampoline via the explicit action constants instead of the underlying activities.
- The widget / pinned-shortcut consent flow (`ClearCacheAppWidget` → `PendingIntent.getActivity(OneClickOpsActivity.class, EXTRA_OP=OP_CLEAR_CACHE)`) is unaffected — that path is in-process and the destructive extra is the user's explicit consent at widget tap time.
- End-to-end verification on Samsung S25 Ultra (`SM-S938B`): trampoline dispatches `OPEN_FINDER` and `OPEN_ONE_CLICK_OPS` correctly; activity stack shows the unexported target as the foreground activity; passing `--ei op 16` to the trampoline action does not trigger the destructive shortcut path because `OneClickOpsActivity` opens with no extras.

### Fixed — Per-app locale store-of-truth reconciliation at startup (2026-05-09)

Companion fix to the iter-22 Per-App Locale Picker that landed earlier today. The original wiring made `Prefs.Appearance.setLanguage()` mirror to `AppCompatDelegate.setApplicationLocales()` so the OS-side per-app locale (Settings → Apps → AppManagerNG → Language on Android 13+) stays in sync when the user changes language **in-app**. It did not handle the inverse: when the user changes the language **in the OS surface**, `AppCompatDelegate` updated but `Prefs` was stale; `LangUtils.getFromPreference()` (the in-app source-of-truth read by `AppearanceUtils.applyOnlyLocale()` on every activity recreate) then overrode the OS choice with the stale Prefs value on the next configuration change.

Fix: new [`AppearanceUtils.reconcileLocalePreference()`](app/src/main/java/io/github/muntashirakon/AppManager/utils/appearance/AppearanceUtils.java) runs on `Application.onCreate()` (via `AppearanceUtils.init()`) **before** any locale is applied:

- If `AppCompatDelegate.getApplicationLocales()` is non-empty and disagrees with `Prefs.Appearance.getLanguage()` → persist the OS-side value into `Prefs` (OS is the most recent authority).
- If `Prefs` has a non-`AUTO` value but `AppCompatDelegate` is empty (first launch after the iter-22 wiring landed, or a user with a long-standing in-app language preference) → push `Prefs` into `AppCompatDelegate` so the OS-side picker reflects reality.
- Wrapped in `try/catch` so a binder failure during `LocaleManager` reconciliation can never kill app startup.

### Compliance — Predictive-Back WebView Freeze (Obtainium #2911) audit (clean) (2026-05-09)
- **Audit clean — no remediation required.** The iter-20 roadmap row's premise that NG ships WebView surfaces in `RulesActivity` and an APK-info preview pane is stale; neither activity exists in NG. Component Rules surfaces are `RulesFragment` RecyclerView UIs, not WebView.
- The single WebView surface in NG is [`HelpActivity`](app/src/main/java/io/github/muntashirakon/AppManager/misc/HelpActivity.java) and it already uses the correct predictive-back propagation pattern: `android:enableOnBackInvokedCallback="true"` declared in the manifest, `OnBackPressedCallback` registered via `getOnBackPressedDispatcher().addCallback(...)`, and the WebView's `canGoBack()` state tracked on `doUpdateVisitedHistory()` so predictive-back animation only previews when there's a back-stack entry.
- The Obtainium #2911 regression class only affects activities that bypass the dispatcher or register a raw `OnBackInvokedCallback` without integrating with the WebView's back-stack — neither pattern is present in NG.
- Audit at [`docs/audits/2026-05-09-predictive-back-webview.md`](docs/audits/2026-05-09-predictive-back-webview.md). Establishes the canonical pattern for any future WebView-hosting activity (in-app changelog viewer planned for v0.5.0, JADX decompile pane in T12). Reference: [S200].

### Added — Upstream repo-rename watcher CI workflow (2026-05-09)
- New [`.github/workflows/upstream-rename-watch.yml`](.github/workflows/upstream-rename-watch.yml) hits the GitHub API on a weekly cadence (Wednesday 09:27 UTC, staggered off CodeQL Thursday 14:43 + dependency-scan Sunday 04:13) plus `workflow_dispatch`. Asserts that `MuntashirAkon/AppManager` still resolves to the same `full_name`; on drift, auto-opens an `upstream-sync`/`eng-debt`-labelled issue containing a 7-step rename audit checklist (workflow `EXPECTED_SLUG`, README baseline + Credits, ROADMAP baseline + research-source citations, CLAUDE.md Origin section, CHANGELOG historical refs, submodule URLs, Obtainium config, Sphinx docs).
- Idempotent — never opens a duplicate issue for the same drift in a single window. Uses unauthenticated GitHub API (full-name lookup needs no auth) so it does not consume `GITHUB_TOKEN` rate limits for the third-party probe; `GITHUB_TOKEN` is used only for the issue creation.
- Closes ROADMAP iter-18 row "Repo-Rename Detection for Upstream Pin" — Eng-Debt Next (Effort 1/5). Reference: [S121].

### Added — Pseudolocale resources on debug builds (2026-05-09)
- `pseudoLocalesEnabled true` set on the `debug` build type in [`app/build.gradle`](app/build.gradle); release builds stay clean.
- Debug AM-NG now ships `en-XA` (accented + bracketed pseudolocale that catches truncation and untranslatable string regressions) and `en-XB` (RTL mirror of English that catches mirroring/layout breakage). Activate via `adb shell setprop persist.sys.locale en-XA` or **Settings → Developer options → Pseudolocale** on Android 13+.
- The CI screenshot-diff portion of the iter-22 T10 row stays open — it gates on the upcoming **Espresso + UI Automator Smoke Pack** providing the headless instrumentation pipe the screenshot capture needs.
- Closes the build-side half of ROADMAP iter-22 T10 row "Pseudolocale Build Variants + RTL CI Pass" (Effort 2/5, [S268]).

### Added — CI Dependency CVE Scan (PR review + weekly OWASP) (2026-05-09)
- New [`.github/workflows/dependency-scan.yml`](.github/workflows/dependency-scan.yml) ships two layers:
    - **PR Dependency Review** (`actions/dependency-review-action@v4`) on every pull request: fails the PR on HIGH/CRITICAL CVEs introduced by a dependency change. Also denies CC-BY-NC* / CC-BY-ND* / AGPL-1.0 license bumps up-front (GPL-3.0-or-later redistribution compatibility — see ROADMAP iter-19 DDG Tracker Radar reject [S69]).
    - **Weekly OWASP Dependency Check** (Sunday 04:13 UTC, staggered off the existing CodeQL Thursday 14:43 cadence) plus `workflow_dispatch`: runs `./gradlew dependencyCheckAggregate` and uploads HTML + SARIF reports as artifacts (30-day retention). Catches CVEs disclosed *after* a dependency landed.
- `org.owasp:dependency-check-gradle:13.1.1` plugin wired into [`build.gradle`](build.gradle) at the root; `dependency_check_version = '13.1.1'` declared in [`versions.gradle`](versions.gradle).
- Local runs default to `failBuildOnCVSS = 11.0` (effectively never fail) so the report is purely informational on developer machines; CI uses `continue-on-error: true` and surfaces the report as an artifact rather than killing the weekly cadence on a single new CVE. NVD API rate limit honored via optional `NVD_API_KEY` secret with anonymous fallback.
- Suppression file path is wired but optional (`config/owasp-suppressions.xml`) — populate on first weekly audit to silence vendored-AAR false positives without losing the failing-on-real-CVE behavior.
- Closes ROADMAP iter-22 row "CI Dependency CVE Scan" — T4 Now (Effort 2/5). Reference: [S274].

### Added — `am://app/<pkg>` short-alias deep link + intent-API documentation (2026-05-09)
- New `am://app/<pkg>?user=<uid>` URI scheme as a short alias for the canonical `app-manager://details?id=<pkg>&user=<uid>`. Parses through the existing [`SelfUriManager.getUserPackagePairFromUri()`](app/src/main/java/io/github/muntashirakon/AppManager/self/SelfUriManager.java) — both schemes share the code path so consumers downstream don't change.
- Intent-filter `<data android:host="app" android:scheme="am"/>` added to the existing `details.AppInfoActivity` activity-alias in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml). Mirrors `hail://`'s shape.
- New [`docs/intent-api.md`](docs/intent-api.md) documents the full URI / broadcast-intent surface: shipped App Info alias, reserved-but-not-yet-wired shapes (`am://freeze/<pkg>`, `am://profile/<id>/run`, `am://install?source=<url>`), and the roadmapped `com.sysadmindoc.appmanagerng.action.*` broadcast schema (signature-permission-gated). Tasker / MacroDroid integration notes spelled out.
- The freeze / profile / install shapes are deliberately not wired yet — they need the broadcast-intent automation surface (iter-22 T8 [S247]) for the authorization model. Reserved here so a future implementation doesn't churn the schema.
- Closes ROADMAP iter-22 T8 row "`am://` URI Scheme — Concrete Schema" (Effort 1/5, [S246]) for the App Info alias slice; remaining shapes carried forward.

### Added — Static launcher shortcuts for power-user entry points (2026-05-09)
- Long-pressing the AppManagerNG icon on the launcher now surfaces three core entry points: **1-Click Ops** (batch operations), **Running Apps** (process inspector), and **Finder** (cross-app search). Shortcuts shipped at [`app/src/main/res/xml/shortcuts.xml`](app/src/main/res/xml/shortcuts.xml) and registered on `SplashActivity` via `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/>`.
- `FinderActivity` and `OneClickOpsActivity` flipped to `exported="true"` in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) so the launcher can dispatch the shortcut intents. `RunningAppsActivity` was already exported.
- Pinned per-app shortcuts (Freeze / Force-Stop / Clear Cache) continue to flow through `ShortcutManagerCompat` in `CreateShortcutDialogFragment` and the existing FreezeUnfreeze service path; this commit is the static-launcher anchor the upcoming dynamic top-N pinned-app set will extend.
- Closes ROADMAP T8 row "Launcher Shortcuts for AM Features" (Issue #660 [S32]). Iter-22 [S252] dynamic per-app shortcut work remains.

### Added — Per-app locale picker now syncs with OS Settings (2026-05-09)
- The in-app **Settings → Appearance → Language** picker now mirrors its selection to `AppCompatDelegate.setApplicationLocales(...)` after persisting the in-app preference. On Android 13+ (API 33+) AppManagerNG appears under **Settings → Apps → AppManagerNG → Language** and the OS-side picker stays in sync with the in-app picker bidirectionally.
- New `AppLocalesMetadataHolderService` registration in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) (with `autoStoreLocales=true`, `enabled=false`) provides the SharedPreferences-backed back-port so per-app locale selection survives process death on API 26-32 devices via androidx.appcompat 1.7.1.
- The `LANG_AUTO` setting maps to `LocaleListCompat.getEmptyLocaleList()` so "Auto" tracks the system locale through the platform mechanism instead of the legacy NG-only override pipeline.
- Existing `AppearanceUtils.applyConfigurationChangesToActivities()` activity-recreate path is unchanged — in-app re-render after a language change is still immediate.
- Closes ROADMAP iter-22 row "Per-App Locale Picker (`AppCompatDelegate.setApplicationLocales`)" — T10 Now (Effort 1/5). Reference: [S269].

## v0.4.1 — 2026-05-08

Maintenance release. Concentrates 19 closed Now/Eng-Debt rows from the iter-19/iter-20 ROADMAP drains plus one CONFIRMED audit finding flagged for design (GCM cipher reuse). All changes ship as user-visible polish + compliance + diagnostics; no breaking format changes. The GCM cipher-reuse bug in `AESCrypto.handleFiles()` is documented but **not yet fixed** — multi-file AES-encrypted backups produced by v0.4.0 and v0.4.1 cannot be trusted to restore. OpenPGP / RSA / ECC backup modes are unaffected; single-file AES backups are unaffected. See the audit at `docs/audits/2026-05-08-gcm-cipher-reuse-large-backup.md` for remediation options. The next release will pick a fix path and ship it behind a backup metadata version flag.

### Audit — GCM cipher reuse in `AESCrypto.handleFiles()` (CONFIRMED BUG, needs-design) (2026-05-08)
- ⚠️ **Confirmed:** [`AESCrypto.handleFiles()`](app/src/main/java/io/github/muntashirakon/AppManager/crypto/AESCrypto.java) instantiates a single `GCMBlockCipher` once before the per-file for-loop and reuses it across every file with the same `mIv`. After file 0's `doFinal()`, the cipher is in finalized state; iteration 1 wraps the same cipher in a fresh `CipherOutputStream`, with behavior that's either fail-fast or silent nonce-reuse depending on BouncyCastle's internals. This matches upstream AM issue #1958.
- GCM mode has a hard cryptographic invariant: `(key, IV)` must NEVER encrypt more than one distinct plaintext. Reuse silently breaks confidentiality and breaks the auth tag. The single-file `encrypt(InputStream, OutputStream)` path creates its own cipher and isn't affected; only the multi-file `handleFiles` path triggers the bug. OpenPGP / RSA / ECC modes are unaffected.
- **Remediation requires backup format planning**, not a one-line cipher re-init (re-init with the same IV is still nonce reuse). Three options documented at [`docs/audits/2026-05-08-gcm-cipher-reuse-large-backup.md`](docs/audits/2026-05-08-gcm-cipher-reuse-large-backup.md): (A) HKDF-Expand-derive per-file IV (no format change, old backups stay broken — they're already corrupt); (B) per-file IV stored alongside ciphertext (clean, requires metadata version bump); (C) fresh Crypto instance per file.
- **No code change shipped** in this commit — the audit is the deliverable. The next pass picks an option, ships the fix behind a metadata-version flag, and adds a synthetic-4-GB-blob round-trip regression test. Reference: AM #1958 / [S138].

### Compliance — Zip-slip protection audit (clean) (2026-05-08)
- **Audit clean — every disk-writing extraction path canonicalizes the output path and rejects traversal entries before any bytes are written.**
- `TarUtils.extract` and `AndroidBackupExtractor.extract` both carry the canonical "double-check" guard from upstream AM v4.0.0-alpha02: pre-write `Paths.normalize(entry.getName())` + `startsWith("../")` rejection, plus post-create `realFilePath.startsWith(realDestPath)` containment verification. Both raise `IOException("Zip slip vulnerability detected!")` with diff-able expected/actual paths on the (extremely unlikely) malicious-archive case.
- Archive-to-archive converters (`SBConverter`, `OABConverter`, `TBConverter`) cache entries by extension to `FileCache.createCachedFile()` or do tar-to-tar metadata copying — they never use the source entry name as a disk path, so they're inherently safe; any malicious entry name is re-encoded into the output archive and rejected at the eventual extraction step. `ApkUtils.getManifestFromApk` is in-memory only.
- Audit at [`docs/audits/2026-05-08-zip-slip-protection.md`](docs/audits/2026-05-08-zip-slip-protection.md). Closes the iter-20 Engineering Debt Register row "Zip-slip protection in APK/backup extraction".

### Compliance — libsu 6.0.0 `Shell.cmd` migration audit (clean) (2026-05-08)
- **Audit clean — zero matches.** Recursive sweep across `app/`, `libcore/`, `libserver/`, `libopenpgp/`, `hiddenapi/`, `server/` returned 0 `Shell.sh(` / `Shell.su(` / `FLAG_REDIRECT_STDERR` references.
- The single `Shell.cmd(` call site in [`RemoteShellImpl.java:25`](app/src/main/java/io/github/muntashirakon/AppManager/ipc/RemoteShellImpl.java#L25) implements the 6.0.0 idiom; all other privileged shell invocations route through NG's `Runner.runCommand` abstraction on top of it.
- Audit at [`docs/audits/2026-05-08-libsu-shell-cmd-migration.md`](docs/audits/2026-05-08-libsu-shell-cmd-migration.md). Closes the iter-20 Engineering Debt Register row "libsu `6.0.0`".

### Added — LocalServer bootstrap-failure signature line (2026-05-08)
- New `logBootstrapFailureSignature()` helper in [`LocalServer.checkConnect()`](app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServer.java) emits a single-line failure signature whenever the privileged-shell handshake throws (`IOException` / `AdbPairingRequiredException`). The signature captures `Build.MANUFACTURER/PRODUCT/DEVICE`, `SDK_INT`, `Build.ID`, `ro.lineage.version` (when present), the exception class + message, and the cause chain.
- Bug reporters can copy this one `Log.e("IPC", …)` line into an issue instead of a full audit log. Targets in particular the LineageOS 23.2 / Android 16 root-binder regression (AM #1962 / [S185]) where the SELinux denial in `system_server` kills the handshake silently. The actual SELinux denial line still has to come from `dmesg` / `logcat` separately, but the device + exception fingerprint is now structured and trivially diff-able across reports.
- Diagnostic logging is wrapped in a try/catch so it can never mask the original failure. Closes the iter-20 Now/T2 row.

### Fixed — A16 QPR2 silent `clearApplicationUserData` failure (2026-05-08)
- [`PackageManagerCompat.clearApplicationUserData()`](app/src/main/java/io/github/muntashirakon/AppManager/compat/PackageManagerCompat.java) now snapshots `IStorageStatsManager.queryStatsForPackage()` `dataBytes + cacheBytes` pre-clear, calls the hidden-API IPC path, re-snapshots post-clear, and when the post-clear size hasn't dropped below the pre-clear baseline (with a 64 KiB tolerance for the user-data dir's skeleton state), runs `pm clear --user N <pkg>` as a shell fallback.
- The IPC path is also fallen-back-to-shell when it throws, so true IPC failures plus the QPR2 silent-success class of bug both route to the same shell remediation. The 64 KiB tolerance avoids false positives on the small placeholder state the OS retains even after a clean wipe; full-MB-or-GB silent-failure cases (Poco F3 / Infinity-X 3.9 / Root mode on QPR2) are caught and recovered.
- New helpers: `clearApplicationUserDataViaIpc()`, `clearApplicationUserDataViaShell()`, `queryAppDataBytesQuietly()`. Reference: AM #1965 / [S184]. Closes the iter-20 Now/T2 row.

### Added — `Ops.isAdbShellRoot()` detection helper (2026-05-08)
- New static helper [`Ops.isAdbShellRoot()`](app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java) returns true when the configured mode is ADB but the working shell's uid is 0 — the "ADB Root" surface KernelSU v3.2.3+ added in 2026 (also reachable via APatch's adb-root toggle and Magisk's kang mode).
- Cheap, all-thread-safe, no shell round-trip. The detection layer intentionally doesn't gate on KernelSU specifically because APatch / Magisk-kang reach the same privilege state; callers wanting a stricter "root manager confirmed" gate pair this with `RootManagerInfo.detect()`.
- The javadoc carries the trust caveat ("anyone can plug in via USB"): a device left unattended with USB debugging enabled grants any laptop the same uid-0 surface, so consumers (the still-pending Privilege Health-Check screen and onboarding wizard) MUST gate elevated trust on explicit user confirmation before treating this as full-root for sensitive ops. Reference: [S166]. Closes the iter-20 Now/T9 row at the detection-foundation layer; UX confirmation flow lands with the Privilege Health-Check screen.

### Changed — Backup-name dialog now autocompletes from prior backup names (2026-05-08)
- The "Multiple backup" name dialog in [`BackupFragment.handleBackup()`](app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupFragment.java) is now backed by `TextInputDropdownDialogBuilder` instead of `TextInputDialogBuilder`. Users tagging a fresh backup get an autocomplete dropdown of every prior backup label across the apps in scope, so re-using the same tag as last time is one tap instead of a full retype.
- New `collectExistingBackupNames()` walks `viewModel.getBackupInfoList()` → per-backup `BackupMetadataV5.metadata.backupName` and feeds the de-duplicated `LinkedHashSet` into `setDropdownItems(items, -1, true)` (filterable), so typing narrows the suggestion list as the user goes. Empty/null names are skipped.
- Re-scoped from the iter-20 row's original wording: NG's multi-tag dao hasn't shipped yet, so the *applicable* user-facing surface today is the backup-name dialog. When the multi-tag dao lands, the same `setDropdownItems` adapter pattern can be reused for the tag-add dialog with zero code change. Reference: Neo-Backup 8.3.17 / [S135]. Closes the iter-20 Now/T8 row.

### Added — Per-OEM Debloat Risk Ribbon (Samsung One UI 8.5) (2026-05-08)
- New [`OemBloatRiskTable`](app/src/main/java/io/github/muntashirakon/AppManager/debloat/OemBloatRiskTable.java) helper resolves vendor-aware known-bad debloat warnings from a `(package, Build.MANUFACTURER, vendor-OS-version)` triple, where vendor-OS-version comes from the platform's vendor-specific system property (`ro.build.version.oneui` for Samsung One UI, `ro.mi.os.version.name` / `ro.miui.ui.version.code` for Xiaomi HyperOS / MIUI).
- First entry: `com.samsung.android.smartsuggestions` on Samsung One UI 8.5 (`ro.build.version.oneui == 80500`) — UAD-NG #1394 documented Settings → Mobile-Networks crash-loop on Galaxy A57. The new warning string `oem_bloat_risk_samsung_smartsuggestions_oneui85` ships with localizable copy directing users to disable/freeze the package instead of removing it.
- Wired into [`BloatwareDetailsDialog.bind()`](app/src/main/java/io/github/muntashirakon/AppManager/debloat/BloatwareDetailsDialog.java) via a new `composeWarning()` helper: vendor-known-bad ribbon leads, the upstream debloat-list warning trails for additional context, and the alert chip is forced to `ALERT_TYPE_WARN` regardless of the upstream removal rating (a system-surface crash loop is not "info").
- Resolution order is exact match → wildcard match (`*` handles devices where the vendor-OS-version property is unreadable). Generic "this looks Samsung-y" warnings stay on the upstream string; this surface is reserved for verified field reports keyed to a specific OEM/version combo. Reference: [S188]. Closes the iter-20 Now/T7 row.

### Added — Cert dialog now shows Subject + Issuer (2026-05-08)
- The "Sign · SHA-256" tag chip's dialog in App Info now exposes the X.509 **Subject** and **Issuer** distinguished names alongside the SHA-256 fingerprint, so users vetting an APK can see who the certificate claims to be issued *to* without dropping to `apksigner verify --print-certs`.
- New `AppInfoViewModel.populateSigningCertInfo()` (replaces `computeSigningCertSha256`) writes `signingCertSha256` / `signingCertSubject` / `signingCertIssuer` together off the same `X509Certificate` instance — Subject/Issuer come from `getSubjectX500Principal().getName()` / `getIssuerX500Principal().getName()` (RFC 2253 form). All three stay `null` for unparseable / multi-signer / unsigned packages.
- [`AppInfoFragment.showCertFingerprintDialog()`](app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java) renders the trio as labelled sections; new strings `cert_fingerprint_dialog_{sha256,subject,issuer}_header`. Copy button still copies fingerprint-only to keep AppVerifier / `apksigner` paste-compatibility.
- The iter-20 row's other layout-density bullets (SDK-row reorder, two-column trackers|SDK, popup `maxHeightPercent`) target an upstream `app_info_card.xml` that doesn't exist in NG — App Info is a full pager fragment, not a bottom-sheet popup, so those don't map. Reference: AM #1966 / [S187]. Closes the iter-20 Now/T21 row (Subject + Issuer scope).

### Changed — AppOps row-tap cycles ALLOWED → IGNORED → ERRORED (2026-05-08)
- Row-tap on an AppOps entry in App Details (`AppDetailsPermissionsFragment`) now cycles **ALLOWED → IGNORED → ERRORED → ALLOWED** instead of binary toggling between ALLOWED and a derived deny.
- The IGNORE (`MODE_IGNORED`) state silently no-ops the op without throwing `SecurityException`, matching platform behavior. It's the correct option for ops that misbehaving apps would otherwise crash on if DENY (`MODE_ERRORED`) is set — Inure build106.5.0 model.
- A short Toast names the new mode after each tap (`AppOpsManagerCompat.modeToName(mode)`); long-press still opens the full single-choice mode picker (FOREGROUND/DEFAULT/etc.) for advanced users.
- New `nextAppOpModeInCycle()` helper in [`AppDetailsPermissionsFragment.java`](app/src/main/java/io/github/muntashirakon/AppManager/details/AppDetailsPermissionsFragment.java). Reference: [S131]. Closes the iter-20 Now/T9 row.

### Added — Sui (Magisk-module Shizuku) detection in onboarding (2026-05-08)
- New `checkSuiViaShell()` probe in [`runner/RootManagerInfo`](app/src/main/java/io/github/muntashirakon/AppManager/runner/RootManagerInfo.java) reads `/data/adb/modules/sui/` whenever the privileged shell already returned a non-NONE root manager (Magisk / KernelSU / APatch). New `RootManagerInfo.suiPresent` boolean carries the result through to consumers.
- [`OnboardingFragment.buildRootManagerSuffix()`](app/src/main/java/io/github/muntashirakon/AppManager/onboarding/OnboardingFragment.java) appends a " + Sui" suffix on the Root status line alongside the existing ZygiskNext suffix; combined cases render as e.g. "Detected · Magisk + Sui" or "Detected · KernelSU + Sui + ZygiskNext".
- Sui has no `moe.shizuku.privileged.api` package install, so the Magisk-module marker is the only authoritative signal — the iter-20 `PackageManager` enumeration approach the row originally proposed is unnecessary once the marker is read directly. The "prefer Sui over Shizuku" routing decision is deferred to the still-pending Privilege Health-Check screen (T5); `info.suiPresent` is the wire for it. Reference: [S178]. Closes the iter-20 Now/T5 row.

### Docs — GrapheneOS A16 background-install fix patch reference (2026-05-08)
- New [`docs/patch-references/2026-05-08-grapheneos-a16-background-install.md`](docs/patch-references/2026-05-08-grapheneos-a16-background-install.md) captures both fixes from GrapheneOS AppStore Release 36: (a) wrap user-confirmation `startActivity()` in an `isResumed` check + defer to `onPostResume()` when paused (Android 16 `IllegalStateException: Can not perform this action after onSaveInstanceState`), and (b) audit `getCallingPackage()` + `getReferrer()` and drop queued `PendingActions` when an external untrusted caller re-targets the activity.
- Port deferred until an Android 16 test device is available; doc lists the exact NG site ([`PackageInstallerActivity.java`](app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/PackageInstallerActivity.java)) and validation steps. Closes the iter-20 Now/T11 row in patch-reference form.

### Fixed — Debloater shortcut crash on pre-A13 / Unisoc devices (2026-05-08)
- Added `.debloat.DebloaterActivityAlias` (`android:exported="true"`, `targetActivity=".debloat.DebloaterActivity"`) in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) so external launcher pins / Tasker shortcuts / third-party app shortcuts resolve to a stable component name on platforms where pinning the underlying activity directly fails with `ActivityNotFoundException`. Reproduces upstream AM #1963 (Moto g22 / Unisoc T606 / Android 12).
- The alias has no `CATEGORY_LAUNCHER` filter — it does not appear as a separate launcher icon. Closes the iter-20 Now/T2 row.

### Compliance — Android 17 static-final reflection audit (1 fix, 1 deferred) (2026-05-08)
- Audited 20 `setAccessible(true)` call sites across `app/`, `libcore/`, `server/` for the Android 17 ban on `Field.set()` against `static final` fields with `setAccessible(true)`.
- 17 sites safe (10 `Method`/`Constructor`, 7 read-only `Field.get`).
- 1 fixed: [`TypefaceUtil.restoreFonts()`](app/src/main/java/io/github/muntashirakon/AppManager/utils/appearance/TypefaceUtil.java) wrote a same-reference back to `Typeface.sSystemFontMap` (static-final). Removed the redundant `Field.set()` call — the map's contents are mutated in place via `remove()` / `put()` so the write-back was a no-op. Behavior preserved.
- 1 deferred: [`RootServiceMain.startService()`](server/src/main/java/io/github/muntashirakon/AppManager/server/RootServiceMain.java) writes to `Resources.mSystem` (static-final). Currently `targetSdk=36` so the site is not yet broken; flagged for the targetSdk=37 bump task with three remediation options documented in the audit. Audit at [`docs/audits/2026-05-08-android17-static-final-reflection.md`](docs/audits/2026-05-08-android17-static-final-reflection.md). Closes the iter-20 Now/Eng-Debt audit row.

### Compliance — Google Play Contacts / Location-Button Policy audit (clean) (2026-05-08)
- Audit clean — the policy does not apply. NG's manifest declares only `READ_PHONE_STATE` (used for the telephony-side mobile/Wi-Fi data-usage split). NG does **not** declare `READ_CONTACTS`, `WRITE_CONTACTS`, `GET_ACCOUNTS`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `READ_PHONE_NUMBERS`, or any call-log / SMS permission.
- The contact and location permission strings that appear in [`PermissionGroupCatalog.java`](app/src/main/java/io/github/muntashirakon/AppManager/permissions/PermissionGroupCatalog.java) are **label constants** for the Permission Inspector UI to render groups when inspecting *other* installed apps; AppManagerNG itself never requests them at runtime. The iter-19 ROADMAP row's claim to the contrary was incorrect; the audit corrects the record.
- No NG UI button reveals contact info or precise location; no remediation required before the 2026-05-15 Google Play Console enforcement window. Audit at [`docs/audits/2026-05-08-google-play-contacts-location-policy.md`](docs/audits/2026-05-08-google-play-contacts-location-policy.md). Closes the iter-19 Now/Eng-Debt row.

### Security — CVE-2026-0073 disclosure for ADB mode (2026-05-08)
- New [`docs/security-advisories/2026-05-08-cve-2026-0073-adb-mode.md`](docs/security-advisories/2026-05-08-cve-2026-0073-adb-mode.md) discloses the Critical zero-click proximal RCE in `adbd` patched in the May 2026 Android Security Bulletin. AppManagerNG's ADB mode and Shizuku-via-wireless-debug provisioning talk to the same daemon, so devices below patch level `2026-05-01` carry residual risk.
- AppManagerNG itself is **not vulnerable** — the bug is in the platform `adbd` binary, not in any code we ship. Advisory documents impact split (USB-ADB on trusted network = moderate, wireless-debug = high), recommended actions for end users + downstream packagers, and the cross-reference to the sideload-verification doc (BR/ID/SG/TH overlap). Closes the iter-20 Now/T5 row; the companion in-app patch-level banner is deferred to the upcoming Onboarding Capability Wizard.

### Compliance — Android 17 `System.load()` read-only native audit (2026-05-08)
- **Audit clean — zero matches.** Recursive sweep across all source roots; AppManagerNG does not extract native libraries to disk via any of its own code paths and does not use `System.load(absolutePath)` anywhere.
- Two `System.loadLibrary("am")` call sites (`AhoCorasick.java:7`, `CpuUtils.java:13`) use the canonical AOSP path; the platform installer handles the read-only flag for bundled `jniLibs/`.
- Forty-plus `IoUtils.copy` call sites — none of them write `.so` files. Audit at [`docs/audits/2026-05-08-android17-system-load-readonly.md`](docs/audits/2026-05-08-android17-system-load-readonly.md). Closes the iter-20 Now/Eng-Debt row.

### Docs — AOSP source-pull retarget to `android-latest-release` (2026-05-08)
- AOSP moved to a trunk-stable publishing cadence in 2026: public source publishing now happens on a Q2 + Q4 schedule rather than continuous; `master` reflects a transient mid-quarter snapshot whose private-API surface may not survive to a published Android release.
- Pinned the **`android-latest-release`** branch as the only safe target for `hiddenapi/` stub harvesting in two places:
  - new "Pulling AOSP source for `hiddenapi/`" section in [`CONTRIBUTING.md`](CONTRIBUTING.md);
  - new [`hiddenapi/README.md`](hiddenapi/README.md) for in-module visibility.
- Both forbid `master` / `main` / `android-mainline` / date-stamped tags and point version-specific backports at version-tagged branches (`android-15.0.0_r1`, `android-16.0.0_r1`, etc.).
- The iter-19 Hidden-API Compatibility Harness will inherit this pinning from day 1; no script exists yet to retarget, so the work is documentation-only. Closes the iter-20 Now/Eng-Debt row.

### Build — Gson 2.13.2 → 2.14.0 (2026-05-08)
- `gson_version` bumped 2.13.2 → 2.14.0 in [`versions.gradle:26`](versions.gradle).
- Built-in `java.time` adapters drop the `--add-opens` requirement on JDK17 CI runners.
- Strict duplicate-JSON-key handling: malformed `{"foo": null, "foo": …}` now throws `JsonSyntaxException` instead of silently overwriting. Audited every Gson call-site (7 files); zero `setLenient(true)` opt-outs in the codebase, so all parse paths benefit. Audit + verification plan at [`docs/audits/2026-05-08-gson-2-14-0-bump.md`](docs/audits/2026-05-08-gson-2-14-0-bump.md). Closes the iter-20 Now/Eng-Debt row; supersedes the Engineering Debt Register entry that pinned 2.13.2.

### Security — BouncyCastle 1.83 → 1.84 (CVE-2026-3505 / 5588 / 5598) (2026-05-08)
- `bouncycastle_version` bumped 1.83 → 1.84 in [`versions.gradle:21`](versions.gradle); CVE list inlined as a trailing comment so the rationale lives at the dependency line.
- Closes **CVE-2026-3505** (PGP AEAD chunk-size DoS — directly relevant since `libopenpgp` powers OpenPGP-encrypted backup archives), **CVE-2026-5588**, and **CVE-2026-5598** (FrodoKEM non-constant-time compare; pre-emptive against future ML-DSA / PQ adoption).
- Audit at [`docs/audits/2026-05-08-bouncycastle-1-84-cve-bump.md`](docs/audits/2026-05-08-bouncycastle-1-84-cve-bump.md). Closes the iter-20 Now/Eng-Debt row; supersedes the long-standing low-urgency Engineering Debt Register entry that pinned 1.83.

### Docs — Sideloading Verification position document (2026-05-08)
- New [`docs/sideload-verification.md`](docs/sideload-verification.md) explaining what AppManagerNG does and does not do regarding Google's [Android Developer Verification](https://developers.google.com/android/play-protect/developer-verification) program — preempts the user-confusion wave when the 2026-09-30 enforcement starts hitting BR/ID/SG/TH users on certified devices.
- README "Install" section gains an `IMPORTANT` callout linking the document for users in the four enforcement regions.
- Closes the Iter-20 Now/T1/Docs row "Sideloading-Verification Position Document" (companion to "Android Developer Verification — BR/ID/SG/TH Enforcement"; that row remains in flight as a code-bearing task tracked separately).

### Docs — ROADMAP iter-20 research delta (2026-05-08)
- Appended "Iter-20 Research Additions" table to `ROADMAP.md` (38 rows: 19 Now / 16 Next / 1 Later / 1 Under Consideration / 1 Watch; 40 new sources S172–S211). Two-day delta from iter-19 (closing 2026-05-06): GitHub-issue mining of MuntashirAkon/AppManager (#1956–#1968), Canta, Hail (#387–#391), Neo-Backup (#1029–#1034), sdmaid-se (#2410–#2413), UAD-NG (#1386–#1394), Hamza417/Inure (#480), Obtainium (#2908–#2911 + discussion #2846), RikkaApps/Shizuku (#2036–#2052); Android 17 / QPR1 Beta 2 / Android Security Bulletin May-2026; Google Play Developer Verification rollout (BR/ID/SG/TH, enforcement 2026-09-30); 7-day GitHub-releases sweep (Neo-Backup 8.3.18, sdmaid-se v1.7.2-rc0 cert publish, Material Components 1.14.0-rc01, Gson 2.14.0, BouncyCastle 1.84 with three CVE fixes, hddq/restoid v0.5.0 restic-backed backup engine, wxxsfxyzm/InstallerX-Revived 26.05); new-competitor harvest the iter-19 list missed (Sui Magisk-module Shizuku, sameerasw/essentials, yume-chan/VolumeManager, pass-with-high-score/universal-installer, Hjsosn/FireWall-Blocks, kerneldroid/Shizuku-modern, BugeStudioTeam/Buge-App-Manager); GrapheneOS forum + XDA Shizuku/QPR1 threads.
- New themes: Android Developer Verification (single biggest sideload-tooling regulatory event of 2026), CVE-2026-0073 adbd zero-click RCE min-patch-level disclosure, BouncyCastle 1.83 → 1.84 PGP-AEAD DoS fix, Sui Magisk-module Shizuku detection, Shizuku 13.6.0 OEM allowlist (Transsion NPE / Mediatek / Pixel-9 QPR1), Shizuku root-backed avoidance for banking apps, OS-revert detection banner (novel — no competitor surfaces this), A16 QPR2 `clearApplicationUserData` fallback shell path with disk-usage-delta verification, LineageOS 23.2 root binder regression probe, Debloater activity-alias for pre-A13 Unisoc devices, App Info popup density refactor, per-OEM debloat risk ribbon (One UI 8.5 SmartSuggestions known-bad), default-app role re-binding after restore, restic-style backup engine (Under Consideration leapfrog), backup scheduler newest-age gate, CIFS/SMB streaming hardening, Wi-Fi configurations backup (root), squashfs writer header validation, FileManager recursive in-folder search, per-app volume via AppOps `OP_AUDIO_VOLUME` (closes upstream #1863), InstallerX-Revived privilege-elevation cascade, GrapheneOS A16 background-install-confirmation fix, split-APK cert-mismatch dialog, predictive-back WebView freeze fix, Material Components 1.14 FocusRingDrawable + SplitButton, AGP 8.13 → 9.2 migration ahead of AGP-10 cliff, AOSP source-pull retarget to `android-latest-release` (trunk-stable cadence), ML-DSA Keystore `KeyPairGenerator` recognition, HKDF-from-master backup key derivation (50K key cap mitigation), `System.load()` read-only native audit, Android 17 static-final reflection severity-promotion, persistent ADB tcpip 5555 detection in Shizuku setup, Doze allowlist diff banner, sideloading-verification position document.
- Iter-19 row promotions: Android Developer Verification rolled into a top-level T1 row; static-final reflection audit promoted to **Now** (severity revision); BouncyCastle bump promoted to **CVE-driven** Now; AGP migration promoted to **Next** with AGP-10 cliff dependency.

### Docs — ROADMAP iter-19 research delta (2026-05-06)
- Appended "Iter-19 Research Additions" table to `ROADMAP.md` covering 30 new items mined from a three-day GitHub-issue / community-pain-point sweep, a Shizuku-era competitor harvest (`timschneeb/awesome-shizuku`), and Android 17 Beta 4 + F-Droid 2.0 platform deltas. New themes: Hidden-API compatibility harness, GCM-cipher reuse on large OBB backup (#1958), Shizuku-permission auto-revoke warning on data-clear (Canta #359), Hidden-Shizuku fork detection, OEM debloat-blocker bypass (OPlus / Samsung / MIUI), per-app rollback / undo, Tasker parameterized intent API, freeze / operation audit-log UI, settings import/export portability, install-date filter, Android 17 16 KB page-size fix, Google Play Contacts/Location-button policy enforcement, KernelSU ADB-Root privilege enum, Blocker-style IFW rule editor, Amarok-Hider `pm hide` toggle, Language-Selector per-app locale via Shizuku, InstallerX-style biometric install gate, debuggable-app rootless backup, F-Droid 2.0 ROM JSON pre-seeding format, F-Droid 2.0 protobuf index v2, Android 17 ACCESS_LOCAL_NETWORK + static-final reflection ban + 50K Keystore cap + ML-DSA cert OIDs + cleartext deprecation, OwnDroid Dhizuku DPM mode, FireOS SYSTEM USER privilege backend, PI install-interception, UpgradeAll getter-plugin API, Material You / Monet widget theming. Two explicit rejects (Shizuku-iptables firewall, Thanox-style Accessibility-Service auto-freeze) per NG philosophy.
- Source Appendix extended S137–S171 (35 new sources). All iter-19 rows cite `[S###]` references.

## v0.4.0 — 2026-05-02

### Fixed — Permission Inspector: recovery action for previously revoked critical packages
- New "Restore system app permissions" action on the Permission Inspector home screen. Re-grants every dangerous permission to a fixed set of OS- and vendor-critical packages (Phone, system UI, Settings, telephony/contacts/media providers, fused location, Google Play services / GSF, Samsung location & IMS, etc.) and clears any persisted ComponentsBlocker permission rules for those packages so a bad state from a pre-guard build cannot survive reboot or reinstall.
- Required because earlier builds without the bulk-revoke guard could leave Phone, voicemail, location services, and other system functions broken via REVOKED_COMPAT appop flags. The recovery action makes that recoverable from inside the app instead of via adb.

### Added — Permission Inspector: master grant + info dialog
- New "Grant for all apps" toolbar action mirrors the existing "Revoke for all" — mass-grants the permission group to every modifiable app on the device. Useful when you've over-revoked and want to start fresh.
- New info icon on the toolbar opens a dialog explaining what the screen does and, importantly, **why some apps are skipped during a bulk revoke** (OS- and vendor-critical packages — GMS, GSF, system UI, telephony/media providers, fused location, Samsung location/IMS, etc. — are excluded from the bulk action because revoking from them can crash system_server). Per-app toggles remain unrestricted.
- The same explanation dialog auto-pops after a bulk revoke whenever any app was skipped, so users see the reason in context.

### Fixed — Permission Inspector: bulk-revoke could reboot device
- The master "Revoke for all apps" action now skips a denylist of critical system packages (`android`, `com.google.android.gms`/`gsf`, `com.android.systemui`, `com.android.settings`, `com.android.phone`, telephony/media/contacts providers, `com.android.location.fused`, Samsung location/IMS/phone services, etc.) and any `com.android.server.*` / `com.google.android.gms.*` subpackage. Revoking `ACCESS_FINE_LOCATION` / `ACCESS_BACKGROUND_LOCATION` from these crashed `system_server` and rebooted the device on Samsung One UI. Per-app toggles remain unrestricted — the guard only applies to the bulk action. A toast now reports both how many apps were revoked and how many were skipped.

### Added — Permission Inspector: review and bulk-revoke permissions across apps

- New top-level screen accessible from the main overflow menu (shield-key icon) that inverts the standard "app -> permissions" view. Catalog lists 12 curated dangerous permission groups (Camera, Microphone, Location, Contacts, SMS, Phone, Files & media, Calendar, Body sensors, Physical activity, Nearby devices, Notifications) each with a "X of Y apps granted" count. Tap a group to drill into the per-permission list of every app that requested it; toggle individual apps with a Material switch, or use the master "Revoke for all apps" toolbar action to mass-revoke in one shot. Persists changes through `ComponentsBlocker` so they survive reinstalls, same as the existing per-app permissions tab. SDK-version-gated for permission groups added in API 29/31/33/34.

### Added — Main List & Item Layout: v2 Design System Integration (v0.5.x surface migration phase 1)
- New `activity_main_v2.xml` and `item_main_v2.xml` wired behind the `PREF_PREMIUM_PREVIEW_BOOL` 
  toggle. When enabled, MainActivity and MainRecyclerAdapter load v2 layouts with refined v2 token 
  palette (calmer surfaces, tighter typography, pill-shaped search, outlined card variants). 
  Layout switching is conditional per-view, allowing zero-impact on classic theme users. 
  Completes v0.5.x phase 1 (top-5 surface migration). Next phases: AppDetails, AppUsage, Settings.

### Added — Troubleshooting: auto-fix battery optimization via root/ADB (SD-Maid parity, ROADMAP iter-18 T20)
- Settings → Troubleshooting → "Battery optimization" now auto-applies the
  exemption when NG has root or ADB privileges (checks `DEVICE_POWER`). If
  permitted: silently grants the whitelist in background, updates summary,
  and shows a confirmation toast. If not: falls back to the system dialog.
  Matches SD-Maid's "auto-fix" UX pattern. No user setup needed.

### Added — App Details: copyable error dialog helper (UAD parity, ROADMAP iter-18 T4)
- New `UIUtils.displayCopyableErrorDialog(context, title, message)` shows a
  Material alert with OK + **Copy** buttons. Copy invokes `ClipboardUtils`
  (which already handles >1MB error blobs via FileProvider URI fallback)
  so users can paste failure detail straight into a bug report instead
  of screenshotting + transcribing. Foundation only; high-traffic toast
  failure sites migrate in a follow-up commit.

### Changed — App Info / AppOps / Permissions: descriptions now selectable (UAD parity, ROADMAP iter-18 T10)
- `item_app_details_appop.xml` and `item_app_details_perm.xml` now mark
  `perm_description`, `perm_protection_level`, `op_mode_running_duration`
  and `op_accept_reject_time` as `textIsSelectable="true"`. Long-press to
  copy permission/op descriptions and runtime metadata directly from the
  list — matches Universal Android Debloater's selectable-description
  affordance.

### Added — Appearance: Preview new design (BETA) toggle (premium polish v0.4.x foundation)
- New Settings → Appearance → "Preview new design (BETA)" switch
  (default OFF, key `PREF_PREMIUM_PREVIEW_BOOL`). When enabled the
  app inflates the v2 design system: a refined teal-leaning palette
  with crisper contrast tiers, tightened typography (no letter-
  spacing hangs on titles), pill-shaped FABs and search surfaces,
  and outlined card variants that respect the layered surface
  hierarchy. Pure-black mode routes to `AppTheme.V2.Amoled` so the
  premium look composes with the existing AMOLED preference.
  Layouts and widget IDs are intentionally untouched in this
  release; only the theme/token plane changes. Restart applies.
  Resources copied verbatim from `design/impl/values/{themes,colors,
  dimens}-v2.xml`; rollout plan: `design/plan/3-rollout.md`.

### Added — Backup: Android 14+ "Keep device awake" warning toast (ROADMAP iter-18 item closed)
- When a backup operation begins on Android 14+ (`SDK_INT >=
  UPSIDE_DOWN_CAKE`), NG now displays a long Toast asking the user
  to keep the device awake and AppManager open until the backup
  finishes. Mitigates Android 14's tightened JobScheduler quotas
  and aggressive Doze kills of long-running foreground services.
  Mirrors Neo Backup 8.3.17 behavior. Source: ROADMAP S135.

### Audit — App list / Finder search history (ROADMAP iter-18 item closed)
- Audited persistent search-term storage per Inure build107.0.1
  privacy posture. NG's `SearchView` usage is already session-only
  in memory — `recent_search`, `searchHistory`,
  `SearchRecentSuggestionsProvider` grep all return zero hits.
  No persistent storage exists; no remediation needed. Source:
  ROADMAP S131.

### Added — App Info: Device page size row (ROADMAP iter-18 item closed)
- New "Device page size" row in App Info under Primary ABI for any
  app with native code. Populated via `Os.sysconf(_SC_PAGESIZE)` and
  rendered as "4 KB", "16 KB (page-size compatibility required)",
  raw bytes for any other value, or "Unknown" if the syscall throws.
  Pairs with the per-lib 16KB-alignment indicator (iter-11) so
  16k-incompatible libs visibly explain the warning instead of
  looking spurious on 4k devices. Source: Termux v0.118.3
  page-size detection (ROADMAP S126).

### Docs — ROADMAP iter-18 research (no code change)
- Research-only iteration. 29 new candidate items added under a new
  "Iter-18 Research Additions" section in `ROADMAP.md`, drawn from
  Shizuku v13.6.0, Magisk v30.7, KernelSU v3.2, Termux v0.118.3,
  Apktool v3.0.2, JADX v1.5.5, APKEditor v1.4.7-8, Hail v1.10.0,
  Inure build107, Material Files v1.7.4, SD Maid SE v1.7.2-rc0,
  UAD-NG v1.2.0, Neo Backup 8.3.17, androidx.glance, and Android
  16/17 platform docs. Highlights: Shizuku trusted-WLAN auto-start
  banner, Magisk `--drop-cap` opt-in semantics surface, KernelSU
  sulog/seccomp parity, Android 16 `SDK_INT_FULL` plumbing audit,
  JobScheduler quota stop-reason surfacing, APKEditor smali
  comment-level "basic", Hail-style auto-freeze QS tile,
  Inure-style AppOps IGNORE flag, UAD-style cross-user package
  state detection, Neo-Backup-style backup sharing button.
  Sources S121–S136 appended to the appendix; baseline line bumped
  with iter-18 summary.

### Added — Settings: Battery optimization entry (ROADMAP Trivial closed)
- New "Battery optimization" preference under Settings → Troubleshooting.
  Summary reflects the current `PowerManager.isIgnoringBatteryOptimizations()`
  state and refreshes on resume. Tap routes to the per-app request prompt
  (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) when optimized, or to the
  system-wide list (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) when
  already exempt so the user can revoke. Pre-M devices see a disabled
  entry with explanatory copy. Manifest now declares
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

### Added — AppType filter: Play App Signing + Overlay flags (eng-debt TODO partially closed)
- The AppType filter (used by Saved Filters and Finder) gains two
  previously-stubbed flags: **Uses Play App Signing** (APK signed by
  Google rather than the developer's release key) and **Overlay app**
  (Resource Runtime Overlay declaring an `<overlay>` manifest tag).
  Both flags work in `with_flags` and `without_flags` modes.
- New `IFilterableAppInfo.usesPlayAppSigning()` / `isOverlay()` methods
  implemented on both `FilterableAppInfo` (eager via
  `PackageUtils.usesPlayAppSigning` and `PackageInfoCompat2.getOverlayTarget`)
  and `ApplicationItem` (lazy via `fetchPackageInfo()`). PWA and
  short-code remain on the TODO list pending a stable detection signal —
  TWA detection requires manifest service-tag sniffing and short-code
  isn't exposed by `PackageManager`.

### Added — Code Editor: language / tab-size / go-to-line pickers (ROADMAP T14 ×3 closed)
- **Language toolbar button** now opens a popup listing all seven
  tmLanguage-backed languages bundled in `assets/languages/`
  (java / json / kotlin / properties / sh / smali / xml). Picking switches
  the syntax highlighter (`mEditor.setEditorLanguage`) and persists via
  the new `CodeEditorViewModel.setLanguage()` setter; the indent-mode
  label re-renders against the chosen language's `useTab/useSpace`
  default.
- **Tab-size toolbar button** opens a 2 / 4 / 8 popup wired to
  `mEditor.setTabWidth(n)` so the user can override the language default
  for files like Makefiles that need real tabs.
- **Position toolbar button** now opens a "Go to line" dialog
  (`TextInputDialogBuilder` numeric input) and moves the cursor via
  `mEditor.setSelection(line - 1, 0)`; out-of-range input clamps to
  `[1, lineCount]`.
- The hardcoded "tabs"/"spaces" suffix on the indent label now reads
  from `R.plurals.editor_tab_size_option_{tabs,spaces}` so non-English
  locales render the correct plural form. Closes the four `CodeEditorFragment.java`
  TODOs (`13/9/22 Display all the supported languages`, `13/9/22 Enable
  setting custom tab size`, `13/9/22 Enable going to custom places`,
  `13/9/22 Use localization`).

### Changed — Plural string audit (ROADMAP T10 closed)
- Three remaining pluralizable count strings converted to `<plurals>`:
  `main_status_showing_apps` ("Showing N of M apps"),
  `main_status_all_apps` ("Showing N apps"), and
  `bar_chart_content_description` ("Bar chart with N data points").
  Callers in `MainActivity.updateListStatus` and
  `BarChartView.updateContentDescription` now use
  `getQuantityString()` so locales whose plural form differs by count
  (Russian / Polish / Arabic / etc.) render correctly. Orphan
  `selected_items_accessibility_description` (no callers) removed.
- The remaining `%d`-using strings in `values/strings.xml` describe
  IDs, positions, range bounds, or "X of Y" composites — none are
  pluralizable, so the audit is closed.

### Added — Share profile as JSON (ROADMAP T8 closed)
- New **Share as JSON** popup action on each profile in the Profiles
  list (`action_share` between Export and Shortcut). Sends the profile's
  pretty-printed JSON via `Intent.ACTION_SEND` — Telegram / KDE Connect
  / email / Gmail draft / Slack pick it up directly, no SAF round-trip
  required. The wire format is identical to what `Export` writes, so
  the receiving NG instance can re-import via the existing Import
  action verbatim. File-export remains for share targets that need an
  attachment.
- The companion file-roundtrip Import + Export paths were already wired
  in `ProfilesActivity` (`ActivityResultContracts.GetContent` /
  `CreateDocument("application/json")`); ROADMAP row was stale, now
  closed.

### Added — Signing-cert SHA-256 chip in App Info (ROADMAP T18 closed)
- New "Sign · SHA-256 21:5F…38:6C" chip in the App Info tag cloud
  surfaces the colon-separated, upper-case SHA-256 fingerprint of the
  current signing certificate. Tap opens a Material dialog showing the
  full digest with a one-tap **Copy** button so users can paste the
  fingerprint directly into AppVerifier or compare against
  `apksigner verify --print-certs` output without leaving NG. Single-
  signer APKs only — multi-signer cases stay routed through the existing
  icon-tap verify-from-clipboard flow.
- Backed by `AppInfoViewModel.computeSigningCertSha256()` (worker-side
  via `PackageUtils.getSignerInfo` + `DigestUtils.SHA_256`); result is
  cached on `TagCloud.signingCertSha256`.

### Changed — VirusTotal poll-wait scales with upload size (engineering-debt TODO closed)
- `VirusTotal.fetchFileReportOrScan` now scales the *first* poll wait
  by file size via the new `computeInitialPollWait(fileSize)` helper —
  roughly +1 s per MB above a 10 MB threshold, clamped to [60 s, 240 s].
  Avoids burning the 4 req/min free-API rate-limit quota on a large
  upload that hasn't finished engine processing yet. Subsequent polls
  remain at the 30 s rate-limit floor. Closes the inline TODO at
  `VirusTotal.java` (originally filed 2022-05-23) and the matching
  Engineering Debt Register row.

### Added — Root manager detection: Magisk / KernelSU / APatch / ZygiskNext (ROADMAP T5 ×3 closed)
- New `runner/RootManagerInfo` helper probes `/data/adb/{magisk,ksu,ap}` via
  the privileged shell when root is granted, and falls back to a
  `PackageManager` lookup of the manager apps
  (`com.topjohnwu.magisk`, `me.weishu.kernelsu`, `com.rifsxd.ksunext`,
  `me.bmax.apatch`) when it isn't. Whenever a non-NONE manager is detected
  through the shell, a follow-up `[ -d /data/adb/modules/zygisksu ]`
  identifies the ZygiskNext layer.
- Onboarding sheet (`OnboardingFragment.refreshCapabilityStatuses`) now
  appends the resolved manager name (and " + ZygiskNext" if applicable)
  to the Root status line — e.g. "Detected · KernelSU + ZygiskNext". The
  probe runs on a background thread (one shell round-trip), result is
  posted back to the main thread, and the suffix update is idempotent so
  the Re-check button can be tapped repeatedly without stacking suffixes.
- Closes ROADMAP T5 rows: KernelSU Detection, APatch Detection,
  ZygiskNext Detection. SuperKey / per-module-count surfacing for APatch
  and ZygiskNext error-count surfacing remain on the Privilege
  Health-Check Screen row.

### Added — Stable signing-cert fingerprint URL (ROADMAP T1 closed)
- New [`docs/fingerprints.txt`](docs/fingerprints.txt) publishes the SHA-256
  signing-cert fingerprint in a comment-tolerant `package:` / `sha256:`
  record format (SD Maid SE precedent), served via the stable
  `raw.githubusercontent.com/.../docs/fingerprints.txt` URL — AppVerifier
  and similar tooling can fetch it programmatically without scraping the
  README. README "Verifying releases" section now points users at the URL.

### Added — Android 17 ProfilingManager OOM/anomaly triggers (ROADMAP T4 closed)
- New `misc/ProfilingTriggerHelper.registerTriggersIfSupported(Context)`
  registers `TRIGGER_TYPE_OOM` and `TRIGGER_TYPE_ANOMALY` via reflection on
  API 37+ devices, so the system auto-captures heap profiles when
  AppManagerNG hits low-memory or anomaly conditions during JADX decompile
  or APK parsing. Silent no-op on anything below API 37 and on any
  reflective lookup failure (compileSdk is still 36 so the profiling
  classes are not present at build time).
- Wired from `AppManager.onCreate()` once per process. The harvest +
  diagnostic-ZIP-attach side of the workflow is deferred until API 37 is
  available on a real device for end-to-end test.

### Compliance — Android 17 per-app Keystore key-cap audit (clean; ROADMAP T2 closed)
- Audit confirms NG can never exceed Android 17's 50,000-key per-app
  `AndroidKeyStore` cap: it generates at most **two** static, idempotently
  guarded aliases (`aes_local_protection` on API ≥ M, plus a legacy
  `rsa_wrap_local_protection` on pre-M devices) — both in
  `CompatUtil.getAesGcmLocalProtectionKey()` behind `containsAlias`
  checks. All backup-crypto paths route through a file-backed BKS
  keystore (`am_keystore.bks` via `KeyStoreManager`) which is outside
  the platform-managed Keystore. No remediation needed; roadmap row
  closed. Audit at
  [docs/audits/2026-05-02-android17-keystore-key-cap.md](docs/audits/2026-05-02-android17-keystore-key-cap.md).

### Changed — Pre-emptive Android 18 share-intent compliance (ROADMAP T3 closed)
- All seven outgoing `ACTION_SEND` / `ACTION_SEND_MULTIPLE` paths that carry
  a content URI (App Info APK share, log viewer attachment chooser, code
  editor share, single + multi file-manager share, diagnostic export, crash
  report) now set both `FLAG_GRANT_READ_URI_PERMISSION` and an explicit
  `ClipData` so the chooser target keeps receiving read access once
  Android 18 removes the implicit auto-grant for SEND/SEND_MULTIPLE/
  IMAGE_CAPTURE. Multi-URI shares from the file manager now build a
  multi-item `ClipData` rather than relying on the EXTRA_STREAM list alone.
- Audit and full inventory at
  [docs/audits/2026-05-02-android18-implicit-uri-grant.md](docs/audits/2026-05-02-android18-implicit-uri-grant.md).
  No `IMAGE_CAPTURE` callers in source; `PackageInstaller` install path
  streams via `openWrite()` and is unaffected.

### Changed — Utils.java flag-string i18n (ROADMAP T3 closed)
- `Utils.getSoftInputString`, `getServiceFlagsString`,
  `getActivitiesFlagsString`, and `getInputFeaturesString` now read their
  flag labels from `strings.xml` (`soft_input_flag_*`, `service_flag_*`,
  `activity_flag_*`, `input_feature_*`) via `ContextUtils.getContext()`,
  so the App Details Activities / Services / Other tabs respect the
  device locale instead of hardcoded English.
- `Utils.getProtectionLevelString` keeps Android's canonical manifest
  `android:protectionLevel="..."` tokens (`dangerous`, `signature`,
  `signature|privileged`, etc.) untranslated by design — they are
  technical identifiers, and `AppDetailsPermissionsFragment` does a
  `protectionLevel.contains("dangerous")` check that must keep working.
  Replaced the stale `FIXME` with a comment documenting the rationale.

### Added — Settings: Mode-of-Ops live capability refresh
- Capability badges (Root / Wireless ADB / USB ADB) now refresh every time
  the Mode-of-Ops settings screen resumes. Toggling Wireless debugging in
  Quick Settings or granting root in another app while Settings is in the
  background now reflects on return — no need to leave the screen entirely.

### Added — Android TV launcher parity (audit)
- Confirmed `SplashActivity` already declares `LEANBACK_LAUNCHER`, the
  manifest declares leanback `uses-feature` with `required="false"` and
  optional touchscreen, and the `ic_banner` mipmap is wired. AppManagerNG
  appears on Android TV / Google TV launchers with no additional work.
  ROADMAP item closed.

### Added — App Info bloatware safety rating
- **Bloatware tag now surfaces the safety call directly** — App Info →
  tag cloud previously showed a generic "Bloatware" chip coloured by
  removal type. Tag text now reads "Bloatware · Safe", "Bloatware ·
  Replace", "Bloatware · Caution", or "Bloatware · Unsafe", so users can
  read the recommendation without tapping into the details dialog.
  Colour is preserved (`ColorCodes.getBloatwareIndicatorColor`).

### Added — Onboarding replay (v0.4.0)
- **Replay welcome wizard** action in Settings → Troubleshooting; clears
  `PREF_ONBOARDING_SHOWN_BOOL` and immediately surfaces the privilege-mode
  picker (Auto / Root / Wireless ADB / ADB-TCP / No-root) so power users
  and testers can revisit the explainers without a fresh install. The
  picker writes the flag back on pick/cancel, so the flow self-heals.
- **Replay quick tour** action in Settings → Troubleshooting; clears
  `PREF_MAIN_TOUR_SHOWN_BOOL` so the main-list tour re-arms on the next
  launch. Toast confirms the reset.
- **Active-mode highlight in onboarding** — when the wizard opens (first
  run or replay), the card matching the currently saved mode is ringed
  with a 2dp `colorPrimary` stroke, so users replaying see at a glance
  which mode is in effect. A11y description is prefixed with "Currently
  active." for screen-reader parity.
- **Pick-Root-without-detection guardrail** — when a user taps the Root
  card and `Ops.hasRoot()` returns false, a confirmation dialog explains
  the situation (root managers can hide su until first request, but most
  ops will fail until granted) and lets them cancel without burning the
  onboarding-shown flag.
- **Re-check capabilities button** in the onboarding sheet — refreshes
  the Root / Wireless-ADB / USB-ADB badges and the active-mode highlight
  in place without dismissing the sheet, so users who toggle Wireless
  debugging from quick-settings or grant root from another app can see
  the new state immediately. Snackbar confirms the refresh.

### Added — Premium facelift design system (foundation)
- **`design/` deliverable folder** (audit, spec, impl, plan, README) — full
  v2 design system reference: palette, typography, 4dp spacing ladder,
  elevation tokens, motion vocabulary, iconography choice, and 5 drop-in
  reference XML files (themes-v2, colors-v2, dimens-v2, item_main_v2,
  activity_main_v2). Read order: [design/README.md](design/README.md).
- **Pain-point inventory** ([design/audit/4-painpoints.md](design/audit/4-painpoints.md))
  catalogues 16 dated surfaces with concrete fix proposals and benchmark
  references (Linear, Arc, Things 3, 1Password 8, Obsidian).
- **4-release rollout plan** ([design/plan/3-rollout.md](design/plan/3-rollout.md))
  for shipping the facelift behind a Pro Mode "Preview new design" toggle
  (v0.4.x foundation → v0.5.x top-5 migration → v0.6.x long tail → v0.7.x
  toggle removal).

### Added — Main list polish (preview)
- **Semantic threshold tinting** for the tracker indicator (green ≤4,
  amber 5-19, red ≥20) and the dangerous-permission badge (success when
  zero granted, warning below 50%, danger 50%+).
- **Risk-tinted package name** on app rows when a heuristic combination
  of granted-perms + tracker count crosses a danger threshold.
- **Restyled main status banner** with stronger metric typography,
  clearer "filtered N of M" affordance, and tonal background.
- **Refined empty state** (`view_main_empty_state.xml`) with explicit
  active-filter description and reset-filter affordance.
- New `ColorCodes` semantic colors and `Widget.AppTheme.Chip.MainFilter` /
  `Widget.AppTheme.Chip.MainSuggestion` chip styles.

### Added — Settings & onboarding polish
- Tighter `m3_preference*.xml` row treatments (preference category
  indicator, dual-pane divider, focused-pane state).
- Onboarding fragment redesign: tonally-tinted mode-of-operation cards,
  status background drawables, plain-language privilege explainer.
- Reorganised `preferences_main.xml` to match the new tier hierarchy.

### Added — Operation Activity Log (ROADMAP T8 closed)
- Persistent journal of every operation AppManagerNG performs (freeze,
  backup, batch, install, profile execution). Per-entry metadata: target
  app(s), operation type, timestamp, mode (root/ADB/Shizuku/no-root),
  risk tier, success/failure, scope.
- `OpHistoryActivity` reachable from main overflow → "History" with
  aggregate summary, package/operation/mode/target search, success/risk
  filter chips, risk-tinted card borders, FAB.
- New helpers: `OperationJournalMetadata`, `OperationPreflight`,
  `OperationHistoryExporter` (with Robolectric test). Per-entry copy/
  delete actions; rerun preflight gates dangerous reruns.
- Debug-only "Add sample entries" menu action under `BuildConfig.DEBUG`
  for development verification — never visible in release builds.

### ROADMAP additions (research-driven)
- New T19 tier: **Package-Aware Storage Analysis** (App Details Storage
  Panel, Leftover Detection After Uninstall, APK Duplicate Finder,
  Backup Duplicate Cleaner — SD Maid SE / UAD-NG models).
- T2 row: **Android 17 Keystore Per-App Key Cap** (50,000-key audit
  before targetSdk=37 bump).
- T3 row: **Android 18 Implicit URI Grant Removal** (preemptive
  `grantUriPermission()` audit before Android 18 ships).
- T7 rows: **Finder Relevance-Based Search Scoring** (Levenshtein) and
  **Finder Description-Field Search** (debloat-list metadata).
- T8 row: **Multi-Tag per App** (Hail v1.10.0 model, many-to-many join
  table on the existing Room schema).
- T9 rows: **Permission Policy Flags Display** + **MiUI-Specific AppOps
  Mapping** (Inure 106.5.0 model).
- T12 rows: **Native Library Sizes in App Details** + **Batch APK
  Installer from File Manager** (Inure 107.0.0/.1 model).
- New sources S65–S68 logged. Full research at
  [docs/research/2026-05-02-android-power-tools.md](docs/research/2026-05-02-android-power-tools.md).

### Compliance
- **Android 17 `MessageQueue` audit (clean)**: lock-free `MessageQueue`
  shipping in Android 17 / targetSdk=37 crashes apps that reach into
  private fields via reflection. Recursive sweep across all source roots
  returned zero matches; root-shell IPC routes through libsu shell
  processes, not `MessageQueue` reflection. Audit at
  [docs/audits/2026-05-02-android17-messagequeue.md](docs/audits/2026-05-02-android17-messagequeue.md).
- **Adaptive Layout for Large Screens audit (clean)**: Android 16 /
  targetSdk=36 ignores `screenOrientation`, `resizeableActivity=false`,
  and aspect-ratio limits on ≥ 600dp displays. Manifest sweep across 43
  activities returned zero fixed-orientation declarations, zero resize
  blockers, zero aspect-ratio limits. Audit at
  [docs/audits/2026-05-02-adaptive-layout.md](docs/audits/2026-05-02-adaptive-layout.md).

### Added
- **Tablet density overrides** (`app/src/main/res/values-w600dp/dimens.xml`,
  `libcore/ui/src/main/res/values-w600dp/dimens.xml`): bumps icon sizes,
  list-row min-height, font sizes, and medium/large/very-large padding
  tiers when the available width is ≥ 600dp (tablets, foldables in
  landscape, Chromebooks, free-form windowed mode). Phone-sized devices
  read the existing `values/dimens.xml` unchanged. No per-layout edits
  required — the new values propagate to every layout already consuming
  these tokens.
- **Sort by Dangerous Permissions**: new `SORT_BY_DANGEROUS_PERMS` option in
  the main app list (Sort menu). Mirrors the `SORT_BY_TRACKERS` shape —
  primary key is granted dangerous perms (most-privileged-by-actual-grant
  apps surface first); secondary key is total declared dangerous perms.
  Wires `dangerous_perm_total` / `dangerous_perm_granted` (Room schema v9)
  into the user-facing UI.
- **Obtainium config** (`docs/distribution/obtainium-config.json`):
  ready-to-import Obtainium AppConfig pointing at GitHub Releases with
  artifact regex for the signed `AppManagerNG-<version>-{arm64-v8a,universal}.apk`
  files (auto-ABI selection enabled). README "Install" section adds an
  "Install via Obtainium" subsection with paste-and-go instructions plus
  an AppVerifier pairing tip.

### Compliance
- **`elegantTextHeight` audit (clean)**: Android 16 / targetSdk=36 silently
  ignores `android:elegantTextHeight`; affects Arabic/Thai/Indic text
  rendering. Recursive sweep across all source roots returned zero
  matches — no remediation required. Audit recorded at
  [docs/audits/2026-05-01-elegant-text-height.md](docs/audits/2026-05-01-elegant-text-height.md).

## v0.3.0 — 2026-06-05

Platform compliance, bug fixes, and observability hardening.

### Fixed
- **BarChartView accessibility** (`usage/BarChartView.java`): replaced deprecated
  `announceForAccessibility()` (ignored by TalkBack on Android 16+) with
  `ViewCompat.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE)`. The 3
  redundant announcement calls alongside virtual-view events were removed; 1 was replaced
  with `updateOverallContentDescription()` so the live region fires on data change.
- **KeyStoreUtils secure memory** (`crypto/ks/KeyStoreUtils.java`): `StringBuilder`
  (non-zeroable) replaced with `CharArrayWriter`; key material byte arrays explicitly zeroed
  after `generatePrivate()` to reduce key-in-memory exposure window.
- **ABX editor** (`editor/CodeEditorViewModel.java`): Android Binary XML files can now be
  opened for inspection in the code editor. Write-back is blocked via `canWrite() = false`
  when `mXmlType == XML_TYPE_ABX` to prevent lossy typed-value → string round-trip.
- **ActivityInterceptor `ACTION_OPEN_DOCUMENT`** (`intercept/ActivityInterceptor.java`):
  `FLAG_ACTIVITY_NEW_TASK` was being added to the document-picker intent, which broke result
  delivery (Android bug: new-task flag + `startActivityForResult` never delivers result).
  Flag is now stripped with `removeFlags()` before launching the picker.

### Added
- **Crash log persistence** (`misc/AMExceptionHandler.java`): crashes are written to
  `getFilesDir()/crashes/crash_TIMESTAMP.log` (capped at 10 files). The crash share
  notification now attaches the log file as a `content://` URI via FmProvider. Upstream
  hardcoded email removed; subject updated to "AppManager NG: Crash Report".
- **Diagnostic export** (`misc/DiagnosticUtils.java` + Settings → About): new "Export
  Diagnostic Report" preference bundles device info, all crash logs, and the last 2 000
  logcat lines (main/system/crash buffers) into a ZIP file and opens the share chooser.
- **CodeQL on main** (`.github/workflows/codeql.yml`): analysis now triggers on pushes to
  `main` (was limited to `master`); `workflow_dispatch` added for on-demand scans.

### Deferred
- `Utils.java` flag-string i18n (5 methods × ~50 string resources) — deferred to v0.4.0.
  Caller Context injection required before extracting strings.

Identity milestone: AppManagerNG now has its own install identity, signing key, and release
pipeline, fully separated from the upstream package.

### Added
- **`applicationId` rename**: install identity changed from `io.github.muntashirakon.AppManager`
  to `io.github.sysadmindoc.AppManagerNG`. Source namespace kept at
  `io.github.muntashirakon.AppManager` (full namespace rename is future work).
- **New release keystore**: `AppManagerNG-release.jks` — 4096-bit RSA, 10,000-day validity.
  SHA-256: `21:5F:B4:70:63:2E:A6:CD:59:A4:BA:AB:35:0A:9E:0B:99:AD:11:0F:DD:FA:F5:A9:EA:64:61:E5:D0:C2:38:6C`
- **GitHub Actions release pipeline** (`.github/workflows/release.yml`): tag push → build →
  sign → upload arm64-v8a + universal APKs to GitHub Releases.
- **CONTRIBUTING.md**: NG-specific contribution guidelines (replaces upstream CONTRIBUTING.rst
  reference); covers AI code policy, commit format, upstream sync protocol, translation note.
- **ROADMAP.md**: comprehensive prioritized roadmap through v0.6.0+ (17 themes, 37 sources).
- **AppVerifier fingerprint** in README for release verification.
- **16KB page size compliance**: `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` CMake argument
  added to `app/build.gradle` for Android 15+ physical device compatibility.

### Fixed
- `LocalFileOverlay.java`: hardcoded application ID fallback now uses `BuildConfig.APPLICATION_ID`
  instead of a literal string, so it tracks applicationId changes automatically.
- `settings.gradle`: `rootProject.name` updated to `AppManagerNG`.

## v0.1.0 — 2026-04-30

Initial AppManagerNG release. Repo bootstrap from upstream
[App Manager](https://github.com/MuntashirAkon/AppManager) commit
[`3d11bcb`](https://github.com/MuntashirAkon/AppManager/commit/3d11bcbc399d3a4f995b544e26d86bd80487fd32)
(2026-04-16, upstream tag context: post-v4.0.5).

### Added
- AppManagerNG-branded README.md with shields.io badges, GPL-3.0-or-later notice, and upstream credit
- CHANGELOG.md (this file)
- Branding/logo prompts directory (`branding/logo-prompts.md`)

### Changed
- App display name (`app_name` resValue): `App Manager` → `AppManagerNG` (release), `AM Debug` → `AM-NG Debug` (debug)
- Android `versionName`: `4.0.5` → `0.1.0`; `versionCode`: `445` → `1`

### Preserved (unchanged from upstream)
- All Java/Kotlin/Native sources
- Package name (`io.github.muntashirakon.AppManager`) and namespace — rebrand deferred to v0.2.0
- License files: `COPYING`, `LICENSES/` directory (REUSE-compliant), per-file SPDX headers
- Build configuration (Gradle, AGP version, dependencies, signing config)
- Documentation: `BUILDING.rst`, `CONTRIBUTING.rst`, `PRIVACY_POLICY.rst`, `docs/`
- F-Droid metadata (`fastlane/`)
- Submodule pointers (`scripts/android-libraries`, `scripts/android-debloat-list`)

### Roadmap
- **v0.2.0** — applicationId + namespace rename to `io.github.sysadmindoc.AppManagerNG`; fresh keystore
- **v0.3.0** — Material 3 dashboard refresh + Pro-mode toggle for advanced features
- **v0.4.0** — Onboarding flow (root/ADB capability detection + plain-language explainer)
- **v0.5.0** — Settings reorganization + in-app search and help
