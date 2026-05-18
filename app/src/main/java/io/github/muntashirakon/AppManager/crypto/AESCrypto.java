// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.io.CipherOutputStream;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.GCMModeCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;

import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.crypto.ks.SecretKeyCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

public class AESCrypto implements Crypto {
    public static final String TAG = "AESCrypto";

    public static final String AES_EXT = ".aes";
    public static final String AES_KEY_ALIAS = "backup_aes";
    public static final int GCM_IV_SIZE_BYTES = 12;
    public static final int MAC_SIZE_BITS_OLD = 32;
    public static final int MAC_SIZE_BITS = 128;
    public static final int FILE_IV_DERIVATION_VERSION = 6;
    public static final int ARCHIVE_KEY_DERIVATION_VERSION = 7;

    private static final byte[] FILE_IV_DERIVATION_DOMAIN =
            "AppManagerNG AES-GCM file IV v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ARCHIVE_KEY_DERIVATION_DOMAIN =
            "AppManagerNG AES-GCM archive key v1".getBytes(StandardCharsets.UTF_8);
    private static final String HKDF_ALGORITHM = "HmacSHA256";

    private final SecretKey mSecretKey;
    private final byte[] mIv;
    @CryptoUtils.Mode
    private final String mParentMode;

    private int mMacSizeBits = MAC_SIZE_BITS;
    private boolean mDeriveIvPerFile;

    public AESCrypto(@NonNull byte[] iv) throws CryptoException {
        this(iv, false);
    }

    public AESCrypto(@NonNull byte[] iv, boolean deriveArchiveKey) throws CryptoException {
        this(iv, CryptoUtils.MODE_AES, null, deriveArchiveKey);
    }

    @NonNull
    @Override
    public String getModeName() {
        return mParentMode;
    }

    protected AESCrypto(@NonNull byte[] iv, @NonNull @CryptoUtils.Mode String mode, @Nullable byte[] encryptedAesKey)
            throws CryptoException {
        this(iv, mode, encryptedAesKey, false);
    }

    protected AESCrypto(@NonNull byte[] iv, @NonNull @CryptoUtils.Mode String mode, @Nullable byte[] encryptedAesKey,
                        boolean deriveArchiveKey)
            throws CryptoException {
        mIv = iv;
        mParentMode = mode;
        SecretKey secretKey;
        switch (mParentMode) {
            case CryptoUtils.MODE_AES:
                SecretKey masterSecretKey = null;
                try {
                    KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
                    masterSecretKey = keyStoreManager.getSecretKey(AES_KEY_ALIAS);
                    if (masterSecretKey == null) {
                        throw new CryptoException("No SecretKey with alias " + AES_KEY_ALIAS);
                    }
                    secretKey = deriveArchiveKey ? getArchiveSecretKey(masterSecretKey, iv) : masterSecretKey;
                } catch (Exception e) {
                    throw new CryptoException(e);
                } finally {
                    if (deriveArchiveKey && masterSecretKey != null) {
                        destroyKey(masterSecretKey);
                    }
                }
                break;
            case CryptoUtils.MODE_RSA:
                // Hybrid encryption using RSA
                if (encryptedAesKey == null) {
                    // No encryption key provided, generate one
                    secretKey = RSACrypto.generateAesKey();
                } else {
                    // Encryption key provided
                    secretKey = RSACrypto.decryptAesKey(encryptedAesKey);
                }
                break;
            case CryptoUtils.MODE_ECC:
                // Hybrid encryption using ECC
                if (encryptedAesKey == null) {
                    // No encryption key provided, generate one
                    secretKey = ECCCrypto.generateAesKey();
                } else {
                    // Encryption key provided
                    secretKey = ECCCrypto.decryptAesKey(encryptedAesKey);
                }
                break;
            default:
                throw new CryptoException("Unsupported mode " + mParentMode);
        }
        mSecretKey = secretKey;
    }

