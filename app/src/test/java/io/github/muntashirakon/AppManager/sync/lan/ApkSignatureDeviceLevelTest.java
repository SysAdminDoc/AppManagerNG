// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.os.Build;

import androidx.annotation.NonNull;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.test.TestApks;

/**
 * Local sync checks a downloaded APK the way this phone's package manager will, at this phone's
 * Android version. Checking every version from the APK's own minimum up turned signatures this
 * phone accepts into "not validly signed" (#22): apksig refuses outright when the APK needs a newer
 * Android than the phone has, and an old v1 signature fails for versions the phone isn't running.
 */
@RunWith(RobolectricTestRunner.class)
public class ApkSignatureDeviceLevelTest {
    private static final String PKG = "com.example.walkie";
    private static KeyPair sKeyA;
    private static KeyPair sKeyB;
    private static String sSignerA;
    private static String sSignerB;

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    @BeforeClass
    public static void makeKeys() throws Exception {
        sKeyA = TestApks.newKey("Key A");
        sKeyB = TestApks.newKey("Key B");
        sSignerA = DigestUtils.getHexDigest(DigestUtils.SHA_256, sKeyA.getCertificate().getEncoded());
        sSignerB = DigestUtils.getHexDigest(DigestUtils.SHA_256, sKeyB.getCertificate().getEncoded());
    }

    @Test
    public void aV3OnlyApkForANewerAndroidIsNotCalledUnsigned() throws Exception {
        // The reporter's Walkie-Talkie 2.0.3: v3 only, built for a newer Android than the phone runs
        int newer = Build.VERSION.SDK_INT + 1;
        File apk = TestApks.signed(file("base.apk"), PKG, newer, newer, sKeyA, false, false, true, null);

        ApkSignatureGate.SignerDigests digests = ApkSigners.VERIFIED_FILE_READER.read(apk);

        assertNotNull("The signature is fine on this phone", digests);
        assertEquals(Collections.singletonList(sSignerA), digests.current);
        ApkSignatureGate.check(Collections.singletonList(apk), claimed(sSignerA), null,
                ApkSigners.VERIFIED_FILE_READER);
        // What really stands in the way: the Android version
        LanSyncDownloader.NeedsNewerAndroidException e = assertThrows(
                LanSyncDownloader.NeedsNewerAndroidException.class,
                () -> LanSyncDownloader.checkInstallableHere(Collections.singletonList(apk), claimed(sSignerA)));
        assertEquals(newer, e.minSdk);
    }

    @Test
    public void theReportersApkVerifiesOnAnAndroid11Phone() throws Exception {
        // minSdk 32 (Android 12L), received on API 30, where apksig threw before checking anything
        File apk = TestApks.signed(file("base.apk"), PKG, 32, 33, sKeyA, false, false, true, null);

        assertNotNull(PackageUtils.getVerifiedSignerInfo(apk, 30));
        assertNotNull(PackageUtils.getVerifiedSignerInfo(apk, 32));
    }

    @Test
    public void anOldV1SignatureVerifiesWhereThisPhoneAcceptsIt() throws Exception {
        // SHA-256 digests in a v1 signature work from Android 4.3 on, and this APK claims Android 2.3.3
        File apk = TestApks.signed(file("base.apk"), PKG, 10, 28, sKeyA, true, false, false, 18);

        ApkSignatureGate.SignerDigests digests = ApkSigners.VERIFIED_FILE_READER.read(apk);

        assertNotNull(digests);
        assertEquals(Collections.singletonList(sSignerA), digests.current);
        assertNull("Android 4.2 would refuse it", PackageUtils.getVerifiedSignerInfo(apk, 17));
    }

    @Test
    public void anUnsignedApkStillFails() throws Exception {
        File apk = TestApks.unsigned(file("base.apk"), PKG, 21, 30);

        assertNull(ApkSigners.VERIFIED_FILE_READER.read(apk));
        ApkSignatureGate.MismatchException e = assertThrows(ApkSignatureGate.MismatchException.class,
                () -> ApkSignatureGate.check(Collections.singletonList(apk), claimed(sSignerA), null,
                        ApkSigners.VERIFIED_FILE_READER));
        assertEquals("The downloaded APK is not validly signed.", e.getMessage());
    }

