# Iter 103 — Hidden-Shizuku Fork Detection

## Roadmap item

- Closed iter-19 T5 `Hidden-Shizuku Fork Detection`.

## Implementation

- Added Shizuku manager package resolution in `ShizukuBridge`.
  - Primary signal: package declaring `moe.shizuku.manager.permission.API_V23`.
  - Legacy fallback: package declaring `moe.shizuku.api.SHIZUKU_SERVICE`.
  - Final fallback: canonical `moe.shizuku.privileged.api`.
- Routed manager-version lookup, trusted-WLAN auto-start / app-info intents, and
  Shizuku-manager clear-data warnings through the resolved manager package.
- Preserved the existing binder and permission checks. The change only broadens
  manager package discovery for renamed/obfuscated Shizuku-compatible managers.
- Updated roadmap, changelog, project context, and privilege-provider docs.

## Verification

- `./gradlew.bat :app:testFlossDebugUnitTest --tests io.github.muntashirakon.AppManager.shizuku.ShizukuBridgeTest --console=plain`
- `./gradlew.bat :app:compileFlossDebugJavaWithJavac --console=plain`
- `./gradlew.bat :app:assembleFlossDebug --console=plain`
