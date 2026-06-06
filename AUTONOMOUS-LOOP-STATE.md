<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Autonomous Loop State

Last updated: 2026-06-06
Assigned project: `C:\Users\--\repos\AppManagerNG`
Current branch: `main`

## Latest Cycle

- Result: completed Cycle 19 implementation for foreground tracker overlay
  hardening.
- Updated: `ROADMAP.md` marks the tracker overlay hardening row shipped,
  `COMPLETED.md` records the closed reliability guardrail, and `CHANGELOG.md`
  records the Unreleased tracker-overlay fix.
- Code: `TrackerOverlayPolicy` centralizes the tracker overlay type, flags,
  safe width, centered-offset clamps, layout-update throttle, and repeated
  failure threshold. `TrackerWindow` now uses `TYPE_ACCESSIBILITY_OVERLAY` on
  API 22+, removes `FLAG_LAYOUT_NO_LIMITS`, refreshes metrics before layout
  updates, clamps expanded/iconified positions, throttles update storms, and
  disables the tracker with an explanation after repeated `WindowManager`
  add/update failures.
- Verification: passed
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.accessibility.activity.TrackerOverlayPolicyTest --tests io.github.muntashirakon.AppManager.accessibility.ActionLabelAccessibilityContractTest`.
  Manual Android 11/12+/current-device overlay walkthroughs remain device-gated.
- Environment note: the ignored local `local.properties` still points at
  `C:\Users\--\AppData\Local\Android\Sdk` so Gradle can use the installed SDK on
  this host. Do not commit `local.properties`.

## Next Cycle

- Continue this same assigned project.
- Next host-verifiable roadmap target: `P2 - Add Private Space/profile
  visibility diagnostics`.
- Start by inspecting `Users`, `PackageManagerCompat`, manifest hidden-profile
  permissions, main-list user/profile selection copy, and any existing profile
  diagnostics tests.
- Implementation constraint: surface honest profile/private-space visibility
  diagnostics from public API signals without implying locked/unavailable
  profiles were scanned.
- Verification target: focused JVM tests for the extracted profile visibility
  mapper, Java compile for touched app code, docs/state update, and `rtk git
  diff --check`.
- Parked follow-ups: device-only Running Apps restore walkthrough, manual
  Android assist invocation, real tag-run attestation verification,
  foreground tracker overlay device walkthroughs, distribution submissions, and
  Android 17 target-SDK gate.
