<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Architecture: privilege providers

AppManagerNG's elevated operations (component blocking, app-ops editing, system-app
freeze, force-stop, kill, backup-of-private-data, etc.) route through **one of four
privilege paths**:

1. **Direct root** — `su` binary (Magisk / KernelSU / APatch / SukiSU).
2. **Shizuku / Sui** — UserService binder bridge run as `shell` (uid 2000) or `system`/`root` depending on how Shizuku was started.
3. **ADB shell** — wireless ADB pair or USB ADB, runs as the `shell` uid via libadb-android's local ADB client.
4. **No-root** — only operations that fit inside ordinary user-installed-app permissions.

This document maps the source-tree surface that picks between them, holds the
binder/IPC contracts, and exposes the result back to the rest of the app.

---

## 1. The decision tree (per-operation)

The selection lives in
[`runner/Runner.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/runner/Runner.java)
plus [`settings/Ops.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java).

```
                          Runner.getInstance()
                                  │
                ┌─────────────────┼─────────────────┐
       Ops.isDirectRoot()?   LocalServices.alive()?   otherwise
                │                  │                       │
                ▼                  ▼                       ▼
        NormalShell(root=true)  PrivilegedShell        NormalShell(root=false)
        (libsu Shell.cmd via      (binder to AMService
         su)                       in libserver/)
```

- `NormalShell(true)` runs commands under a libsu-backed `su` shell. Used when the user has granted root and AM is configured for the **Root** mode.
- `PrivilegedShell` is the Shizuku / ADB path — `LocalServices` boots a long-lived binder service (defined in `libserver/`) under whichever uid Shizuku or `adb shell` provides, and `PrivilegedShell` issues commands to it via IPC.
- `NormalShell(false)` is the no-root fallback — runs in the AM process's own uid.

The user-facing **mode picker** in Settings → Mode of Operation feeds the `Ops.MODE_*`
state that gates which `Runner` is returned.

---

## 2. The Shizuku/Sui binder path

`libserver/` contains an AIDL-defined binder service (`AMService` / equivalents) plus a
`LocalServer` bootstrap that the AM process starts via:

- **Shizuku** — `Shizuku.bindUserService()` (Shizuku-API ≥ 11) tells the Shizuku Manager to spawn a `UserService` running under Shizuku's uid (`shell` on Android 11+ `adb` pair, or `root` if Shizuku was started via `su`). The UserService loads AM's `libserver` bridge code and exposes the binder back to AM.
- **Sui** — the Magisk-module variant of Shizuku ([S178]). Same binder contract; bootstraps via `/data/adb/modules/sui/` instead of the standalone Shizuku Manager app.
- **ADB** — for wireless or USB ADB, libadb-android (vendored as `libadb_version = 3.1.1`) opens an ADB transport, runs `app_process -Djava.class.path=… <bootstrap>` over `adb shell`, and the bootstrap launches the same `LocalServer` binder. Onboarding's "Wireless ADB pairing" wizard (shipped 2026-05-14) walks the user through `adb pair` / `adb connect` on Android 11+ devices.

