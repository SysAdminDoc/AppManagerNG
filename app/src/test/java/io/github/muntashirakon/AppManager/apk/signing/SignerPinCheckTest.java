// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.signing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.os.Environment;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowEnvironment;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.sync.lan.ApkSignatureGate;
import io.github.muntashirakon.AppManager.sync.lan.ApkSigners;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;

@RunWith(RobolectricTestRunner.class)
public class SignerPinCheckTest {
    // SHA-256 of each fixture's signing certificate, as keytool -printcert -jarfile reports it
    private static final String TEXTLAUNCHER_SIGNER = "4ca3d35b3ae3e8c7a56d96f715cd1f5165b8103c8d238dd19d0d26ed3eef0c3d";
    private static final String DNSFILTER_SIGNER = "d7d98dc22da624e1bb36815b3c426d3555c23c53acd3e6585ab383de874c9f35";
    private static final String TEXTLAUNCHER = "oandbackups/ademar.textlauncher/base.apk";
    private static final String DNSFILTER = "oandbackups/dnsfilter.android/base.apk";
    private static final String PACKAGE = "ademar.textlauncher";
    private static final String SIGNER_A = repeat('a');
    private static final String SIGNER_B = repeat('b');
    private static final String SIGNER_C = repeat('c');

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private SignerPinStore mStore;

    @Before
    public void setUp() {
        mStore = new SignerPinStore(new File(mTemp.getRoot(), SignerPinStore.FILE_NAME));
        // Robolectric reports external storage as unmounted, which puts FileUtils.getCachePath() in the
        // private cache. Mount it the way a device has it, so the shared and private caches differ.
        for (File externalCache : ContextUtils.getContext().getExternalCacheDirs()) {
            if (externalCache != null) {
                ShadowEnvironment.setExternalStorageState(externalCache, Environment.MEDIA_MOUNTED);
            }
        }
    }

    @Test
    public void anUnpinnedPackageReadsNothing() throws IOException {
        mStore.put("some.other.app", new SignerPin(Collections.singletonList(SIGNER_A), 1));
        Source neverOpened = new Source("base.apk", null);

        assertNull(SignerPinCheck.check(Collections.singletonList(PACKAGE), Collections.singletonList(neverOpened),
                mStore, apk -> {
                    throw new AssertionError("nothing should be verified");
                }));
        assertEquals(0, neverOpened.opens);
    }

    @Test
    public void anApkSignedByThePinnedKeyPassesAndIsHashed() throws Exception {
        pin(PACKAGE, TEXTLAUNCHER_SIGNER);
        File apk = resource(TEXTLAUNCHER);

        String sha256 = SignerPinCheck.check(Collections.singletonList(PACKAGE),
                Collections.singletonList(Source.of(apk)), mStore, ApkSigners.VERIFIED_FILE_READER);

        assertEquals(sha256(Files.readAllBytes(apk.toPath())), sha256);
    }

    @Test
    public void anApkSignedByAnotherKeyIsBlockedNamingBothKeys() throws IOException {
        pin(PACKAGE, TEXTLAUNCHER_SIGNER);

        SignerPinCheck.Blocked blocked = assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(
                Collections.singletonList(PACKAGE), Collections.singletonList(Source.of(resource(DNSFILTER))),
                mStore, ApkSigners.VERIFIED_FILE_READER));

