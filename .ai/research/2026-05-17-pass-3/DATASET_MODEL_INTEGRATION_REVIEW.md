<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# DATASET_MODEL_INTEGRATION_REVIEW — 2026-05-17 pass 3

Delta against pass-2's [`DATASET_MODEL_INTEGRATION_REVIEW.md`](../2026-05-17-pass-2/DATASET_MODEL_INTEGRATION_REVIEW.md).

Pass-3 added **zero new datasets / models / integrations / external APIs**. This file
stays thin for the same structural reason documented in pass-1 and pass-2: NG is a
power-user Android package manager, not an ML/data project.

---

## 1. Why pass-3 added nothing

Pass-3 was an internal audit / docs / CI pass:

- **Audit doc verdicts** describe code state, not datasets
- **Policy docs** describe maintainer process, not datasets
- **CI workflow** runs a Markdown link-checker (lychee), not a data integration

The architecture docs from pass-2 (`docs/architecture/02-backup-format.md`,
`03-hidden-api-bypass.md`) cover the dataset + platform-API integration patterns
descriptively; pass-3's additions reference those rather than extending them.

---

## 2. Dataset inventory (unchanged since pass-1 / pass-2)

- `MuntashirAkon/android-libraries` (submodule, varies per-entry licenses)
- `MuntashirAkon/android-debloat-list` (submodule, GPL-3.0)
- `SysAdminDoc/android-debloat-list` fork (S234)
- `UAD-NG/uad_lists.json` (referenced, not bundled)
- `UAD-NG/universal-android-preinstalled-lists` (referenced)
- Exodus tracker DB (MIT, embedded via submodule)
- IzzyOnDroid `apkscanner-data` (referenced [S214], not bundled)
- Achno Samsung debloat list (referenced [S215], one-time validation)
- Rejected: DuckDuckGo Tracker Radar (CC-BY-NC-SA — GPL-incompatible)

---

## 3. New external integrations cited in pass-3

The new audit + policy / CI deliverables cite these external surfaces (read-only
references, no integration):

| Surface | Where cited | Integration type |
|---|---|---|
| `lycheeverse/lychee-action@v2` | `.github/workflows/docs-link-check.yml` | CI-only — link-check runner; no runtime / on-device impact |
| JaCoCo 0.8.13 | `docs/policy/jacoco-coverage-rollout.md` (planned, not yet wired) | Build-only — Gradle plugin; no runtime / on-device impact |
| `codecov/codecov-action@v4` | `docs/policy/jacoco-coverage-rollout.md` (optional Codecov step) | CI-only — coverage badge; not committed |
| `1.3.6.1.4.1.2.267.12.6.5` / `.8.7` ML-DSA OIDs | `docs/audits/2026-05-17-android17-ml-dsa-keystore-oid.md` | Display-only — JDK cert API |
| Shizuku release feed | `docs/audits/2026-05-17-shizuku-android17-compat.md` (planned watcher) | CI-only — release-tag probe; not committed |

All are CI / build / display surfaces — none affect the on-device runtime behaviour or
introduce new network egress for end users.

---

## 4. Why this file stays thin

The pattern from pass-1 / pass-2 holds:

- NG does not train, fine-tune, or run ML models
- NG does not scrape external sites (Exodus / debloat-list datasets ship as
  bundled / submoduled JSON)
- NG does not phone home for analytics (FOSS Anti-Features compliance forbids it)
- NG does not provide a search index of any external corpus

The dataset / integration "surface" is **the platform's own APIs** plus the bundled /
submoduled debloat + tracker datasets. Both are covered exhaustively in the existing
roadmap and architecture docs. Adding net-new entries here would either duplicate or
contradict that coverage.

The iter-27 priority queue (Shizuku impl, JaCoCo, ML-DSA polish, release-feed CI
watcher) is similarly internal — no new datasets, no model artifacts, no external API
integrations beyond what's already in the existing roadmap rows.

If a future session adds a genuinely new data surface — e.g. the "Auto-update debloat
definitions" ROADMAP T7 row landing with multi-mirror priority + IPFS gateway fallback
per iter-22 [S233-S235] — this file gets a substantive update at that time. Today is
not that day.
