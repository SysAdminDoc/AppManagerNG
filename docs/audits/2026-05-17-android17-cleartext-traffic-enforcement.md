<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Audit: Android 17 `usesCleartextTraffic` enforcement

**Date:** 2026-05-17
**Source:** https://developer.android.com/about/versions/17/behavior-changes-17 (S206); https://developer.android.com/about/versions/17/behavior-changes-all (S207)
**Audited against:** repo at `47eb040` (iter-25 deliverables commit)
**Roadmap row:** ROADMAP §"Engineering Debt Register" — Android 17 `usesCleartextTraffic` deprecation; closes one of the five open sub-audits in the targetSdk=37 batch.

## Premise

Android 17 begins enforcement of `usesCleartextTraffic`. Apps that omit the Network
Security Config manifest attribute will trigger lint errors on targetSdk=37 ([S55]).
The audit verifies that NG declares `networkSecurityConfig`, that the config rejects
cleartext at the base level, and that no `http://` cleartext URLs are reachable from
production code.

## Sweep methodology

- `grep -rn "usesCleartextTraffic\|networkSecurityConfig" app/src/main/AndroidManifest.xml`
- `Read app/src/main/res/xml/network_security_config.xml` in full
- `grep -rn "http://[^\"]*" app/src/main/java/` (filtering out XML-namespace declarations on `http://schemas.android.com/...`)
- Verified the manifest declaration wires the config

## Findings

- **`AndroidManifest.xml:172`** declares `android:networkSecurityConfig="@xml/network_security_config"`. ✅ wired.
- **`AndroidManifest.xml` has no `usesCleartextTraffic` attribute** at all. ✅ The default behaviour for targetSdk=37 + a declared Network Security Config is no-cleartext.
- **[`app/src/main/res/xml/network_security_config.xml`](../../app/src/main/res/xml/network_security_config.xml)** declares `<base-config cleartextTrafficPermitted="false" />` as the default. ✅
- Two HTTPS-pinned `<domain-config>` entries (`www.virustotal.com`, `beta.pithus.org`) — both inherit the base-config no-cleartext default since they don't override it. ✅
- One scoped opt-in `<domain-config cleartextTrafficPermitted="true">` for `127.0.0.1` + `localhost` (loopback only). ✅ Required for `libadb-android`'s loopback ADB transport when the user pairs against a same-device port; no risk on the wire because loopback never leaves the device.
- The only `http://` references in NG's `app/src/main/java/` tree are XML-namespace declarations (`xmlns:android="http://schemas.android.com/apk/res/android"`) and SVG-namespace URLs in drawables — these are URI identifiers, not network calls. No HTTP `URL` / `OkHttpClient` construction uses cleartext schemes.

## Verdict

✅ **clean** — zero remediation required.

NG already ships the textbook-correct posture: Network Security Config wired, base-config rejects cleartext, pinned domains are HTTPS-only, loopback is explicitly opted in. The targetSdk=37 bump introduces no compliance risk on this axis.

## Follow-ups

- None required.
- For the iter-26 carryover sub-audits, this is **audit 1 of 5** of the open Android 17 targetSdk=37 compliance batch — see ROADMAP §"Engineering Debt Register".
