<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Continue from here — after iter 125

## Current state

- Branch: `main`
- Latest completed row: T9 **Android 17 ML-DSA Certificate OID Recognition**
- Validation completed:
  - `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.utils.UtilsCertificateAlgorithmTest" --console=plain`

## What just shipped

The ML-DSA row was already implemented in live source, but the regression test
was not runnable in the JVM test runner because loading `Utils` initialized
`OsEnvironment` and hit hidden `UserHandle.myUserId()`. Iter-125 removed that
class-load dependency and closed the roadmap row with a passing focused test.

## Next visible roadmap work

The next visible unshipped `Next` row after iter 125 is:

| Row | Tier | Status |
| --- | --- | --- |
| **Android 17 cleartext Deprecation Warning** | T9 | **Next** |

## Notes for the next pass

- Inspect the App Info Network Security panel before editing. The row asks for a
  badge when an app sets `android:usesCleartextTraffic="true"` without a network
  security config.
- Check whether manifest/network-security parsing already exposes both values;
  if so, keep the patch in the display layer.
