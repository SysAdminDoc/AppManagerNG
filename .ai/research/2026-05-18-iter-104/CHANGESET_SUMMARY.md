# Iter 104 — OEM Debloat-Blocker Bypass

## Roadmap item

- Closed iter-19 T7 `OEM Debloat-Blocker Bypass`.

## Implementation

- Extended `OemBloatRiskTable` with uninstall-fallback policy keyed by
  package name plus manufacturer/build/vendor-version signals.
  - Samsung One UI 8.5: `com.samsung.android.smartsuggestions`.
  - MIUI-family devices: `com.miui.core`.
  - OPlus / ColorOS / OxygenOS / Realme builds: exact uninstall-guarded
    package IDs for OPlus system recovery, OTA, security, app-platform, and
    safecenter components.
- Debloater rows and the details dialog now surface the OEM-protected warning.
- Batch remove confirmation now counts protected selections and defaults the
  primary action to `Disable instead`, routing protected targets through the
  existing freeze batch operation.
- Explicit removal remains available as a separate override action for users
  who intentionally want to attempt uninstall/remove-for-user anyway.

## Verification

- `./gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.debloat.OemBloatRiskTableTest --console=plain`
- `./gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `./gradlew.bat :app:assembleFlossDebug --console=plain`
