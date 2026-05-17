<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# DATASET_MODEL_INTEGRATION_REVIEW — 2026-05-17 pass 4

This pass is intentionally thin on datasets and models.

Relevant integration surfaces:

- Shizuku release feed via GitHub API for fix-candidate monitoring.
- Existing Android debloat/tracker datasets were not changed.
- No ML/AI model, benchmark, scraper, analytics, or new data source was introduced.

The only new "data" behavior is CI polling of public GitHub release metadata and issue
text patterns (`Android 17`, `#1965`, `#1967`).