        String message = Objects.requireNonNull(blocked.getMessage());
        assertTrue(message, message.contains(SignerPin.format(TEXTLAUNCHER_SIGNER)));
        assertTrue(message, message.contains(SignerPin.format(DNSFILTER_SIGNER)));
        assertTrue(message, message.contains(PACKAGE));
    }

    @Test
    public void aPinOnThePackageTheBaseApkDeclaresApplies() throws IOException {
        // A restore can name one package while its base APK is another; a pin on either counts
        pin(PACKAGE, DNSFILTER_SIGNER);

        assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(Arrays.asList("named.in.backup", PACKAGE),
                Collections.singletonList(Source.of(resource(TEXTLAUNCHER))), mStore, ApkSigners.VERIFIED_FILE_READER));
    }

    @Test
    public void everyApkOfTheInstallHasToMatch() throws IOException {
        pin(PACKAGE, TEXTLAUNCHER_SIGNER);
        List<Source> apks = Arrays.asList(Source.of(resource(TEXTLAUNCHER)), new Source("split_config.en.apk",
                Files.readAllBytes(resource(DNSFILTER).toPath())));

        SignerPinCheck.Blocked blocked = assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(
                Collections.singletonList(PACKAGE), apks, mStore, ApkSigners.VERIFIED_FILE_READER));

        assertTrue(blocked.getMessage(), blocked.getMessage().startsWith("split_config.en.apk "));
    }

    @Test
    public void aTamperedApkIsBlocked() throws IOException {
        pin(PACKAGE, TEXTLAUNCHER_SIGNER);
        File copy = mTemp.newFile("tampered.apk");
        Files.copy(resource(TEXTLAUNCHER).toPath(), copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try (RandomAccessFile raf = new RandomAccessFile(copy, "rw")) {
            raf.seek(raf.length() / 2);
            int b = raf.read();
            raf.seek(raf.length() / 2);
            raf.write(b ^ 0xff);
        }

        SignerPinCheck.Blocked blocked = assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(
                Collections.singletonList(PACKAGE), Collections.singletonList(Source.of(copy)), mStore,
                ApkSigners.VERIFIED_FILE_READER));

        assertTrue(blocked.getMessage(), blocked.getMessage().contains("has no signature that verifies"));
    }

    @Test
    public void aRotatedKeyWhoseLineageHoldsTheSinglePinnedKeyPasses() throws IOException {
        pin(PACKAGE, SIGNER_A);
        ApkSignatureGate.DigestReader rotated = apk -> new ApkSignatureGate.SignerDigests(
                Collections.singletonList(SIGNER_B), Arrays.asList(SIGNER_A, SIGNER_B));

        assertEquals(sha256("apk".getBytes(StandardCharsets.UTF_8)), SignerPinCheck.check(
                Collections.singletonList(PACKAGE), Collections.singletonList(new Source("base.apk", bytes("apk"))),
                mStore, rotated));
    }

    @Test
    public void aLineageDoesNotSatisfyAPinOfSeveralKeys() throws IOException {
        mStore.put(PACKAGE, new SignerPin(Arrays.asList(SIGNER_A, SIGNER_C), 1));
        ApkSignatureGate.DigestReader rotated = apk -> new ApkSignatureGate.SignerDigests(
                Collections.singletonList(SIGNER_B), Arrays.asList(SIGNER_A, SIGNER_C, SIGNER_B));

        assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(Collections.singletonList(PACKAGE),
                Collections.singletonList(new Source("base.apk", bytes("apk"))), mStore, rotated));
    }

    @Test
    public void theHashCoversEveryApkInSessionOrder() throws IOException {
        pin(PACKAGE, SIGNER_A);
        ApkSignatureGate.DigestReader signedByA = apk -> new ApkSignatureGate.SignerDigests(
                Collections.singletonList(SIGNER_A), Collections.emptyList());

        String sha256 = SignerPinCheck.check(Collections.singletonList(PACKAGE),
                Arrays.asList(new Source("base.apk", bytes("first")), new Source("split.apk", bytes("second"))),
                mStore, signedByA);

        assertEquals(sha256(bytes("firstsecond")), sha256);
    }

    @Test
    public void theCheckedCopiesAreDeleted() throws IOException {
        pin(PACKAGE, TEXTLAUNCHER_SIGNER);
        // The copies are made in the private cache; FileCache's shared folder must stay untouched too
        File privateCopies = privateCopies();
        File cacheDir = new File(FileUtils.getCachePath(), "files");
        List<String> before = listOf(privateCopies);
        List<String> beforeShared = listOf(cacheDir);

        SignerPinCheck.check(Collections.singletonList(PACKAGE), Collections.singletonList(Source.of(resource(TEXTLAUNCHER))),
                mStore, ApkSigners.VERIFIED_FILE_READER);
        assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(Collections.singletonList(PACKAGE),
                Collections.singletonList(Source.of(resource(DNSFILTER))), mStore, ApkSigners.VERIFIED_FILE_READER));

        assertEquals(before, listOf(privateCopies));
        assertEquals(beforeShared, listOf(cacheDir));
    }

    @Test
    public void aCopyCutShortByAnUncheckedExceptionIsDeletedToo() throws IOException {
        pin(PACKAGE, SIGNER_A);
        SignerPinCheck.Source breaksMidway = new SignerPinCheck.Source() {
            @NonNull
            @Override
            public String getName() {
                return "base.apk";
            }

            @NonNull
            @Override
            public InputStream openInputStream() {
                return new ByteArrayInputStream(bytes("first")) {
                    @Override
                    public synchronized int read(byte[] b, int off, int len) {
                        throw new IllegalStateException("zip closed");
                    }
                };
            }
        };
        List<String> before = listOf(privateCopies());

        assertThrows(IllegalStateException.class, () -> SignerPinCheck.check(Collections.singletonList(PACKAGE),
                Collections.singletonList(breaksMidway), mStore, apk -> {
                    throw new AssertionError("nothing should be verified");
                }));
        assertEquals(before, listOf(privateCopies()));
    }

    @Test
    public void copiesADeadCheckLeftBehindAreSweptOnceTheyAreADayOld() throws IOException {
        pin(PACKAGE, SIGNER_A);
        File privateCopies = privateCopies();
        assertTrue(privateCopies.isDirectory() || privateCopies.mkdirs());
        File stale = new File(privateCopies, "copy-stale.apk");
        File recent = new File(privateCopies, "copy-recent.apk");
        Files.write(stale.toPath(), bytes("stale"));
        Files.write(recent.toPath(), bytes("recent"));
        long now = System.currentTimeMillis();
        assertTrue(stale.setLastModified(now - 25L * 60 * 60 * 1000));
        assertTrue(recent.setLastModified(now - 23L * 60 * 60 * 1000));
        try {
            SignerPinCheck.check(Collections.singletonList(PACKAGE),
                    Collections.singletonList(new Source("base.apk", bytes("first"))), mStore,
                    apk -> new ApkSignatureGate.SignerDigests(Collections.singletonList(SIGNER_A), Collections.emptyList()));

            assertFalse(stale.exists());
            assertTrue(recent.exists());
        } finally {
            assertTrue(!stale.exists() || stale.delete());
            assertTrue(recent.delete());
        }
    }

    @Test
    public void theSignerIsReadFromACopyNoOtherAppCanWrite() throws IOException {
        pin(PACKAGE, SIGNER_A);
        assertNotEquals(ContextUtils.getContext().getCacheDir().getCanonicalFile(),
                FileUtils.getCachePath().getCanonicalFile());
        List<File> readFrom = new ArrayList<>();
        ApkSignatureGate.DigestReader reader = apk -> {
            readFrom.add(apk);
            return new ApkSignatureGate.SignerDigests(Collections.singletonList(SIGNER_A), Collections.emptyList());
        };

        SignerPinCheck.check(Collections.singletonList(PACKAGE),
                Collections.singletonList(new Source("base.apk", bytes("first"))), mStore, reader);

        assertEquals(1, readFrom.size());
        assertEquals(privateCopies().getCanonicalFile(),
                Objects.requireNonNull(readFrom.get(0).getParentFile()).getCanonicalFile());
    }

    @Test
    public void aPinnedInstallWithNoApkIsBlocked() throws IOException {
        pin(PACKAGE, SIGNER_A);

        assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(Collections.singletonList(PACKAGE),
                Collections.emptyList(), mStore, ApkSigners.VERIFIED_FILE_READER));
    }

    @Test
    public void anApkThatCannotBeReadIsBlocked() throws IOException {
        pin(PACKAGE, SIGNER_A);
        SignerPinCheck.Source unreadable = new SignerPinCheck.Source() {
            @NonNull
            @Override
            public String getName() {
                return "base.apk";
            }

            @NonNull
            @Override
            public InputStream openInputStream() throws IOException {
                throw new IOException("gone");
            }
        };
        List<String> before = listOf(privateCopies());

        assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(Collections.singletonList(PACKAGE),
                Collections.singletonList(unreadable), mStore, apk -> {
                    throw new AssertionError("nothing should be verified");
                }));
        // It fails before any copy is made, so nothing is left behind
        assertEquals(before, listOf(privateCopies()));
    }

    @Test
    public void aReadThatFailsPartWayIsBlockedAndItsCopyDeleted() throws IOException {
        pin(PACKAGE, SIGNER_A);
        SignerPinCheck.Source failsPartWay = new SignerPinCheck.Source() {
            @NonNull
            @Override
            public String getName() {
                return "base.apk";
            }

            @NonNull
            @Override
            public InputStream openInputStream() {
                return new InputStream() {
                    private int mReads;

                    @Override
                    public int read() throws IOException {
                        throw new IOException("read error");
                    }

                    @Override
                    public int read(@NonNull byte[] b, int off, int len) throws IOException {
                        if (mReads++ == 0) {
                            b[off] = 'P';
                            return 1;
                        }
                        throw new IOException("read error");
                    }
                };
            }
        };
        List<String> before = listOf(privateCopies());

        assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(Collections.singletonList(PACKAGE),
                Collections.singletonList(failsPartWay), mStore, apk -> {
                    throw new AssertionError("nothing should be verified");
                }));
        assertEquals(before, listOf(privateCopies()));
    }

    @Test
    public void aSourceThatFailsToCloseIsBlockedAndItsCopyDeleted() throws IOException {
        pin(PACKAGE, SIGNER_A);
        SignerPinCheck.Source failsToClose = new SignerPinCheck.Source() {
            @NonNull
            @Override
            public String getName() {
                return "base.apk";
            }

            @NonNull
            @Override
            public InputStream openInputStream() {
                // The whole APK is read, then closing it fails, as a FUSE path's close can
                return new ByteArrayInputStream(bytes("whole apk")) {
                    @Override
                    public void close() throws IOException {
                        throw new IOException("close error");
                    }
                };
            }
        };
        List<String> before = listOf(privateCopies());

        assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(Collections.singletonList(PACKAGE),
                Collections.singletonList(failsToClose), mStore, apk -> {
                    throw new AssertionError("nothing should be verified");
                }));
        assertEquals(before, listOf(privateCopies()));
    }

    @Test
    public void anApkWhoseReadGrantIsGoneIsBlockedInsteadOfCrashingTheInstall() throws IOException {
        pin(PACKAGE, SIGNER_A);
        SignerPinCheck.Source revoked = new SignerPinCheck.Source() {
            @NonNull
            @Override
            public String getName() {
                return "base.apk";
            }

            @NonNull
            @Override
            public InputStream openInputStream() {
                throw new SecurityException("Permission Denial: reading a content URI");
            }
        };

        SignerPinCheck.Blocked blocked = assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(
                Collections.singletonList(PACKAGE), Collections.singletonList(revoked), mStore, apk -> {
                    throw new AssertionError("nothing should be verified");
                }));
        assertTrue(blocked.getMessage(), blocked.getMessage().contains("Could not read base.apk"));
    }

    @Test
    public void aDamagedPinListBlocksEveryInstall() throws IOException {
        Files.write(new File(mTemp.getRoot(), SignerPinStore.FILE_NAME).toPath(), bytes("{\"schema_version\":1,"));

        SignerPinCheck.Blocked blocked = assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(
                Collections.singletonList(PACKAGE), Collections.singletonList(Source.of(resource(TEXTLAUNCHER))),
                mStore, ApkSigners.VERIFIED_FILE_READER));

        assertTrue(blocked.getMessage(), blocked.getMessage().contains("Settings > Privacy > Signer pins"));
    }

    @Test
    public void apksWhosePackageCantBeReadAreRefusedWhileAnotherAppIsPinned() throws IOException {
        // They could be the pinned app under any name the backup claims
        pin(PACKAGE, TEXTLAUNCHER_SIGNER);
        Source neverOpened = new Source("base.apk", null);

        SignerPinCheck.Blocked blocked = assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(
                "named.in.backup", () -> null, Collections.singletonList(neverOpened), mStore, apk -> {
                    throw new AssertionError("nothing should be verified");
                }));

        assertTrue(blocked.getMessage(), blocked.getMessage().contains("Could not read which app"));
        assertEquals(0, neverOpened.opens);
    }

    @Test
    public void apksWhosePackageCantBeReadAreStillCheckedAgainstTheClaimedPackagesPin() throws IOException {
        pin(PACKAGE, TEXTLAUNCHER_SIGNER);

        assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check(PACKAGE, () -> null,
                Collections.singletonList(Source.of(resource(DNSFILTER))), mStore, ApkSigners.VERIFIED_FILE_READER));
        File apk = resource(TEXTLAUNCHER);
        assertEquals(sha256(Files.readAllBytes(apk.toPath())), SignerPinCheck.check(PACKAGE, () -> null,
                Collections.singletonList(Source.of(apk)), mStore, ApkSigners.VERIFIED_FILE_READER));
    }

    @Test
    public void aPinOnTheDeclaredPackageAppliesWhateverTheBackupClaims() throws IOException {
        pin(PACKAGE, DNSFILTER_SIGNER);

        assertThrows(SignerPinCheck.Blocked.class, () -> SignerPinCheck.check("named.in.backup", () -> PACKAGE,
                Collections.singletonList(Source.of(resource(TEXTLAUNCHER))), mStore, ApkSigners.VERIFIED_FILE_READER));
        assertNull(SignerPinCheck.check("named.in.backup", () -> "another.app",
                Collections.singletonList(Source.of(resource(TEXTLAUNCHER))), mStore, ApkSigners.VERIFIED_FILE_READER));
    }

    @Test
    public void nothingIsParsedWhenNoAppIsPinned() throws IOException {
        Source neverOpened = new Source("base.apk", null);

        assertNull(SignerPinCheck.check(PACKAGE, () -> {
            throw new AssertionError("no package should be parsed");
        }, Collections.singletonList(neverOpened), mStore, apk -> {
            throw new AssertionError("nothing should be verified");
        }));
        assertEquals(0, neverOpened.opens);
    }

    private void pin(@NonNull String packageName, @NonNull String digest) throws IOException {
        mStore.put(packageName, new SignerPin(Collections.singletonList(digest), 1));
    }

    @NonNull
    private static File resource(@NonNull String path) {
        return new File(Objects.requireNonNull(SignerPinCheckTest.class.getClassLoader().getResource(path)).getFile());
    }

    @NonNull
    private static byte[] bytes(@NonNull String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @NonNull
    private static String repeat(char c) {
        char[] chars = new char[64];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    @NonNull
    private static String sha256(@NonNull byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    @NonNull
    private static File privateCopies() {
        return new File(ContextUtils.getContext().getCacheDir(), SignerPinCheck.COPY_FOLDER);
    }

    @NonNull
    private static List<String> listOf(@NonNull File dir) {
        String[] names = dir.list();
        List<String> out = names != null ? new ArrayList<>(Arrays.asList(names)) : new ArrayList<>();
        Collections.sort(out);
        return out;
    }

    private static final class Source implements SignerPinCheck.Source {
        private final String mName;
        private final byte[] mBytes;
        private final File mFile;
        int opens;

        Source(@NonNull String name, byte[] bytes) {
            this(name, bytes, null);
        }

        private Source(@NonNull String name, byte[] bytes, File file) {
            mName = name;
            mBytes = bytes;
            mFile = file;
        }

        static Source of(@NonNull File file) {
            return new Source(file.getName(), null, file);
        }

        @NonNull
        @Override
        public String getName() {
            return mName;
        }

        @NonNull
        @Override
        public InputStream openInputStream() throws IOException {
            ++opens;
            if (mFile != null) {
                return new FileInputStream(mFile);
            }
            if (mBytes == null) {
                throw new AssertionError("this APK should not be read");
            }
            return new ByteArrayInputStream(mBytes);
        }
    }
}
