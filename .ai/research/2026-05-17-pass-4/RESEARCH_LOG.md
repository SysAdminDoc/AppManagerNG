<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# RESEARCH_LOG — 2026-05-17 pass 4

## Queries and checks

- Searched web for `RikkaApps Shizuku releases 13.6.0 Android 17 issue 1965 1967`.
- Opened official GitHub release page and Shizuku issues `#1965`, `#1967`, `#1988`.
- Opened `thedjchi/Shizuku#172` after issue comments pointed to active fork work.
- Queried GitHub REST API for recent Shizuku releases and issue states/comments.
- Re-read pass-3 continuation and changed the pass-4 plan to implement the non-destructive
  code pieces that did not require an Android 17 device.

## Saturation note

No broad competitor remine was run in pass 4. Pass 2/3 already covered the active
Android power-tool competitor surface. This pass was intentionally narrow: Shizuku
Android-17 risk and Android-17 ML-DSA cert-display polish.

## Failed / blocked checks

- `.\gradlew.bat :app:testDebugUnitTest --tests ...ShizukuBridgeTest` failed before Gradle
  started because `JAVA_HOME` is not set and no `java` executable is on `PATH`.
- Searches for local Java/JBR under common Windows locations did not find an installed JDK.
