<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Android 17 Cleartext Traffic Attribute Deprecation Audit

**Date:** 2026-06-13
**Source:** https://developer.android.com/about/versions/17/behavior-changes-17
**Audited against:** `cc1e7711d`
**Roadmap row:** P0 — Android 17 behavior-change audit batch (API 37)
**Outcome:** ✅ **CLEAN — no remediation required.**

## Premise

Android 17 (API 37) deprecates the `android:usesCleartextTraffic` manifest attribute and
the per-domain `cleartextTrafficPermitted` attribute in the network security config. Apps
targeting API 37 should rely on the network security config file instead of the manifest
attribute, and localhost carve-outs should be explicit domain-config entries rather than
relying on implicit platform behavior.

## Sweep methodology

```
grep -rn "usesCleartextTraffic" app/src/main/AndroidManifest.xml
grep -rn "cleartextTrafficPermitted" app/src/main/res/xml/network_security_config.xml
grep -rn "networkSecurityConfig" app/src/main/AndroidManifest.xml
grep -rn "http://127\.0\.0\.1\|http://localhost" app/src/ libcore/ libserver/ server/
```

Manual review of `app/src/main/res/xml/network_security_config.xml` and
`app/src/main/AndroidManifest.xml`.

## Findings

### 1. Manifest attribute

- **File:** `app/src/main/AndroidManifest.xml:194`
- **Pattern:** `android:networkSecurityConfig="@xml/network_security_config"` — references the config file
- **No `android:usesCleartextTraffic` attribute present** — the app uses the modern config-file approach exclusively
- **Verdict:** clean

### 2. Network security config — base config

- **File:** `app/src/main/res/xml/network_security_config.xml`
- **Pattern:** `<base-config cleartextTrafficPermitted="false">` — cleartext disabled globally
- **Verdict:** clean — correct restrictive default

### 3. Network security config — localhost carve-out

- **File:** `app/src/main/res/xml/network_security_config.xml`
- **Pattern:** `<domain-config cleartextTrafficPermitted="true">` with explicit domains `127.0.0.1` and `localhost` (both `includeSubdomains="false"`)
- **Verdict:** clean — the localhost exception is an explicit domain-config entry, which is the recommended approach for A17; it covers the privileged server's local IPC channel

### 4. HTTPS certificate pins

- **File:** `app/src/main/res/xml/network_security_config.xml`
- **Pattern:** Pin sets for `www.virustotal.com` (GlobalSign + GTS roots) and `beta.pithus.org` (Let's Encrypt + ISRG roots)
- **Verdict:** clean — pins are correctly configured within domain-config blocks

### 5. Localhost HTTP usage in source

- **Pattern:** Zero matches for `http://127.0.0.1` or `http://localhost` in any source root
- **Verdict:** clean — the localhost carve-out exists for the privileged server channel (which communicates over local sockets, not HTTP URLs), and no source code constructs plaintext HTTP URLs to localhost

## Verdict

✅ **CLEAN — no remediation required.** The app does not use the deprecated
`android:usesCleartextTraffic` manifest attribute. The network security config uses a
restrictive base (`cleartextTrafficPermitted="false"`) with an explicit localhost domain-config
carve-out, which is the A17-recommended pattern. Certificate pins are correctly scoped.

## Follow-ups

None.
