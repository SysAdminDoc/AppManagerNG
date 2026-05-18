<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue From Here — Iter 112

## Current state

- KernelSU sulog/seccomp diagnostics are implemented in Settings ->
  Privileges.
- Focused JVM parser coverage passes.
- `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md` are updated for
  iter-112.

## Next roadmap candidates

1. T2 **Android 16 `SDK_INT_FULL` Plumbing Audit** — centralize SDK-major/minor
   comparisons behind an Android utility without behavior change for minor `0`.
2. T12 **APKEditor `--smali-comment-level basic`** — expose APKEditor decode
   comment-level and remove-annotation options.
3. T9 **KernelSU App Profile Awareness** — builds naturally on this iteration's
   KernelSU diagnostics surface.

## Verification to preserve

- Keep `KernelSuDiagnosticsTest` as the focused regression test for sulog
  parsing and seccomp mode formatting.
- Run `:app:assembleFlossDebug` after nearby Settings -> Privileges changes
  because XML preference wiring and string placeholders are compile-time
  checked there.
