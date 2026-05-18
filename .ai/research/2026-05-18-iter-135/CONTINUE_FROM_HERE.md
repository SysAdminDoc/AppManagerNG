# Continue From Here — Iter 135

## Completed

- Shipped the T11 InstallerX-Revived privilege-elevation cascade row.
- Added installer route chips and temporary ADB -> Shizuku -> root provider activation with configured-mode restore.
- Updated `ROADMAP.md`, `CHANGELOG.md`, and `PROJECT_CONTEXT.md`.

## Verified

- Focused JVM test:
  - `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.apk.installer.InstallerPrivilegeCascadeTest" --console=plain`

## Next roadmap row

- Continue at the next unshipped row after iter 135. In the same section this is likely T12 **Split-APK Cert-Mismatch Dialog**, unless a higher-priority open `Now` row is intentionally selected first.

## Watch points

- Real Dhizuku package-install execution is still blocked by the existing minSdk/API strategy decision. Keep future Dhizuku install work behind that decision instead of adding the API AAR directly.
- Device validation should exercise at least one no-root system-confirmation install and one ADB/Shizuku-backed install to verify the temporary mode restoration is invisible to the user.
