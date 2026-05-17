<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# SOURCE_REGISTER — 2026-05-17

Every source consulted by this autonomous-research session. Local sources first, then
external. **The canonical project source register lives at [`ROADMAP.md`](../../../ROADMAP.md)
§"Source Appendix" (S01–S315) — this file is the audit trail for *today's* session
specifically, including the small handful of net-new sources added below.**

---

## 1. Local sources consulted

| ID | Path | Used for |
|----|------|----------|
| L01 | `CLAUDE.md` | Stack, build commands, gotchas, version status, status-iter-7 currency check |
| L02 | `AGENTS.md` | Tool-specific instruction file inventory |
| L03 | `README.md` | Public surface, feature catalog, signing-cert fingerprint, install paths |
| L04 | `ROADMAP.md` | Plan + 315-source register + Engineering Debt Register + Premium Polish Track |
| L05 | `CHANGELOG.md` | Shipped work 2026-04-30 → 2026-05-16 (v0.1.0 → v0.4.2 + Unreleased) |
| L06 | `codexprompt.md` | Premium-facelift design brief (driver for `design/`) |
| L07 | `versions.gradle` | Dependency pin truth |
| L08 | `research/iter-20-delta.md` | Iter-20 issue mining (2026-04-15 → 2026-05-08) |
| L09 | `docs/audits/*` | 14 audit files for behaviour-change verdicts |
| L10 | `docs/research/*` | 4 research files + iter-6-delta |
| L11 | `docs/policy/minsdk-21-ceiling.md` | API 21-22 dep-floor decision tree |
| L12 | `docs/distribution/{obtainium-config.json,reproducible-builds.md,backup-destinations.md,package-visibility.md}` | Distribution / FOSS-channel posture |
| L13 | `docs/security-advisories/2026-05-08-cve-2026-0073-adb-mode.md` | CVE advisory pattern |
| L14 | `docs/intent-api.md` | `app-manager://` + `am://` contract |
| L15 | `docs/sideload-verification.md` | Google Play developer-verification position |
| L16 | `design/{spec,impl,plan,audit,README.md}` | Premium-facelift artifacts already produced |
| L17 | `.github/workflows/*.yml` | CI surface (6 workflows) |
| L18 | `app/src/main/java/io/github/muntashirakon/AppManager/` tree | Package-tree map (629 .java files) |
| L19 | Git history (97a339a back ~100 commits) | Iter-21 → iter-23 narrative + uncommitted-work audit |
| L20 | `git diff` of 6 uncommitted files | In-progress hardening + bug fixes (see STATE_OF_REPO §5) |

---

## 2. External sources cross-referenced

