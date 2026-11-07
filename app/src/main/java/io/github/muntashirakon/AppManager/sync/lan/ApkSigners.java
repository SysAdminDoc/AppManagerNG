// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.Nullable;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

/**
 * Turns signer certificates into the SHA-256 digests Local sync compares.
 */
public final class ApkSigners {
    /**
     * Reads signers from an APK file with apksig and only accepts a signature that verifies.
     */
    public static final ApkSignatureGate.DigestReader VERIFIED_FILE_READER =
            apk -> fromSignerInfo(PackageUtils.getVerifiedSignerInfo(apk));

    private ApkSigners() {
    }

    @Nullable
    public static ApkSignatureGate.SignerDigests fromSignerInfo(@Nullable SignerInfo signerInfo) {
        if (signerInfo == null) {
            return null;
        }
        List<String> current = digestsOf(signerInfo.getCurrentSignerCerts());
        if (current == null || current.isEmpty()) {
            return null;
        }
        List<String> lineage = digestsOf(signerInfo.getSignerCertsInLineage());
        return new ApkSignatureGate.SignerDigests(current,
                lineage != null ? lineage : Collections.<String>emptyList());
    }

    @Nullable
    private static List<String> digestsOf(@Nullable X509Certificate[] certs) {
        if (certs == null) {
            return null;
        }
        List<String> digests = new ArrayList<>(certs.length);
        for (X509Certificate cert : certs) {
            try {
                digests.add(DigestUtils.getHexDigest(DigestUtils.SHA_256, cert.getEncoded()));
            } catch (CertificateEncodingException e) {
                return null;
            }
        }
        return digests;
    }
}
