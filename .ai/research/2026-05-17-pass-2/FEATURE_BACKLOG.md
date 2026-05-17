<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# FEATURE_BACKLOG — 2026-05-17 pass 2

Delta against [`../2026-05-17/FEATURE_BACKLOG.md`](../2026-05-17/FEATURE_BACKLOG.md).

Pass-1 surfaced 14 candidate items (F-NEW-01 → F-NEW-14). Pass-2 executed the
highest-leverage ones. This file is the **closure ledger** for pass-1 plus a single
new iter-25 finding.

---

## 1. Pass-1 backlog closure status

| ID | Title | Pass-1 tier | Pass-2 outcome |
|---|---|---|---|
| **F-NEW-01** | Finder regex predicate fix — commit existing fix | Now | ✅ **Shipped** — commit `73387cd` |
| **F-NEW-02** | Install transcript URI redactor hardening — commit | Now | ✅ **Shipped** — commit `bcb2874` |
| **F-NEW-03** | Onboarding root-probe fragment-detach race fix — commit | Now | ✅ **Shipped** — commit `25c629a` |
| **F-NEW-04** | CLAUDE.md Status section refresh + Canonical pointer | Now | ✅ **Shipped** in pass-1 commit `2846225` (CLAUDE.md is gitignored, edit is local-only) |
| **F-NEW-05** | README.md Roadmap preview refresh | Now | ✅ **Shipped** in pass-1 commit `2846225` |
| **F-NEW-06** | Top-of-ROADMAP "Now/Next/Later" summary block | Under Consideration | **Carried forward** — design call for the maintainer; iter-25 added a richer "Last updated" line that partially addresses this |
| **F-NEW-07** | Source register S316–S320 append | Now | ✅ **Shipped** in pass-1 commit `2846225`; pass-2 added S321–S325 |
| **F-NEW-08** | `versions.gradle` material_version minSdk-cascade comment | Now | ✅ **Shipped** in pass-1 commit `2846225` |
| **F-NEW-09** | Material-1.14 → minSdk-23 cascade analysis in ceiling ledger | Next | **Carried to iter-26**; Material 1.14.0 still alpha so deferral is fine |
| **F-NEW-10** | Issue-state refresh script for S-source register | Under Consideration | **Carried** — decision call; not pursued |
| **F-NEW-11** | JaCoCo coverage badge in `tests.yml` | Next | **Carried to iter-26** |
| **F-NEW-12** | `docs/audits/README.md` documenting audit-doc doctrine | Now | ✅ **Shipped** in pass-1 commit `2846225` |
| **F-NEW-13** | Markdown link checker in `lint.yml` covering PROJECT_CONTEXT.md | Next | **Carried to iter-26** |
| **F-NEW-14** | `docs/architecture/{01-privilege,02-backup,03-hidden-api}.md` | Next | ✅ **Shipped this pass** — 4 files (3 docs + README), commit pending |

**Now-tier**: 8/8 shipped (5 in pass-1 commit, 3 in pass-2 fix commits). Plus the
highest-leverage Next-tier item (F-NEW-14) executed early.

**Carried to iter-26**: 4 Next-tier items (F-NEW-09, F-NEW-11, F-NEW-13 — all CI / docs
hygiene) plus 2 Under-Consideration items (F-NEW-06, F-NEW-10 — design calls).

---

## 2. New iter-25 finding

### F-NEW-25-01 — Shizuku Android-17 Compatibility Audit & Mitigation

- **Theme:** T5 / Eng-Debt
- **Source:** [Shizuku #1965](https://github.com/RikkaApps/Shizuku/issues/1965), [#1967](https://github.com/RikkaApps/Shizuku/issues/1967) (both 2026-03-27 [S321, S322])
- **Problem:** Shizuku 13.6.0 fails to enumerate managed packages on Android 17 Beta 3 — Application Management surface returns blank. NG's iter-23 Shizuku integration (shipped 2026-05-14) reaches through the same binder. Threat: devices upgrading to Android 17 stable (June 2026 [S324]) lose NG's Shizuku mode.
- **Action plan:**
  1. Run NG against an Android 17 Beta image (Pixel 9 / 9a emulator or device per iter-19 [S148]).
  2. Reproduce the Shizuku enumeration failure under NG's `ShizukuBridge.java`.
  3. Identify the regression class — is it binder-API breakage, hidden-API removal in `LoadedApk`, or something else?
  4. Add runtime detection — if `Build.VERSION.SDK_INT == 37 && shizukuVersionCode < <future-fix>` then surface an onboarding warning and recommend ADB-mode fallback (NG's wireless ADB wizard from iter-23).
  5. Watch Shizuku release feed for a 13.6.x / 13.7 release fixing the regression. Pin compile-time `shizuku_version` once available.
- **Effort:** 3/5 audit + 2/5 fallback messaging = 4/5 cumulative
- **Tier:** **Now** (one of two new Now-tier items; the other being the targetSdk=37 audit batch which is Next-tier per its size)

### F-NEW-25-02 — Android 17 targetSdk=37 audit batch sequencing

- **Theme:** Eng-Debt
- **Source:** [S324] Android 17 release notes; Platform Stability locked 2026-03-26 at Beta 3; stable June 2026
- **Problem:** Five sub-audits remain open: `ACCESS_LOCAL_NETWORK` runtime permission, `usesCleartextTraffic` enforcement, `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` migration, ECH default-on for TLS, ML-DSA Keystore OID recognition. Each is its own `docs/audits/<date>-android17-<topic>.md` file per the audit doctrine.
- **Action plan:** Run all 5 audits before the v0.7.x window (when targetSdk would naturally bump alongside the platform stable rollout). Each audit follows the standard doctrine in `docs/audits/README.md`.
- **Effort:** 5 × 2/5 = ~4/5 cumulative
- **Tier:** **Next** (sized for the v0.7.x window — not urgent, not abandoned)

---

## 3. Iter-26 carried backlog (effective)

Items the next session should be aware of, ordered by priority:

1. **F-NEW-25-01** — Shizuku A17 audit (Now)
2. **F-NEW-25-02** — 5 open Android 17 sub-audits (Next; sized for v0.7.x)
3. **F-NEW-09** — minSdk cascade analysis (Next)
4. **F-NEW-11** — JaCoCo coverage badge (Next)
5. **F-NEW-13** — Markdown link checker (Next)
6. **F-NEW-06** — Top-of-ROADMAP summary (UC; design call)
7. **F-NEW-10** — Issue-state refresh script (UC; design call)

Items 1 and 2 are external-platform-driven and have natural deadlines (June 2026 for
Pixel A17 rollout). Items 3-5 are project-internal velocity multipliers. Items 6-7
are design calls.

---

## 4. Items deliberately not added

Same shape as pass-1's "deliberately not added" list. No new candidates surfaced
that warranted inclusion in this pass.
