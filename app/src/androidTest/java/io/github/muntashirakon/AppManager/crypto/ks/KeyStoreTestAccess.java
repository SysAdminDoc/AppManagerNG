// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.crypto.RandomChar;
import io.github.muntashirakon.AppManager.utils.Utils;

/**
 * Makes and removes App Manager's KeyStore the way the first-run dialog and its delete button do,
 * for instrumented tests on an install that never opened the app.
 */
public final class KeyStoreTestAccess {
    private KeyStoreTestAccess() {
    }

    public static void savePassword(@NonNull Context context) {
        char[] password = new char[30];
        new RandomChar().nextChars(password);
        try {
            assertTrue("Could not save the KeyStore password",
                    KeyStoreManager.savePass(context, KeyStoreManager.PREF_AM_KEYSTORE_PASS, password));
        } finally {
            Utils.clearChars(password);
        }
    }

    /** Forgets a KeyStore a test made: the copy in memory, its file and its saved password. */
    public static void deleteKeyStore() {
        KeyStoreManager.clearCachedKeyStore();
        assertTrue("Could not delete " + KeyStoreManager.AM_KEYSTORE_FILE,
                !KeyStoreManager.AM_KEYSTORE_FILE.exists() || KeyStoreManager.AM_KEYSTORE_FILE.delete());
        assertTrue("Could not forget the KeyStore password", KeyStoreManager.sSharedPreferences.edit()
                .remove(KeyStoreManager.PREF_AM_KEYSTORE_PASS).commit());
    }
}
