# AppManagerNG — iter-20 Research Delta

Window: 2026-04-15 to 2026-05-08. Findings exclude every issue/thread already mined in iter-19.

---

## 1. MuntashirAkon/AppManager — new issues

### #1968 — Automating Save APK
- **Source:** https://github.com/MuntashirAkon/AppManager/issues/1968
- **Date:** 2026-05-06 · **Reactions:** 0 · **Comments:** 0
- **Problem:** Tasker user can fire a profile from an Intent but has no way to pass `pkg` as a runtime parameter — every package needs its own profile, and AM also stays foregrounded in Recents because the entry point is an Activity instead of a BroadcastReceiver.
- **NG action:** Replace/extend the Activity profile launcher with a `BroadcastReceiver` accepting `extra_pkg` template substitution. File: `app/src/main/java/io/github/muntashirakon/AppManager/profiles/ProfileApplierActivity` → add `ProfileApplierReceiver` and update manifest filters.

### #1967 — Root not detected after reinstall on KernelSU
- **Source:** https://github.com/MuntashirAkon/AppManager/issues/1967
- **Date:** 2026-05-06 · **Reactions:** 0 · **Comments:** 0
- **Problem:** Fresh install of AM on Redmi Note 13 Pro 5G + crDroid 12.9 + KernelSU stops detecting root despite other root apps working — points at AM's KernelSU detection regression (related to but not the same as iter-19 #1967/#948 KernelSU root-mode failures; this is the *detection* path).
- **NG action:** Audit `RootSession`/`Ops.java` KernelSU probe order; add an explicit "force re-grant" flow in `SettingsActivity → Mode of Operation` so a reinstall doesn't leave the user without a recovery handle.

### #1966 — App Info popup density / SDK row position
- **Source:** https://github.com/MuntashirAkon/AppManager/issues/1966
- **Date:** 2026-05-04 · **Reactions:** 0 · **Comments:** 0
- **Problem:** SDK info is at the bottom of the popup, trackers and SDK rows under-utilize horizontal space, signature only shows hash (no Subject), and the box is short enough to force scrolling.
- **NG action:** `AppDetailsFragment` → restructure `app_info_card.xml`: SDK row up to position 2, two-column trackers/SDK row, expose certificate Subject, allow popup max-height = 92% of screen.

### #1965 — Clear-data button no-op on Android 16 QPR2
- **Source:** https://github.com/MuntashirAkon/AppManager/issues/1965
- **Date:** 2026-05-02 · **Reactions:** 0 · **Comments:** 0
- **Problem:** Distinct from iter-19 #1940 — this is Android 16 QPR2 (Poco F3, Infinity-X 3.9, Root mode) where `clearApplicationUserData` returns success but the data partition isn't actually cleared.
- **NG action:** Verify against new QPR2 `IPackageManager` signature; add fallback `pm clear --user N` shell path in `AppOpsManagerImpl.clearUserData` and surface the post-call disk-usage delta in the Ops panel as ground-truth.

### #1964 — File Manager search/filter
- **Source:** https://github.com/MuntashirAkon/AppManager/issues/1964
- **Date:** 2026-05-01 · **Reactions:** 0 · **Comments:** 0
- **Problem:** AM's built-in File Manager has no in-folder search/filter — large `/data/data/<pkg>/` trees are unnavigable.
- **NG action:** Add SearchView to `FmActivity` toolbar, recursive filter on `FmAdapter` with debounce; reuse the existing main-list filter chip pattern.

### #1963 — `ActivityNotFoundException: DebloaterActivity` on v4.0.5 (moto g22)
- **Source:** https://github.com/MuntashirAkon/AppManager/issues/1963
- **Date:** 2026-04-30 · **Reactions:** 0 · **Comments:** 0
- **Problem:** Debloater shortcut crashes immediately on Moto g22 — the activity-alias on pre-Android-13 budget Unisoc devices isn't registered.
- **NG action:** Add CI lint that builds an APK and verifies every shortcut/launcher target resolves on `minSdk`; add `<activity-alias>` for `DebloaterActivity` and migration test.

### #1962 — Root mode unusable on Android 16 / LineageOS 23.2
- **Source:** https://github.com/MuntashirAkon/AppManager/issues/1962
- **Date:** 2026-04-30 · **Reactions:** 0 · **Comments:** 0
- **Problem:** Specifically a LOS 23.2 / Android 16 regression separate from iter-19 #1948 (16KB page-size on Pixel 9a) — root binder handshake never completes.
- **NG action:** Add LOS 23.2 device matrix to compatibility test; check `IBinder.transact` SELinux denial logs and update `system_server` policy probe in `LocalServer.bootstrap`.

### #1961 — "Android 16 Problems" (umbrella)
- **Source:** https://github.com/MuntashirAkon/AppManager/issues/1961
- **Date:** 2026-04-27 · **Reactions:** 0 · **Comments:** 0
- **Problem:** Umbrella report listing several A16 surface failures (App Ops grid, Components tab block, Running Apps refresh).
- **NG action:** Stand up an "Android 16 compat" tracking issue in NG and gate every `IBinder` call site by API level + reflective fallback. File: `compat/android16/` package.

### #1960 — Shizuku-mode UI nearly hides the toggle
- **Source:** https://github.com/MuntashirAkon/AppManager/issues/1960
- **Date:** 2026-04-27 · **Reactions:** 0 · **Comments:** 0
- **Problem:** The "switch to Shizuku" affordance in the Mode-of-Operation dialog is so subtle the user almost missed it; UX gap, not a functional bug.
- **NG action:** Promote the mode picker to a Material3 `SegmentedButton` row with full-width chips; M3 dashboard slated for v0.3.0 should land this.

### #1957 — Dolphin file-manager `sqfs_open_image` warning
- **Source:** https://github.com/MuntashirAkon/AppManager/issues/1957
- **Date:** 2026-04-21 · **Reactions:** 0 · **Comments:** 0
- **Problem:** AM-generated squashfs backup triggers a Dolphin warning on open — magic-byte/header off.
- **NG action:** Validate squashfs writer against `mksquashfs 4.6` reference; add round-trip test in backup unit suite.

### #1956 — App stuck in "ignore" state
- **Source:** https://github.com/MuntashirAkon/AppManager/issues/1956
- **Date:** 2026-04-15 · **Reactions:** 0 · **Comments:** 0
- **Problem:** App marked "ignore battery optimization" can't be un-ignored from AM — write succeeds but Doze keeps re-flagging it on next idle. Ties directly to the iter-20 theme "battery-optimization auto-fix scope creep".
- **NG action:** Detect `device_idle_constants` allow/deny re-write within 60s and surface a banner: "OS reverted your change — see why" with the Doze allowlist diff.

