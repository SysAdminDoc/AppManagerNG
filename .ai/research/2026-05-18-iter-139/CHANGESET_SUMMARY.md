# Iter 139 - Android 17 ML-DSA key algorithm display

Date: 2026-05-18

## Roadmap row

- T9: Android 17 ML-DSA Keystore `KeyPairGenerator` Recognition

## Summary

- Iter 125 already closed ML-DSA certificate OID display names.
- This pass closes the separate Android 17 `KeyProperties` key algorithm path.
- Added `Utils.prettifyKeyAlgorithmName()` for `ML-DSA`, `ML-DSA-65`, and
  `ML-DSA-87`.
- Package Info public-key algorithm rows now use the shared helper.
- Signer verification logging now uses the same helper for public-key algorithm
  output.
- The helper keeps Android 17 algorithm values as strings so the project can
  stay on compile SDK 36 until the targetSdk 37 batch.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.utils.UtilsCertificateAlgorithmTest" --console=plain`

## Notes

- The focused test run still prints the existing JDK 21 source/target 8 and
  deprecation warnings. The build passed.
