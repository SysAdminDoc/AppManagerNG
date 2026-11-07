// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_A;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_B;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_C;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import androidx.annotation.NonNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class ApkSignatureGateTest {
    // SHA-256 of each fixture's signing certificate, as keytool -printcert -jarfile reports it
    static final String TEXTLAUNCHER_SIGNER = "4ca3d35b3ae3e8c7a56d96f715cd1f5165b8103c8d238dd19d0d26ed3eef0c3d";
    static final String DNSFILTER_SIGNER = "d7d98dc22da624e1bb36815b3c426d3555c23c53acd3e6585ab383de874c9f35";
    static final String TEXTLAUNCHER = "oandbackups/ademar.textlauncher/base.apk";
    static final String DNSFILTER = "oandbackups/dnsfilter.android/base.apk";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    @Test
    public void verifiedReaderReportsTheCertificateDigest() {
        ApkSignatureGate.SignerDigests digests = ApkSigners.VERIFIED_FILE_READER.read(SyncFixtures.resource(TEXTLAUNCHER));
        assertNotNull(digests);
        assertEquals(Collections.singletonList(TEXTLAUNCHER_SIGNER), digests.current);
        ApkSignatureGate.SignerDigests other = ApkSigners.VERIFIED_FILE_READER.read(SyncFixtures.resource(DNSFILTER));
        assertNotNull(other);
        assertEquals(Collections.singletonList(DNSFILTER_SIGNER), other.current);
    }

    @Test
    public void verifiedReaderRejectsATamperedCopy() throws IOException {
        File copy = tampered(TEXTLAUNCHER);
        assertNull(ApkSigners.VERIFIED_FILE_READER.read(copy));
        assertThrows(ApkSignatureGate.MismatchException.class, () -> ApkSignatureGate.check(
                Collections.singletonList(copy), claimed(TEXTLAUNCHER_SIGNER), null, ApkSigners.VERIFIED_FILE_READER));
    }

    @Test
    public void verifiedReaderRejectsAFileThatIsNotAnApk() throws IOException {
        File junk = mTemp.newFile("junk.apk");
        Files.write(junk.toPath(), new byte[]{'P', 'K', 3, 4, 0, 0});
        assertNull(ApkSigners.VERIFIED_FILE_READER.read(junk));
    }

    @Test
    public void honestApkPasses() throws IOException {
        File apk = SyncFixtures.resource(TEXTLAUNCHER);
        ApkSignatureGate.check(Collections.singletonList(apk), claimed(TEXTLAUNCHER_SIGNER), null,
                ApkSigners.VERIFIED_FILE_READER);
        ApkSignatureGate.check(Collections.singletonList(apk), claimed(TEXTLAUNCHER_SIGNER.toUpperCase(java.util.Locale.ROOT)),
                claimed(TEXTLAUNCHER_SIGNER), ApkSigners.VERIFIED_FILE_READER);
    }

    @Test
    public void apkSignedByAnotherKeyThanListedIsRejected() {
        File apk = SyncFixtures.resource(TEXTLAUNCHER);
        assertThrows(ApkSignatureGate.MismatchException.class, () -> ApkSignatureGate.check(
                Collections.singletonList(apk), claimed(DNSFILTER_SIGNER), null, ApkSigners.VERIFIED_FILE_READER));
    }

    @Test
    public void apkSignedByAnotherKeyThanTheInstalledAppIsRejected() {
        File apk = SyncFixtures.resource(TEXTLAUNCHER);
        assertThrows(ApkSignatureGate.MismatchException.class, () -> ApkSignatureGate.check(
                Collections.singletonList(apk), claimed(TEXTLAUNCHER_SIGNER), claimed(DNSFILTER_SIGNER),
                ApkSigners.VERIFIED_FILE_READER));
    }

    @Test
    public void splitSignedByAnotherKeyIsRejected() {
        List<File> files = Arrays.asList(SyncFixtures.resource(TEXTLAUNCHER), SyncFixtures.resource(DNSFILTER));
        assertThrows(ApkSignatureGate.MismatchException.class, () -> ApkSignatureGate.check(
                files, claimed(TEXTLAUNCHER_SIGNER), null, ApkSigners.VERIFIED_FILE_READER));
    }

    @Test
    public void splitThatDoesNotVerifyIsRejected() throws IOException {
        List<File> files = Arrays.asList(SyncFixtures.resource(TEXTLAUNCHER), tampered(TEXTLAUNCHER));
        assertThrows(ApkSignatureGate.MismatchException.class, () -> ApkSignatureGate.check(
                files, claimed(TEXTLAUNCHER_SIGNER), null, ApkSigners.VERIFIED_FILE_READER));
    }

    @Test
    public void rotatedKeyWithTheInstalledKeyInItsLineagePasses() throws IOException {
        File base = new File("base.apk");
        File split = new File("split.apk");
        Map<File, ApkSignatureGate.SignerDigests> signed = new HashMap<>();
        signed.put(base, new ApkSignatureGate.SignerDigests(Collections.singletonList(SIGNER_B),
                Arrays.asList(SIGNER_A, SIGNER_B)));
        signed.put(split, new ApkSignatureGate.SignerDigests(Collections.singletonList(SIGNER_B),
                Collections.<String>emptyList()));
        ApkSignatureGate.DigestReader reader = signed::get;
        List<File> files = Arrays.asList(base, split);

        ApkSignatureGate.check(files, claimed(SIGNER_B), claimed(SIGNER_A), reader);
        assertThrows(ApkSignatureGate.MismatchException.class,
                () -> ApkSignatureGate.check(files, claimed(SIGNER_B), claimed(SIGNER_C), reader));
    }

    @Test
    public void unsignedOrEmptyInputIsRejected() {
        File base = new File("base.apk");
        assertThrows(ApkSignatureGate.MismatchException.class, () -> ApkSignatureGate.check(
                Collections.<File>emptyList(), claimed(SIGNER_A), null, apk -> null));
        assertThrows(ApkSignatureGate.MismatchException.class, () -> ApkSignatureGate.check(
                Collections.singletonList(base), claimed(SIGNER_A), null, apk -> null));
        assertThrows(ApkSignatureGate.MismatchException.class, () -> ApkSignatureGate.check(
                Collections.singletonList(base), claimed(SIGNER_A), null,
                apk -> new ApkSignatureGate.SignerDigests(Collections.<String>emptyList(), Collections.<String>emptyList())));
    }

    @NonNull
    static SyncApp claimed(@NonNull String signer) {
        return SyncFixtures.app("ademar.textlauncher", 1, signer);
    }

    /**
     * A copy with one byte flipped in the middle of the file, inside the signed entries.
     */
    @NonNull
    private File tampered(@NonNull String resource) throws IOException {
        File copy = new File(mTemp.getRoot(), "tampered-" + System.nanoTime() + ".apk");
        Files.copy(SyncFixtures.resource(resource).toPath(), copy.toPath());
        try (RandomAccessFile raf = new RandomAccessFile(copy, "rw")) {
            long middle = raf.length() / 2;
            raf.seek(middle);
            int b = raf.read();
            raf.seek(middle);
            raf.write(b ^ 0x01);
        }
        return copy;
    }
}