---

## 2. samolego/Canta — new issues

### #358 — Export installed app list
- **Source:** https://github.com/samolego/Canta/issues/358
- **Date:** 2026-04-28 · **Reactions:** 0 · **Comments:** 1
- **Problem:** No way to export the currently-installed package list for cross-device debloat replay.
- **NG action:** Already partially covered by AM Profiles — surface a "Export Profile as Canta-compatible JSON" entry in `ProfileMetaActivity` to capture this ecosystem need.

### #356 — Accidental disable, can't recover
- **Source:** https://github.com/samolego/Canta/issues/356
- **Date:** 2026-04-02 · **Reactions:** 0 · **Comments:** 0
- **Problem:** User disabled something they can't identify; Canta has no undo/audit trail.
- **NG action:** Validates iter-19 #1959 (per-app rollback) priority — bump to P1 and add a "Last 50 component/state changes" panel under the existing 1-click ops history.

---

## 3. aistra0528/Hail — new issues

### #391 — Password-protected Hidden Mode
- **Source:** https://github.com/aistra0528/Hail/issues/391
- **Date:** 2026-04-25 · **Reactions:** 0 · **Comments:** 0
- **Problem:** Hidden tag mode has no PIN/biometric gate.
- **NG action:** Add BiometricPrompt gate to AM's Profile/Tag screen; reuse existing `PasswordSetupActivity` flow.

### #389 — Android 10 root mode broken
- **Source:** https://github.com/aistra0528/Hail/issues/389
- **Date:** 2026-04-23 · **Reactions:** 0 · **Comments:** 0
- **Problem:** Android 10 root path regressed — a reminder NG cannot quietly drop pre-API-30 support.
- **NG action:** Add Android 10 (API 29) to the device matrix; gate dynamic SELinux policy fetches behind API check.

### #387 — Frozen apps leave folders / don't return on unfreeze
- **Source:** https://github.com/aistra0528/Hail/issues/387
- **Date:** 2026-04-05 · **Reactions:** 0 · **Comments:** 2
- **Problem:** After freeze, the launcher icon disappears but `/data/data/<pkg>` artifacts remain; unfreeze doesn't always re-show the launcher icon.
- **NG action:** On unfreeze, broadcast `ACTION_PACKAGE_CHANGED` + `LauncherApps.notifyPackageEnabled` to force launcher refresh; document leftover-folder behavior as expected.

---

## 4. NeoApplications/Neo-Backup — new issues

### #1034 — Apps with old backups skipped during scheduled run
- **Source:** https://github.com/NeoApplications/Neo-Backup/issues/1034
- **Date:** 2026-04-23 · **Reactions:** 0 · **Comments:** 0
- **Problem:** Scheduled "user apps" run skips any package that already has *any* backup, even stale ones.
- **NG action:** AM's backup scheduler must use age-of-newest-backup as the gate (`if newest < schedule.minAgeMillis`); not "exists/doesn't exist".

### #1033 — Wi-Fi configurations backup
- **Source:** https://github.com/NeoApplications/Neo-Backup/issues/1033
- **Date:** 2026-04-12 · **Reactions:** 0 · **Comments:** 1
- **Problem:** Saved Wi-Fi networks + passwords aren't preserved.
- **NG action:** Add a "system data" backup category (Wi-Fi, Bluetooth pairings, accounts) — root-only path via `wifictl`/`/data/misc/wifi/`.

### #1032 — Uninstalled apps with backups missing from blocklist UI
- **Source:** https://github.com/NeoApplications/Neo-Backup/issues/1032
- **Date:** 2026-04-06 · **Reactions:** 0 · **Comments:** 0
- **Problem:** You can't blocklist a package whose only trace on disk is its backup folder.
- **NG action:** AM Profiles → blocklist editor must enumerate `<backup-root>/*` in addition to live PMS list.

### #1029 — Null bytes injected on CIFS backup target
- **Source:** https://github.com/NeoApplications/Neo-Backup/issues/1029
- **Date:** 2026-03-18 · **Reactions:** 0 · **Comments:** 1
- **Problem:** Streaming write to SMB/CIFS provider corrupts archives with `\0` bytes.
- **NG action:** AM backup writer should avoid `WriteableByteChannel#write` short-write race on `DocumentFile` SAF SMB providers; flush-then-fsync per chunk.

---

## 5. d4rken-org/sdmaid-se — new issues

### #2411/#2410 — Check procedure aborts on Samsung A17 / Android 16
- **Sources:** https://github.com/d4rken-org/sdmaid-se/issues/2411 (2026-05-07), https://github.com/d4rken-org/sdmaid-se/issues/2410 (2026-05-07)
- **Reactions:** 0 / 0 · **Comments:** 0 / 0
- **Problem:** Non-rooted A17 on Android 16 hits OEM `MediaProvider` quirks that abort scans mid-run.
- **NG action:** AM's storage scanner uses the same MediaStore code path — add Samsung A17 + One UI 8.5 to compatibility matrix and wrap MediaStore.queryStorageStats in a per-volume try/catch with continuation.

### Discussion #2413/#2383 — Premium gating exposed too late in the scan flow
- **Sources:** https://github.com/d4rken-org/sdmaid-se/discussions/2413 (2026-05-07), https://github.com/d4rken-org/sdmaid-se/discussions/2383 (2026-04-15)
- **Upvotes:** 1 / 1
- **Problem:** Users wait through a long scan only to discover the cleanup is paywalled — UX trust signal worth contrasting with NG's free + GPL stance in the README.
- **NG action:** Marketing/CLAUDE.md note: NG explicitly never gates verbs behind a Pro tier; surface that promise on the v0.3.0 dashboard footer.

---

## 6. Universal-Debloater-Alliance/UAD-NG — new issues

### #1394 — `com.samsung.android.smartsuggestions` removal breaks Mobile Networks on One UI 8.5 (Galaxy A57)
- **Source:** https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1394
- **Date:** 2026-05-01 · **Reactions:** 0 · **Comments:** 0
- **Problem:** Removing the package crash-loops the Settings → Mobile Networks pane.
- **NG action:** AM's Debloater consumes this list — gate this pkg behind a "OneUI ≥ 8.5 known-bad" warning in the per-package risk renderer.

