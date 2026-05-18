<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->

# Iter 125 — Android 17 ML-DSA certificate OID closure

## Roadmap row

T9 **Android 17 ML-DSA Certificate OID Recognition** is shipped.

## What changed

- Verified live source already maps Android 17 ML-DSA-65 and ML-DSA-87
  signature OIDs in `Utils.getCertificateSignatureAlgorithmName()`.
- Verified Package Info and Scanner both use the shared helper.
- Verified Package Info still displays the canonical signature OID alongside the
  readable algorithm name.
- Removed `Utils`' class-load dependency on `OsEnvironment` by making the Termux
  login path a direct `/data/data/...` constant. This lets the ML-DSA regression
  test run in the JVM unit-test runner without hitting hidden `UserHandle`
  methods during static initialization.
- Updated `ROADMAP.md`, `CHANGELOG.md`, `PROJECT_CONTEXT.md`, and the ML-DSA
  audit note.

## Local validation

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.utils.UtilsCertificateAlgorithmTest" --console=plain` passed.

## Notes

- The first focused test run failed before the assertions with
  `NoSuchMethodError: android.os.UserHandle.myUserId()` from `OsEnvironment`
  static initialization. The path-constant change fixed that root cause.
