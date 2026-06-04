<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Manifest permissions catalogue

Every `<uses-permission>` element AppManagerNG declares, with a one-line
justification and the call site that consumes it. The list exists for
F-Droid / IzzyOnDroid Anti-Features review (a 1700-line manifest reads as
a wall of permissions to a reviewer with no context) and for downstream
users auditing what the app can in principle do.

Generated and last reconciled against
[`app/src/main/AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) on
2026-05-25 (iter-145). Re-verify with:

```
grep -oE 'android:name="[^"]*permission\.[^"]*"' app/src/main/AndroidManifest.xml | sort -u
```

## 1. User-grantable runtime permissions

Permissions that show up in the Android permission UI; the app must request
them at runtime on Android 6+ before use.

| Permission | Why declared | Primary call site |
|---|---|---|
| `INTERNET` | VirusTotal upload, Pithus lookup, debloat-definition manifest fetch — all opt-in via Settings → Privacy → "Use the Internet" feature switch. **No telemetry; no third-party trackers.** | `apk/dexopt/network/*`, `debloat/DebloatDefinitionsUpdater` |
| `ACCESS_NETWORK_STATE` | Constraint for the Scheduled Auto-Backup `NetworkType.CONNECTED` requirement; checked before VirusTotal upload to avoid metered surprises. | `backup/schedule/AutoBackupWorker`, `virustotal/VirusTotal` |
| `ACCESS_LOCAL_NETWORK` | Android 17+ Wireless ADB pairing (mDNS discovery uses the local-network sandbox). | `adb/WirelessAdbWiz` |
| `POST_NOTIFICATIONS` | Foreground service progress notifications (batch ops, installer, scheduled backup). Asked contextually via the iter-22 prompt-sequencing helper. | `notifications/NotificationUtils`, `batchops/BatchOpsService` |
| `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` | Legacy storage access for APK / backup files on pre-API-30 devices. SAF supersedes both on API 30+. | `fm/FmActivity`, `backup/Path` |
| `MANAGE_EXTERNAL_STORAGE` | All-files access on API 30+ for the bundled file manager and backup volumes outside the SAF root. **Asked only when the user explicitly opens the file manager or backup destination picker.** | `fm/MountStorageActivity` |
| `READ_PHONE_STATE` | Used by `AppUsageStatsManager` to differentiate the active SIM when reporting mobile-data usage per app. | `usage/AppUsageStatsManager` |
| `RECEIVE_BOOT_COMPLETED` | Scheduled Auto-Backup re-enqueue on reboot; future NF-09 routine triggers (`TYPE_ON_BOOT`). | `backup/schedule/AutoBackupBootReceiver` (future: `profiles/trigger/BootCompletedReceiver`) |
| `FOREGROUND_SERVICE` | Required since Android 9 for any service the app starts via `startForegroundService()`. | `batchops/BatchOpsService`, `installer/PackageInstallerService`, `profiles/ProfileApplierService`, `backup/schedule/AutoBackupWorker` |
| `FOREGROUND_SERVICE_DATA_SYNC` | Android 14+ subtype for backup / install / batch foreground services. | same services as `FOREGROUND_SERVICE` |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ subtype for ADB / Shizuku bridge services that do not fit any standard FGS subtype. | `ipc/LocalServerService` |
| `WAKE_LOCK` | Partial wake-lock during batch backup / restore so a 10-minute backup doesn't get cut by Doze. Centralised via `CpuUtils.acquireWakeLock`. | `batchops/BatchOpsService`, `backup/BackupManager` |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Android-standard prompt asking the user to add AppManagerNG to the Doze allowlist before long backups. | `self/SelfBatteryOptimization` |
| `REQUEST_INSTALL_PACKAGES` | Direct install path when AppManagerNG is the user-chosen installer. | `apk/installer/PackageInstallerActivity` |
| `REQUEST_DELETE_PACKAGES` | Android-standard prompt-based uninstall path. | `apk/uninstaller/UninstallerActivity` |
| `QUERY_ALL_PACKAGES` | Required by every cross-app surface (main list, Finder, Permission Inspector, Debloater). Without this AppManagerNG can only see itself on Android 11+. **The single most-load-bearing permission in the app.** | `db/utils/AppDb`, `filters/FilteringUtils` |
| `INSTALL_SHORTCUT` (`com.android.launcher.permission.INSTALL_SHORTCUT`) | Pinned home-screen shortcuts (freeze / force-stop / clear-cache). | `shortcut/CreateShortcutDialogFragment` |
| `RUN_COMMAND` (`com.termux.permission.RUN_COMMAND`) | Optional Termux integration: launch a shell command in Termux when the user picks "Open in Termux" from the editor / file manager. | `terminal/TermActivity` |
| `API` (`com.rosan.dhizuku.permission.API`) | Dhizuku Provider detection (iter-91); not yet used for DPM operations (gated on the minSdk-26 contract). | `dhizuku/DhizukuBridge` |

## 2. Privileged permissions (Shizuku / root / Sui / Dhizuku)

These are `signature|privileged`-level permissions Android refuses to grant
to ordinary apps. AppManagerNG declares them so that **when** a privileged
shell (Shizuku / Sui / root) injects them at runtime, the app picks up the
capabilities without needing additional UI. Declaring a permission AppManagerNG
*can't actually use* without privilege is the standard pattern; F-Droid's
Anti-Features review accepts it.

The `SelfPermissions.checkSelfOrRemotePermission()` helper is the canonical
read site: it returns true when either the local process holds the permission
OR the bound `IPrivilegedService` (Shizuku / root) reports it as held in its
own UID.

### App-management privileged

| Permission | What it unlocks | Required for |
|---|---|---|
| `CHANGE_COMPONENT_ENABLED_STATE` | `pm enable / disable` per component. | Component blocking under ADB / Shizuku without root. |
| `FORCE_STOP_PACKAGES` | `am force-stop`. | App Details → Force Stop. |
| `KILL_UID` | Kill all processes in a UID. | Running Apps → "Kill". |
| `INSTALL_PACKAGES` | Silent install (no system confirmation dialog). | Installer privilege cascade (iter-135). |
| `INSTALL_EXISTING_PACKAGES` (`com.android.permission.INSTALL_EXISTING_PACKAGES`) | `pm install-existing` — install an already-present APK for another user. | Multi-user app install. |
| `INSTALL_TEST_ONLY_PACKAGE` | Install APKs marked `android:testOnly="true"`. | Sideloading dev / debug builds. |
| `DELETE_PACKAGES` | Silent uninstall. | Debloater rootless removal. |
| `CLEAR_APP_USER_DATA` | `pm clear`. | App Details → Clear Data / Debloat clear-leftovers. |
| `CLEAR_APP_CACHE` / `DELETE_CACHE_FILES` / `INTERNAL_DELETE_CACHE_FILES` | App-specific and system-wide cache wipe. | 1-Click Ops → Clear Cache. |
| `GET_PACKAGE_SIZE` | Per-app storage usage (deprecated by `StorageStatsManager` on API 26+; kept for the legacy path). | Storage panel under Shizuku where `PACKAGE_USAGE_STATS` is unavailable. |
| `SUSPEND_APPS` | `pm suspend` — third freeze method alongside disable and hide. | App Details → Freeze. |
| `ACCESS_HIDDEN_PROFILES` | API 35+ work / clone-profile enumeration. | Multi-user picker. |
| `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Silent install over an existing version. | Installer route chip "Silent install". |
| `ENFORCE_UPDATE_OWNERSHIP` | API 34+ install ownership flag. | Installer route chip "Update ownership". |

### Permission-management privileged

| Permission | What it unlocks |
|---|---|
| `GRANT_RUNTIME_PERMISSIONS` / `REVOKE_RUNTIME_PERMISSIONS` | Bulk grant/revoke without going through the system permission UI. |
| `GET_RUNTIME_PERMISSIONS` | Read the per-permission flag bitfield (system-fixed, policy-fixed, etc.). |
| `ADJUST_RUNTIME_PERMISSIONS_POLICY` | Adjust per-permission policy flags. |

### AppOps privileged

| Permission | What it unlocks |
|---|---|
| `MANAGE_APPOPS` / `MANAGE_APP_OPS_MODES` | Read and write any AppOp on any UID. |
| `GET_APP_OPS_STATS` | Per-package AppOp run statistics. |
| `UPDATE_APP_OPS_STATS` | Maintenance of AppOp stats; held alongside `MANAGE_APPOPS`. |

### Network / firewall

| Permission | What it unlocks |
|---|---|
| `MANAGE_NETWORK_POLICY` | Net-policy editor: per-UID metered / unmetered network blocking. |
| `NETWORK_SETTINGS` | Read OS network config (used for the Wireless ADB pairing surface). |
| `MANAGE_NOTIFICATION_LISTENERS` | Read which apps have notification-listener access. |

### System surfaces

| Permission | What it unlocks |
|---|---|
| `READ_LOGS` | Logcat viewer + Support Info Bundle's 120-line tail. |
| `DUMP` | `dumpsys` access for app-info / battery / network panels. |
| `INTERACT_ACROSS_USERS` / `INTERACT_ACROSS_USERS_FULL` | Operate on packages installed for users ≠ self. |
| `MANAGE_USERS` | Enumerate device users. |
| `MANAGE_SENSORS` | Toggle the "Sensors off" system AppOp. |
| `INJECT_EVENTS` | Synthetic key events for the Wireless ADB pairing flow. |
| `GET_TASKS` / `REAL_GET_TASKS` | Running Apps surface. |
| `PACKAGE_USAGE_STATS` | Per-app screen time, foreground/background time, network buckets. User-grantable through Settings → Usage access; the runtime shell can also write the AppOp directly. |
| `SYSTEM_ALERT_WINDOW` | Overlay used by the foreground-component-tracking accessibility surface. |
| `DEVICE_POWER` | Self-Doze-exemption flip from `SelfBatteryOptimization`. |
| `BACKUP` | Use the platform `BackupAgent` framework to read package data (legacy backup path). |
| `WRITE_SECURE_SETTINGS` | Toggle `Settings.Secure` system bits (e.g., disable verifier). |
| `START_ANY_ACTIVITY` | Activity launcher / Intent Interceptor when the target is not exported. |
| `CHANGE_OVERLAY_PACKAGES` | Per-app overlay enable/disable (T18 pull-from-upstream surface). |
| `UPDATE_DOMAIN_VERIFICATION_USER_SELECTION` | App Details → "Open by default" link-handling state changes. |

## 3. Custom permissions

| Permission | Owner | Why |
|---|---|---|
| `${applicationId}.permission.AUTOMATION` | AppManagerNG | Signature-only permission gating the iter-39 / iter-102 `AutomationReceiver` and `AutomationUriActivity`. Third-party apps can only fire the automation broadcast if signed with AppManagerNG's release key. |

## 4. Hardware / capability features

Declared with `android:required="false"` so the app remains installable on
TV, foldables, and devices without the listed hardware:

| `<uses-feature>` | Why optional |
|---|---|
| `android.software.leanback` | Android TV / Google TV launcher support. |
| `android.hardware.touchscreen` | TV builds via the `LEANBACK_LAUNCHER` category — touch is not guaranteed. |

## 5. What AppManagerNG *deliberately does not* request

| Not requested | Why |
|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` / `ACCESS_BACKGROUND_LOCATION` | No location feature. Location-using apps are observed, not used. |
| `RECORD_AUDIO` / `CAMERA` / `READ_CONTACTS` / `READ_SMS` / `READ_CALL_LOG` | Same — observed surfaces only. |
| `BIND_VPN_SERVICE` | NG is not a VPN. The roadmap row "VPN plugin flags" is parked behind this absence. |
| `BIND_ACCESSIBILITY_SERVICE` (registered) | NG ships an Accessibility-Service-based foreground-component tracker (`accessibility/`) but it stays user-opt-in; the manifest does not auto-bind. |
| `BIND_DEVICE_ADMIN` | Dhizuku detection alone — NG never asks to be a device admin. |
| Push-messaging permissions (FCM, JPush, etc.) | No remote notifications. |
| Play Billing | F-Droid build is unconditionally free; the `full` flavor has no commercial Play surface. |
| Telemetry / analytics SDK auto-grant permissions | None of those SDKs ship in NG. |

## 6. F-Droid Anti-Features cross-check

The bundled config in `docs/distribution/build-flavors.md` keeps the `floss`
build clean of:

- `Tracking` — no analytics SDK; no telemetry network calls.
- `NonFreeNet` — no proprietary network service is hit by the floss flavor.
- `NonFreeAdd` — no commercial in-app upgrades.
- `NonFreeAssets` — bundled scanner datasets are generated from the
  `android-libraries` source data (Exodus / IzzyOnDroid / LibSmali-style
  signature inputs, GPL-compatible for NG redistribution); the debloat dataset
  is `android-debloat-list` (CC-BY-SA-4.0, GPL-3.0-compatible). No scanner or
  debloat verdict requires a runtime remote service.

`UpstreamNonFree` would only apply if the upstream MuntashirAkon/AppManager
fork shipped a non-FOSS component, which it doesn't.

## 7. Update protocol

When a manifest permission changes:

1. Update this doc in the same commit.
2. Cite the call site so a reviewer can trace the request to use.
3. Add a row in `docs/audits/` if the change is in response to a platform
   behaviour change (Android-N target SDK enforcement, A12 implicit-receiver,
   A14 foreground-service subtypes, etc.).
4. Make sure the `floss` flavor doesn't pick up a Play-only or
   commercial-network permission as a transitive dep change.