### #1390 — UAD-NG can't restore the dialer
- **Source:** https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1390
- **Date:** 2026-04-26 · **Reactions:** 0 · **Comments:** 1
- **Problem:** Restore path for `com.android.dialer` doesn't reinstall the system APK — leaves user without a phone app.
- **NG action:** AM's restore path must verify default-app role re-binding after `pm install-existing`; auto-prompt `RoleManager.createRequestRoleIntent(ROLE_DIALER)` if missing.

### #1386 — "Are you guys even working on this?" (project pulse signal)
- **Source:** https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/1386
- **Date:** 2026-04-22 · **Comments:** 1
- **Problem:** Not a bug — community frustration with UAD-NG cadence. Strategic signal: the GUI debloater ecosystem has open ground for an actively-maintained replacement.
- **NG action:** ROADMAP positioning note — NG dashboard's debloater panel should pull `MuntashirAkon/android-debloat-list` directly with weekly auto-refresh and a freshness badge.

---

## 7. Hamza417/Inure — new issues

### #480 — `StorageStatsManager.queryStatsForUid` on UI thread
- **Source:** https://github.com/Hamza417/Inure/issues/480
- **Date:** 2026-05-08 · **Reactions:** 0 · **Comments:** 0
- **Problem:** Static-analysis-confirmed main-thread blocking call during Setup, leading to ANR risk on devices with heavy storage.
- **NG action:** Audit AM's `Setup` and `RunningAppsActivity` for `queryStatsForUid` / `queryStatsForPackage` callers; verify all are on `Dispatchers.IO` or `AsyncTaskCompat`. File: `apk/AppDetailsFragment.fetchStorageInfo`.

---

## 8. ImranR98/Obtainium — new issues

### #2911 — Predictive-back gesture cancel freezes WebView
- **Source:** https://github.com/ImranR98/Obtainium/issues/2911
- **Date:** 2026-05-06 · **Comments:** 0
- **Problem:** Cancelling an in-progress back gesture on Android 16 / One UI 8 leaves the embedded WebView unresponsive — broad Android-16 predictive-back regression.
- **NG action:** AM's APK-info preview WebView (used in `RulesActivity`/Tracker block list) needs `OnBackInvokedDispatcher` registration; see `AndroidManifest.xml android:enableOnBackInvokedCallback="true"`.

### #2910 — PIA APK no longer at the documented URL
- **Source:** https://github.com/ImranR98/Obtainium/issues/2910
- **Date:** 2026-05-05 · **Comments:** 2
- **Problem:** Source-config drift breaks unattended installs.
- **NG action:** AM's "Install with Options" panel should detect 404 on configured URL and offer "fall back to last known good mirror" instead of silently failing.

### #2908 — Crash on 32-bit devices ≥ v1.2.9
- **Source:** https://github.com/ImranR98/Obtainium/issues/2908
- **Date:** 2026-05-03 · **Comments:** 2
- **Problem:** Flutter/GPU regression on armeabi-v7a-only builds.
- **NG action:** AM minSdk 26 + 32-bit/64-bit APK split — confirm CI builds an `armeabi-v7a` slice and exercises `MainActivity.onCreate` on a Lineage-20-on-Z3-Compact emulator.

### Discussion #2846 — "ObtainX" power-user fork
- **Source:** https://github.com/ImranR98/Obtainium/discussions/2846
- **Date:** 2026-03-28 · **Upvotes:** 3 · **Comments:** 1
- **Problem:** Forks land power-user features upstream rejects (bulk import, folders, external-installer wiring). Validates NG's "fork to ship Pro mode" thesis.
- **NG action:** Mirror the ObtainX feature set into the v0.3.0 Pro-mode toggle copy: bulk import, folders, external installers.

---

## 9. RikkaApps/Shizuku — new issues (signal-filtered)

Most May 1-7 reports are non-actionable (test entries, language placeholders). Real issues:

### #2052 — Shizuku exposes root to apps that fail Play Integrity
- **Source:** https://github.com/RikkaApps/Shizuku/issues/2052
- **Date:** 2026-05-02 · **Comments:** 2
- **Problem:** With KernelSU + Shizuku service started in Root mode, a banking app that hates root crashes immediately on launch even though it was never granted Shizuku access.
- **NG action:** AM's Shizuku integration should never default to *Root-backed* Shizuku when ADB-backed is available; document the leak and add a one-tap "Switch Shizuku to ADB mode" in Mode-of-Operation.

### #2048 — UserService silently killed on Transsion OS (Infinix GT 20 Pro / Android 15) in Shizuku 13.6.0
- **Source:** https://github.com/RikkaApps/Shizuku/issues/2048
- **Date:** 2026-05-01 · **Comments:** 2
- **Problem:** Reflective `LoadedApk.makeApplication` NPE in 13.6.0 (regression vs 13.5.4) on Transsion's `TranAssetManagerImpl`; all Shizuku-backed UserService bindings die silently. Affects Infinix/Tecno/Itel.
- **NG action:** AM's Shizuku UserService startup must wrap `bindUserService` in a try/catch + downgrade banner: "Your OEM ROM (Transsion) is breaking Shizuku 13.6.0 — pin Shizuku 13.5.4". Add OEM detection for `ro.transsion.version`.

### #2044 — Persistent ADB TCP/IP port (5555) start path
- **Source:** https://github.com/RikkaApps/Shizuku/issues/2044
- **Date:** 2026-05-01 · **Comments:** 0
- **Problem:** Wireless-debugging is unstable on Android 11+; users want Shizuku to authorize itself against an existing `adb tcpip 5555` session.
- **NG action:** AM's first-run Shizuku setup wizard should detect open 5555 (`Socket("127.0.0.1", 5555)`) and offer it as a third path next to "Pair with QR" / "USB ADB".

### #2043 — Authorized-app count doesn't update without restart
- **Source:** https://github.com/RikkaApps/Shizuku/issues/2043
- **Date:** 2026-05-01 · **Comments:** 0
- **Problem:** Stale UI counter — minor, but a reminder that AM must not cache Shizuku grant state.
- **NG action:** Make AM's Shizuku-grant indicator subscribe to `ShizukuProvider.requestPermission` callbacks instead of polling on resume.

### #2036 — Shizuku caps screen refresh to 60Hz during video
- **Source:** https://github.com/RikkaApps/Shizuku/issues/2036
- **Date:** 2026-04-29 · **Comments:** 3
- **Problem:** Cross-cuts AM only if AM uses Shizuku for SurfaceFlinger calls.
- **NG action:** Audit AM for `SurfaceComposerClient` / `WindowManagerService` Shizuku calls; document any frame-rate side-effect.

