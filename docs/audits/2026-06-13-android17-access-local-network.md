<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Android 17 `ACCESS_LOCAL_NETWORK` Runtime Permission Audit

**Date:** 2026-06-13
**Source:** https://developer.android.com/about/versions/17/behavior-changes-17
**Audited against:** `cc1e7711d`
**Roadmap row:** P0 — Android 17 behavior-change audit batch (API 37)
**Outcome:** ✅ **CLEAN (audit) — permission declared, runtime flow implemented.**

## Premise

Android 17 (API 37) introduces `ACCESS_LOCAL_NETWORK` as a runtime permission required
for mDNS service discovery, multicast, and local-network communication. Apps targeting
API 37 that use `NsdManager` or `AdbMdns` for wireless-ADB pairing/discovery must request
this permission at runtime or those APIs silently fail.

## Sweep methodology

```
grep -rn "ACCESS_LOCAL_NETWORK" app/src/ libcore/ libserver/
grep -rn "AdbMdns\|NsdManager\|mDNS\|multicast" app/src/
grep -rn "local.network\|localNetwork\|LOCAL_NETWORK" app/src/
```

Manifest inspection: `app/src/main/AndroidManifest.xml` permission declarations.
Source roots: `app/src/`, `libcore/`, `libserver/`, `libopenpgp/`, `hiddenapi/`, `server/`.

## Findings

### 1. Manifest declaration

- **File:** `app/src/main/AndroidManifest.xml:16`
- **Pattern:** `<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />`
- **Verdict:** clean — permission is declared

### 2. Runtime permission check

- **File:** `app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java:1005-1009`
- **Pattern:** `isLocalNetworkPermissionMissing()` checks `Build.VERSION.SDK_INT >= SDK_ANDROID_17` AND `targetSdkVersion >= SDK_ANDROID_17` AND permission not granted
- **Verdict:** clean — conditional check is correct; only fires when both runtime and target are A17+

### 3. Runtime permission request UI

- **File:** `app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java:1011-1034`
- **Pattern:** `displayLocalNetworkPermissionMessage()` shows a MaterialAlertDialog with rationale, then calls `ActivityCompat.requestPermissions()` for `ACCESS_LOCAL_NETWORK`; falls back to app settings intent on failure
- **Verdict:** clean — follows standard Android runtime permission UX pattern

### 4. Installer status handling

- **File:** `app/src/main/java/io/github/muntashirakon/AppManager/servermanager/WifiWaitService.java:145-146`
- **Pattern:** Checks for `STATUS_LOCAL_NETWORK_PERMISSION_REQUIRED` status code from the ADB connection layer
- **Verdict:** clean — handles the permission-denied signal from the privileged channel

### 5. mDNS discovery (delegated to library)

- **File:** `app/src/main/java/io/github/muntashirakon/AppManager/adb/AdbUtils.java:45,56`
- **Pattern:** Instantiates `AdbMdns` objects (from `io.github.muntashirakon.adb.android`) for wireless ADB daemon discovery
- **Verdict:** clean — mDNS discovery is delegated to `libadb-android` which handles the NsdManager calls internally; the app's responsibility is the permission declaration + runtime request, both of which are in place

## Verdict

✅ **CLEAN (audit)** — `ACCESS_LOCAL_NETWORK` is declared in the manifest, guarded by a
runtime check in `Ops.java`, requested with a rationale dialog, and the connection layer
handles the permission-denied status. No remediation required.

## Follow-ups

None. The permission flow is complete for the current targetSdk=36 and will activate
cleanly when targetSdk is bumped to 37.
