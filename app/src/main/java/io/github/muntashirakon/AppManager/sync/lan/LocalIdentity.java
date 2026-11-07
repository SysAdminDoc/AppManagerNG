// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;

/**
 * This device's long-term P-256 key. Paired devices remember it by fingerprint, so a device that
 * was paired once can be recognised later without comparing digits again. The key never leaves
 * the device: only the public point and signatures over a session transcript are sent.
 */
public final class LocalIdentity {
    @NonNull
    private final KeyPair mKeyPair;
    @NonNull
    private final byte[] mPublicPoint;
    @NonNull
    private final String mFingerprint;

    private LocalIdentity(@NonNull KeyPair keyPair) throws GeneralSecurityException {
        mKeyPair = keyPair;
        mPublicPoint = SyncCrypto.encodePoint(keyPair.getPublic());
        mFingerprint = SyncCrypto.fingerprint(mPublicPoint);
    }

    @NonNull
    public static LocalIdentity generate(@NonNull SecureRandom random) throws GeneralSecurityException {
        return new LocalIdentity(SyncCrypto.generateKeyPair(random));
    }

    @NonNull
    public static LocalIdentity restore(@NonNull byte[] pkcs8PrivateKey, @NonNull byte[] publicPoint)
            throws GeneralSecurityException {
        return new LocalIdentity(SyncCrypto.restoreKeyPair(pkcs8PrivateKey, publicPoint));
    }

    @NonNull
    public byte[] getPublicPoint() {
        return mPublicPoint.clone();
    }

    @NonNull
    public byte[] getEncodedPrivateKey() {
        return mKeyPair.getPrivate().getEncoded();
    }

    @NonNull
    public String getFingerprint() {
        return mFingerprint;
    }

    @NonNull
    byte[] sign(@NonNull byte[] data) throws GeneralSecurityException {
        return SyncCrypto.sign(mKeyPair.getPrivate(), data);
    }
}
