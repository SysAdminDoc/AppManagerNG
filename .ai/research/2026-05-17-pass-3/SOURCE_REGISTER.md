<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# SOURCE_REGISTER — 2026-05-17 pass 3

Delta against pass-2's [`SOURCE_REGISTER.md`](../2026-05-17-pass-2/SOURCE_REGISTER.md).

**No new external sources cited in pass-3.** Every audit in this pass referenced the
existing S01–S325 register or in-repo local sources. The canonical full register
remains at `ROADMAP.md` §"Source Appendix".

---

## 1. Why pass-3 added no new external sources

Pass-3 was an **execution pass** — running grep / Read sweeps against the local
source tree to produce audit verdicts. The verdicts cite:

- **External (already in the register)**: S55, S121, S122, S148, S168, S169, S170, S178, S205, S206, S207, S321, S322, S324, S325
- **Local (paths into the repo itself)**: `AndroidManifest.xml`, `network_security_config.xml`, `Ops.java`, `Utils.java`, `PackageUtils.java`, `ScannerFragment.java`, `ShizukuBridge.java`, `versions.gradle`

No fresh external query was needed because pass-2 had already established the
freshness baseline (10 external queries, 5 new sources cited as S321-S325).

---

## 2. Local sources newly cited this pass

These existed before this session but were first cited in pass-3 audit docs:

| Path | Purpose | First cited |
|---|---|---|
| [`app/src/main/res/xml/network_security_config.xml`](../../../app/src/main/res/xml/network_security_config.xml) | Cleartext rejection at base-config; HTTPS pin sets for VirusTotal + Pithus; loopback opt-in | iter-26 cleartext audit |
| [`app/src/main/AndroidManifest.xml`](../../../app/src/main/AndroidManifest.xml#L172) (line 172: `networkSecurityConfig` declaration) | Wires the security config | iter-26 cleartext audit |
| [`app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java`](../../../app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java#L603) (line 603: `pairAdbInput()`) | Confirms ADB pairing is input-driven, not discovery-driven | iter-26 ACCESS_LOCAL_NETWORK audit |
| [`app/src/main/java/io/github/muntashirakon/AppManager/utils/Utils.java`](../../../app/src/main/java/io/github/muntashirakon/AppManager/utils/Utils.java#L528) (line 528: `getSigAlgName()` display) | Display-only sigAlg name; no string branch | iter-26 ML-DSA audit |
| [`app/src/main/java/io/github/muntashirakon/AppManager/utils/PackageUtils.java`](../../../app/src/main/java/io/github/muntashirakon/AppManager/utils/PackageUtils.java#L745) (line 745: shows name + OID) | Already forward-compatible — surfaces both name and OID | iter-26 ML-DSA audit |
| [`app/src/main/java/io/github/muntashirakon/AppManager/scanner/ScannerFragment.java`](../../../app/src/main/java/io/github/muntashirakon/AppManager/scanner/ScannerFragment.java#L439) | Display-only sigAlg name | iter-26 ML-DSA audit |
| [`app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java`](../../../app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java) (full file) | 7 probes, all Throwable-caught | iter-26 Shizuku A17 audit |

---

## 3. Source-register hygiene for iter-27

When the Shizuku release-feed CI watcher (iter-27 Priority 4) lands, it will surface a
new source URL: the Shizuku release tag matching the A17 fix. That URL should be
appended as `S326` to the ROADMAP Source Appendix at the time of the release-watcher's
first auto-issue.

When the ML-DSA OID prettify-name map (iter-27 Priority 3) lands, it cites the existing
S205 (Google Security Blog ML-DSA announcement) — no new source needed.

When the JaCoCo wire-in (iter-27 Priority 2) lands, it should add the JaCoCo release-
notes URL as `S327` (or whatever the next free ID is):

| Proposed | URL | Used for |
|---|---|---|
| `S327` | https://github.com/jacoco/jacoco/releases | JaCoCo version pin (0.8.13) source — for `versions.gradle` comment + the policy doc |
| `S328` | https://github.com/lycheeverse/lychee-action/releases | lychee-action v2 — for `.github/workflows/docs-link-check.yml` provenance |

(Numbers are placeholders; the maintainer renumbers at append time.)

---

## 4. The session-cumulative source count

After 3 passes:

- Pre-session: S01-S315 (in ROADMAP)
- Pass 1 added S316-S320
- Pass 2 added S321-S325
- Pass 3 added 0

**Session-cumulative**: 10 new external sources across 3 passes, all appended to
`ROADMAP.md` §"Source Appendix". The register is exhaustive enough that pass-3 found
nothing requiring a new citation despite running 5 platform-compatibility audits.
This is the saturation outcome.
