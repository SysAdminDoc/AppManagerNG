<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Audit: Android 17 Encrypted Client Hello (ECH) default-on

**Date:** 2026-05-17
**Source:** https://developer.android.com/about/versions/17/behavior-changes-17 (S206); https://developer.android.com/about/versions/17/features (S53)
**Audited against:** repo at `47eb040` (iter-25 deliverables commit)
**Roadmap row:** ROADMAP §"Engineering Debt Register" — Android 17 ECH default-on; closes one of the five open sub-audits in the targetSdk=37 batch.

## Premise

Apps targeting API 37 get Encrypted Client Hello (ECH) applied to all TLS connections by
default. NG must add a `<domainEncryption>` element to `network_security_config.xml` if
any network endpoint must opt out of ECH to avoid negotiation failures on older firmware
or middleboxes.

## Sweep methodology

- Inspect [`app/src/main/res/xml/network_security_config.xml`](../../app/src/main/res/xml/network_security_config.xml) for all declared domains.
- Identify each domain's purpose:
  - `www.virustotal.com` — VirusTotal API (public HTTPS endpoint)
  - `beta.pithus.org` — Pithus APK analysis service (public HTTPS endpoint)
  - `127.0.0.1` + `localhost` — loopback for libadb-android pairing
- For each public domain, evaluate whether the endpoint server is known to negotiate ECH cleanly.
- Identify whether any local LAN traffic exists that might hit an ECH-incompatible middlebox.

## Findings

- **Public HTTPS endpoints**: `www.virustotal.com` and `beta.pithus.org` are both fronted by modern TLS infrastructure (Google PKI for VirusTotal, Let's Encrypt for Pithus). Both endpoints serve standard TLS 1.3 and are expected to handle ECH default-on without renegotiation. ✅
- **Loopback** (`127.0.0.1` / `localhost`): ECH is irrelevant — loopback traffic doesn't traverse the network, so no Client Hello is ever transmitted. ✅
- **LAN traffic**: none — confirmed in [`2026-05-17-android17-access-local-network.md`](2026-05-17-android17-access-local-network.md). No middleboxes between NG and a LAN peer.
- **Captive-portal / DNS-over-HTTPS / proxy concerns**: NG doesn't ship a captive-portal probe, a DNS resolver, or a proxy-aware HTTP client beyond the standard `OkHttpClient` used for the two pinned VirusTotal/Pithus endpoints.
- **Certificate pinning**: NG pins both public endpoints to specific CA digests in `network_security_config.xml`. Pinning is orthogonal to ECH — the pin verifies the server certificate after the TLS handshake; ECH affects only the SNI/ClientHello encryption phase. No interaction.

## Verdict

✅ **clean** — zero remediation required.

NG's three network destinations are all ECH-clean: two public HTTPS endpoints on modern
TLS infrastructure, and loopback that isn't a network transit at all. No
`<domainEncryption>` opt-out is needed; the default ECH-on behaviour on targetSdk=37 is
safe.

## Follow-ups

- If NG ever adds a network endpoint that hits an enterprise middlebox or a legacy
  CDN known to break on ECH ClientHello, add `<domainEncryption>off</domainEncryption>`
  inside that `<domain-config>` block. Don't blanket-opt-out at `<base-config>` —
  ECH default-on is a privacy-positive default we should preserve.
- Cross-reference the F-Droid 2.0 protobuf index v2 work (ROADMAP T11, open) — if NG
  ever ingests an F-Droid mirror over a self-signed cert via `KeyChain`-installed trust
  (Material Files v1.7.2 model [S132]), the ECH posture is unchanged but the cert chain
  needs its own audit.

This is **audit 4 of 5** of the open Android 17 targetSdk=37 compliance batch.
