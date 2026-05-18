<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 118 — JADX 1.5.5 Viewer Follow-Up Triage

## Roadmap items

Parked:

- T12 **JADX 1.5.5 `.apks` Ingestion + UI Zoom**
- T10 **JADX 1.5.5 FlatLaf CJK Composite Font**

## Audit result

- `versions.gradle` pins `jadx_version = "1.4.7"` for the MuntashirAkon
  Android JADX fork.
- `app/build.gradle` only depends on `jadx-core` and `jadx-dex-input`.
- The only live JADX call site is `DexUtils.toJavaCode()`, which creates a
  temporary dex from smali/class definitions and asks `JadxDecompiler` for the
  first generated Java class.
- AppManagerNG has no embedded JADX GUI, no external JADX CLI handoff, no
  decompile-pane zoom preference, and no Swing/FlatLaf host.

## Decision

Both rows are real acceptance criteria for the future T12 JADX viewer, but
adding `.apks` handoff or FlatLaf `UIManager` wiring now would create dead code.
The roadmap rows are parked as blocked by the main T12 viewer/dependency-upgrade
slice.

## Verification

- `rg -n "DexUtils|decompile\\(|JadxArgs|jadx" app/src/main/java app/src/test/java -g "*.java"`
- `rg -n "JADX|jadx|Code tab|decompiler|decompile|FlatLaf|input-apks|zoom" app build.gradle versions.gradle gradle.properties settings.gradle docs ROADMAP.md`
