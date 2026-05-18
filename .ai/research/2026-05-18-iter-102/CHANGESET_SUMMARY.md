# Iter 102 — Tasker Parameterized Intent API

## Roadmap item

- Closed iter-19 T8 `Tasker Parameterized Intent API`.

## Implementation

- Added `AutomationRequest` to normalize public automation requests from:
  - BROWSABLE `am://` operation URIs.
  - legacy `appmanager://run-profile/<id>` profile-run URIs.
  - Tasker/MacroDroid start-activity intents using the existing
    `io.github.sysadmindoc.AppManagerNG.action.*` action constants.
- Added exported `AutomationUriActivity` as the public confirmation-gated entry
  point. It requires normal `BaseActivity` authentication, validates requests,
  shows a confirmation dialog, and then dispatches through existing services:
  `BatchOpsService`, `ProfileApplierService`, `PackageInstallerActivity`, and
  `AppDetailsActivity.getIntentForTrackers()`.
- Added `EXTRA_PROFILE_OVERRIDES`. Profile overrides are parsed as JSON and
  merged into a temporary profile snapshot for one run only; saved profile
  `id`, `name`, and `type` are preserved.
- Extended `AutomationReceiver` so the signature-gated broadcast path accepts
  the same profile override extra.
- Updated `docs/intent-api.md` with public URI shapes, activity-intent usage,
  broadcast extras, and Tasker/MacroDroid guidance.

## Verification

- `git diff --check`
- `./gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.automation.AutomationRequestTest --tests io.github.muntashirakon.AppManager.automation.AutomationIntentsTest --console=plain`
- `./gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `./gradlew.bat :app:assembleFlossDebug --console=plain`
