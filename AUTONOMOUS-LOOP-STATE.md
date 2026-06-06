<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Autonomous Loop State

Last updated: 2026-06-06
Assigned project: `C:\Users\--\repos\AppManagerNG`
Current branch: `main`

## Latest Cycle

- Result: completed Cycle 20 implementation for Private Space/profile
  visibility diagnostics.
- Updated: `ROADMAP.md` marks the Private Space/profile visibility row shipped,
  `COMPLETED.md` records the closed profile-diagnostics guardrail, and
  `CHANGELOG.md` records the Unreleased profile visibility addition.
- Code: `ProfileVisibilityDiagnostics` centralizes user/profile metadata mapping
  for classic `UserInfo` flags plus reflected current platform `userType` and
  private-profile signals. `UserInfo.toLocalizedString()` now labels Private
  Space, work, clone, guest, restricted, generic profile, quiet/locked,
  disabled, and ephemeral states across the existing shared user selector
  surfaces. Advanced -> Selected users now explains when Android 15+ hidden
  profiles may be not visible from the current mode/state, and the empty
  app-list copy points users at profile visibility.
- Verification: passed
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.users.ProfileVisibilityDiagnosticsTest`.
  Manual Android 15+ Private Space locked/unlocked/hidden walkthrough remains
  device-gated.
- Environment note: the ignored local `local.properties` still points at
  `C:\Users\--\AppData\Local\Android\Sdk` so Gradle can use the installed SDK on
  this host. Do not commit `local.properties`.

## Next Cycle

- Continue this same assigned project.
- Next host-verifiable roadmap target: `P2 - Add profile membership inverse
  filters`.
- Start by inspecting `MainListOptions`, `MainViewModel`, the profile filter
  state, Finder/filter option reuse, and existing profile/filter tests.
- Implementation constraint: support both "in selected profile" and "not in
  selected profile" without breaking static package-list profiles or
  filter-based profiles.
- Verification target: focused JVM tests for included/excluded profile
  membership filtering, Java compile for touched app code, docs/state update,
  and `rtk git diff --check`.
- Parked follow-ups: device-only Running Apps restore walkthrough, manual
  Android assist invocation, real tag-run attestation verification,
  foreground tracker overlay device walkthroughs, distribution submissions, and
  Android 17 target-SDK gate.
