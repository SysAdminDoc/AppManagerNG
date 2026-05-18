<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 120 — App Info `pm hide` Toggle

## What changed

- Added a dedicated App Info Hide/Unhide horizontal action when AppManagerNG has
  `MANAGE_USERS` through the active privilege path.
- The action calls `PackageManagerCompat.hidePackage()` directly, refreshes App
  Info after success, and shows targeted failure copy for hide vs unhide.
- Hiding AppManagerNG itself now asks for confirmation because the app will
  disappear from launcher/package-list surfaces.
- The existing Hidden tag-cloud badge remains the status indicator; this pass
  deliberately does not mutate the saved freeze method or collapse hide into
  disable/suspend semantics.

## Verification

- `.\gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## Notes

- The first compile attempt caught a duplicate `hide_app` string name from the
  existing freeze-method copy. The quick-action labels now use
  `quick_hide_app` / `quick_unhide_app`.
