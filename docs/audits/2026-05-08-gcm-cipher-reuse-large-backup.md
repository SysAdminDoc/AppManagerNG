<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# GCM Cipher Reuse on Large / Multi-File Backups — Audit

**Date:** 2026-05-08
**Roadmap reference:** [ROADMAP.md](../../ROADMAP.md) — Now / T6 row "GCM Cipher Reuse on Large OBB Backup".
**Outcome:** ⚠️ **CONFIRMED BUG. Remediation requires a backup format version bump and is FLAGGED FOR DESIGN before code change.**

## Background

Upstream AM issue #1958 ([S138]) reported that backups of large (>2 GB) OBB archives produce corrupt output: the GCM authentication tag fails verification on restore, suggesting the IV got reset mid-stream or the cipher was reused for multiple plaintexts under the same key+IV pair.

GCM mode has a **hard cryptographic invariant**: a given (key, IV) pair must NEVER encrypt more than one distinct plaintext. Reuse silently breaks confidentiality (XORing two ciphertexts with the same keystream leaks the XOR of the plaintexts) and breaks the authentication tag (the receiver can't tell which plaintext belongs to which tag). This is a textbook nonce-reuse vulnerability.

## Scope

Trace the encrypt and decrypt paths in [`AESCrypto`](../../app/src/main/java/io/github/muntashirakon/AppManager/crypto/AESCrypto.java) end-to-end and identify any code path where a single `(key, IV)` is reused across two or more distinct plaintexts.

## Finding

In [`AESCrypto.handleFiles()`](../../app/src/main/java/io/github/muntashirakon/AppManager/crypto/AESCrypto.java) (lines 161–192 at audit time), the GCM cipher is **instantiated and initialized exactly once before the per-file for loop**:

```java
GCMModeCipher cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
cipher.init(forEncryption, getParams());
for (int i = 0; i < inputFiles.length; i++) {
    // ... open is + os ...
    try (OutputStream cipherOS = new CipherOutputStream(os, cipher)) {
        IoUtils.copy(is, cipherOS);
    }
    // closing cipherOS calls cipher.doFinal() → writes GCM auth tag,
    // leaves cipher in terminal state
}
```

Each loop iteration opens a new `CipherOutputStream` wrapping the **same** `cipher` instance. After iteration 0's `try-with-resources` closes, `CipherOutputStream.close()` calls `cipher.doFinal()`, which appends the GCM authentication tag to file 0's stream and leaves the cipher in a finalized state. Iteration 1 then writes through that same finalized cipher, with behavior that depends on BouncyCastle's `GCMBlockCipher` internals — either an `IllegalStateException` (fail-fast) or a silent reset that re-uses the same key+IV (the catastrophic case the upstream issue describes).

`getParams()` (line 106) returns an `AEADParameters` constructed from the same fixed `mIv` field every call, so even if the cipher were re-initialized between files, it would still encrypt every file with the same IV.

The single-file path (`encrypt(InputStream, OutputStream)` at line 131) creates its own cipher and isn't affected; only `handleFiles(...)` with `inputFiles.length > 1` triggers the bug.

## Threat model

A backup operation that calls `handleFiles(...)` with multiple input files (e.g. APK + data + OBB + external data + KeyStore) produces files where:

- **File 0** decrypts correctly.
- **Files 1..N** are at minimum unauthenticated (tag verification will fail on restore — the upstream issue's symptom) and at worst leak partial plaintext via keystream-reuse XOR cryptanalysis if an attacker can guess parts of file 0 or any other file.

This matches the upstream issue: large OBB backups end up split across `handleFiles` paired with other backup components, so the bug surfaces preferentially on large/multi-file operations.

## Remediation options

The fix CANNOT be a one-line cipher re-init inside the loop because the `mIv` field is fixed across the call — re-init with the same IV is still a nonce-reuse bug. Three viable directions, in increasing order of disruption:

### Option A — Per-file derived IV via HKDF-Expand (preferred)

Keep the on-disk format compatible by deriving each file's IV from the master IV plus the file index:

```
ivᵢ = HKDF-Expand(masterIv ‖ key, "AESCrypto.fileIv" ‖ i, 12 bytes)
```

The master IV stays in the backup metadata as today; restore-time derivation is deterministic from the file order. No format change, no metadata change. **Backups produced by the old code stay broken** (no way to make a backup with one IV decrypt correctly to multiple files), but new backups become safe.

### Option B — Per-file fresh IV stored alongside ciphertext

Generate a fresh `generateIv()` IV per file, prepend the 12-byte IV to each output file's ciphertext, strip it back off on decrypt. This is the **cryptographically simplest** correct option but requires:

- A new backup metadata version (so restore-time logic knows to read the IV prefix).
- A migration path or graceful failure for old single-master-IV backups (probably: "this backup was produced with a known-broken version; please re-create it from a current install").

### Option C — Fresh `Crypto` instance per file

Hoist the cipher construction into the loop, generate a fresh IV inside, and store the per-file IV in metadata. Functionally equivalent to Option B but with a wider blast radius across `BackupCryptSetupHelper` / `BackupMetadataV5` / restore logic.

## Recommendation

**Hold the code change until a design decision** picks between Option A (no format change, but old backups stay broken — which they already are) and Option B (clean format, but harder migration story). Either option requires a regression test that round-trips a synthetic 4 GB blob across the multi-file path, asserting:

- Each file's auth tag verifies.
- Files 0..N XOR'd against each other don't reveal a low-entropy mask (the keystream-reuse-detection signal).

Until the fix lands, large multi-file backups (apk + data + obb) under AES mode CANNOT be trusted to restore. Single-file path and OpenPGP / RSA / ECC modes are unaffected.

## Action items

1. Open a tracking issue on the NG repo summarizing the finding + the three remediation options.
2. Pin the existing single-master-IV format behind a feature flag once the new path lands; surface a banner on AES-mode backup creation pointing users at the new format.
3. Add an in-app warning ("Avoid AES-mode for backups containing large OBB or multi-file data until v0.X.0") on the backup dialog until remediation ships. **Tracked separately** — UX surfacing is not part of this audit's scope.

## References

- [S138]: https://github.com/MuntashirAkon/AppManager/issues/1958 — upstream report of GCM cipher reuse / IV reset on >2 GB OBB backups.
- NIST SP 800-38D §8.3 — GCM nonce uniqueness requirement.
- BouncyCastle `GCMBlockCipher` Javadoc — finalized-state reuse semantics.
