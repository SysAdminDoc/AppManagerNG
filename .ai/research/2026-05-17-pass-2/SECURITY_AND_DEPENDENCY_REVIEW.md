<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# SECURITY_AND_DEPENDENCY_REVIEW — 2026-05-17 pass 2

Delta against [`../2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md`](../2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md).
Pass-1 inventoried 14 audits, the active dep surface, and 3 hardening opportunities
already in the working tree. Pass-2 shipped those hardenings as commits, ran a fresh
external sweep, and surfaced one new external risk.

---

## 1. Hardenings shipped this pass

| Commit | Item | Severity at time of shipping |
|---|---|---|
| `73387cd` | Finder regex predicates compiled as regex, not literals | Low — functional bug; iter-23 tracker-name regex work was silently failing |
| `bcb2874` | Install transcript URI redactor — userinfo / query / fragment leak | **Low-Medium** — user-initiated transcript surface (Copy diagnostic info → public issue paste). Leaked credentials only if the user actively pastes after triggering the install from an authenticated mirror or signed-URL download. |
| `25c629a` | Onboarding root-probe fragment-detach race | Very low — silent worker-thread crash; only effect was unpopulated UI suffix |

All three landed within hours of being identified. None had user-visible severity high
enough to warrant an out-of-band release.

---

## 2. Active dependency surface (no version changes this pass)

The full inventory in pass-1's §1 remains current. **Pass-2 verified externally** that
the load-bearing deps are still on their latest patch line:

- BouncyCastle `1.84` — no `1.85` published (CVE feed clean)
- libsu `6.0.0` — no `6.0.1` published
- Gson `2.14.0` — no `2.14.1` / `2.15.0` published
- Shizuku-API `13.1.5` — no compile-time bump available (Shizuku Manager runtime is the moving target at 13.6.0)
- Material Components `1.13.0` — **1.14.0 still in alpha** (alpha06/07/08 reported [S325]); minSdk-21 ceiling decision deferred
- AGP `8.13.2` — AGP 9.x available but iter-23 row classified the cliff as "Medium — not urgent"
- JADX `1.4.7` — JADX 1.5.5 latest (7 releases behind; gated on T12)

No dep-line urgent enough to ship a bump this pass.

---

## 3. New external risk surfaced

### Shizuku 13.6.0 on Android 17 Beta 3 — Application Management broken

- **Severity:** **High** for NG's Shizuku integration when Android 17 stable lands
- **Source:** [Shizuku #1965](https://github.com/RikkaApps/Shizuku/issues/1965), [Shizuku #1967](https://github.com/RikkaApps/Shizuku/issues/1967), both 2026-03-27. WebFetch verified the regression class on 2026-05-17 — issues remain open with 0 maintainer comments after ~50 days.
- **Threat model:** NG's [`shizuku/ShizukuBridge.java`](../../../app/src/main/java/io/github/muntashirakon/AppManager/shizuku/ShizukuBridge.java) reaches through Shizuku's `UserService` binder to perform privileged operations. If Shizuku's own Application Management can't enumerate packages on A17, NG's elevated-op surface in Shizuku mode is **blocked**. Android 17 stable lands June 2026 (Pixel first); a Shizuku user with NG installed who takes the A17 OTA will land in a non-functional state until either Shizuku ships a fix or NG implements detection + fallback.
- **Mitigation tracked:** ROADMAP iter-25 §"Shizuku Android-17 Compatibility Watch" — T5 Now-tier row. Action plan in [`FEATURE_BACKLOG.md`](FEATURE_BACKLOG.md) §2 F-NEW-25-01.

This is **the only new external risk** surfaced this pass.

---

## 4. CVE feed delta since pass-1

Re-queried the Android Security Bulletin for May 2026 (Source: source.android.com) and
external CVE pages for our pinned deps. No new CVEs against current pins since pass-1.

CVE-2026-0073 (ADB-mode RCE) remains the most recent known CVE affecting NG users. The
in-repo advisory at [`docs/security-advisories/2026-05-08-cve-2026-0073-adb-mode.md`](../../../docs/security-advisories/2026-05-08-cve-2026-0073-adb-mode.md)
remains current; the onboarding patch-level warning for ADB workflows is shipped
([`feat: refine capability onboarding`](https://github.com/SysAdminDoc/AppManagerNG/commit/6759f35), 2026-05-14).

---

## 5. Audit-doc inventory delta

Pass-1 inventoried 14 audits. Pass-2 added no new audit files — the three fix commits
were small enough to land via commit body + CHANGELOG entries rather than full audit
docs.

**Iter-26 must produce two batches of new audits**:

1. **`docs/audits/<YYYY-MM-DD>-shizuku-android17-compat.md`** — verdict on the Shizuku A17 regression. Either `confirmed, needs-design` (with mitigation plan) or `remediated` (if a runtime fallback + version-detection lands the same session).
2. **Five `docs/audits/<YYYY-MM-DD>-android17-<topic>.md`** files for the targetSdk=37 batch: ACCESS_LOCAL_NETWORK, usesCleartextTraffic, MODE_BACKGROUND_ACTIVITY_START_ALLOWED, ECH default-on, ML-DSA Keystore OID.

All follow the doctrine documented at [`docs/audits/README.md`](../../../docs/audits/README.md).

---

## 6. CI / process integrity (no changes this pass)

- `dependency-scan.yml` (OWASP `dependency-check 10.0.3`) — weekly + on PR. Healthy.
- `codeql.yml` — weekly. Healthy.
- `tests.yml` — on push to `main` (added 2026-05-16). Will pick up the new `FilterOptionTest` and `InstallTranscriptTest` cases on next push.
- `lint.yml` — Android lint on push. Will lint the new architecture docs.
- `release.yml` — two-clean-build reproducibility gate. No release this pass; the gate will fire on the next version tag.
- `upstream-rename-watch.yml` — weekly slug probe. Healthy.

**Gap closure**: pass-1 noted "no link checker; no JaCoCo coverage badge". Both are
queued for iter-26 (F-NEW-11, F-NEW-13).

---

## 7. License posture (no changes this pass)

- GPL-3.0-or-later, REUSE-compliant, per-file SPDX headers — all maintained in pass-2 additions.
- Every new file in `docs/architecture/`, `.ai/research/2026-05-17-pass-2/`, and the audit-doc README carries the SPDX header.
- No new third-party deps proposed this pass.

---

## 8. Summary

The security and dependency surface is **healthier than pass-1 left it**:

- Three hardenings shipped (Finder regex, InstallTranscript URI redactor, onboarding race)
- No new CVEs against current pins
- The minSdk-21 ceiling stays defensible (Material 1.14 still alpha)
- Audit doctrine now documented

The one new external risk (Shizuku A17 regression) is captured in ROADMAP iter-25 as a
Now-tier item with an action plan. Iter-26 will run the audit and ship the runtime
detection / fallback.
