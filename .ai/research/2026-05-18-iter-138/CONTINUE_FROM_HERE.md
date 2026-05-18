# Continue from iter 138

## Current state

- Material Components remains pinned at 1.13.0.
- Material 1.14.0 stable exists, but it is blocked by the API 23 floor.
- Do not raise `min_sdk` unless a dedicated roadmap row accepts dropping API
  21-22 devices and updates `CONTRIBUTING.md`, onboarding copy, the minSdk
  ledger, and the pinned dependency cluster together.

## Next roadmap scan

- Compose Material 3 1.5.0-alpha19 remains a later note only.
- Android 17 ML-DSA Keystore `KeyPairGenerator` Recognition is the next nearby
  T9 row. Compare it against iter 125, which already closed ML-DSA certificate
  OID display-name mapping, before writing code.
- If the ML-DSA row is duplicate/stale, move to the Keystore 50K HKDF row or
  Persistent ADB tcpip 5555 Detection in Shizuku Setup.

## Suggested first commands

```powershell
git status --short --branch
Select-String -Path ROADMAP.md -Pattern "ML-DSA|HKDF|tcpip" -Context 2,3
rg "ML-DSA|KEY_ALGORITHM_ML_DSA|KeyPairGenerator|CertUtils" app/src hiddenapi
```
