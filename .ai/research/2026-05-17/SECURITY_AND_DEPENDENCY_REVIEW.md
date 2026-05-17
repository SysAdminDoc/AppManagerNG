<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# SECURITY_AND_DEPENDENCY_REVIEW — 2026-05-17

Dependency changelogs, security advisories, upgrade opportunities, hardening ideas.
Cross-references the audit doctrine in `docs/audits/` and the in-repo advisory at
`docs/security-advisories/`.

---

## 1. Active dependency-version surface

Pulled from [`versions.gradle`](../../../versions.gradle) at 2026-05-17. Annotated with
upgrade pressure where present.

### Critical (security / load-bearing crypto)

| Dep | Pinned | Latest known | Verdict | Notes |
|---|---|---|---|---|
| `bouncycastle` | **1.84** | 1.84 | ✅ Current | Bumped 2026-05-08 closing **CVE-2026-3505** (PGP AEAD chunk-size DoS), **CVE-2026-5588**, **CVE-2026-5598** (FrodoKEM non-constant-time compare). Audit at [`docs/audits/2026-05-08-bouncycastle-1-84-cve-bump.md`](../../../docs/audits/2026-05-08-bouncycastle-1-84-cve-bump.md). |
| `libsu` | **6.0.0** | 6.0.0 | ✅ Current | `Shell.sh` / `Shell.su` deprecated; `Shell.cmd` migration audit clean per [`docs/audits/2026-05-08-libsu-shell-cmd-migration.md`](../../../docs/audits/2026-05-08-libsu-shell-cmd-migration.md). |
| `apksig-android` | 4.4.0 | (current) | ✅ | MuntashirAkon fork; tracks upstream `androidx-platform-tools` apksig. |
| `libadb-android` | 3.1.1 | (current) | ✅ | MuntashirAkon fork. ADB-over-TCP / wireless ADB ground truth. |
| `hiddenapibypass` | 6.1 | (current) | ✅ | LSPosed/AndroidHiddenApiBypass. Android-17 static-final reflection ban audited at [`docs/audits/2026-05-08-android17-static-final-reflection.md`](../../../docs/audits/2026-05-08-android17-static-final-reflection.md) (1 fix, 1 deferred). |
| `gson` | **2.14.0** | 2.14.0 | ✅ Current | Bumped 2026-05-08; strict duplicate-JSON-key handling now throws (previously silent overwrite). Audit at [`docs/audits/2026-05-08-gson-2-14-0-bump.md`](../../../docs/audits/2026-05-08-gson-2-14-0-bump.md). |

### Constrained by minSdk-21 ceiling

These deps have dropped API 21-22 support in a later line; pinned at the last
API-21-22-compatible release. Documented at
[`docs/policy/minsdk-21-ceiling.md`](../../../docs/policy/minsdk-21-ceiling.md).

| Dep | Pinned (last 21-22 compat) | Newer line (drops 21-22) | Effect |
|---|---|---|---|
| `activity` | 1.11.0 | 1.12.x | Cannot adopt new Activity APIs on the 1.12 line. |
| `biometric` | 1.4.0-alpha04 | 1.4.0-alpha05 | Cannot adopt newer biometric prompt APIs. |
| `room` | 2.7.2 | 2.8.x | Cannot adopt Room 2.8 features (e.g. KSP-only paths, KMP support). |
| `webkit` | 1.14.0 | 1.15.x | WebView APIs frozen at 1.14. |
| `material` | **1.13.0** | **1.14.0-rc01 raises minSdk to 23** | **The single dep that, if unblocked, would let several others move in lockstep.** ROADMAP eng-debt row tracks this. |

**Decision point**: bumping `min_sdk = 23` (Android 6.0 Marshmallow) drops a vanishingly small user segment (Android 5.0/5.1 was ~3% of Play distribution in late 2024; lower now). It would unblock Material 1.14, Activity 1.12, Biometric 1.4.0-alpha05, Room 2.8, WebKit 1.15. The ceiling ledger should explicitly document this cascade — see `FEATURE_BACKLOG.md` → F-NEW-09.

### Tooling

| Dep | Pinned | Latest | Verdict |
|---|---|---|---|
| `agp_version` | 8.13.2 | AGP 9.2 stable / AGP 9.x active | **One major behind**. AGP 9.2 requires Gradle 9.4.1 and SDK Build Tools 36; R8 tightens `-keepattributes *Annotation*` wildcard. AGP 10 cliff is mid-2026 (deprecated DSL removal). |
| `dependency_check_version` | 10.0.3 | (auth-current per Gradle Plugin Portal) | Used by `dependency-scan.yml`. Plugin version was pinned 2026-05-08 per CHANGELOG. |
| `compile_sdk` | 36 | 36 (Android 16) | Current. Android 17 (API 37) bump is a multi-PR series per ROADMAP eng-debt batch. |
| `build_tools` | 36.1.0 | 36.1.0 | Current. |
| `jadx-android` | **1.4.7** | **1.5.5** | **7 releases behind**. Upgrade is gated on T12 APK editing being scoped; not blocking today. Critical multi-thread UI fix in 1.5.5; `.apks` support in 1.5.4+. |
| `baksmali` | 3.0.9 | (current) | Will be revisited against Apktool 3.0.2 ("Apktool Remastered") at T12 implementation time. |

