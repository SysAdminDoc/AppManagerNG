# Continue from iter 139

## Current state

- ML-DSA certificate OID labels were already covered by iter 125.
- ML-DSA key algorithm labels are now covered by iter 139.
- Compile SDK remains 36; Android 17 `KeyProperties` ML-DSA constants are
  represented as strings until compile SDK 37 lands.

## Next roadmap scan

- Keystore 50K HKDF-from-master backup key derivation is the next nearby T9
  implementation row.
- Persistent ADB tcpip 5555 Detection in Shizuku Setup is also a small T5 row
  nearby if the keystore row proves coupled to future backup-key work.

## Suggested first commands

```powershell
git status --short --branch
Select-String -Path ROADMAP.md -Pattern "Keystore 50K|HKDF|tcpip" -Context 2,3
rg "AESCrypto|am_keystore|KeyStore|generate.*key|backup key|AES" app/src/main/java app/src/test/java
```
