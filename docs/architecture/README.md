<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# docs/architecture/

Architecture notes for AppManagerNG. Three docs cover the load-bearing subsystems that
power the rest of the app; everything else is either ordinary Android-Views code or
described inline at the relevant `app/` package tree.

## Documents

| # | Topic | What it covers |
|---|---|---|
| 01 | [Privilege providers](01-privilege-providers.md) | Root / Shizuku / Sui / ADB / no-root path selection; the `Runner` decision tree; the `LocalServices` binder bridge; root-manager detection; the `SelfPermissions.checkSelfOrRemotePermission()` capability fan-out pattern. |
| 02 | [Backup format](02-backup-format.md) | On-disk layout; metadata schema v1–v6; the five crypto modes (`MODE_NO_ENCRYPTION` / `MODE_AES` / `MODE_RSA` / `MODE_ECC` / `MODE_OPEN_PGP`); platform-Keystore vs file-BKS surfaces; the cross-version restore contract; network-destination considerations. |
| 03 | [Hidden-API bypass](03-hidden-api-bypass.md) | The 4-layer stack: `dev.rikka.tools.refine` + `AndroidHiddenApiBypass` runtime, the `hiddenapi/` stub source set (~80 files across 13 subsystem namespaces), `compat/*Compat.java` wrappers, and `app/` call sites. The Android-version migration cliff and how the proposed Compatibility Harness amortises it. |

## When to update

- A new subsystem becomes load-bearing — write its own doc here.
- A schema, crypto mode, or privilege path changes shape — extend the relevant doc.
- A new Android version's hidden-API surface materially changes — note it in doc 03 §3 alongside the audit verdict.
- A doc starts disagreeing with the source tree — fix the doc, not the source. The source is authoritative.

## Cross-cutting references

- [`docs/audits/`](../audits/) — per-behavior-change verdicts. Read these alongside doc 03 to see what's been actively checked.
- [`docs/audits/README.md`](../audits/README.md) — the audit-doc doctrine that pairs with these architecture docs.
- [`docs/policy/minsdk-21-ceiling.md`](../policy/minsdk-21-ceiling.md) — the dep-floor decision tree that gates several architecture choices (Material Components ceiling, Activity / Biometric / Room / WebKit pinned lines).
- [`PROJECT_CONTEXT.md`](../../PROJECT_CONTEXT.md) — canonical project entry point with reading order.
- [`ROADMAP.md`](../../ROADMAP.md) — the planning surface that schedules architecture changes.

Closes ROADMAP **T11 — Developer Experience → Architecture Documentation** row and
iter-25 backlog item **F-NEW-14**.
