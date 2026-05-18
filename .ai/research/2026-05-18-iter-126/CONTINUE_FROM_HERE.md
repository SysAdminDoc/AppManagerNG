<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — after iter 126

## Current state

- Branch: `main`
- Latest completed row: T9 **Android 17 cleartext Deprecation Warning**
- Validation completed:
  - `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.details.info.AppInfoViewModelCleartextTest" --console=plain`
  - `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## What just shipped

App Info now warns when an inspected app sets manifest-wide cleartext traffic
without a Network Security Config. The implementation uses the existing
hidden-API shim layer to read `networkSecurityConfigRes`, derives a focused
tag-cloud bit in `AppInfoViewModel`, and renders a caution tag/dialog in
`AppInfoFragment`.

## Next visible roadmap work

The next visible unshipped `Next` row after iter 126 is:

| Row | Tier | Status |
| --- | --- | --- |
| **Material You / Monet Widget Theming** | Premium-Polish | **Next** |

## Notes for the next pass

- Before editing widget resources, inspect the existing widget providers,
  layouts, and any Glance/widget roadmap notes. The row asks for Monet/dynamic
  color awareness without forcing a Compose migration.
- Skip the adjacent Dhizuku and FireOS rows for now because they are marked
  `Later` / `Under Consideration`, not active `Next`.
