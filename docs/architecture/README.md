<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# docs/architecture/

Architecture notes for AppManagerNG. The tracked repository currently keeps this
index as the durable architecture surface; deeper topic docs may exist in local
or archived working copies, but they are not part of the tracked checkout unless
listed below.

## Documents

| # | Topic | What it covers |
|---|---|---|
| Index | This file | Tracked architecture status, update triggers, and cross-cutting references. |

## Current Verdicts

- 2026-06-11: Android 16 hidden-API stubs were ported from upstream
  `MuntashirAkon/AppManager` commits `eff7f587c` and `04ed88d03` into
  AppManagerNG commits `62aded77a` and `4daff20f`. Local verification:
  `:hiddenapi:compileDebugJavaWithJavac :app:compileFlossDebugJavaWithJavac`.
  The instrumented emulator gate now runs the hidden-API compatibility baseline
  on API 36 and API 37.

## When to update

- A new subsystem becomes load-bearing — write or track its own doc here.
- A schema, crypto mode, or privilege path changes shape — extend the relevant tracked doc.
- A new Android version's hidden-API surface materially changes — note it above alongside the audit verdict.
- A doc starts disagreeing with the source tree — fix the doc, not the source. The source is authoritative.

## Cross-cutting references

- [`docs/audits/`](../audits/) — per-behavior-change verdicts.
- [`docs/audits/README.md`](../audits/README.md) — the audit-doc doctrine that pairs with these architecture docs.
- [`ROADMAP.md`](../../ROADMAP.md) — the planning surface that schedules architecture changes.

Closes ROADMAP **T11 — Developer Experience → Architecture Documentation** row and
iter-25 backlog item **F-NEW-14**.
