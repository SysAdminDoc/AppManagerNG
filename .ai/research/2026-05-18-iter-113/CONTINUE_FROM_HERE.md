<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue From Here — Iter 113

## Current state

- Android 16 full-SDK comparison plumbing is implemented and tested.
- The only current raw `SDK_INT >= 36` scheduled-backup gates now use
  `AndroidUtils.sdkAtLeast(Build.VERSION_CODES.BAKLAVA, 0)`.
- `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md` are updated for
  iter-113.

## Next roadmap candidates

1. T12 **APKEditor `--smali-comment-level basic`** — expose APKEditor decode
   comment-level and remove-annotation options.
2. T9 **KernelSU App Profile Awareness** — natural follow-on to the KernelSU
   diagnostics row.
3. T11 **Inure-Style Batch APK Installer** — batch install flow over selected
   APK paths.

## Verification to preserve

- Keep `AndroidUtilsTest` as the focused guard for full-SDK encoding and
  major/minor comparisons.
- Run `:app:assembleFlossDebug` after version-gate changes because new API
  references must compile against the current SDK and desugaring setup.