    public void setMacSizeBits(int macSizeBits) {
        if (macSizeBits == MAC_SIZE_BITS || macSizeBits == MAC_SIZE_BITS_OLD) {
            mMacSizeBits = macSizeBits;
        }
    }

    public void setFileIvDerivationEnabled(boolean enabled) {
        mDeriveIvPerFile = enabled;
    }

    @NonNull
    private AEADParameters getParams() {
        return getParams(mIv);
    }

    @NonNull
    private AEADParameters getParams(@NonNull byte[] iv) {
        // We need to generate it dynamically due to MAC size issues
        return new AEADParameters(new KeyParameter(mSecretKey.getEncoded()), mMacSizeBits, iv);
    }

    @CallSuper
    @NonNull
    protected byte[] getEncryptedAesKey() throws CryptoException {
        if (mParentMode.equals(CryptoUtils.MODE_RSA)) {
            return RSACrypto.encryptAesKey(mSecretKey);
        }
        if (mParentMode.equals(CryptoUtils.MODE_ECC)) {
            return ECCCrypto.encryptAesKey(mSecretKey);
        }
        // Invalid mode
        throw new CryptoException("Not in RSA or ECC mode");
    }

    @WorkerThread
    @Override
    public void encrypt(@NonNull Path[] inputFiles, @NonNull Path[] outputFiles) throws IOException {
        handleFiles(true, inputFiles, outputFiles);
    }

