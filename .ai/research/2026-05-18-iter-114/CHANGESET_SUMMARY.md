<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 114 — APKEditor `--smali-comment-level basic`

## Roadmap item

Shipped T12 **APKEditor `--smali-comment-level basic`** for the decode path
that exists in NG today.

## Implementation

- Added `SmaliDecodeOptions` as the central option contract for decoded smali:
  - `none`
  - `basic` (default)
  - `verbose`
- Mapped those levels onto the currently available Google smali/baksmali flags:
  `debugInfo`, `codeOffsets`, and `accessorComments`.
- Added an opt-in post-processor that strips common Nullable / NotNull /
  RequiresApi annotation blocks from decoded smali output.
- Threaded the options through `DexFileSystem` into `DexClasses`, preserving
  existing constructor defaults for other call sites.
- Added Settings -> File Manager controls for the comment level and annotation
  stripping toggle.
- Added focused JVM coverage for comment-level mapping, default normalization,
  annotation-block stripping, and trailing-newline preservation.

## Scope note

The repo still has no embedded APKEditor CLI/library decode-rebuild backend.
This iteration intentionally applied the APKEditor-inspired option contract to
the existing File Manager / Dex VFS smali decode path instead of adding an
unused APKEditor dependency.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.dex.SmaliDecodeOptionsTest --console=plain`

## Sources used

- Roadmap source S127: `https://github.com/REAndroid/APKEditor/releases`