This session relied on the existing **S01–S315** register in `ROADMAP.md`. The full table
is not duplicated here — see [`ROADMAP.md` → "Source Appendix"](../../../ROADMAP.md#source-appendix).
Selected sources that were touched most heavily in this consolidation:

- **S22 / S121 / S22** — Shizuku v13.6.0 (Android 16 QPR1 support, trusted-WLAN auto-start)
- **S121** — `MuntashirAkon/AppManager` upstream slug — anchored by `upstream-rename-watch.yml`
- **S137** — AM #1940 (upstream maintainer "80 hours per Android version migration" comment); load-bearing for the Hidden-API Compatibility Harness Now/Eng-Debt row
- **S138** — AM #1958 (GCM cipher reuse on >2 GB OBB backup); fix shipped 2026-05-16 via metadata-v6
- **S168 / S167** — F-Droid 2.0 alpha8 (Apr 24, 2026) + ROM JSON pre-seeding format
- **S172 / S173 / S310** — Android Developer Verification rollout (BR/ID/SG/TH, enforcement 2026-09-30)
- **S175** — Android Security Bulletin May 2026 / CVE-2026-0073 ADB-mode advisory
- **S203** — AGP 9.x release notes (Gradle 9.4.1 + AGP 10 cliff)
- **S57 / S201** — Material Components 1.14.0-rc01 minSdk-23 raise
- **S291 → S302** — competitor GitHub-stars snapshot (2026-05-16)

---

## 3. Net-new sources added today

These sources were verified during this session and represent claims not previously
captured in the S01–S315 register. They are **proposed** for inclusion as S316 → S320 in
the next ROADMAP iteration; the maintainer should append them to the Source Appendix.

| Proposed ID | URL | Used for |
|---|---|---|
| **S316** | https://f-droid.org/en/news/ | F-Droid 2.0-alpha9 release (2026-05-08) — supersedes iter-19's alpha8 reference at [S168]. Confirms protobuf index v2 work continues; no breaking change since alpha8. |
| **S317** | https://f-droid.org/2026/01/24/fdroid-basic-2.0-alpha.html | F-Droid Basic 2.0 alpha announcement (2026-01-24). Context for the 2.0-alphaN cadence used by ROADMAP T11 row "F-Droid 2.0 Index v2 Protobuf". |
| **S318** | https://www.apkmirror.com/apk/xingchen-rikka/shizuku-manager/shizuku-13-6-0-r1086-2650830c-release/ | Shizuku 13.6.0 build target SDK 36 (Android 16) — confirms iter-18 [S121] / [S22] claim about Android 16 QPR1 support is current as of 2026-05-17. APKMirror upload 2025-05-26 (note the year mismatch with iter-18 — iter-18 cited "2026"; APKMirror metadata is 2025. Independent verification still needed; flagging as low-confidence date). |
| **S319** | Z:\repos\AppManagerNG\.ai\research\2026-05-17\ | **This research run.** Self-reference for any future session needing to trace the 2026-05-17 consolidation pass. |
| **S320** | https://source.android.com/docs/whatsnew/android-16-release | AOSP Android 16 / Android 16 QPR1 / Android 16 QPR2 release notes — primary reference for behaviour-change claims; cross-validates roadmap S124, S44, S45 group of citations. |

**Note on S318 date confusion**: ROADMAP iter-18 dates Shizuku 13.6.0 as 2026; APKMirror metadata is 2025. The roadmap itself dates work from 2026-04 onward (post-bootstrap), so this is most likely a roadmap-side typo for the Shizuku release year, not an actual date conflict. The behavioural claims (Android 16 QPR1 support, trusted-WLAN auto-start) are independently verifiable from the APK manifest and are not affected by the date question.

---

## 4. Sources I did *not* re-verify

The 315-source register includes many GitHub-issue references mined in iter-18 → iter-23.
This session **did not re-verify** issue states because:

1. Most are weeks old, and the issue states naturally shift; the roadmap rows depending on
   them already cite the issue text rather than the issue state.
2. Re-mining the same surface would duplicate iter-18 → iter-23 work that's already
   exhaustive.

If a future session needs to refresh the issue-state column, the most efficient pattern is
a `gh api` script that takes the S-numbered issue URLs and emits a CSV of
`{id, state, comment_count, last_update}` — left as a tooling exercise.

---

## 5. Domains touched by today's WebSearch

Two queries were issued:

1. `Shizuku 13.6 Android 16 QPR1 release notes 2026` — primary hits: `apkmirror.com`, `source.android.com`, `developer.android.com`, `github.com/RikkaApps/Shizuku/releases`.
2. `F-Droid 2.0 alpha index v2 protobuf 2026` — primary hits: `f-droid.org`, `apkmirror.com`.

Both queries confirmed existing roadmap claims; only the alpha9 freshness data point is
net-new (proposed as S316).

---

## 6. Audit-doc cross-reference

The 14 [`docs/audits/*.md`](../../../docs/audits/) files are themselves a structured
source registry. Each file documents:

- The Android-version or library behavior change being audited (with a source link)
- The source-tree sweep performed (`grep` patterns, file lists)
- The verdict (clean / N findings, remediation, deferred)

A future-session source-saturation check should treat the audit files as **the** source of
truth for "did NG already check this?" — they cover six Android-17 behavior changes (so
far), the Android-18 implicit-URI-grant removal, the Google Play Contacts/Location-button
policy enforcement, predictive-back regressions, GCM cipher reuse, and zip-slip
protection. Cross-referencing the audit doc before writing a new audit row is high-EV.
