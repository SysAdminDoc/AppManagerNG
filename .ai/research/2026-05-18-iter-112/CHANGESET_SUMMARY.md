<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 112 — KernelSU Sulog & Seccomp Status

## Roadmap item

Shipped T9 **KernelSU Sulog & Seccomp Status**.

## Implementation

- Added `KernelSuDiagnostics` for Settings -> Privileges. It only activates
  when `RootManagerInfo` confirms KernelSU through the privileged
  `/data/adb/ksu` marker, keeping installed-package-only detection labeled as
  unavailable.
- Read AppManagerNG's current process seccomp mode through
  `Os.prctl(PR_GET_SECCOMP, ...)`, matching KernelSU's per-app seccomp status
  model without depending on KernelSU Manager internals.
- Tailed `/data/adb/ksu/log/sulog` through the active privileged shell and
  surfaced recent denial-style lines (`deny`, `denied`, `reject`, `fail`,
  `avc`) in a copyable details dialog.
- Added a Settings -> Privileges KernelSU row and strings for active,
  unavailable, missing-sulog, no-denials, and probe-failure states.
- Added an Open action that launches KernelSU Manager or KernelSU Next when a
  visible manager launcher exists.
- Added focused parser coverage for seccomp labels, injected `prctl` mode,
  missing seccomp handling, and sulog denial collection.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.runner.KernelSuDiagnosticsTest --console=plain`

## Sources used

- Roadmap source S123: `https://github.com/tiann/KernelSU/releases`
