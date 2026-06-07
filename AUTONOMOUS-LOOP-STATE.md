<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Autonomous Loop State

Last updated: 2026-06-07
Assigned project: `C:\Users\--\repos\AppManagerNG`
Current branch: `main`

## Latest Cycle

- Result: completed Cycle 182 source-audit closure for crash share intent
  attachment hardening.
- Updated: `ROADMAP.md`, `COMPLETED.md`, and `CHANGELOG.md` now record the
  crash share intent attachment hardening and its verification target.
- Code: crash notification sharing now builds its `ACTION_SEND` intent through a
  tested helper; crash attachments have pinned URI stream, read-grant, and
  `ClipData` behavior while no-attachment crash shares stay text-only.
- Verification: passed
  `:app:compileFullDebugJavaWithJavac :app:testFullDebugUnitTest --tests io.github.muntashirakon.AppManager.misc.AMExceptionHandlerTest --tests io.github.muntashirakon.AppManager.misc.SupportInfoBundleTest`;
  `rtk git diff --check`; and prohibited tool/attribution diff scan.
- Environment note: the ignored local `local.properties` still points at
  `C:\Users\--\AppData\Local\Android\Sdk` so Gradle can use the installed SDK on
  this host. Do not commit `local.properties`.

## Next Cycle

- Continue this same assigned project.
- Next roadmap target: continue auditing one-off share builders and chooser
  metadata for host-verifiable URI-grant and metadata defects.
- Check whether diagnostic ZIP sharing still trusts nullable metadata, omits
  chooser grant evidence, or exposes unchecked provider-controlled values that
  can be pinned with focused host tests.
- Verification target: focused JVM/static tests for any source change, Java
  compile for touched app code, docs/state update, and `rtk git diff --check`.
- Parked follow-ups: device-only Running Apps restore walkthrough, manual
  Android assist invocation, real tag-run attestation verification,
  foreground tracker overlay device walkthroughs, distribution submissions, and
  Android 17 target-SDK gate.
