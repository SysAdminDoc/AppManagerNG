<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue From Here - Iter 121

## Current state

Iter 121 shipped the T9 Language-Selector-style per-app locale row. App Info now
shows and writes Android 13+ per-app language overrides through the privileged
`ILocaleManager` service path for the selected package/user.

## Verification run

- `.\scripts\generate-hidden-api-baseline.ps1`
- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.details.info.AppLocaleOptionsTest --console=plain`
- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.compat.HiddenApiDescriptorBaselineTest --console=plain`
- `.\gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.details.info.AppLocaleOptionsTest --tests io.github.muntashirakon.AppManager.compat.HiddenApiDescriptorBaselineTest --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --console=plain`

## Next roadmap candidates

- The next visible unshipped roadmap row is T9 **InstallerX-Style Biometric
  Install Gate**. NG already has app/session biometric lock surfaces; the row
  asks for an optional per-action biometric gate around install, uninstall, and
  clear-data flows.

## Watch-outs

- Keep the biometric gate optional and scoped per destructive/high-risk action;
  do not make ordinary App Info browsing depend on biometrics.
- Audit existing uninstall, installer, and clear-data entry points before
  adding prompts so batch, direct, and shortcut/automation paths remain
  consistent.
