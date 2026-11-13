// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static io.github.muntashirakon.AppManager.sync.lan.ApkSignatureGateTest.DNSFILTER;
import static io.github.muntashirakon.AppManager.sync.lan.ApkSignatureGateTest.DNSFILTER_SIGNER;
import static io.github.muntashirakon.AppManager.sync.lan.ApkSignatureGateTest.TEXTLAUNCHER;
import static io.github.muntashirakon.AppManager.sync.lan.ApkSignatureGateTest.TEXTLAUNCHER_SIGNER;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@RunWith(RobolectricTestRunner.class)
public class LanSyncDownloaderTest {
    private static final String PKG = "ademar.textlauncher";
    /**
     * Part way into the larger fixture, past the first few chunks.
     */
    private static final int PART_BYTES = 200_000;

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private ExecutorService mExecutor;
    private LocalIdentity mHostIdentity;
    private LocalIdentity mClientIdentity;
    private SyncFixtures.Catalog mCatalog;
    private File mDir;
    private File mApk;
    private byte[] mApkBytes;
    private File mBigApk;
    private byte[] mBigApkBytes;
    private Future<Void> mHost;
    private SyncClientSession mClient;

    @Before
    public void setUp() throws GeneralSecurityException, IOException {
        mExecutor = Executors.newFixedThreadPool(3);
        mHostIdentity = LocalIdentity.generate(new SeededRandom(51));
        mClientIdentity = LocalIdentity.generate(new SeededRandom(52));
        mCatalog = new SyncFixtures.Catalog();
        mDir = new File(mTemp.getRoot(), LanSyncDownloader.DIR_NAME);
        mApk = SyncFixtures.resource(TEXTLAUNCHER);
        mApkBytes = Files.readAllBytes(mApk.toPath());
        mBigApk = SyncFixtures.resource(DNSFILTER);
        mBigApkBytes = Files.readAllBytes(mBigApk.toPath());
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (mClient != null) {
                mClient.close();
                // The host saw a clean goodbye
                mHost.get(10, TimeUnit.SECONDS);
            }
        } finally {
            mExecutor.shutdownNow();
        }
    }

    @Test
    public void singleApkIsKeptAsTheReceivedCopy() throws Exception {
        mCatalog.add(listed(mApk, TEXTLAUNCHER_SIGNER, "base.apk"), mApk);
        SyncApp remote = connectAndList();
        AtomicLong done = new AtomicLong();
        AtomicLong total = new AtomicLong();

        LanSyncDownloader.Result result = LanSyncDownloader.pull(mClient, remote, null, mDir,
                ApkSigners.VERIFIED_FILE_READER, (d, t) -> {
                    done.set(d);
                    total.set(t);
                });

        assertEquals(LanSyncDownloader.MIME_APK, result.mimeType);
        assertFalse("The installer must not delete the received copy", result.owned);
        assertEquals(PKG, result.packageName);
        assertArrayEquals(mApkBytes, Files.readAllBytes(result.file.toPath()));
        assertEquals(Collections.singletonList(ReceivedApkStore.DIR_NAME), dirContents(mDir));
        ReceivedApkStore.Entry entry = onlyEntry();
        assertTrue(entry.complete);
        assertEquals(entry.key, result.entryKey);
        assertEquals(entry.file("base.apk"), result.file);
        assertEquals(Arrays.asList("base.apk", "meta.json"), dirContents(entry.dir));
        assertEquals(mApkBytes.length, done.get());
        assertEquals(mApkBytes.length, total.get());
    }

    @Test
    public void splitsArriveAsAStoredApksBundleTheInstallerOwns() throws Exception {
        mCatalog.add(listed(mApk, TEXTLAUNCHER_SIGNER, "base.apk", "split_config.en.apk"), mApk, mApk);
        SyncApp remote = connectAndList();
        SyncApp installed = SyncFixtures.app(PKG, 1, TEXTLAUNCHER_SIGNER);

        LanSyncDownloader.Result result = LanSyncDownloader.pull(mClient, remote, installed, mDir,
                ApkSigners.VERIFIED_FILE_READER, null);

        assertEquals(LanSyncDownloader.MIME_APKS, result.mimeType);
        assertTrue(result.owned);
        assertEquals(mDir, result.file.getParentFile());
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(result.file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                names.add(entry.getName());
                assertEquals(ZipEntry.STORED, entry.getMethod());
                try (InputStream in = zip.getInputStream(entry)) {
                    assertArrayEquals(mApkBytes, readAll(in));
                }
            }
        }
        assertEquals(Arrays.asList("base.apk", "split_config.en.apk"), names);
        // The bundle goes with the install, the received files stay
        assertTrue(result.file.delete());
        assertEquals(Arrays.asList("base.apk", "meta.json", "split_config.en.apk"), dirContents(onlyEntry().dir));
    }

    @Test
    public void hostLyingAboutTheSignerIsStoppedBeforeTheInstaller() throws Exception {
        // The host claims the key the installed app has, but the file is signed by another one
        mCatalog.add(listed(mApk, DNSFILTER_SIGNER, "base.apk"), mApk);
        SyncApp remote = connectAndList();
        SyncApp installed = SyncFixtures.app(PKG, 1, DNSFILTER_SIGNER);

        assertThrows(ApkSignatureGate.MismatchException.class, () -> LanSyncDownloader.pull(mClient, remote,
                installed, mDir, ApkSigners.VERIFIED_FILE_READER, null));
        assertNothingReceived();
    }

    @Test
    public void appThatIsNotNewerIsNeverRequested() throws Exception {
        mCatalog.add(listed(mApk, TEXTLAUNCHER_SIGNER, "base.apk"), mApk);
        SyncApp remote = connectAndList();

        assertThrows(IOException.class, () -> LanSyncDownloader.pull(mClient, remote,
                SyncFixtures.app(PKG, remote.versionCode, TEXTLAUNCHER_SIGNER), mDir,
                ApkSigners.VERIFIED_FILE_READER, null));
        assertThrows(IOException.class, () -> LanSyncDownloader.pull(mClient, remote,
                SyncFixtures.app(PKG, 1, DNSFILTER_SIGNER), mDir, ApkSigners.VERIFIED_FILE_READER, null));
        assertTrue(mCatalog.fileLookups.isEmpty());
    }

    @Test
    public void failedTransferLeavesNothingBehind() throws Exception {
        // Listed one byte short, so the host reports the file changed
        SyncApp wrongSize = new SyncApp(PKG, "Text Launcher", 5, "5", false,
                Collections.singletonList(TEXTLAUNCHER_SIGNER), Collections.<String>emptyList(),
                Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", mApk.length() - 1)));
        mCatalog.add(wrongSize, mApk);
        SyncApp remote = connectAndList();

        SyncClientSession.RemoteError error = assertThrows(SyncClientSession.RemoteError.class,
                () -> LanSyncDownloader.pull(mClient, remote, null, mDir, ApkSigners.VERIFIED_FILE_READER, null));
        assertEquals(SyncMessages.ERR_CHANGED, error.code);
        assertNothingReceived();
    }

    @Test
    public void aFinishedEntryIsReusedWithoutAskingTheHost() throws Exception {
        mCatalog.add(listed(mApk, TEXTLAUNCHER_SIGNER, "base.apk"), mApk);
        SyncApp remote = connectAndList();
        LanSyncDownloader.Result first = LanSyncDownloader.pull(mClient, remote, null, mDir,
                ApkSigners.VERIFIED_FILE_READER, null);
        assertEquals(1, mCatalog.fileLookups.size());

        AtomicLong done = new AtomicLong();
        LanSyncDownloader.Result again = LanSyncDownloader.pull(mClient, remote, null, mDir,
                ApkSigners.VERIFIED_FILE_READER, (d, t) -> done.set(d));

        assertEquals("Nothing was asked for the second time", 1, mCatalog.fileLookups.size());
        assertEquals(first.file, again.file);
        assertEquals(mApkBytes.length, done.get());
        assertArrayEquals(mApkBytes, Files.readAllBytes(again.file.toPath()));
    }

    @Test
    public void aBrokenOffDownloadContinuesFromItsPart() throws Exception {
        mCatalog.add(listed(mBigApk, DNSFILTER_SIGNER, "base.apk"), mBigApk);
        SyncApp remote = connectAndList();
        File part = seedPart(remote, Arrays.copyOf(mBigApkBytes, PART_BYTES));
        AtomicLong firstProgress = new AtomicLong(-1);

        LanSyncDownloader.Result result = LanSyncDownloader.pull(mClient, remote, null, mDir,
                ApkSigners.VERIFIED_FILE_READER, (d, t) -> firstProgress.compareAndSet(-1, d));

        assertTrue("Only the rest came over the wire", firstProgress.get() > PART_BYTES);
        assertArrayEquals(mBigApkBytes, Files.readAllBytes(result.file.toPath()));
        assertFalse(part.exists());
    }

    @Test
    public void aHostFromTheFirstReleaseStartsThePartOver() throws Exception {
        mCatalog.add(listed(mBigApk, DNSFILTER_SIGNER, "base.apk"), mBigApk);
        SyncApp remote = connectAndList(1, Long.MAX_VALUE);
        seedPart(remote, Arrays.copyOf(mBigApkBytes, PART_BYTES));
        AtomicLong firstProgress = new AtomicLong(-1);

        LanSyncDownloader.Result result = LanSyncDownloader.pull(mClient, remote, null, mDir,
                ApkSigners.VERIFIED_FILE_READER, (d, t) -> firstProgress.compareAndSet(-1, d));

        assertTrue("The whole file came again", firstProgress.get() < PART_BYTES);
        assertArrayEquals(mBigApkBytes, Files.readAllBytes(result.file.toPath()));
    }

    @Test
    public void aCutConnectionKeepsThePartForTheNextTry() throws Exception {
        mCatalog.add(listed(mBigApk, DNSFILTER_SIGNER, "base.apk"), mBigApk);
        SyncApp remote = connectAndList(SyncMessages.REVISION, PART_BYTES);

        IOException cut = assertThrows(IOException.class, () -> LanSyncDownloader.pull(mClient, remote, null,
                mDir, ApkSigners.VERIFIED_FILE_READER, null));
        assertFalse(cut instanceof SyncProtocolException);
        assertFalse(cut instanceof SyncClientSession.RemoteError);
        mClient.close();
        assertThrows(Exception.class, () -> mHost.get(10, TimeUnit.SECONDS));
        ReceivedApkStore.Entry entry = onlyEntry();
        assertFalse(entry.complete);
        File part = entry.part("base.apk");
        long kept = part.length();
        assertTrue("Kept " + kept + " bytes", kept > 0 && kept < mBigApkBytes.length);
        assertArrayEquals(Arrays.copyOf(mBigApkBytes, (int) kept), Files.readAllBytes(part.toPath()));

        // Later, on a connection that holds
        AtomicLong firstProgress = new AtomicLong(-1);
        SyncApp again = connectAndList();
        LanSyncDownloader.Result result = LanSyncDownloader.pull(mClient, again, null, mDir,
                ApkSigners.VERIFIED_FILE_READER, (d, t) -> firstProgress.compareAndSet(-1, d));

        assertTrue(firstProgress.get() > kept);
        assertArrayEquals(mBigApkBytes, Files.readAllBytes(result.file.toPath()));
        assertTrue(onlyEntry().complete);
    }

    @Test
    public void aPartThatDoesNotMatchIsThrownAway() throws Exception {
        mCatalog.add(listed(mBigApk, DNSFILTER_SIGNER, "base.apk"), mBigApk);
        SyncApp remote = connectAndList();
        File part = seedPart(remote, new byte[PART_BYTES]);

        assertThrows(SyncProtocolException.class, () -> LanSyncDownloader.pull(mClient, remote, null, mDir,
                ApkSigners.VERIFIED_FILE_READER, null));

        assertFalse("A part with bad bytes would fail every later try too", part.exists());
        // The client ended the session over the bad digest
        mClient.close();
        mClient = null;
        mHost.cancel(true);
    }

    @Test
    public void purgeStaleRemovesOnlyOldLooseFiles() throws IOException {
        assertTrue(mDir.mkdirs());
        File old = new File(mDir, "old.apks");
        File fresh = new File(mDir, "fresh.apks");
        File received = new File(mDir, ReceivedApkStore.DIR_NAME);
        Files.write(old.toPath(), new byte[1]);
        Files.write(fresh.toPath(), new byte[1]);
        assertTrue(received.mkdirs());
        assertTrue(old.setLastModified(1_000_000L));
        assertTrue(fresh.setLastModified(9_000_000L));
        assertTrue(received.setLastModified(1_000_000L));

        LanSyncDownloader.purgeStale(mDir, 5_000_000L, 10_000_000L);

        assertEquals(Arrays.asList("fresh.apks", ReceivedApkStore.DIR_NAME), dirContents(mDir));
    }

    // Helpers

    @NonNull
    private SyncApp listed(@NonNull File apk, @NonNull String signer, @NonNull String... names) {
        List<SyncApp.ApkFileInfo> files = new ArrayList<>();
        for (String name : names) {
            files.add(new SyncApp.ApkFileInfo(name, apk.length()));
        }
        return new SyncApp(PKG, "Text Launcher", 5, "5", false, Collections.singletonList(signer),
                Collections.<String>emptyList(), files);
    }

    @NonNull
    private File seedPart(@NonNull SyncApp remote, @NonNull byte[] bytes) throws IOException {
        ReceivedApkStore.Entry entry = new ReceivedApkStore(mDir).open(remote, System.currentTimeMillis());
        File part = entry.part(remote.files.get(0).name);
        Files.write(part.toPath(), bytes);
        return part;
    }

    @NonNull
    private SyncApp connectAndList() throws IOException {
        return connectAndList(SyncMessages.REVISION, Long.MAX_VALUE);
    }

    /**
     * @param cutAfter the host's connection breaks once it has written this many bytes
     */
    @NonNull
    private SyncApp connectAndList(int revision, long cutAfter) throws IOException {
        TestPipe.Duplex duplex = new TestPipe.Duplex();
        FrameIo hostIo = new FrameIo(duplex.bToA.in, new CuttingOutputStream(duplex.aToB.out, cutAfter));
        mHost = mExecutor.submit(() -> {
            HostSession session = new HostSession(hostIo, HostSession.NO_TIMEOUTS, "Host", mHostIdentity,
                    new SeededRandom(61), new SyncFixtures.MemoryTrust(), s -> ApprovalGate.Decision.ALLOW, mCatalog,
                    null);
            session.setRevision(revision);
            session.run();
            return null;
        });
        mClient = SyncClientSession.connect(duplex.endpointB(), HostSession.NO_TIMEOUTS, "Client", mClientIdentity,
                new SeededRandom(62), null);
        List<SyncApp> apps = mClient.list(false);
        assertEquals(1, apps.size());
        return apps.get(0);
    }

    @NonNull
    private ReceivedApkStore.Entry onlyEntry() {
        List<ReceivedApkStore.Entry> entries = new ReceivedApkStore(mDir).list();
        assertEquals(1, entries.size());
        return entries.get(0);
    }

    private void assertNothingReceived() {
        assertTrue("A rejected download must leave nothing behind", new ReceivedApkStore(mDir).list().isEmpty());
        assertEquals(Collections.<String>emptyList(), dirContents(new File(mDir, ReceivedApkStore.DIR_NAME)));
        for (String name : dirContents(mDir)) {
            assertFalse("No bundle either", name.endsWith(".apks"));
        }
    }

    @NonNull
    private static List<String> dirContents(@NonNull File dir) {
        String[] names = dir.list();
        List<String> list = names != null ? new ArrayList<>(Arrays.asList(names)) : new ArrayList<String>();
        Collections.sort(list);
        return list;
    }

    @NonNull
    private static byte[] readAll(@NonNull InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * A connection that drops once a set number of bytes went through, like Wi-Fi going away.
     */
    private static final class CuttingOutputStream extends OutputStream {
        private final OutputStream mOut;
        private long mLeft;

        CuttingOutputStream(@NonNull OutputStream out, long cutAfter) {
            mOut = out;
            mLeft = cutAfter;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            if (len > mLeft) {
                mOut.close();
                throw new IOException("The connection dropped");
            }
            mLeft -= len;
            mOut.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            mOut.flush();
        }

        @Override
        public void close() throws IOException {
            mOut.close();
        }
    }
}
