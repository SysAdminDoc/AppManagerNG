// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.security.NoSuchAlgorithmException;

public class KeyStoreUtilsTest {
    @Test
    public void getSupportedKeyFactoryAlgorithmAcceptsRsaAndEcAliases() throws Exception {
        assertEquals("RSA", KeyStoreUtils.getSupportedKeyFactoryAlgorithm("RSA"));
        assertEquals("EC", KeyStoreUtils.getSupportedKeyFactoryAlgorithm("EC"));
        assertEquals("EC", KeyStoreUtils.getSupportedKeyFactoryAlgorithm("ECDH"));
        assertEquals("EC", KeyStoreUtils.getSupportedKeyFactoryAlgorithm("ECDSA"));
    }

    @Test
    public void getSupportedKeyFactoryAlgorithmRejectsUnsupportedAlgorithm() {
        assertThrows(NoSuchAlgorithmException.class,
                () -> KeyStoreUtils.getSupportedKeyFactoryAlgorithm("DSA"));
    }
}