### Other

`androidx_core`, `annotation`, `appcompat`, `documentfile`, `duration_picker`,
`fastscroll`, `splashscreen`, `swipe_refresh`, `preferences`, `simplemagic`,
`sun-security-android`, `unapkm-android`, `zstd-jni`, `desugar_jdk_libs`, `refine`,
`speed_dial`, `window`, `arsclib`, `sora-editor` — all on currently-supported lines; no
flagged upgrade pressure or CVE today.

---

## 2. Security-audit posture (the audit doctrine)

The project runs a **per-behavior-change audit doctrine**: when an Android version
ships a behavior change (or a CVE is disclosed against a dep), an audit doc lands at
`docs/audits/<yyyy-mm-dd>-<topic>.md`. The doc records the source link, the sweep
performed, and the verdict.

**14 audits exist as of 2026-05-17.** Their verdicts:

| Audit | Verdict |
|---|---|
| 2026-05-01 elegantTextHeight | clean (zero source matches) |
| 2026-05-02 adaptive layout | clean (0 fixed orientations / resizable=false / aspect-ratio limits) |
| 2026-05-02 Android-17 keystore key cap | clean (≤2 AndroidKeyStore aliases total) |
| 2026-05-02 Android-17 MessageQueue | clean (zero MessageQueue/nativePollOnce reflection) |
| 2026-05-02 Android-18 implicit URI grant | remediated (7 paths fixed) |
| 2026-05-08 Android-17 static-final reflection | 1 fix, 1 deferred |
| 2026-05-08 Android-17 System.load read-only | clean |
| 2026-05-08 BouncyCastle 1.84 CVE bump | shipped (CVE-2026-3505 / 5588 / 5598) |
| 2026-05-08 GCM cipher reuse large backup | confirmed, fixed in metadata-v6 (2026-05-16) |
| 2026-05-08 Google Play Contacts/Location policy | n/a (NG declares neither permission) |
| 2026-05-08 Gson 2.14.0 bump | shipped (strict duplicate-JSON-key handling) |
| 2026-05-08 libsu Shell.cmd migration | clean |
| 2026-05-08 zip-slip protection | clean (every extraction path guarded) |
| 2026-05-09 predictive-back WebView | clean (HelpActivity uses correct dispatcher pattern) |

This pattern is **the most valuable observability artifact in the repo** for a future
contributor — verdicts are recorded and dated, so re-investigating a topic always starts
with "did we already audit this?".

**Gap (F-NEW-12)**: there is no `docs/audits/README.md` documenting the convention. A
trivial doc would lower the cost of adding the next audit.

---

## 3. CVE / advisory inventory

### Currently tracked

- **CVE-2026-0073** (Android Security Bulletin May 2026, [S175]). Critical zero-click proximal RCE in `adbd` running as shell user; affects Android 14/15/16/16-qpr2 below the 2026-05-01 patch level. NG's local advisory: [`docs/security-advisories/2026-05-08-cve-2026-0073-adb-mode.md`](../../../docs/security-advisories/2026-05-08-cve-2026-0073-adb-mode.md). Action: ROADMAP iter-23 row added an onboarding warning for ADB workflows when device security patch is older than 2026-05-01 — shipped in `feat: refine capability onboarding` (`6759f35`).
- **CVE-2026-3505 / 5588 / 5598** (BouncyCastle, [S176]) — closed in 1.84 bump.

### Out-of-scope but worth tracking

- **F-Droid 2.0 protobuf index v2** ([S168]; updated to alpha9 today per `SOURCE_REGISTER.md` §3). Not a CVE; future-distribution-compatibility risk if NG ships an embedded F-Droid client. Deferred to T11 plugin ecosystem.

### Standing watch

- **Android 17 / API 37 compliance batch** — six audits required before bumping `targetSdk = 37`; ROADMAP Eng-Debt Register tracks. **Open**:
  - Static-final reflection ban — 1 deferred
  - `ACCESS_LOCAL_NETWORK` runtime permission (for wireless-ADB LAN discovery)
  - `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` → `_ALLOW_IF_VISIBLE` migration
  - `usesCleartextTraffic` enforcement (audit-pending)
  - Native DCL (`System.load()` read-only) — clean
  - ML-DSA Keystore OID recognition — open
  - ECH default-on for TLS — open if any LAN-pair endpoint must opt-out

The Android-17 bump is a sequencing question — none of these are urgent today, but the
cumulative effort is the highest single eng-debt item on the board.

### Theoretical