[`shizuku/ShizukuBridge.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java)
is the single Java surface that owns Shizuku.

The privilege state visible inside `PrivilegedShell` is **whatever uid Shizuku/ADB
provides** — usually `shell` (uid 2000), occasionally `root` (uid 0). This matters
because some hidden-APIs require `system`-uid; AM uses the libserver to run those
through an even-narrower binder hop or via `pm` shell commands that the OS gates on
`shell` uid.

The compile-time pin is `shizuku_version = 13.1.5` in
[`versions.gradle`](../../versions.gradle). The **runtime** integration onboarding now
checks for Shizuku Manager **≥ 13.6.0** because of Android 16 QPR1 compatibility ([S121]
/ [S22]).

**Known-bad runtime warning (iter-57)**: Shizuku Manager 13.6.0 is treated as a
warning-only compatibility risk on Transsion/Infinix/Tecno/Itel Android 15 ROMs,
Mediatek platform tags, and Pixel 9 / Android 16 QPR1-class builds. The canonical
detector is
[`ShizukuBridge.getOemCompatibilityWarning(Context)`](../../app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java);
onboarding, Settings -> Operating Mode, Settings -> Privileges, and Mode Doctor
surface the "pin Shizuku 13.5.4" guidance without disabling Shizuku outright.

**Root-backed avoidance (iter-58)**: Shizuku sessions started through `su` expose
uid 0 to AppManagerNG and can still trip banking / Play Integrity-strict app
heuristics on some devices even when those apps were never granted Shizuku
permission ([S181]). The canonical detector is
[`ShizukuBridge.isRootBacked()`](../../app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java).
`Ops.autoDetectRootSystemOrAdbAndPersist()` now skips root-backed Shizuku when
local ADB is already available, and the Settings -> Mode of Operation Shizuku
hint row offers a one-tap switch into ADB mode with an explanatory tooltip.

**Open issue (iter-25)**: Shizuku 13.6.0 has reported regressions on Android 17 Beta 3 —
[Shizuku #1965](https://github.com/RikkaApps/Shizuku/issues/1965) ("Application
Management page blank") and [Shizuku #1967](https://github.com/RikkaApps/Shizuku/issues/1967)
("Not working on Android 17 Beta 3"). Android 17 stable lands June 2026; NG's Shizuku
integration shipped 2026-05-14 will need a compatibility audit before then. Tracked in
the iter-25 follow-up research run.

---

## 3. Root-manager detection (cosmetic surface)

[`runner/RootManagerInfo.java`](../../app/src/main/java/io/github/muntashirakon/AppManager/runner/RootManagerInfo.java)
is **not** part of the privilege selection — it's a diagnostic helper for the onboarding
wizard and the future Privilege Health-Check screen (T5 row, open). It probes:

| Manager | Detection signal | Surfaced as |
|---|---|---|
| Magisk | `/data/adb/magisk/` directory + `magisk` binary | " · Magisk" suffix in onboarding root status |
| KernelSU | `/data/adb/ksu/` + `me.weishu.kernelsu` / `com.rifsxd.ksunext` package | " · KernelSU" suffix |
| APatch | `/data/adb/ap/` + `me.bmax.apatch` package | " · APatch" suffix |
| ZygiskNext | `/data/adb/modules/zygisksu` (when a non-NONE manager is present) | " + ZygiskNext" appended |
| Sui | `/data/adb/modules/sui/` | "Sui" badge in Shizuku card |

The probe is best-effort — it runs through a privileged shell when one is available, and
falls back to `PackageManager` lookups otherwise. Failure is silent and the suffix
simply stays blank.

A second helper [`Ops.isAdbShellRoot()`](../../app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java)
detects the "ADB Root" surface KernelSU v3.2.3+ added: the configured mode is **ADB** but
the working shell's uid is 0 (also reachable via APatch's adb-root toggle and Magisk's
kang mode).

---

## 4. Capability fan-out — `SelfPermissions.checkSelfOrRemotePermission()`

The single most-quoted gate in elevated-op code is
`SelfPermissions.checkSelfOrRemotePermission(<permission>)`. The pattern:

- Try the permission against the **AM process** itself (works in no-root mode if AM already has it, e.g. `QUERY_ALL_PACKAGES`).
- If that fails, try against the **active privileged shell** — uid `shell` typically holds permissions like `FORCE_STOP_PACKAGES`, `KILL_BACKGROUND_PROCESSES`, `INSTALL_PACKAGES`, `DELETE_PACKAGES`, and many `android.permission.MANAGE_*` system-restricted permissions because they're granted to the `adb` UID.

The Force-Stop action in App Info is the canonical example
([`AppInfoFragment.java:1747`](../../app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java#L1747)):
it registers the action when `checkSelfOrRemotePermission(FORCE_STOP_PACKAGES)` returns
true — which inherently dispatches through whatever provider is active (binder, root, or
ADB shell) without the call site needing to care.

This pattern is **why most of the elevated-op surface "just works" once Shizuku is
authorised** — there's no separate Shizuku code path; the privilege probe routes through
whatever provider holds the permission.

---

## 5. Cross-references

- ROADMAP **T5 — Rootless Users (Shizuku)**: the user-facing tier that drives this architecture.
- ROADMAP **Engineering Debt Register → Android 17 targetSdk=37 compliance batch**: gates the privilege-provider work for the next platform-version bump.
- ROADMAP **iter-19 [S139]** Canta #359 Shizuku permission auto-revoke: a known stale-trust window in the binder lifecycle.
- ROADMAP **iter-19 [S152]** Hidden-Shizuku fork detection: shipped in iter-103 by resolving the manager package from the installed owner of `moe.shizuku.manager.permission.API_V23`, with a legacy service-permission fallback before the canonical `moe.shizuku.privileged.api` package.
- `libserver/` module — the AIDL binder service. Audit before bumping `targetSdk` to 37 (BAL hardening on `IntentSender.sendIntent()` per [S206]).
- `libadb-android` (`libadb_version = 3.1.1`) — ADB transport layer; depends on platform-tools mDNS backend behaviour ([S74]).

---

## 6. Things this doc deliberately does not cover

- **AIDL contract specifics** for the LocalServer binder — those live in the AIDL `.aidl` files under `libserver/src/main/aidl/`. Read them when changing the contract.
- **libsu Shell.cmd usage patterns** — covered by the [`docs/audits/2026-05-08-libsu-shell-cmd-migration.md`](../audits/2026-05-08-libsu-shell-cmd-migration.md) audit.
- **The Dhizuku fourth-tier privilege path** — Under Consideration in ROADMAP T5 [S71, S84]; not implemented today.
- **The FireOS SYSTEM USER fifth-tier path** — Under Consideration in ROADMAP T11 [S147]; not implemented today.

When either UC item lands, this doc should grow a §7 covering the fifth/fourth tier.
