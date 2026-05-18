# Continue from iter 137

## Current state

- AGP is pinned to 9.2.0 in `versions.gradle`.
- Gradle wrapper is pinned to 9.4.1 with the published distribution checksum.
- Native debug builds pin NDK 28.2.13676358.
- Floss debug, full debug, and floss debug JVM unit tests pass on the upgraded
  toolchain.

## Next roadmap scan

- Material Components 1.14 remains gated on a stable release and the minSdk 23
  decision. Read `docs/policy/minsdk-21-ceiling.md` before changing it.
- Compose Material 3 1.5.0-alpha19 is a later note only; NG is still an Android
  Views app and `codexprompt.md` explicitly blocks a Compose migration proposal.
- Android 17 ML-DSA Keystore `KeyPairGenerator` Recognition appears next, but
  iter 125 already closed the certificate OID display-name mapping. Inspect
  `app/src/main/java/io/github/muntashirakon/AppManager/apk/signing/` and the
  iter-125 notes before deciding whether the remaining algorithm-enumeration
  premise is real or duplicate.
- Keystore 50K HKDF-from-master derivation is likely the next substantive T9
  implementation row if the ML-DSA `KeyPairGenerator` row proves duplicate.
- Persistent ADB tcpip 5555 Detection in Shizuku Setup is also actionable after
  the nearby audit-only Android 17 rows.

## Suggested first commands

```powershell
git status --short --branch
Select-String -Path ROADMAP.md -Pattern "\*\*Next\*\*" -Context 0,2
.\gradlew.bat :app:assembleFlossDebug --console=plain
```
