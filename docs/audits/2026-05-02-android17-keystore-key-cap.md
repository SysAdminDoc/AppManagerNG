# Android 17 per-app Keystore key cap — audit

**Date:** 2026-05-02
**Scope:** ROADMAP T2 "Android 17 Keystore Per-App Key Cap" + Engineering Debt Register row "Android 17 per-app Keystore key cap"
**Reference:** [S55] Android 17 behaviour-changes-all — non-system apps targeting API 37 are limited to 50,000 Keystore keys per app.

## Method

1. `grep -rn "AndroidKeyStore\|KeyGenParameterSpec\|KeyProperties" app/src/main/java`
2. Read every match in context.
3. Cross-reference with `KeyStoreManager` (the app's primary key-management entry point) and every backup-crypto class (`AESCrypto`, `RSACrypto`, `OpenPGPCrypto`).

## Inventory

### AndroidKeyStore alias creation

Exactly **two** static aliases ever land in `AndroidKeyStore`, both in `app/src/main/java/io/github/muntashirakon/AppManager/crypto/ks/CompatUtil.java`:

| Alias constant | Value | Where created | Created when |
|----------------|-------|---------------|--------------|
| `AES_LOCAL_PROTECTION_KEY_ALIAS` | `"aes_local_protection"` | `CompatUtil.getAesGcmLocalProtectionKey()` | API ≥ M, on first encrypt/decrypt — guarded by `keyStore.containsAlias(...)` |
| `RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS` | `"rsa_wrap_local_protection"` | `CompatUtil.getAesGcmLocalProtectionKey()` | API < M only (legacy KitKat–Lollipop path) — guarded by `keyStore.containsAlias(...)` |

Both creation paths are idempotent: the `containsAlias` check returns the existing key on every subsequent call. There is **no per-package, per-backup, or per-operation alias creation** that could accumulate over time.

### File-backed BKS keystore (NOT subject to the cap)

`KeyStoreManager` writes to `am_keystore.bks` (a file-backed Bouncy Castle / Java keystore) at:

```
KeyStoreManager.AM_KEYSTORE_FILE_NAME = "am_keystore.bks"
```

The file-backed keystore is a separate `KeyStore.getInstance("BKS")` instance held in the app's data directory — it is **not** the platform-managed `AndroidKeyStore` and is therefore **not** counted against the per-app 50,000-key limit. Aliases in `am_keystore.bks` (`adb_key`, `signing_key`, `aes_key` for backup encryption, etc.) can grow with app usage but live in user-space storage, not the system keystore.

### Backup-crypto paths

| Path | Storage | Cap-relevant? |
|------|---------|---------------|
| `backup/RestoreOp.java` + `AESCrypto`, `RSACrypto`, `OpenPGPCrypto` | `am_keystore.bks` (BKS) via `KeyStoreManager` | No — file-backed keystore |
| `backup/encryption/...` | Same — derives keys, stores via `KeyStoreManager` | No — file-backed keystore |
| `crypto/ks/CompatUtil.java` (local-protection AES key) | `AndroidKeyStore` | Yes — counts as 1–2 toward the 50K cap |

`grep` across `app/src/main/java/io/github/muntashirakon/AppManager/backup/**` returned **zero** `AndroidKeyStore` references.

### Vendored libraries

`grep -rn "AndroidKeyStore" libs/` returned no matches. The `libs/` AAR-vendored deps (apksig-android, libadb-android, jadx-android, ARSCLib, sora-editor) do not generate `AndroidKeyStore` keys for the host app's UID.

## Conclusion

AppManagerNG cannot exceed the Android 17 per-app `AndroidKeyStore` cap of 50,000 keys: the app generates **at most 2** `AndroidKeyStore` aliases over its entire lifetime on a device, both static and idempotently `containsAlias`-guarded. All backup-crypto paths route through a *file-backed* BKS keystore (`am_keystore.bks`) which is outside the platform-managed Keystore and therefore outside the cap.

No remediation required. The roadmap row and engineering-debt register row are closed.

## Verification commands

```bash
grep -rn "AndroidKeyStore" app/src/main/java
grep -rn "KeyGenParameterSpec\.Builder" app/src/main/java
grep -rn "AndroidKeyStore" libs/
grep -rn "AndroidKeyStore" app/src/main/java/io/github/muntashirakon/AppManager/backup
```

Re-run before bumping `targetSdkVersion` to 37 to confirm no new dynamic `AndroidKeyStore` alias creation has been introduced.
