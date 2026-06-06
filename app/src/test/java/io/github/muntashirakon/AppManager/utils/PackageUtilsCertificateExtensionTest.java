// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PackageUtilsCertificateExtensionTest {
    @Test
    public void certificateExtensionLabelIncludesKnownDescription() {
        assertEquals("keyUsage (Restrictions on allowed cryptographic operations)",
                PackageUtils.getCertificateExtensionLabel("2.5.29.15"));
        assertEquals("AuthorityInfoAccess (OCSP and issuer certificate access methods)",
                PackageUtils.getCertificateExtensionLabel("1.3.6.1.5.5.7.1.1"));
    }

    @Test
    public void certificateExtensionLabelFallsBackToUnknownOid() {
        assertEquals("1.2.3.4.5", PackageUtils.getCertificateExtensionLabel("1.2.3.4.5"));
    }
}
