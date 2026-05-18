# Iter 140 - AES backup archive-key derivation

Date: 2026-05-18

## Roadmap row

- T9: Keystore 50K Cap - HKDF-from-Master Backup Key Derivation

## Summary

- Bumped the current backup metadata version from 6 to 7.
- Added AES-mode archive-key derivation in `AESCrypto`.
- New metadata-v7 AES backups derive their content key with HKDF-SHA256 from
  the file-backed `am_keystore.bks` AES master key and archive IV.
- v6-and-older AES backups keep using the historical master-key path for
  restore compatibility.
- RSA/ECC hybrid backups remain unchanged because they already use an encrypted
  per-archive AES key.
- Updated the Android 17 keystore-cap audit addendum: the change does not add
  any dynamic `AndroidKeyStore` aliases.

## Verification

- `.\gradlew.bat :app:testFlossDebugUnitTest --tests "io.github.muntashirakon.AppManager.crypto.AESCryptoTest" --console=plain`

## Notes

- The first focused run caught a Java compile issue caused by treating
  `CryptoException` as an `Exception`; the catch was simplified and the focused
  test then passed.
- Existing Java 8 source/target and deprecation warnings still print under JDK
  21.
