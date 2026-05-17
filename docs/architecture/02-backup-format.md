<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Architecture: backup format

AppManagerNG's backup engine is the largest single subsystem outside the main app/
package tree. This document describes the on-disk format, the cryptographic envelope,
the metadata schema versioning, and the round-trip restore contract.

**Source roots:**
- [`app/src/main/java/io/github/muntashirakon/AppManager/backup/`](../../app/src/main/java/io/github/muntashirakon/AppManager/backup/) — the engine (`BackupOp`, `RestoreOp`, `VerifyOp`, `BackupManager`, `MetadataManager`, `BackupItems`, `BackupCryptSetupHelper`, `CryptoUtils`).
- [`app/src/main/java/io/github/muntashirakon/AppManager/crypto/`](../../app/src/main/java/io/github/muntashirakon/AppManager/crypto/) — the cipher implementations (`AESCrypto`, `RSACrypto`, `ECCCrypto`, `OpenPGPCrypto`, `DummyCrypto`).
- [`app/src/main/java/io/github/muntashirakon/AppManager/crypto/ks/`](../../app/src/main/java/io/github/muntashirakon/AppManager/crypto/ks/) — the BKS file-backed keystore (`KeyStoreManager` + helpers).

---

## 1. On-disk layout (one backup per app)

A single backup is a **directory tree under the user-selected backup root**, namespaced
by package name and user-id-plus-tag:

```
<backup-root>/
  <pkg>/                            e.g. "com.example.app/"
    <userId>_<tag>/                 e.g. "0_default/", "0_pre-upgrade/"
      info_v5.am.json               metadata (header + crypto envelope, schema v5/v6)
      meta_v5.am.json               package metadata (versionName/Code, signatures, etc.)
      <data>.tar.gz.<ext>           tar-gzipped data parts (one per data category)
      master.key                    encrypted master AES key (when applicable)
      <certs/.cert>                 signing-cert pubkey
      misc/                         everything else (icon, screenshots, etc.)
      checksums.txt                 SHA-256 / SHA-512 / etc. per file (algo from header)
```

- **`<userId>_<tag>`** namespaces per-Android-user backups and per-snapshot tags (user-supplied label or default).
- **Data parts** are split per data-category — APK base, APK splits, internal data (`/data/data/<pkg>`), external data (`/sdcard/Android/data/<pkg>`), OBB (`/sdcard/Android/obb/<pkg>`), media, extras (SSAID, permissions, battery-opt). The engine writes one tar per category that the user opted into via `BackupFlags`.
- **`checksums.txt`** lets `RestoreOp` and `VerifyOp` reject tampered or partially-flushed restores before the actual restore writes touch the live data.

---

## 2. Metadata schema versioning

`MetadataManager` reads/writes the JSON header. Schema versions in source today:

| Version | Marker on disk | Introduced | What changed |
|---|---|---|---|
| **v1–v3** | (legacy) | Pre-fork | Original schemas inherited from upstream. Read-only; new backups never written at this version. |
| **v4** | `info_v4.am.json` | Upstream | 64-bit GCM MAC stabilization. |
| **v5** | `info_v5.am.json` | Upstream | 128-bit GCM MAC; current default for unencrypted + RSA + ECC + PGP backups; encrypted AES used a single archive-level IV. |
| **v6** | `info_v6.am.json` (or v5 header with `metadata.metadataVersion = 6`) | **NG iter-23 (2026-05-16)** | **Per-file AES-GCM IV derivation** — the IV for each file is derived from the archive-level IV + the canonical filename. Closes the GCM cipher-reuse issue ([S138] / [`docs/audits/2026-05-08-gcm-cipher-reuse-large-backup.md`](../audits/2026-05-08-gcm-cipher-reuse-large-backup.md)) for new backups while keeping legacy backups restorable with the historical behaviour. |

The schema version is stored in the header JSON itself; `RestoreOp` branches at parse
time. **Old metadata-v5 backups remain fully restorable** under v6-aware AM — this is a
load-bearing compatibility constraint and any future schema bump must do the same.

---

## 3. Crypto modes — `CryptoUtils.MODE_*`

`CryptoUtils` selects between five concrete implementations. The picker is exposed in
the backup-flags UI as "Encryption: …".

| `MODE_*` | Class | What it does | Key material |
|---|---|---|---|
| `MODE_NO_ENCRYPTION` | [`DummyCrypto`](../../app/src/main/java/io/github/muntashirakon/AppManager/crypto/DummyCrypto.java) | Pass-through; no encryption (still SHA-256-checksummed). | None. |
| `MODE_AES` | [`AESCrypto`](../../app/src/main/java/io/github/muntashirakon/AppManager/crypto/AESCrypto.java) | AES-256-GCM. **Android Keystore-backed `SecretKey`** — non-exportable, hardware-isolated where TEE is available. Per-file IV derivation as of v6. | `AndroidKeyStore` aliases `aes_local_protection` (M+) / `rsa_wrap_local_protection` (pre-M legacy wrap). |
| `MODE_RSA` | [`RSACrypto`](../../app/src/main/java/io/github/muntashirakon/AppManager/crypto/RSACrypto.java) | RSA-OAEP-wrapped AES session key per backup. User holds the RSA private key in the file-backed BKS keystore. | File-backed BKS (`am_keystore.bks` via `KeyStoreManager`). |
| `MODE_ECC` | [`ECCCrypto`](../../app/src/main/java/io/github/muntashirakon/AppManager/crypto/ECCCrypto.java) | ECIES-style hybrid — ECDH + AES-GCM. | File-backed BKS. |
| `MODE_OPEN_PGP` | [`OpenPGPCrypto`](../../app/src/main/java/io/github/muntashirakon/AppManager/crypto/OpenPGPCrypto.java) | OpenPGP via the **OpenIntents OpenPGP API**. The user's PGP provider (typically OpenKeychain) holds the keys; NG never sees the secret material. | External — selected via `Prefs.Encryption.getOpenPgpProvider()`. |

