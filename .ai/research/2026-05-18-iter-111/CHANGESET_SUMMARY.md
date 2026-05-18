<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 111 — Magisk `--drop-cap` Opt-In Surface

## Roadmap item

Shipped T9 **Magisk `--drop-cap` Opt-In Surface**.

## Implementation

- Extended `RootCapabilityDiagnostics` to collect Magisk `-v` and `-V` output
  from the active privileged shell when Magisk is available.
- Added a bounded `magiskpolicy --live --print-rules` probe that records whether
  relevant Magisk/capability live-policy rules are visible without dumping the
  entire SELinux policy into the UI.
- Kept `UID` + `/proc/$$/status` `CapEff` as the source-of-truth runtime result,
  then appended Magisk-specific version/default-policy context to Settings ->
  Privileges -> Capability dropping.
- Labeled Magisk v30.7+ as the new opt-in capability-dropping behavior: Linux
  capabilities are kept unless `su` is launched with `--drop-cap`.
- Expanded parser tests for Magisk version capture, live-policy states, and the
  v30.7 version-code floor.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.runner.RootCapabilityDiagnosticsTest --console=plain`

## Sources used

- Official Magisk v30.7 release notes: `https://github.com/topjohnwu/magisk/releases`
- Official Magisk tools documentation: `https://raw.githubusercontent.com/topjohnwu/Magisk/master/docs/tools.md`
