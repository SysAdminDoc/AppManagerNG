<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 119 — Digital Assistant Quick Actions

## What changed

- Added `AssistActionActivity`, a dedicated transparent `android.intent.action.ASSIST`
  entry point for the long-press home / power Digital Assistant gesture.
- Moved the `ACTION_ASSIST` manifest route away from the generic
  `ActivityInterceptor` and onto the new assistant activity.
- Added `AssistTargetResolver`, which prefers platform assist target extras and
  falls back to the newest resumed usage-event package when Usage Access is
  available.
- The assistant sheet offers Force Stop, Freeze/Unfreeze, and Open app details
  actions according to the active privilege path, plus a Running Apps fallback
  when no foreground package can be resolved.
- Added focused resolver coverage in `AssistTargetResolverTest`.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.assistant.AssistTargetResolverTest --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## Notes

- Users still need to choose AppManagerNG as the system Digital Assistant/default
  assist app in Android settings before the platform sends assistant gestures to
  this handler.