Two keystore surfaces exist:

- **Platform `AndroidKeyStore`** — used only by `AESCrypto` for the `aes_local_protection` alias (and `rsa_wrap_local_protection` legacy on pre-M). **At most 2 aliases over NG's lifetime** per the [`docs/audits/2026-05-02-android17-keystore-key-cap.md`](../audits/2026-05-02-android17-keystore-key-cap.md) verdict; comfortably under Android 17's 50,000-alias-per-UID cap.
- **File-backed BKS keystore** (`am_keystore.bks`) — under `crypto/ks/`, used by RSA, ECC, and as a recovery path. Lives **outside** `AndroidKeyStore` so it doesn't count against the per-UID cap and migrates across devices via the backup mechanism itself.

---

## 4. Backup pipeline (BackupOp)

[`BackupOp.runBackup()`](../../app/src/main/java/io/github/muntashirakon/AppManager/backup/BackupOp.java)
walks the data categories the user enabled in `BackupFlags`. For each category:

1. **Enumerate** source paths (e.g. `/data/data/<pkg>` via privileged shell).
2. **Tar+gzip** the paths to the staging directory.
3. **Encrypt** the tar with the selected `Crypto` implementation (or pass-through for `DummyCrypto`).
4. **Compute and persist** the SHA-256 (algorithm name read back from `info.checksumAlgo`) into `BackupItems.Checksum` and emit `checksums.txt`.
5. On any error, raise `BackupException` — the `BackupManager` orchestrator cleans staging dirs and reports the per-app outcome.

For v6 metadata: each file's GCM IV is `HKDF(archiveIv, canonicalFilename)` rather than a
single archive-level IV — closing the cipher-reuse risk for the ~2GB+ OBB case where the
GCM IV space is exhausted ([S138]).

---

## 5. Restore pipeline (RestoreOp + VerifyOp)

`VerifyOp` runs first: recomputes the digest of every file the metadata names, raises
`BackupException` on mismatch. Then `RestoreOp` walks the same data categories in
reverse and writes back via the active privileged shell.

**Cross-version restore contract** — `RestoreOp` MUST be able to restore any backup
written by any prior schema version of AM/NG. This is enforced by:

- `info.checksumAlgo` is stored, not assumed — a backup made with SHA-256 still restores under SHA-256 even if NG ships SHA-512 by default in a future schema.
- The metadata-v5 IV-reuse behaviour is preserved for old backups; only v6+ uses the derived per-file IV.
- 64-bit GCM MAC (v3-and-older) is still accepted at restore time; new backups write 128-bit (v4+).

A breaking change to any of those would orphan every user's existing backups and is a
non-starter.

---

## 6. Network destinations (T6 row, open)

The backup engine itself writes through `DocumentFile` SAF abstractions, so any
SAF-aware DocumentsProvider works as a destination today (Material Files SMB / WebDAV,
Solid Explorer, Files by Google, etc.). Direct first-class SMB / WebDAV / cloud
destinations are tracked in ROADMAP T6 as open items.

A known reliability issue at the `WriteableByteChannel.write()` layer was documented in
Neo-Backup #1029 (S191): short-write race on SAF SMB providers can corrupt streams
mid-write. Any work on T6 SMB / WebDAV destinations should adopt **flush-then-fsync per
chunk** rather than relying on the SAF stream's terminal flush.

See [`docs/distribution/backup-destinations.md`](../distribution/backup-destinations.md)
for the user-facing destination matrix and per-DocumentsProvider reliability notes.

---

## 7. Cross-references

- [`docs/audits/2026-05-08-gcm-cipher-reuse-large-backup.md`](../audits/2026-05-08-gcm-cipher-reuse-large-backup.md) — IV-reuse confirmation that drove the v6 schema.
- [`docs/audits/2026-05-02-android17-keystore-key-cap.md`](../audits/2026-05-02-android17-keystore-key-cap.md) — verdict on the per-UID 50K-alias cap.
- [`docs/audits/2026-05-08-zip-slip-protection.md`](../audits/2026-05-08-zip-slip-protection.md) — extraction-path containment.
- ROADMAP **T6 — Backup Polish** — the user-facing tier.
- ROADMAP **iter-19 [S138]** — AM #1958 GCM cipher reuse upstream issue.
- ROADMAP **iter-20 [S208]** — `hddq/restoid` (root + restic chunked AEAD) — alternative architectural reference for the >2 GB case.

---

## 8. Things this doc deliberately does not cover

- **The exact JSON shape of `info_vN.am.json` and `meta_vN.am.json`** — read `MetadataManager` and the `struct/` sub-package; they version-evolve faster than a doc would track.
- **`adb/` and `convert/` subpackages** — they handle the upstream "import adb-backup file" and "convert OAndBackup/SwiftBackup format" cases respectively. Out of scope for the canonical NG backup format.
- **Backup retention / scheduling** — both are open ROADMAP T6 items; will need their own doc when shipped.