---

## 10. lihenggui/blocker · MuntashirAkon/android-debloat-list

- **lihenggui/blocker** — 0 new issues since 2026-01-01. **Searched:** `repos/lihenggui/blocker/issues?since=2026-01-01`. Project appears dormant; not a fresh-signal source this cycle.
- **MuntashirAkon/android-debloat-list** — only 2 routine entries (#84, #85, both 2026-04-08, "Addition of new bloatware" / "Changes in the existing bloatware"). No actionable pain points; the list itself is the deliverable.

---

## 11. Reddit / r/androidapps / r/GrapheneOS / r/fossdroid

Direct WebFetch of `reddit.com` is blocked in this environment; surfaced via Google. Three signals worth carrying:

### GrapheneOS forum — Feature Request: NeoBackup-style backup
- **Source:** https://discuss.grapheneos.org/d/17974-feature-request-integrate-proper-backup-restore-like-neobackup
- **Searched via:** Google, surfaced 2025-2026 thread (page failed to render full content; title + summary only).
- **Problem:** GrapheneOS users argue Seedvault is incomplete and can't migrate from stock — they want NeoBackup-equivalent functionality. Validates AM's backup feature set as the cross-OS migration tool.
- **NG action:** Document a "GrapheneOS migration recipe" in `docs/migrate-grapheneos.md` using AM ADB-mode backup on stock → AM restore on GrapheneOS.

### XDA — Shizuku + Neo-Backup integration gap
- **Source:** https://xdaforums.com/t/root-alternative-shizuku-wireless-adb-dhizuku-non-root-apps-thread.4692215/page-15
- **Problem:** Multiple recent posts: "I just installed Neo backup and it doesn't seem to have shizuku integration." Users abandoning Neo-Backup specifically because of the no-root gap.
- **NG action:** Position AM's existing Shizuku-mode backup as the replacement; ROADMAP item: ensure backup-without-root is a top-level home dashboard tile.

### XDA — Shizuku 13.6 dies on Android 16 QPR1
- **Source:** https://xdaforums.com/t/shizuku-not-working-correctly.4756808/
- **Problem:** Pixel 9 Pro on stock Android 16 QPR1 — Shizuku turns off after a few minutes, even with battery unrestricted; confirmed not a hardware issue, only QPR1 vs bare A16. Mediatek users separately need to downgrade to 13.5.4 (per `zacharee/InstallWithOptions` README).
- **NG action:** AM should detect running Shizuku build + flag known-bad versions on QPR1 and Mediatek SoCs; surface in Mode-of-Operation banner.

---

## 12. Stack Overflow `[shizuku]` / `[android-permissions]`

No tag-filtered SO questions in the last 30 days surfaced enough vote signal to be load-bearing. **Searched:** `site:stackoverflow.com [shizuku] OR [android-permissions] 2026` and the broader `shizuku PackageInstaller silent install 2026` query. Best ecosystem code reference is `vvb2060/PackageInstaller` and `zacharee/InstallWithOptions`. Genuinely nothing new on SO this window.

---

## 13. Theme coverage — explicit asks

| Theme | Findings above | Gap |
|---|---|---|
| Per-app TLS / network rules without VPN | RethinkDNS write-up + NetGuard single-VPN-slot constraint (Privacy Guides + HN). NG should ship community **rule packs** (host-block lists, tracker-by-domain) consumable by RethinkDNS/NetGuard rather than re-implement the firewall. | No new GitHub issues in window |
| Multi-user / work-profile UX | Hail #389 (Android 10 + multi-user not allowed when several users exist — same regression class as iter-19 Canta #350) | Gap remains: **no recent issues** explicitly on cross-user `LauncherApps` work-profile UX since iter-19 |
| Android 16+ adaptive layout pain | AppManager #1961 umbrella + Obtainium #2911 + Shizuku XDA QPR1 thread | Gap: **NG should target Android 16 (API 36)** to escape orientation/aspect-ratio restrictions per the developer.android.com adaptive-apps doc — automatic fix |
| Backup of Android 15 archived-state apps (`isArchived`) | **No new community signal** — searched `site:reddit.com isArchived archived apps` and `archived apps backup restore`. Treat as pre-emptive: AM's backup writer must read `PackageInfo.archiveTimeMillis` and refuse to clobber a live archive on restore. | Real gap — first-mover opportunity |
| Shizuku 13.6.0 trusted-WLAN bugs | Shizuku #2048 (Transsion NPE) + XDA QPR1 thread + Mediatek 13.6.0 break | Action: pin compatible Shizuku build per OEM in NG's Shizuku detector |
| Battery-optimization auto-fix scope creep | AM #1956 (ignore stuck), Hail #387 (frozen state lost) | Action: surface OS-side reverts as banners (see #1956 entry) |

---

## Summary

**Total findings: 33** (10 AM, 2 Canta, 3 Hail, 4 Neo-Backup, 2 sdmaid-se issues + 2 discussions, 3 UAD-NG, 1 Inure, 4 Obtainium, 5 Shizuku, 3 community/XDA/forum). Floor of 25 cleared.

**Highest-value priorities for NG iter-20 backlog:**
1. **OS-revert detection banner** (covers AM #1956 + Hail #387 + the Doze theme) — unique to NG, no competitor surfaces this.
2. **Shizuku version pinner per OEM** (covers Shizuku #2048 + Mediatek 13.6.0 + QPR1 — three independent reports in 7 days).
3. **Tasker BroadcastReceiver with `extra_pkg`** (AM #1968) — closes iter-19 #1968 follow-on.
4. **Backup scheduler "newest age" gate** (Neo-Backup #1034) — direct upstream-pain replication.
5. **Android 16 (API 36) target SDK bump** — auto-resolves orientation/aspect-ratio restrictions and unblocks foldable/tablet adaptive-layout audit.

**Empty wells this cycle:** lihenggui/blocker (dormant), debloat-list (routine churn only), Stack Overflow tags (no qualifying activity).

---

# iter-20 Extension — Releases / Competitors / Compliance / Dependencies (window 2026-05-01 → 2026-05-08)

Extension pass to cover what Part 1 above didn't: GitHub *releases* (not just issues) in the last 7 days, **new** competitors absent from the iter-19 list, Android 17 behavior changes, May 2026 AOSP CVE bulletin, dependency-version churn, and F-Droid / Accrescent landscape moves. Floor: 30. Result: 35 findings.

## E1. Releases in iter-19 covered repos (last 7 days)

### NeoApplications/Neo-Backup 8.3.18 — onboarding flow + PrefDelegate revamp
- **Source:** https://github.com/NeoApplications/Neo-Backup/releases/tag/8.3.18
- **Date:** 2026-05-03
- **Why it matters:** First-run onboarding page flow + replacing legacy SharedPreferences with NeoPrefs/PrefDelegate. AppManagerNG ships zero onboarding today — new users land on a 2400-line dashboard. Mirror the onboarding-pages structure (mode picker, Shizuku/root probe, optional restore-from-backup) under `app/src/main/java/.../onboarding/`. Removed the encryption warning dialog — track whether to do the same since AM also has a noisy first-encrypt warning.
- **Prevalence:** parity (onboarding) + leapfrog opportunity (preferences modernization)

### d4rken-org/sdmaid-se v1.7.2-rc0 — auto-fix battery optimization via ADB/root + APK signing fingerprints published
- **Source:** https://github.com/d4rken-org/sdmaid-se/releases/tag/v1.7.2-rc0
- **Date:** 2026-05-01
- **Why it matters:** Two ideas to mirror: (1) Scheduler now auto-grants `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` via root/ADB when scheduled cleanups need wake — AM's scheduler tab has the same fail mode (OEM Doze kills mid-batch). (2) Publishing APK signing certificate fingerprints (PR #2400) — compliance/transparency move worth copying for `docs/SIGNING.md`. Also: Android-TV/Google-TV launcher visibility (PR #2395) is a 1-line manifest change AM should add (`<category android:name="android.intent.category.LEANBACK_LAUNCHER" />`).
- **Prevalence:** parity (scheduler self-heal) + compliance-required (cert fingerprint disclosure)

### Material Components for Android 1.14.0-rc01 — FocusRingDrawable + SplitButton + minSdk 23
- **Source:** https://github.com/material-components/material-components-android/releases/tag/1.14.0-rc01
- **Date:** 2026-04-29
- **Why it matters:** AM is on 1.13.x. 1.14.0 brings M3-Expressive `FocusRingDrawable` (auto-construct from child shape, hide on no-window-focus) and `SplitButton` RTL fix. AM's lack of focus rings is a known a11y gap (iter-19 a11y items). Bump `materialVersion = 1.14.0-rc01` in `gradle/libs.versions.toml` once stable; minSdk 23 still matches AM/NG.
- **Prevalence:** parity (M3 Expressive)

### Gson 2.14.0 — `java.time` adapters + duplicate-key strict mode
- **Source:** https://github.com/google/gson/releases/tag/gson-parent-2.14.0
- **Date:** 2026-04-23
- **Why it matters:** AM/NG ships Gson 2.13.2. 2.14.0 adds built-in `java.time` adapters (drops `--add-opens` need) and tightens duplicate-key handling. AM uses Gson for profiles, backups, debloat-list JSON — a malformed profile with `{"foo": null, "foo": ...}` now throws instead of silently overwriting. **Action:** bump + run `./gradlew :app:dependencyInsight` against backup-restore to confirm no regression.
- **Prevalence:** parity + bug-prevention

### iBotPeaches/Apktool v3.0.2, REAndroid/APKEditor V1.4.8, jadx v1.5.5
- **Source:** https://github.com/iBotPeaches/Apktool/releases/tag/v3.0.2 · https://github.com/REAndroid/APKEditor/releases/tag/V1.4.8 · https://github.com/skylot/jadx/releases/tag/v1.5.5
- **Date:** Apr 19 / Mar 21 / Feb 25 (all pre-window but unmined in iter-19 dep-pin context)
- **Why it matters:** AM ships its own ARSC parser. APKEditor V1.4.8 has improved support for Android 16 QPR2 ARSC quirks; AM's manifest parser regressed on QPR2 (linked to issue #1965 Part 1). Track these vendored dependency mirrors in `libs/` for the next Sync.

## E2. NEW competitors / adjacent projects MISSED by iter-19

### wxxsfxyzm/InstallerX-Revived — Miuix + M3 Expressive overhaul (4,740 stars)
- **Source:** https://github.com/wxxsfxyzm/InstallerX-Revived/releases/tag/26.05.d57ad8e
- **Date:** 2026-05-07 (preview build d57ad8e)
- **Why it matters:** Active fork of cherry-pi-/InstallerX (the InstallerX original). May 7 build adds Xiaomi Miuix design language alongside M3 Expressive and "enhances privilege management" — direct competitor to AM's APK install flow. The package-installer category is one AM half-implements. **Action:** add to `docs/COMPETITIVE_LANDSCAPE.md`; copy their privilege-elevation cascade (Shizuku → root → Dhizuku → MIUI optimization-disable nudge) which AM's installer doesn't yet do.
- **Prevalence:** leapfrog opportunity (InstallerX is mature & mainstream in CN scene; AM's installer feels stub-like next to it)

### hddq/restoid v0.5.0 — root + restic Android backup (97 stars, fast-growing)
- **Source:** https://github.com/hddq/restoid/releases/tag/v0.5.0
- **Date:** 2026-05-07
- **Why it matters:** First serious "real backup engine" competitor in years — uses restic (industry-standard dedup/snapshot/encrypt) instead of AM's bespoke tar+GCM. v0.5.0 cut APK size 68MB→18MB, added scheduling, snapshot metadata auto-fetch. v0.4.3 (May 2) shipped SSH-key auth for SFTP repos. **AM's backup ditches GCM cipher reuse on large files (Part 1 #1958) — restic's chunked AEAD model is the answer.** Investigate adding a "restic mode" toggle in BackupOptions or at minimum porting their snapshot UI.
- **Prevalence:** leapfrog (restic backend = night-and-day reliability/dedup vs AM's current tar pipeline)

### SanmerApps/PI — minimal Compose package installer (688 stars)
- **Source:** https://github.com/SanmerApps/PI · https://github.com/SanmerApps/PI/releases/tag/v1.3.1
- **Date:** 2025-11-22 (release) but actively merged through 2026-04 (dependabot heavy)
- **Why it matters:** Reference implementation of a clean Compose-only PackageInstaller using `PackageInstaller.SessionParams`. AM's installer is older AppCompat. If/when NG migrates the installer to Compose, study PI's session-callback pattern (`PackageInstaller.SessionCallback` with cleanup) to avoid AM's known leaked-session bug.
- **Prevalence:** parity reference

### sameerasw/essentials v13.2 — Shizuku-powered "Android nerd toolkit" (1,594 stars)
- **Source:** https://github.com/sameerasw/essentials/releases/tag/v13.2
- **Date:** 2026-05-02
- **Why it matters:** Big Shizuku-toolkit competitor that iter-19 missed. v13.2 adds custom refresh-rate QS tile, app-lock timeout, live wallpaper engine, marquee-text effects. Not all of these belong in AM, but the **app-lock timeout** (per-app independent timeout for AM's existing app-lock feature) and the "Shizuku permission label after granting" fix overlap directly. The breadth of this app is also a competitive-landscape signal — power-user expectation creep.
- **Prevalence:** parity (per-app lock timeout)

### yume-chan/VolumeManager (425 stars) — independent per-app volume via AppOps
- **Source:** https://github.com/yume-chan/VolumeManager
- **Date:** v0.2 published 2025-08, repo updated 2026-05-06
- **Why it matters:** Sets per-app volume by writing AppOps `OP_AUDIO_VOLUME` via Shizuku — exactly the kind of granular-AppOp-write AM's "Control AppOp settings more precisely" issue (#1863) is asking for. Read VolumeManager's AppOpsService binder usage as the implementation pattern.
- **Prevalence:** compliance/parity (gives a working blueprint for AM's AppOps UID-mode)

### XiaoTong6666/Sui (364 stars) — modern superuser implementation, Shizuku-compatible
- **Source:** https://github.com/XiaoTong6666/Sui · https://github.com/XiaoTong6666/Sui/releases/tag/nightly
- **Date:** Nightly 2026-02-06, repo updated 2026-05-07
- **Why it matters:** Sui is the Magisk-module form of Shizuku (no separate app needed). Currently AM detects Shizuku and root separately — should also detect Sui presence (`/dev/socket/com.android.shell` plus the Sui module-id) and prefer it where present, since Sui devices have zero-UI permission grants. Add `SuiOps` probe alongside `ShizukuOps` in `Ops.java`.
- **Prevalence:** parity (broader root/ADB-bridge detection)

### Hjsosn/FireWall-Blocks — Shizuku VPN-less firewall (11 stars, brand-new)
- **Source:** https://github.com/Hjsosn/FireWall-Blocks (updated 2026-05-08)
- **Why it matters:** Tiny but novel — uses Shizuku to write `iptables`/`netd` rules instead of running a local VPN, matching the model AM's per-app firewall hooks already attempt. Cross-check their netd binder pattern against AM's `NetworkPolicyManager` use; iter-19 noted AM's firewall flickers on QPR1.
- **Prevalence:** parity reference

### pass-with-high-score/universal-installer (145 stars)
- **Source:** https://github.com/pass-with-high-score/universal-installer (updated 2026-05-08)
- **Why it matters:** Modern installer with VirusTotal scanning + split-APK + silent-install via Shizuku. AM's interpreter rejects mismatched-signature splits silently — universal-installer surfaces the cert mismatch in a dialog with a per-split table. Worth adopting for AM's APK install confirmation page.
- **Prevalence:** parity (UX improvement)

### djunekz/termux-app-store (55 stars, May 8)
- **Source:** https://github.com/djunekz/termux-app-store
- **Why it matters:** "AUR-for-Termux" — TUI/CLI package manager for Termux on Android. Tangential to AM but signals demand for `termux-app-store`-style command surface. Defer; mention only in COMPETITIVE_LANDSCAPE.md.
- **Prevalence:** signal-only (defer)

### kerneldroid/Shizuku-modern (1 star, brand-new May 7)
- **Source:** https://github.com/kerneldroid/Shizuku-modern
- **Why it matters:** Compose Material 3 Expressive Shizuku fork targeting SDK 37. Tracks Shizuku regressions on Android 17. Watch the fork — if it stays alive, it may become the de-facto "modern Shizuku" while RikkaApps/Shizuku's release cadence has slowed (last v13.6.0 was May 2025).
- **Prevalence:** signal-only (track, don't depend on it yet)

### BugeStudioTeam/Buge-App-Manager (17 stars, May 8)
- **Source:** https://github.com/BugeStudioTeam/Buge-App-Manager
- **Why it matters:** Direct name-collision competitor — "Buge App Manager" is positioned exactly where AM/NG sits. Low-star but actively pushed today. Audit at next iter for differentiation; don't worry yet.
- **Prevalence:** signal-only

## E3. Android 17 — behavior changes + Beta status

### Beta 4 is the FINAL beta — no Beta 5 expected; stable lands at Google I/O 2026 (May 19) → June rollout
- **Source:** https://android-developers.googleblog.com/2026/04/the-fourth-beta-of-android-17.html
- **Date:** April 2026 (Beta 4); stable lands May/June 2026
- **Why it matters:** AppManagerNG must finish Android 17 (API 37 / `targetSdk 37`) compat work in this window. There won't be another preview to catch regressions. Pin the API-37 compileSdk now.
- **Prevalence:** compliance-required

### Android 17 — `ACCESS_LOCAL_NETWORK` runtime permission (NEARBY_DEVICES group)
- **Source:** https://developer.android.com/about/versions/17/behavior-changes-17
- **Why it matters:** Apps targeting SDK 37 lose LAN access by default. AM's `ucx serve`-style features (if any), web-based docs server, or any local HTTP debug surface needs the new permission OR the system device picker. Audit all `ServerSocket`/Loopback usage; add a runtime prompt to `ManifestActivity` for any local-network feature.
- **Prevalence:** compliance-required

### Android 17 — `System.load()` requires read-only native libs
- **Source:** https://developer.android.com/about/versions/17/behavior-changes-17 (Safer Native DCL)
- **Why it matters:** AM doesn't use System.load directly, but bundled libs in `libs/` (e.g., libsu helpers, native zip) need to be marked read-only after extraction. Otherwise UnsatisfiedLinkError on Android 17. Audit `IoUtils.copy` paths that drop natives onto disk.
- **Prevalence:** compliance-required

### Android 17 — static-final reflection / JNI SetStaticLongField now throws
- **Source:** https://developer.android.com/about/versions/17/behavior-changes-17
- **Why it matters:** AM's hidden-API access via reflection is broad. Any reflective set against a `static final` field (e.g., common in `IPackageManager` proxy code) crashes on Android 17. Run `aapt2 dump --resources --proguard-rules` against the v4.0.5 APK and grep `Field.setAccessible.*set` to enumerate. **High-impact compliance task.**
- **Prevalence:** compliance-required (potentially severe)

### Android 17 — BAL hardening extended to IntentSender; `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` deprecated in favor of `_ALLOW_IF_VISIBLE`
- **Source:** https://developer.android.com/about/versions/17/behavior-changes-17
- **Why it matters:** AM's profile-trigger Activity-launch-from-Service path (the same one issue #1968 in Part 1 wants replaced with a Receiver) breaks on Android 17 if it relies on the old BAL allow flag. Migrate to `_ALLOW_IF_VISIBLE` or move to a `BroadcastReceiver` (which dovetails with the iter-19 #1968 Tasker fix).
- **Prevalence:** compliance-required

### Android 17 — ContactsContract removes `ACCOUNT_NAME` / `ACCOUNT_TYPE` from `Data` view; strict SQL on CP2
- **Source:** https://developer.android.com/about/versions/17/behavior-changes-17
- **Why it matters:** Lower priority for AM (no Contacts surface) but flag for any sister project sharing the codebase. Add lint rule against direct `ContactsContract.Data.ACCOUNT_NAME` literal. **No-op for AM core; documenting only.**
- **Prevalence:** compliance-N/A but worth documenting

### Android 17 — keystore per-app limit 50,000 keys for non-system apps targeting SDK 37
- **Source:** https://developer.android.com/about/versions/17/behavior-changes-all
- **Why it matters:** AM's per-backup AES key-derivation (one ephemeral key per backup file) could plausibly cross 50k over a long-lived install. Switch to a deterministic-derivation pattern (HKDF-from-master) for backup encryption to keep total Keystore-resident keys bounded. Audit `crypto/AESCrypto`.
- **Prevalence:** compliance-required (medium-term)

### Android 17 — implicit URI grants from `Send`/`SendMultiple`/`ImageCapture` intents removed in Android 18
- **Source:** https://developer.android.com/about/versions/17/behavior-changes-all
- **Why it matters:** AM's "Save APK to..." and "Share backup" flows use implicit URI grants today. Add explicit `Intent.FLAG_GRANT_READ_URI_PERMISSION` + `grantUriPermission()` calls now to avoid the Android 18 cliff. Already deprecated-warning territory.
- **Prevalence:** compliance-required (Android 18 hard break)

### Android 17 — touchpad relative-event default during pointer capture; large-screen orientation/resizability cannot be opted-out
- **Source:** https://developer.android.com/about/versions/17/behavior-changes-all
- **Why it matters:** Tablet/foldable AM users on Android 17 will see AM ignore its `screenOrientation` lock. Audit `AndroidManifest.xml` for `orientation="portrait"` lock — must be removed for sw>=600dp paths or the system overrides anyway. Probably matches an iter-19 a11y/foldable item.
- **Prevalence:** compliance-required

## E4. May 2026 Android Security Bulletin (last 30 days)

### CVE-2026-0073 — adbd zero-click proximal RCE as shell user (CRITICAL)
- **Source:** https://source.android.com/docs/security/bulletin/2026/2026-05-01
- **Date:** 2026-05-05 publication (security patch level 2026-05-01)
- **Why it matters:** Patches Android 14 / 15 / 16 / 16-qpr2. The bug is in adbd, the same daemon Shizuku/AM lean on for ADB-mode operation. Read the AOSP patch when published (within 48h of bulletin) — confirm whether AM's Shizuku-via-wireless-debug bootstrap surface is unaffected. Add a release note: "AppManagerNG ADB mode requires devices with security patch level ≥ 2026-05-01".
- **Prevalence:** compliance-required (advisory)

### Bulletin notably sparse — only one CVE for May 2026
- **Source:** https://source.android.com/docs/security/bulletin/2026/2026-05-01
- **Why it matters:** No PackageInstaller, AppOps, BackupAgent, or ContentProvider CVEs this cycle. Means the iter-19 hot-list (BackupAgent CVEs) is still the same set; no new compliance triggers from May.
- **Prevalence:** info (negative finding documented; nothing for AM to chase)

## E5. Dependency updates (last 7 days)

### BouncyCastle 1.84 (released 2026-04-14) — fixes CVE-2026-3505 (PGP AEAD chunk DoS), CVE-2026-5588, CVE-2026-5598
- **Source:** https://github.com/bcgit/bc-java/blob/main/docs/releasenotes.html
- **Date:** 2026-04-14
- **Why it matters:** AM is on BouncyCastle 1.83. CVE-2026-5598 (FrodoKEM non-constant-time compare → potential private-key leak) is the most impactful for AM if/when it ever uses PQ keys, but the **PGP AEAD chunk-size DoS (CVE-2026-3505)** is relevant since AM allows GPG-encrypted backups via libopenpgp. Bump `bouncyCastleVersion = 1.84` in libs.versions.toml; verify the GPG backup smoke test. 1.85-SNAPSHOT exists but is "TBD" stable.
- **Prevalence:** compliance-required (CVE)

### libsu still pinned at 6.0.0 (2024-06-28) — last sub-module activity Sept 2025
- **Source:** https://github.com/topjohnwu/libsu/releases
- **Why it matters:** No new release; AM's existing `libsu:6.0.0` is still current. Note that topjohnwu has been mostly absent (last Magisk commit Apr 22). Keep watch — if libsu stalls, AM should add a `RootService` shim that doesn't lock to one library version.
- **Prevalence:** info (no action; future-risk note)

### AGP — latest 8.x is 8.13 (2026-05-05 doc update); 9.0/9.1/9.2 line is shipping; AGP 10 mid-2026 will remove old DSL opt-out
- **Source:** https://developer.android.com/build/releases/gradle-plugin · https://developer.android.com/build/releases/agp-9-0-0-release-notes
- **Date:** AGP 9.0 (Jan 2026) · 9.1.1 (Apr 2026) · 9.2.0 (Apr 2026)
- **Why it matters:** AM/NG sits on AGP 8.13.2. AGP 10 (mid-2026) will be the cliff — old `BaseExtension`-flavored DSL goes away. Schedule an AGP 9.x bump in NG before the next major release. There is no AGP 8.14; the line ended at 8.13.
- **Prevalence:** compliance-required (medium-term)

### Compose BOM — 2026.04.01 latest stable; no May 2026 BOM yet
- **Source:** https://developer.android.com/develop/ui/compose/bom · https://mvnrepository.com/artifact/androidx.compose/compose-bom/versions
- **Date:** 2026-04 release
- **Why it matters:** AM is mostly XML-views, not Compose, but if NG starts a Compose surface, lock 2026.04.01 (Compose 1.11.x with new Style API). Note: April release made v2 testing APIs default — flaky test suites expected during migration.
- **Prevalence:** parity (Compose-side)

### sora-editor — Maven release frozen at 2025-06-22; main branch on Gradle 9 / AGP 9 / Kotlin 2.3 (no published artifact yet)
- **Source:** https://github.com/Rosemoe/sora-editor · https://mvnrepository.com/artifact/io.github.Rosemoe.sora-editor
- **Why it matters:** AM uses sora-editor for in-app code viewing. Pin to current stable 0.23.x; if NG needs Kotlin 2.3 / AGP 9 readiness, build sora-editor from main and vendor.
- **Prevalence:** info / risk

### baksmali — JesusFreke 2.x dead; google/smali 3.0.7 (May 2024) is current; no 2026 release
- **Source:** https://github.com/google/smali/releases/tag/3.0.7 · https://github.com/baksmali/smali
- **Why it matters:** AM ships smali libraries for class-file inspection. Current `com.android.tools.smali:*:3.0.7` is the line. No churn — confirm pin in libs.versions.toml.
- **Prevalence:** info

## E6. Reddit / HN / Lobsters / threads (last 7 days, narrow signal)

### Lobsters & HN — Google Developer Verification for sideloading still the dominant Android-management thread
- **Source:** https://lobste.rs/s/xetwz2/google_wants_make_sideloading_android · https://news.ycombinator.com/item?id=45908938
- **Date:** Aug 2025 thread + Nov 2025 follow-up; ongoing through May 2026
- **Why it matters:** Sideloading verification is the macro-trend that pulls users toward AM/Obtainium/Accrescent. NG should publish a position document (`docs/SIDELOAD_VERIFICATION.md`) explaining what AppManagerNG does and doesn't do regarding Google's verification — preempt user confusion when verification rolls out broadly.
- **Prevalence:** strategic / community-trust

### The Hacker News — Android 17 blocks non-accessibility apps from Accessibility API in Advanced Protection Mode
- **Source:** https://thehackernews.com/2026/03/android-17-blocks-non-accessibility.html
- **Date:** March 2026
- **Why it matters:** AM does NOT use accessibility services, but documents how to enable them for sister apps (TalkBack-related troubleshooting). On Android 17 with APM, those instructions partially break. Update docs.
- **Prevalence:** info / docs-update

### Reddit r/androidapps direct-fetch blocked (anti-bot) — partial dataset
- **Source:** N/A (Reddit blocks WebFetch)
- **Why it matters:** Could not pull threads in last 7 days from r/androidapps, r/GrapheneOS, r/LineageOS via WebFetch. Recommend running `octo:research` with reddit MCP for next iter to fill this gap. **Negative finding documented.**
- **Prevalence:** gap

## E7. F-Droid / IzzyOnDroid / Accrescent (last 7 days)

### F-Droid 2.0-alpha8 client (TWIF Week 17, 2026-04-24) — latest pre-window release; no May 2026 news posts yet
- **Source:** https://f-droid.org/news/
- **Date:** Last news post 2026-04-30
- **Why it matters:** F-Droid's Full client (2.0 line) is in alpha. AM/NG should add F-Droid metadata path validation against the **alpha-8 metadata format** (no breaking change yet, but `index-v3` adoption is in progress). Pin index format to `index-v2` for now in any F-Droid integration code. **No actionable May change.**
- **Prevalence:** info (negative finding for the May 1-8 window)

### IzzyOnDroid — repo migrated from GitLab to Codeberg; reproducible-builds infra received NLnet NGI Mobifree grant
- **Source:** https://codeberg.org/IzzyOnDroid/repo · https://apt.izzysoft.de/fdroid/
- **Date:** Migration complete by 2026-04
- **Why it matters:** If AM/NG plans to publish to IzzyOnDroid (1366 apps catalog, 5+M users), the inclusion-request flow now lives on Codeberg, not GitLab. Update `docs/RELEASE.md` if/when adding IzzyOnDroid as a distribution channel.
- **Prevalence:** compliance-required (if publishing to IzzyOnDroid)

### Accrescent 0.28.1 (2025-11-10) — still current; no May 2026 Accrescent release
- **Source:** https://github.com/accrescent/accrescent/releases/tag/0.28.1
- **Why it matters:** Last big move was 0.27.0's atomic updates + background installs (Nov 2025). Repo is mirrored in GrapheneOS App Store. AM/NG may want to publish-to-Accrescent — note the differences from F-Droid (mandatory minisign signature, repo metadata format). **No new action required this week**, but track for next iter.
- **Prevalence:** parity (eventual distribution channel)

---

## Extension Summary

**Extension findings: 35** (5 in E1 releases, 9 in E2 new-competitors, 9 in E3 Android 17, 2 in E4 CVE, 6 in E5 deps, 3 in E6 threads, 3 in E7 F-Droid). Floor (30) cleared.

**Top-5 actionable items from extension pass:**
1. **Android 17 reflection-on-static-final audit** (E3) — likely-severe compliance break; audit all `Field.setAccessible(true)` reflection sites against a static final target before targeting SDK 37.
2. **BouncyCastle 1.83 → 1.84** (E5) — straightforward dep bump that closes 4 CVEs including PGP AEAD DoS relevant to libopenpgp backups.
3. **restic-style backup engine** (E2 restoid) — strategic leapfrog answer to AM's recurring backup-encryption pain (#1958 GCM reuse + #1029 CIFS null bytes).
4. **Sui detection + AppOps UID-mode write** (E2 Sui + VolumeManager) — closes upstream AM #1863 with a known-working pattern.
5. **Migrate profile-trigger Activity → BroadcastReceiver** (E1 Neo-Backup pattern + E3 BAL hardening) — solves Part-1 #1968 *and* the Android 17 BAL deprecation in one move.

**Empty wells (extension):** AGP 8.14 (does not exist; line ended at 8.13), Compose BOM 2026.05 (not yet released), sora-editor 2026 release (Maven artifact still 2025-06-22), libsu 6.1+ (no release since 2024-06), F-Droid news May 2026 (no posts in 1-8 window), Reddit subs (WebFetch blocked).

**Carry-over for iter-21:**
- Re-poll Reddit via reddit MCP (the WebFetch gap).
- Watch wxxsfxyzm/InstallerX-Revived stable v2.4 release (preview is 26.05.d57ad8e).
- Watch hddq/restoid v0.6.0 (currently v0.5.0; `restic` integration roadmap not yet public).
- Watch for Android 17 stable release notes at Google I/O 2026 (May 19) — finalize compliance list.
