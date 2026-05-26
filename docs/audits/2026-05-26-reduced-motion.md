<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Reduced-Motion Audit Slice

Date: 2026-05-26.
Status: partial source hardening; manual device verification remains open.

## Scope

- App-owned Material shared-axis transitions in Settings, Help, and Code
  Editor.
- App-owned fragment transaction animations in Settings and Scanner.
- UI tracker overlay window animation.
- System animation-scale detection via `Settings.Global`.

## Fixed

- Added `MotionUtils.shouldReduceMotion(Context)`, which treats any of
  `window_animation_scale`, `transition_animation_scale`, or
  `animator_duration_scale` equal to `0` as a request to reduce motion.
- Centralized Material shared-axis setup so Settings subpages skip enter/return
  transitions when reduced motion is active.
- Routed Help and Code Editor search show/hide transitions through the same
  reduced-motion gate.
- Routed Settings and Scanner fragment transaction animations through the
  reduced-motion gate.
- Disabled the UI tracker overlay toast-style window animation when reduced
  motion is active.
- Added Robolectric coverage for disabled, enabled, missing, and malformed
  animation-scale settings.

## Still Open

- Manual verification on device/emulator with system animation scales set to
  `0`: Settings navigation, Scanner navigation, Help search, Code Editor
  search, and UI tracker overlay show/minimize/expand.
- Material component internal motion that is not directly controlled by this
  app, including progress indicator show/hide behavior, FAB hide-on-scroll
  behavior, ripple feedback, dialogs, and bottom sheets.
- Predictive-back and OEM "Remove animations" behavior beyond the three global
  animation-scale settings.