- **Hidden-API Compatibility Harness** (ROADMAP iter-19 Eng-Debt Now). Upstream's
  maintainer noted "a migration to a new version of Android roughly takes 80 hours
  alone" [S137]. The harness is the highest-leverage future investment because every
  Android-N+1 ships at most 12 months out. **Not actually a security vuln** but a
  velocity-multiplier on every future Android-version bump. Strongly endorsed; tracked
  by ROADMAP.

---

## 4. Hardening opportunities discovered today

These are net-new — they came out of the in-progress-work audit (see
`STATE_OF_REPO.md` §5 and `FEATURE_BACKLOG.md` F-NEW-01 → F-NEW-03), not from the
existing roadmap.

### H-01 — Install transcript URI redactor — userinfo / query / fragment hole

**Status: fix already in working tree, awaiting commit.**

The existing `redactSourceUri()` only stripped after the first `/` of the path. A URI
shaped `https://user:pass@host?token=secret` (no `/` path) was returned verbatim — the
authority's userinfo prefix, the query, and the fragment all leaked into the install
transcript that the user can share with a public issue tracker. The fix uses RFC 3986
§3.2 authority parsing.

**Severity:** Low-Medium. The transcript surface is user-initiated (user taps "Copy
diagnostic info") so leaks require user action; on the other hand, a user pasting a
transcript into a public issue almost certainly intends *not* to leak the credentials.

### H-02 — Finder regex predicates were neutered into literals

**Status: fix already in working tree.**

Not a security issue per se but a correctness issue with security implications: a
filter row meant to find apps matching `.*facebook.*` would never have matched,
silently. If anyone built a privacy-audit workflow around the regex predicates, they
would have been getting false negatives.

**Severity:** Low. Functional bug; no exposure.

### H-03 — Onboarding root-probe could `IllegalStateException` on detached fragment

**Status: fix already in working tree.**

Background-thread `requireContext()` on a detached fragment. Not user-facing because the
worker thread death is silent; the only effect is that the root-manager-suffix string
in onboarding stays default-blank. The fix captures the `Application` context on the
main thread before posting.

**Severity:** Very low. Stability hygiene.

---

## 5. CI / process integrity

- **`dependency-scan.yml`** runs OWASP `dependency-check 10.0.3` weekly + on PR. Healthy.
- **`codeql.yml`** runs CodeQL on push + weekly. CodeQL alert triage shipped in v0.3.0 (ROADMAP T4 row).
- **`tests.yml`** runs JUnit + Robolectric on push to `main` (added 2026-05-16 per CHANGELOG `ci: tests/lint now run on main`).
- **`lint.yml`** runs Android lint.
- **`release.yml`** runs a **two-clean-build reproducibility gate** comparing SHA-256 of every emitted release APK (hardened 2026-05-16). Publishing is blocked on hash mismatch.
- **`upstream-rename-watch.yml`** weekly probes `MuntashirAkon/AppManager` slug, opens issue on drift. Idempotent.

**Gap**: no link checker; no JaCoCo coverage badge. See `FEATURE_BACKLOG.md` F-NEW-11 /
F-NEW-13.

---

## 6. License posture

- License: **GPL-3.0-or-later** throughout. REUSE-compliant with per-file SPDX headers.
- Vendored dep license inventory in [`LICENSES/`](../../../LICENSES/). Apache-2.0, BSD-2/3, CC-BY-SA-4.0, GPL-2.0, ISC, MIT, WTFPL — all GPL-compatible.
- **Active rejection**: DuckDuckGo Tracker Radar dataset (CC-BY-NC-SA, non-commercial clause incompatible with GPL redistribution) — ROADMAP UC row [S69] documents this. Contributors should propose new tracker SDK signatures to the Exodus database (MIT) instead.
- **F-Droid Anti-Features watch**: NG's Anti-Features posture is FOSS-default. If telemetry, crash reporting, or analytics ever land, they go behind opt-in flags and a `floss` vs `full` build flavor split — see ROADMAP iter-19 [S244, S245] for the precedent (LibChecker's inverse pattern).

---

## 7. Summary

The dependency and security surface is **healthier than the average two-month-old
Android project** because:

- Every load-bearing crypto / privilege dep is current (BouncyCastle, libsu, apksig).
- The audit doctrine catches behavior-change drift before targetSdk bumps.
- CI runs OWASP dep-check + CodeQL + a reproducibility gate.
- Two CVEs were closed in May 2026 (BouncyCastle trio + the metadata-v6 GCM-reuse fix).

The standing risks are:

1. **minSdk-21 ceiling** freezes Material at 1.13. Plan a decision call before 2027.
2. **Android 17 (API 37) targetSdk bump** is a six-audit sequence with two open findings.
3. **AGP 8.13.2 → 9.x** has a deadline in mid-2026 per Google's AGP-10 cliff.
4. **jadx 1.4.7 → 1.5.5** is gated on T12 APK editing being scoped.

All four are tracked in the existing Engineering Debt Register. Nothing in the
dependency surface today is past its dose-by-date.
