<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# STATE_OF_REPO — 2026-05-17 pass 3

Delta against [`../2026-05-17-pass-2/STATE_OF_REPO.md`](../2026-05-17-pass-2/STATE_OF_REPO.md).
Pass 3 is the iter-26 follow-through that executed the full Android 17 targetSdk=37
audit batch plus the queued CI / docs hygiene items.

---

## 1. Commits added this session

```
2adbe49 docs: iter-26 — close Android 17 targetSdk=37 audit batch + Shizuku A17 design
47eb040 docs: iter-25 — architecture stand-up + research-run pass 2     (pass 2)
25c629a fix(onboarding): capture Application context before background root-manager probe
bcb2874 security(installer): redact userinfo / query / fragment in install transcript URI
73387cd fix(finder): user-supplied regex predicates compiled as regex, not literals
2846225 docs: add PROJECT_CONTEXT + 2026-05-17 research-run consolidation  (pass 1)
```

Branch is **22 commits ahead of `origin/main`** (started session at 16; +6 across passes 1-3). Push still deferred per VM auth constraint.

---

## 2. Audit-doc directory delta

Pass-2 left the doctrine README + 14 audit files. Pass-3 adds **six new audit files** for the Android 17 targetSdk=37 batch + Shizuku A17:

```
docs/audits/
├── README.md                                          (pass-1)
├── 2026-05-01-elegant-text-height.md                  (pre-existing)
├── 2026-05-02-adaptive-layout.md                      (pre-existing)
├── 2026-05-02-android17-keystore-key-cap.md           (pre-existing)
├── 2026-05-02-android17-messagequeue.md               (pre-existing)
├── 2026-05-02-android18-implicit-uri-grant.md         (pre-existing)
├── 2026-05-08-android17-static-final-reflection.md    (pre-existing)
├── 2026-05-08-android17-system-load-readonly.md       (pre-existing)
├── 2026-05-08-bouncycastle-1-84-cve-bump.md           (pre-existing)
├── 2026-05-08-gcm-cipher-reuse-large-backup.md        (pre-existing)
├── 2026-05-08-google-play-contacts-location-policy.md (pre-existing)
├── 2026-05-08-gson-2-14-0-bump.md                     (pre-existing)
├── 2026-05-08-libsu-shell-cmd-migration.md            (pre-existing)
├── 2026-05-08-zip-slip-protection.md                  (pre-existing)
├── 2026-05-09-predictive-back-webview.md              (pre-existing)
├── 2026-05-17-android17-access-local-network.md       (pass-3) ✅ clean
├── 2026-05-17-android17-bal-intentsender.md           (pass-3) ✅ clean
├── 2026-05-17-android17-cleartext-traffic-enforcement.md (pass-3) ✅ clean
├── 2026-05-17-android17-ech-default-on.md             (pass-3) ✅ clean
├── 2026-05-17-android17-ml-dsa-keystore-oid.md        (pass-3) ✅ clean (audit)
└── 2026-05-17-shizuku-android17-compat.md             (pass-3) ⚠ confirmed, needs-design
```

**20 audit docs total**, up from 14 at start of pass-3. Of the 6 new ones, 5 are `clean` and 1 is `confirmed, needs-design` (Shizuku — gated on device verification).

---

## 3. What state the working tree is in

After the iter-26 commit:

```
git status --short:
?? .ai/research/2026-05-17-pass-3/   (this dir)
```

Working tree is clean except for the pass-3 audit-trail files being staged.

---

## 4. ROADMAP / CHANGELOG state

- **ROADMAP `Last updated`** refreshed to iter-26 summary; iter-25 preserved as `Prior:`.
- **ROADMAP `Iter-26 Closures (2026-05-17)`** sub-section added with 8 entries (5 A17 audits + Shizuku audit + 2 hygiene items).
- **ROADMAP Engineering Debt Register** — the "Android 17 targetSdk=37 compliance" row is marked **closed** with cross-references to all 5 audit files; the row that called out 4 sub-audits is now a closure pointer.
- **CHANGELOG Unreleased** gained 5 new entries this pass:
  - Compliance — Android 17 targetSdk=37 audit batch closed clean
  - Audit — Shizuku Android-17 compatibility (confirmed, needs-design)
  - Added — minSdk-21 cascade analysis
  - Added — Docs Markdown link checker CI workflow
  - Added — JaCoCo coverage rollout plan

---

## 5. Verification on the audit pass

Each audit was driven by Grep / Read sweeps and recorded methodology in the audit doc itself:

| Audit | Sweep coverage |
|---|---|
| cleartext-traffic | `usesCleartextTraffic`/`networkSecurityConfig` grep across manifest; full read of `app/src/main/res/xml/network_security_config.xml`; `http://` grep over `app/src/main/java/` (only namespace-URI hits, no real cleartext). |
| ACCESS_LOCAL_NETWORK | `NsdManager` / `MulticastSocket` / mDNS / `NetworkInterface.getNetworkInterfaces()` grep across `app/src/main/java/` (0 production hits). Inspected `Ops.java:603 pairAdbInput()` and confirmed input-driven flow. |
| BAL hardening | `MODE_BACKGROUND_ACTIVITY_START` + `setPendingIntentBackgroundActivityStartMode` + `ActivityOptions.makeBasic` grep across `app/src/main/java/` (0 production hits). |
| ECH default-on | Full read of `network_security_config.xml`; enumerated all 3 destination domains; cross-checked against Android 17 ECH default-on docs. |
| ML-DSA OID | `getSigAlgOID` + `getSigAlgName` grep — 3 call sites in `Utils.java`, `PackageUtils.java`, `ScannerFragment.java`; read each call site for string-branch behaviour (none — display-only). |
| Shizuku A17 | Full read of `ShizukuBridge.java` (132 lines); inventoried the 7 probe methods; mapped each to its Throwable-catch surface; cross-referenced against the Shizuku issue body via WebFetch. |

No build was run (Windows host, walk-away session). All audits are static-analysis verdicts.

---

## 6. Updated dep + tooling versions

No `versions.gradle` changes in pass-3. The JaCoCo plan documents adding `jacoco_version = "0.8.13"` when the maintainer lands the JaCoCo wire-in commit — that bump is queued for iter-27.

---

## 7. Health snapshot

- **The Android 17 targetSdk=37 path is now source-side clear**. Engineering Debt Register's largest single open item is closed (the 5 sub-audits + the pre-existing batch from iter-19/iter-20). Remaining external blockers: 1 deferred finding from iter-20's static-final-reflection audit, and the Shizuku A17 regression. Both are tracked.
- **Audit-doc inventory** is at 20 verdicts, up from 14. The doctrine continues to scale linearly with platform / dep changes.
- **CI/docs hygiene** gained one workflow (link checker) and one policy plan (JaCoCo). The next CI addition (when JaCoCo lands) brings the total to 7 GitHub Actions workflows.
- **Project planning surface** remains internally consistent across passes 1-3 — no contradictions, no regressions in the ROADMAP / CHANGELOG / audit-doc / PROJECT_CONTEXT.md set.

---

## 8. Continuation

See [`CONTINUE_FROM_HERE.md`](CONTINUE_FROM_HERE.md). Iter-27 priorities:

1. Shizuku A17 implementation (device verification → runtime detection + onboarding banner)
2. JaCoCo wire-in (maintainer's local-build commit)
3. Optional: ML-DSA OID prettify-name map (T9 polish)
4. Optional: convert the Shizuku release-feed CI watch from manual to automated
