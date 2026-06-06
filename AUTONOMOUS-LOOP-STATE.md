<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Autonomous Loop State

Last updated: 2026-06-06
Assigned project: `C:\Users\--\repos\AppManagerNG`
Current branch: `main`

## Latest Cycle

- Result: completed Cycle 21 implementation for profile membership inverse
  filters.
- Updated: `ROADMAP.md` marks the profile membership inverse row shipped,
  `COMPLETED.md` records the closed profile-filter guardrail, and
  `CHANGELOG.md` records the Unreleased profile membership filter addition.
- Code: the main app-list profile picker now persists an `Exclude selected
  profile` option. `MainViewModel` evaluates selected-profile membership
  separately from the normal main-list filter expression, preserving selected
  user, install-date, search, and refinement filters while supporting include
  and inverse membership for both static package-list profiles and filter-based
  profiles. The filtered empty-state copy distinguishes `In profile: ...` from
  `Not in profile: ...`, and `FilterItem` now exposes single-item match helpers
  for filter-based profile membership checks.
- Verification: passed
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.main.ProfileMembershipFilterTest --tests io.github.muntashirakon.AppManager.filters.FilterItemTest`.
- Environment note: the ignored local `local.properties` still points at
  `C:\Users\--\AppData\Local\Android\Sdk` so Gradle can use the installed SDK on
  this host. Do not commit `local.properties`.

## Next Cycle

- Continue this same assigned project.
- Next roadmap target: `T21-H Material 3 Adaptive layouts for tablets / large
  screens`.
- Start by auditing the current Settings, Main list, and App Details layout and
  navigation surfaces, plus existing `WindowWidthSizeClass` coverage and
  `androidx.window` dependency usage.
- Implementation constraint: the roadmap marks the actual wide-screen layout
  restructure as device-gated; do host-verifiable prep only unless the change can
  be validated without claiming tablet/foldable navigation correctness.
- Verification target: focused JVM tests or source-level guardrails for any prep
  slice, Java compile for touched app code, docs/state update, and
  `rtk git diff --check`.
- Parked follow-ups: device-only Running Apps restore walkthrough, manual
  Android assist invocation, real tag-run attestation verification,
  foreground tracker overlay device walkthroughs, distribution submissions, and
  Android 17 target-SDK gate.
