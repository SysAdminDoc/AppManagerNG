// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;

public class ECCCrypto extends AESCrypto {
    public static final String TAG = "ECCCrypto";

    public static final String ECC_EXT = ".ecc";
    public static final String ECC_KEY_ALIAS = "backup_ecc";

    private static final String ECC_CIPHER_TYPE = "ECIES";
    private static final int AES_KEY_SIZE_BITS = 256;

    public ECCCrypto(@NonNull byte[] iv, @Nullable byte[] encryptedAesKey) throws CryptoException {
        super(iv, CryptoUtils.MODE_ECC, encryptedAesKey);
    }

    @NonNull
    @Override
    public byte[] getEncryptedAesKey() throws CryptoException {
        return super.getEncryptedAesKey();
    }

    @NonNull
    static SecretKey generateAesKey() {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[AES_KEY_SIZE_BITS/8];
        random.nextBytes(key);
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        Utils.clearBytes(key);
        return secretKey;
    }

    @NonNull
    static SecretKey decryptAesKey(@NonNull byte[] encryptedAesKey) throws CryptoException {
        KeyPair keyPair;
        try {
            KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
            keyPair = keyStoreManager.getKeyPair(ECC_KEY_ALIAS);
            if (keyPair == null) {
                throw new CryptoException("No KeyPair with alias " + ECC_KEY_ALIAS);
            }
        } catch (Exception e) {
            throw new CryptoException(e);
        }
        try {
            Cipher cipher = Cipher.getInstance(ECC_CIPHER_TYPE, new BouncyCastleProvider());
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivateKey());
            byte[] rawKey = cipher.doFinal(encryptedAesKey);
            try {
                return new SecretKeySpec(rawKey, "AES");
            } finally {
                java.util.Arrays.fill(rawKey, (byte) 0);
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException
                | IllegalBlockSizeException e) {
            throw new CryptoException(e);
        }
    }

    @NonNull
    static byte[] encryptAesKey(@NonNull SecretKey key) throws CryptoException {
        KeyPair keyPair;
        try {
            KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
            keyPair = keyStoreManager.getKeyPair(ECC_KEY_ALIAS);
            if (keyPair == null) {
                throw new CryptoException("No KeyPair with alias " + ECC_KEY_ALIAS);
            }
        } catch (Exception e) {
            throw new CryptoException(e);
        }
        byte[] encoded = key.getEncoded();
        try {
            Cipher cipher = Cipher.getInstance(ECC_CIPHER_TYPE, new BouncyCastleProvider());
            cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublicKey());
            return cipher.doFinal(encoded);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException
                | IllegalBlockSizeException e) {
            throw new CryptoException(e);
        } finally {
            java.util.Arrays.fill(encoded, (byte) 0);
        }
    }
}
