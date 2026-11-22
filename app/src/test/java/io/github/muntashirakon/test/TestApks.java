// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.test;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.apksig.ApkSigner;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreUtils;

/**
 * Small APKs built and signed inside a test, so a signature case can be made without checking a
 * binary fixture in. The manifest is real binary XML; the rest is a placeholder dex.
 */
public final class TestApks {
    private static final long TEN_YEARS_MS = 10L * 365 * 24 * 60 * 60 * 1000;

    private TestApks() {
    }

    @NonNull
    public static KeyPair newKey(@NonNull String commonName) throws GeneralSecurityException, IOException {
        return KeyStoreUtils.generateRSAKeyPair("CN=" + commonName, 2048, System.currentTimeMillis() + TEN_YEARS_MS);
    }

    /**
     * An APK with only a manifest and a placeholder dex, not signed at all.
     */
    @NonNull
    public static File unsigned(@NonNull File out, @NonNull String packageName, int minSdk, int targetSdk)
            throws IOException {
        AndroidManifestBlock manifest = new AndroidManifestBlock();
        manifest.setPackageName(packageName);
        manifest.setVersionCode(1);
        manifest.setVersionName("1.0");
        manifest.setMinSdkVersion(minSdk);
        manifest.setTargetSdkVersion(targetSdk);
        manifest.refreshFull();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.write(manifest.getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.write(new byte[]{'d', 'e', 'x', '\n', '0', '3', '5', 0});
            zip.closeEntry();
        }
        return out;
    }

    /**
     * Builds and signs an APK with the chosen schemes.
     *
     * @param signerMinSdk the oldest Android the signer prepares the signature for, which picks
     *                     the digests a v1 signature uses (SHA-256 from 18 on), or {@code null}
     *                     to take it from the manifest like a normal build does
     */
    @NonNull
    public static File signed(@NonNull File out, @NonNull String packageName, int minSdk, int targetSdk,
                              @NonNull KeyPair key, boolean v1, boolean v2, boolean v3,
                              @Nullable Integer signerMinSdk) throws Exception {
        File input = new File(out.getParentFile(), "unsigned-" + out.getName());
        unsigned(input, packageName, minSdk, targetSdk);
        ApkSigner.SignerConfig config = new ApkSigner.SignerConfig.Builder("test", key.getPrivateKey(),
                Collections.singletonList((X509Certificate) key.getCertificate())).build();
        ApkSigner.Builder signer = new ApkSigner.Builder(Collections.singletonList(config))
                .setInputApk(input)
                .setOutputApk(out)
                .setV1SigningEnabled(v1)
                .setV2SigningEnabled(v2)
                .setV3SigningEnabled(v3)
                .setV4SigningEnabled(false);
        if (signerMinSdk != null) {
            signer.setMinSdkVersion(signerMinSdk);
        }
        signer.build().sign();
        if (!input.delete()) {
            input.deleteOnExit();
        }
        return out;
    }
}