    @Test
    public void aTamperedApkStillFails() throws Exception {
        File apk = TestApks.signed(file("base.apk"), PKG, 26, 30, sKeyA, false, true, true, null);
        assertNotNull(ApkSigners.VERIFIED_FILE_READER.read(apk));
        try (RandomAccessFile raf = new RandomAccessFile(apk, "rw")) {
            // Inside the manifest entry, which every scheme covers
            raf.seek(60);
            int b = raf.read();
            raf.seek(60);
            raf.write(b ^ 0xff);
        }

        assertNull(ApkSigners.VERIFIED_FILE_READER.read(apk));
    }

    @Test
    public void aV1OnlyApkTargetingAndroid11IsRefusedLikeThePhoneDoes() throws Exception {
        // Android 11 and later want v2 or newer from an app targeting API 30
        File apk = TestApks.signed(file("base.apk"), PKG, 21, 30, sKeyA, true, false, false, null);

        assertNull(ApkSigners.VERIFIED_FILE_READER.read(apk));
    }

    @Test
    public void anotherKeyThanTheOtherDeviceListedStillFails() throws Exception {
        File apk = TestApks.signed(file("base.apk"), PKG, 32, 33, sKeyB, false, false, true, null);

        ApkSignatureGate.MismatchException e = assertThrows(ApkSignatureGate.MismatchException.class,
                () -> ApkSignatureGate.check(Collections.singletonList(apk), claimed(sSignerA), null,
                        ApkSigners.VERIFIED_FILE_READER));
        assertEquals("The downloaded APK is signed by a different key than the other device reported.",
                e.getMessage());
    }

    @Test
    public void aSplitSignedByAnotherKeyStillFails() throws Exception {
        File base = TestApks.signed(file("base.apk"), PKG, 32, 33, sKeyA, false, false, true, null);
        File split = TestApks.signed(file("split_config.arm64_v8a.apk"), PKG, 32, 33, sKeyB, false, false,
                true, null);
        List<File> files = Arrays.asList(base, split);

        ApkSignatureGate.MismatchException e = assertThrows(ApkSignatureGate.MismatchException.class,
                () -> ApkSignatureGate.check(files, claimed(sSignerA), null, ApkSigners.VERIFIED_FILE_READER));
        assertEquals("A split APK is not signed by the same key as the base APK.", e.getMessage());
        // The same split signed by the right key goes through
        File goodSplit = TestApks.signed(file("split_config.xxhdpi.apk"), PKG, 32, 33, sKeyA, false, false,
                true, null);
        ApkSignatureGate.check(Arrays.asList(base, goodSplit), claimed(sSignerA), null,
                ApkSigners.VERIFIED_FILE_READER);
    }

    @Test
    public void anApkThisPhoneCanRunIsInstallable() throws Exception {
        File apk = TestApks.signed(file("base.apk"), PKG, 26, 30, sKeyA, false, true, true, null);

        LanSyncDownloader.checkInstallableHere(Collections.singletonList(apk), claimed(sSignerA));
    }

    @Test
    public void theListedMinimumCountsWhenTheFileSaysNothing() throws IOException {
        // A file apksig can't read for its minimum falls back to what the other device listed
        File junk = mTemp.newFile("base.apk");
        SyncApp listed = new SyncApp(PKG, "Walkie", 3, "2.0.3", false, Collections.singletonList(sSignerA),
                Collections.<String>emptyList(), Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", 1)),
                Collections.<String>emptyList(), Build.VERSION.SDK_INT + 2);

        LanSyncDownloader.NeedsNewerAndroidException e = assertThrows(
                LanSyncDownloader.NeedsNewerAndroidException.class,
                () -> LanSyncDownloader.checkInstallableHere(Collections.singletonList(junk), listed));
        assertEquals(Build.VERSION.SDK_INT + 2, e.minSdk);
    }

    @NonNull
    private File file(@NonNull String name) throws IOException {
        File dir = mTemp.newFolder();
        return new File(dir, name);
    }

    @NonNull
    private static SyncApp claimed(@NonNull String signer) {
        return SyncFixtures.app(PKG, 3, signer);
    }
}
