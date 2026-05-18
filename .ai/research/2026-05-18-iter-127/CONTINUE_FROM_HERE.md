<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — after iter 127

## Current state

- Branch: `main`
- Latest completed row: Premium-Polish **Material You / Monet Widget Theming**
- Validation completed:
  - `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## What just shipped

Existing home-screen widgets now use a shared widget palette helper. Usage
widgets, one-click Clear Cache, and Log Recording apply dynamic text/icon/surface
tints at update time, and the XML layouts use dynamic Material color attrs with
Android 12+ system color mappings for picker previews and fallback rendering.

## Next visible roadmap work

The next visible unshipped `Next` row after iter 127 is:

| Row | Tier | Status |
| --- | --- | --- |
| **Default-App Role Re-Binding After Restore** | T6 | **Next** |

## Notes for the next pass

- Start by inspecting the backup metadata model and restore flow to see whether
  default-app role state is captured today. The row requires prompting via
  `RoleManager.createRequestRoleIntent(...)` after restoring apps that used to
  hold dialer, SMS, home, or browser roles.
- If role state is not currently captured, keep the first slice small: persist
  role ownership in backup metadata, then add restore-time prompt orchestration.
