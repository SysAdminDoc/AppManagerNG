<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# DATASET_MODEL_INTEGRATION_REVIEW — 2026-05-17 pass 2

Delta against [`../2026-05-17/DATASET_MODEL_INTEGRATION_REVIEW.md`](../2026-05-17/DATASET_MODEL_INTEGRATION_REVIEW.md).

Pass-1 explained why this file is thin: AppManagerNG is a power-user Android package
manager, not an ML/AI project. It consumes datasets (debloat list, tracker fingerprints)
and platform APIs, but has no model artifacts, no inference paths, no scraping, no
analytics.

Pass-2 found nothing to add. **No new datasets, model integrations, or external APIs
were introduced.** The architecture docs that landed this pass (privilege-providers,
backup-format, hidden-API-bypass) cover the platform-API surface and dataset
integration patterns from the engineering-design angle; that material complements but
does not extend the dataset inventory.

---

## 1. Datasets inventory (unchanged since pass-1)

- `MuntashirAkon/android-libraries` (submodule, varies per-entry licenses)
- `MuntashirAkon/android-debloat-list` (submodule, GPL-3.0-or-later with per-package metadata)
- `SysAdminDoc/android-debloat-list` fork — referenced [S234], +112+562 entries vs upstream
- `UAD-NG/uad_lists.json` (referenced, not bundled, GPL-3.0)
- `UAD-NG/universal-android-preinstalled-lists` (referenced, GPL-3.0)
- `Exodus tracker DB` (MIT, embedded via the libraries submodule)
- `IzzyOnDroid apkscanner-data` (referenced [S214], not bundled — GPL-2.0 audit deferred)
- F-Droid Anti-Features rules (documentation, not a dataset to bundle)
- **Rejected**: DuckDuckGo Tracker Radar (CC-BY-NC-SA, incompatible with GPL redistribution)
- One-time validation cross-check: Achno Samsung-specific debloat list [S215]

---

## 2. Why pass-2 added nothing

The categories that *could* admit new dataset / integration work:

- **New tracker database** — no fresh source surfaced; Exodus + IzzyOnDroid `apkscanner-data` cover the space well. DDG Tracker Radar remains license-incompatible.
- **New debloat list** — `SysAdminDoc/android-debloat-list` fork already mirrors UAD-NG additions; no new OEM-specific list surfaced today.
- **Network APIs (CVE, OSV, F-Droid index)** — all remain UC / Rejected per pass-1; NG is offline-first.
- **Model artifacts** — N/A; NG does not ship or load ML models.

The fundamental reason this file stays thin: **NG's value proposition is on-device, offline-first, dataset-curated**. Adding model inference would require an opt-in network surface that conflicts with the FOSS Anti-Features posture documented at ROADMAP [S244].

---

## 3. The architecture docs as a complement

The new [`docs/architecture/02-backup-format.md`](../../../docs/architecture/02-backup-format.md)
describes which platform APIs (`AndroidKeyStore`, `BiometricPrompt`, `DocumentsProvider`,
`StorageManager`) the backup engine consumes. The new
[`docs/architecture/03-hidden-api-bypass.md`](../../../docs/architecture/03-hidden-api-bypass.md)
exhaustively lists the 80+ hidden-API stubs NG reaches against. These are not datasets
or models — they're API integrations — but they constitute the "external integration"
column for a reader who is looking for that view.

For future ROADMAP rows that propose new platform-API integration (e.g. Health Connect
[S220], Privacy Sandbox SDK Runtime [S216], Credential Manager [S221]): cite the
architecture docs as the architectural model and add the new wrapper under `compat/`.

---

## 4. No dataset / integration drift since pass-1

Pass-2's external sweep verified:

- Exodus, debloat-list, UAD-NG `uad_lists.json` — no signal of dataset corruption or schema change
- F-Droid 2.0 protobuf index v2 — alpha9 stable; no breaking change since alpha8
- Android platform APIs cited above — Android 17 Beta 3 platform stability locked 2026-03-26; API surface is final until June 2026 stable
- Shizuku Manager 13.6.0 — runtime target unchanged (binder regression on A17 Beta 3 surfaced; see Security review)

---

## 5. Iter-26 carry

The next session may want to revisit:

- **Auto-fetch debloat definitions** (ROADMAP T7 row) — this would be the project's first opt-in network surface for dataset refresh. Multi-mirror priority (GitHub raw → Codeberg raw → IPFS) per iter-22 [S233-S235] design.
- **F-Droid 2.0 protobuf index v2 parser** (ROADMAP T11 row) — gated on T11 plugin ecosystem; not urgent.

Both remain Next-tier in ROADMAP; no movement this pass.
