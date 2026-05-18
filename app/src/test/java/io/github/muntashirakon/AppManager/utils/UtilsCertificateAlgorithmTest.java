// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UtilsCertificateAlgorithmTest {
    @Test
    public void prettifySignatureAlgorithmNameMapsAndroid17MlDsaOids() {
        assertEquals("ML-DSA-65 (Dilithium)", Utils.prettifySignatureAlgorithmName(
                "1.3.6.1.4.1.2.267.12.6.5", "1.3.6.1.4.1.2.267.12.6.5"));
        assertEquals("ML-DSA-87 (Dilithium)", Utils.prettifySignatureAlgorithmName(
                "1.3.6.1.4.1.2.267.12.8.7", "1.3.6.1.4.1.2.267.12.8.7"));
    }

    @Test
    public void prettifySignatureAlgorithmNamePreservesUnknownFallback() {
        assertEquals("SHA256withRSA", Utils.prettifySignatureAlgorithmName(
                "1.2.840.113549.1.1.11", "SHA256withRSA"));
        assertEquals("", Utils.prettifySignatureAlgorithmName("1.2.3.4", null));
    }

    @Test
    public void prettifyKeyAlgorithmNameMapsAndroid17MlDsaAlgorithms() {
        assertEquals("ML-DSA (Dilithium)", Utils.prettifyKeyAlgorithmName("ML-DSA"));
        assertEquals("ML-DSA-65 (Dilithium)", Utils.prettifyKeyAlgorithmName("ML-DSA-65"));
        assertEquals("ML-DSA-87 (Dilithium)", Utils.prettifyKeyAlgorithmName("ML-DSA-87"));
    }

    @Test
    public void prettifyKeyAlgorithmNamePreservesUnknownFallback() {
        assertEquals("RSA", Utils.prettifyKeyAlgorithmName("RSA"));
        assertEquals("", Utils.prettifyKeyAlgorithmName(null));
    }
}
