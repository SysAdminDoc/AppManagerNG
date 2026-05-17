<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Audit: Android 17 `ACCESS_LOCAL_NETWORK` runtime permission

**Date:** 2026-05-17
**Source:** https://developer.android.com/about/versions/17/behavior-changes-17 (S206); https://android-developers.googleblog.com/2026/04/the-fourth-beta-of-android-17.html (S170)
**Audited against:** repo at `47eb040` (iter-25 deliverables commit)
**Roadmap row:** ROADMAP §"Engineering Debt Register" — Android 17 `ACCESS_LOCAL_NETWORK` runtime permission; closes one of the five open sub-audits in the targetSdk=37 batch.

## Premise

Android 17 (API 37) introduces the `ACCESS_LOCAL_NETWORK` runtime permission. Enforcement
is mandatory for apps that target Android 17 or higher — apps that try to discover or
talk to peers on the local LAN without declaring the permission and getting it granted
at runtime will be blocked. The audit verifies whether NG performs any LAN discovery or
LAN egress that would require the permission.

## Sweep methodology

- `grep -rn "NsdManager\|MulticastSocket\|JmDNS\|InetAddress.*\.local\|getNetworkInterfaces" app/src/main/java/`
- `grep -rn "libadb.*pair\|adbPair" app/src/main/java/` — to identify the wireless ADB pairing surface
- Verified the `AdbPairingService` flow:
  - [`app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java:603`](../../app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java#L603) — `pairAdbInput()` accepts user-typed host + port + pairing code, no discovery.
- Inspected `AdbPairingService` flow: launches a foreground service that connects via `libadb-android` (`libadb_version = 3.1.1`) to a user-specified IP+port. **TCP to a user-typed endpoint** — not multicast / mDNS / LAN discovery.

## Findings

- **Zero `NsdManager` matches** across `app/src/main/java/`. ✅
- **Zero `MulticastSocket` matches**. ✅
- **Zero JmDNS / mDNS-style discovery libraries**. ✅
- **Zero `NetworkInterface.getNetworkInterfaces()` calls** in production code (the grep hit count was 12 files, all under `.ai/research/` documentation pages or upstream `iter-20-delta.md`, not source).
- **Wireless ADB pairing** uses `libadb-android` TCP against a user-entered IP+port. The user types the host and port from the device's own Wireless Debugging panel, just like USB-cable-pair on Android < 11. **No LAN discovery surface**.
- **ADB connect** (post-pair) uses the same persistent `<host>:<port>` from preferences. No discovery.
- **Loopback ADB** (when the user pairs against a same-device daemon) is `127.0.0.1` — loopback is explicitly excluded from `ACCESS_LOCAL_NETWORK` per the Android 17 docs ([S206]).

## Verdict

✅ **clean** — zero remediation required.

NG's wireless ADB pairing wizard (shipped 2026-05-14) is **input-driven**, not
discovery-driven. The user copies a pairing code + host + port from the device's
own Wireless Debugging screen. No multicast, no mDNS, no `NsdManager`. The targetSdk=37
bump introduces no `ACCESS_LOCAL_NETWORK` requirement for NG.

If a future feature introduces LAN discovery (e.g. "find my Android TV on this network"
for cross-device companion-app sync per ROADMAP T18 row), this audit must be re-run and
the manifest extended with `<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK"/>`
plus a runtime grant flow.

## Follow-ups

- None required for the current source tree.
- Tracking: any future feature that adds a `NsdManager.discoverServices()` call must declare `ACCESS_LOCAL_NETWORK` + runtime-grant + this audit re-run.
- Cross-reference: F-Droid Anti-Features posture — adding a discovery permission would invite an `Tracking` anti-feature flag if not justified; document the use case in `docs/distribution/package-visibility.md`'s sibling permission-justification dossier when the feature lands.

This is **audit 2 of 5** of the open Android 17 targetSdk=37 compliance batch.
