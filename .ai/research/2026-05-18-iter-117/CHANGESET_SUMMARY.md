<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 117 — Termux SELinux Context Display

## Roadmap item

Shipped T9 **Termux SELinux Context Display**.

## Implementation

- Added `AppSelinuxContexts` as a focused App Info helper for SELinux context
  collection and normalization.
- App Info now records `seInfo`, data/source file contexts, and running-process
  SELinux contexts in `AppInfoViewModel.AppInfo`.
- The App Info More Info section now surfaces copyable rows for SELinux policy
  info, SELinux file contexts, and SELinux process contexts.
- Live process contexts are matched by package list or package-prefixed process
  name, then read from `/proc/<pid>/attr/current`; blank or unreadable contexts
  are omitted.
- Added focused Robolectric coverage for matching, normalization, missing
  process-name fallback, and no-match behavior.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.details.info.AppSelinuxContextsTest --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## Sources used

- Roadmap source S126: Termux v0.118.3 SELinux environment context reference.
