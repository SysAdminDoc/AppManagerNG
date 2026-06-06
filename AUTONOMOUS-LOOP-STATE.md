<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Autonomous Loop State

Last updated: 2026-06-06
Assigned project: `C:\Users\--\repos\AppManagerNG`
Current branch: `main`

## Latest Cycle

- Result: completed Cycle 23 host-verifiable guardrail for T21-H Main/App
  Details activity embedding.
- Updated: `ROADMAP.md` records the T21-H Main/App Details split contract
  guardrail and `CHANGELOG.md` records the Unreleased split contract test.
- Code: `MainActivityEmbeddingContractTest` pins the existing
  `main_activity_splits.xml` MainActivity/AppDetails split pair, placeholder
  rule, split minimum width, split ratio, finish behavior, locale layout
  direction, and manifest exposure for the embedded activities. The actual
  tablet/foldable navigation walkthrough remains device-gated.
- Verification: passed
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.main.MainActivityEmbeddingContractTest --tests io.github.muntashirakon.AppManager.main.WindowWidthSizeClassTest`.
- Environment note: the ignored local `local.properties` still points at
  `C:\Users\--\AppData\Local\Android\Sdk` so Gradle can use the installed SDK on
  this host. Do not commit `local.properties`.

## Next Cycle

- Continue this same assigned project.
- Next roadmap target: continue `T21-H Material 3 Adaptive layouts for tablets /
  large screens`.
- Start by auditing whether any remaining T21-H work can be validated from host
  tests without changing runtime tablet/foldable navigation. If the remaining
  T21-H work is only device-gated, move to the next open host-verifiable roadmap
  item or promote a new source-backed audit slice.
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
