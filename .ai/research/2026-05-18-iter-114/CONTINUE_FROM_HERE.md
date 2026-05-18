<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue From Here — Iter 114

## Current state

- File Manager now exposes smali decode comment-level preferences:
  `none`, `basic`, and `verbose`.
- `basic` is the default and preserves the existing debug-build line-info
  behavior while disabling extra code-offset/accessor comments.
- Optional annotation stripping removes common Nullable / NotNull / RequiresApi
  annotation blocks from decoded smali.
- The settings feed `DexFileSystem`, which passes the selected options into
  `DexClasses`.
- `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md` are updated for
  iter-114.

## Next roadmap candidates

1. T9 **KernelSU App Profile Awareness** — natural follow-on to the KernelSU
   diagnostics row.
2. T11 **Inure-Style Batch APK Installer** — batch install flow over selected
   APK paths.
3. T9 **Termux SELinux Context Display** — app-security surface for process and
   file SELinux context display.

## Verification to preserve

- Keep `SmaliDecodeOptionsTest` focused on comment-level mapping and stripping
  edge cases.
- Run `:app:assembleFlossDebug` after preference/resource changes.