    @Override
    public void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream)
            throws IOException {
        // Init cipher
        GCMModeCipher cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
        cipher.init(true, getParams());
        // Convert unencrypted stream to encrypted stream
        try (OutputStream cipherOS = new CipherOutputStream(encryptedStream, cipher)) {
            IoUtils.copy(unencryptedStream, cipherOS);
        }
    }

    @WorkerThread
    @Override
    public void decrypt(@NonNull Path[] inputFiles, @NonNull Path[] outputFiles) throws IOException {
        handleFiles(false, inputFiles, outputFiles);
    }

    @Override
    public void decrypt(@NonNull InputStream encryptedStream, @NonNull OutputStream unencryptedStream)
            throws IOException {
        // Init cipher
        GCMModeCipher cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
        cipher.init(false, getParams());
        // Convert encrypted stream to unencrypted stream
        try (InputStream cipherIS = new CipherInputStream(encryptedStream, cipher)) {
            IoUtils.copy(cipherIS, unencryptedStream);
        }
    }

    @WorkerThread
    private void handleFiles(boolean forEncryption, @NonNull Path[] inputFiles, @NonNull Path[] outputFiles) throws IOException {
        // `files` is never null here
        if (inputFiles.length == 0) {
            Log.d(TAG, "No files to de/encrypt");
            return;
        }
        if (inputFiles.length != outputFiles.length) {
            throw new IOException("The number of input and output files are not the same.");
        }
        // Encrypt/decrypt files
        for (int i = 0; i < inputFiles.length; i++) {
            Path inputPath = inputFiles[i];
            Path outputPath = outputFiles[i];
            Log.i(TAG, "Input: %s\nOutput: %s", inputPath, outputPath);
            String canonicalFileName = getCanonicalFileNameForIv(forEncryption, inputPath.getName(), outputPath.getName(),
                    CryptoUtils.getExtension(mParentMode));
            GCMModeCipher cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
            cipher.init(forEncryption, getParams(getIvForFile(canonicalFileName)));
            try (InputStream is = inputPath.openInputStream();
                 OutputStream os = outputPath.openOutputStream()) {
                if (forEncryption) {
                    try (OutputStream cipherOS = new CipherOutputStream(os, cipher)) {
                        IoUtils.copy(is, cipherOS);
                    }
                } else {  // Cipher.DECRYPT_MODE
                    try (InputStream cipherIS = new CipherInputStream(is, cipher)) {
                        IoUtils.copy(cipherIS, os);
                    }
                }
            }
        }
        // Total success
    }

    @NonNull
    private byte[] getIvForFile(@NonNull String canonicalFileName) {
        if (!mDeriveIvPerFile) {
            return mIv;
        }
        return deriveIvForFile(mIv, canonicalFileName);
    }

    @VisibleForTesting
    @NonNull
    static String getCanonicalFileNameForIv(boolean forEncryption, @NonNull String inputName,
                                            @NonNull String outputName, @NonNull String cryptoExtension) {
        String candidate = forEncryption ? outputName : inputName;
        if (!cryptoExtension.isEmpty() && candidate.endsWith(cryptoExtension)) {
            return candidate.substring(0, candidate.length() - cryptoExtension.length());
        }
        return candidate;
    }

    @VisibleForTesting
    @NonNull
    static byte[] deriveIvForFile(@NonNull byte[] baseIv, @NonNull String canonicalFileName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(FILE_IV_DERIVATION_DOMAIN);
            digest.update((byte) 0);
            digest.update(baseIv);
            digest.update((byte) 0);
            digest.update(canonicalFileName.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(digest.digest(), GCM_IV_SIZE_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    @NonNull
    private static SecretKey getArchiveSecretKey(@NonNull SecretKey masterSecretKey, @NonNull byte[] archiveIv)
            throws CryptoException {
        byte[] masterKey = masterSecretKey.getEncoded();
        if (masterKey == null || (masterKey.length != 16 && masterKey.length != 32)) {
            throw new CryptoException("AES master key must be 128 or 256 bits.");
        }
        byte[] archiveKey = deriveArchiveKey(masterKey, archiveIv, masterKey.length);
        try {
            return new SecretKeySpec(archiveKey, "AES");
        } finally {
            Utils.clearBytes(masterKey);
            Utils.clearBytes(archiveKey);
        }
    }

    @VisibleForTesting
    @NonNull
    static byte[] deriveArchiveKey(@NonNull byte[] masterKey, @NonNull byte[] archiveIv, int keyLengthBytes)
            throws CryptoException {
        if (keyLengthBytes <= 0 || keyLengthBytes > 32) {
            throw new CryptoException("Invalid AES archive key length " + keyLengthBytes);
        }
        try {
            byte[] prk = hmac(archiveIv, masterKey);
            try {
                return hkdfExpand(prk, ARCHIVE_KEY_DERIVATION_DOMAIN, keyLengthBytes);
            } finally {
                Utils.clearBytes(prk);
            }
        } catch (Exception e) {
            throw new CryptoException(e);
        }
    }

    @NonNull
    private static byte[] hkdfExpand(@NonNull byte[] pseudoRandomKey, @NonNull byte[] info, int length)
            throws Exception {
        byte[] output = new byte[length];
        byte[] previous = new byte[0];
        int offset = 0;
        int counter = 1;
        while (offset < length) {
            Mac mac = Mac.getInstance(HKDF_ALGORITHM);
            mac.init(new SecretKeySpec(pseudoRandomKey, HKDF_ALGORITHM));
            mac.update(previous);
            mac.update(info);
            mac.update((byte) counter);
            previous = mac.doFinal();
            int copyLength = Math.min(previous.length, length - offset);
            System.arraycopy(previous, 0, output, offset, copyLength);
            offset += copyLength;
            ++counter;
        }
        Utils.clearBytes(previous);
        return output;
    }

    @NonNull
    private static byte[] hmac(@NonNull byte[] key, @NonNull byte[] data) throws Exception {
        Mac mac = Mac.getInstance(HKDF_ALGORITHM);
        mac.init(new SecretKeySpec(key, HKDF_ALGORITHM));
        return mac.doFinal(data);
    }

    @Override
    public void close() {
        destroyKey(mSecretKey);
    }

    private static void destroyKey(@NonNull SecretKey secretKey) {
        try {
            SecretKeyCompat.destroy(secretKey);
        } catch (DestroyFailedException e) {
            Log.w(TAG, "Could not destroy AES secret key.", e);
        }
    }
}
