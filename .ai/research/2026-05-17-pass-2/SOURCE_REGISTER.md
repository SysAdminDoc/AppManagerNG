<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# SOURCE_REGISTER — 2026-05-17 pass 2

Delta against [`../2026-05-17/SOURCE_REGISTER.md`](../2026-05-17/SOURCE_REGISTER.md).
The canonical full register lives at `ROADMAP.md` §"Source Appendix" (S01–S325 as of
this pass).

---

## 1. New sources added today

| ID | URL | Used for |
|---|---|---|
| **S321** | https://github.com/RikkaApps/Shizuku/issues/1965 | Shizuku #1965 (2026-03-27): Android 17 Beta 3 — Application Management page blank; Shizuku 13.6.0 cannot enumerate managed packages. Direct threat to NG's iter-23 Shizuku integration before Android 17 stable June 2026. |
| **S322** | https://github.com/RikkaApps/Shizuku/issues/1967 | Shizuku #1967 (2026-03-27): Android 17 Beta 3 — "The app won't get the shizuku and nothing in the app list." Companion regression to S321. |
| **S323** | https://github.com/NeoApplications/Neo-Backup/releases | Neo-Backup 8.3.18 (2026-05-04): supersedes the 8.3.17 cited at iter-18 [S135] / iter-19 [S114]. No NG-action delta. |
| **S324** | https://developer.android.com/about/versions/17/release-notes | Android 17 stable release-notes scaffold; Platform Stability reached at Beta 3 (2026-03-26); stable rollout target **June 2026** to Pixel devices first. Anchors the targetSdk=37 audit-batch sequencing. |
| **S325** | https://github.com/material-components/material-components-android/releases | Material Components 1.14.0-alpha06/07/08 status as of 2026-05-17: **1.14.0 stable has not shipped**. minSdk-21 ceiling decision can stay deferred. |

Each S-ID has been appended to `ROADMAP.md` §"Source Appendix" by the iter-25
deliverables commit. The S316 → S320 additions from pass-1 already landed in commit
`2846225`.

---

## 2. Sources verified during pass-2

External verification queries this pass:

| Query | Method | Outcome |
|---|---|---|
| Shizuku 13.7 release notes 2026 | WebSearch | **No 13.7 release**; 13.6.0 still latest; surfaced #1965 / #1967 Android-17 regressions. |
| Hail v1.11 / v1.12 release 2026 | WebSearch | No release after v1.10.0 (2026-01-01); iter-18 [S130] / iter-23 [S294] data current. |
| Magisk v30.8 / KernelSU v3.3 release 2026 | WebSearch | No newer releases: Magisk v30.7 latest, KernelSU v3.2.4 latest. Iter-18 [S122] / iter-19 [S166] data current. |
| Canta latest 2026 | WebSearch | v3.2.2 still latest. Iter-19 [S43] data current. |
| Neo-Backup latest 2026 | WebSearch | **8.3.18 (2026-05-04)** — minor bump from 8.3.17. Source updated as S323. |
| Material Components 1.14.0 stable status | WebSearch | Still alpha (alpha06/07/08). No stable release. Source updated as S325. |
| Android 17 final release date | WebSearch | June 2026 to Pixel devices; Beta 3 platform-stability locked 2026-03-26. Source updated as S324. |
| Android 17 Beta 4 behavior changes | WebSearch | Confirms iter-19 / iter-23 [S169, S170, S206] are current. |
| Android Security Bulletin May 2026 (CVE-2026-0073) | WebSearch | Confirms iter-20 [S175] / `docs/security-advisories/2026-05-08-cve-2026-0073-adb-mode.md` are current. |
| F-Droid 2.0 alpha10/alpha11 | WebSearch | No alpha10+ found; alpha9 (2026-05-08) still latest per pass-1 S316. |

---

## 3. Sources I deliberately did not re-query

Same rationale as pass-1's RESEARCH_LOG §3 — the 320-source register exhaustively covers
the competitor / platform / dep / CVE / FOSS-channel surface. Spot-checking specific
S-IDs verified currency where useful; full re-mining of the same surface would
duplicate iter-18 → iter-23 work.

Specifically not re-queried:
- iter-18 → iter-23 issue-states (issue *content* > issue *state* per pass-1)
- AGP 9.x progression (no urgency; iter-23 captured 8.13.2 → 9.x cliff)
- F-Droid index v2 protobuf spec (deferred behind T11)
- εxodus tracker DB updates (no high-velocity drift signal expected)
- JADX 1.5.5 / Apktool 3.0.2 (gated on T12 APK editing)

---

## 4. Local sources consulted this pass

Beyond the pass-1 set:

| Path | Purpose |
|---|---|
| `app/src/main/java/io/github/muntashirakon/AppManager/runner/{Runner.java,RootManagerInfo.java,NormalShell.java,PrivilegedShell.java,RunnerUtils.java}` | Privilege-provider architecture doc source-of-truth |
| `app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java` | `Ops.isDirectRoot()`, `Ops.isAdbShellRoot()`, mode-selection truth |
| `app/src/main/java/io/github/muntashirakon/AppManager/backup/{BackupOp.java,RestoreOp.java,VerifyOp.java,BackupItems.java,BackupCryptSetupHelper.java,CryptoUtils.java,MetadataManager.java}` | Backup-format doc source-of-truth |
| `app/src/main/java/io/github/muntashirakon/AppManager/crypto/{AESCrypto.java,RSACrypto.java,ECCCrypto.java,OpenPGPCrypto.java,DummyCrypto.java}` | Crypto mode coverage |
| `hiddenapi/` (entire module tree, ~80 files listed) | Hidden-API stub set coverage for the third architecture doc |
| `hiddenapi/README.md` | AOSP source-pull protocol citation |
| `app/src/main/java/io/github/muntashirakon/AppManager/compat/` (28 files) | Compat-wrapper layer coverage |
| `app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java` | Shizuku integration single-surface citation |

These are not new external sources — they're the local source files cited in the new
[`docs/architecture/`](../../../docs/architecture/) trio.

---

## 5. Where new sources go next time

S326 onward should append to `ROADMAP.md` §"Source Appendix" with the same one-line shape:

```md
| S326 | <url> | <one-line purpose; cite verbatim from the source if it's a CVE or behavior-change note> |
```

If the source is a GitHub issue, prefer the issue URL over the repo URL so the citation
remains stable when the issue changes state. If the source is an Android docs page,
cite the version-specific URL (`/versions/17/`) rather than the latest-version
redirect, because docs pages re-write when a new version ships.
