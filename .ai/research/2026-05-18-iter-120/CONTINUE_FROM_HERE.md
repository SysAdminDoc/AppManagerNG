<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue From Here — Iter 120

## Current state

Iter 120 shipped the T7 Amarok-style `pm hide` toggle. App Info's horizontal
action strip now exposes Hide or Unhide for installed non-static-shared-library
apps when the current privilege path has `MANAGE_USERS`.

## Verification run

- `.\gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## Next roadmap candidates

- The next visible unshipped roadmap row is T9 **Language-Selector Per-App
  Locale**: App Info already reads per-app locale; the row asks for an Android
  13+ write path through the existing privileged/hidden-API access patterns.

## Watch-outs

- Do not treat per-app locale as a no-root app preference. It requires Android
  13+ framework APIs and a privilege path that can reach hidden or system APIs.
- Keep multi-user behavior explicit. App Info already tracks `mUserId`; locale
  writes should apply to the selected user/package pair, not always the owner
  user.
