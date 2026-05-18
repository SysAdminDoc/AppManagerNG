# Iter 137 - AGP 9.2 build migration

Date: 2026-05-18

## Roadmap row

- Eng-Debt: AGP 8.13.2 -> 9.2.0 Migration

## Summary

- Upgraded Android Gradle Plugin to 9.2.0 and the Gradle wrapper to 9.4.1.
- Pinned NDK 28.2.13676358 after AGP installed it for the native debug build.
- Converted Android build scripts to Gradle-10-safe assignment syntax.
- Replaced the old `server` module variant hook with `androidComponents` and
  `sdkComponents`, avoiding removed AGP APIs and Gradle 10 execution-time
  `Task.project` warnings.
- Enabled generated `resValue` support explicitly and switched the app to the
  optimized default ProGuard file required by AGP 9.2.
- Hardened host unit-test runtime setup by adding explicit JVM `org.json` and
  hidden-API test dependencies, replacing Android `SparseArray` use in MIUI
  app-op name setup, and letting log setup tolerate missing Android context.

## Verification

- `.\gradlew.bat --version --console=plain`
- `.\gradlew.bat :app:assembleFlossDebug --warning-mode all --console=plain`
- `.\gradlew.bat :app:assembleFullDebug --console=plain`
- `.\gradlew.bat :app:testFlossDebugUnitTest --console=plain`

## Notes

- The Java compiler still reports existing JDK 21 source/target 8 warnings.
  These are not Gradle deprecation warnings and were already outside this row.
- `:app:assembleFlossDebug --warning-mode all` completed without Gradle
  deprecation warnings after the script cleanup.
