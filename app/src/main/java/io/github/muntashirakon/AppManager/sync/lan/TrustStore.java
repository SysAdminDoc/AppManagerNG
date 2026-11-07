// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.TreeMap;

/**
 * This device's Local sync identity key and the devices it remembers. Kept in its own preference
 * file that backups and device transfers leave out, so a copy of the key never shows up on
 * another phone.
 */
public final class TrustStore implements TrustedDevices {
    public static final String PREFS_NAME = "lan_sync_trust";
    private static final String KEY_IDENTITY_PRIVATE = "identity_private";
    private static final String KEY_IDENTITY_PUBLIC = "identity_public";
    private static final String TRUSTED_PREFIX = "trusted.";

    @NonNull
    private final SharedPreferences mPrefs;

    public TrustStore(@NonNull Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Loads the identity key, creating one the first time or when the stored one is unreadable.
     * Replacing it means devices that remembered this one will ask to compare digits again.
     */
    @WorkerThread
    @NonNull
    public synchronized LocalIdentity getOrCreateIdentity(@NonNull SecureRandom random)
            throws GeneralSecurityException {
        String privateHex = mPrefs.getString(KEY_IDENTITY_PRIVATE, null);
        String publicHex = mPrefs.getString(KEY_IDENTITY_PUBLIC, null);
        if (privateHex != null && publicHex != null) {
            try {
                return LocalIdentity.restore(SyncCrypto.fromHex(privateHex), SyncCrypto.fromHex(publicHex));
            } catch (GeneralSecurityException | IllegalArgumentException ignore) {
                // Unreadable key: start over with a new one below
            }
        }
        LocalIdentity identity = LocalIdentity.generate(random);
        mPrefs.edit()
                .putString(KEY_IDENTITY_PRIVATE, SyncCrypto.toHex(identity.getEncodedPrivateKey()))
                .putString(KEY_IDENTITY_PUBLIC, SyncCrypto.toHex(identity.getPublicPoint()))
                .apply();
        return identity;
    }

    @Override
    public synchronized boolean isTrusted(@NonNull String fingerprint) {
        return mPrefs.contains(TRUSTED_PREFIX + fingerprint);
    }

    @Override
    public synchronized void remember(@NonNull String fingerprint, @NonNull String name) {
        mPrefs.edit().putString(TRUSTED_PREFIX + fingerprint, name).apply();
    }

    @Override
    public synchronized void forget(@NonNull String fingerprint) {
        mPrefs.edit().remove(TRUSTED_PREFIX + fingerprint).apply();
    }

    @NonNull
    @Override
    public synchronized Map<String, String> list() {
        Map<String, String> devices = new TreeMap<>();
        for (Map.Entry<String, ?> entry : mPrefs.getAll().entrySet()) {
            if (entry.getKey().startsWith(TRUSTED_PREFIX) && entry.getValue() instanceof String) {
                devices.put(entry.getKey().substring(TRUSTED_PREFIX.length()), (String) entry.getValue());
            }
        }
        return devices;
    }
}
