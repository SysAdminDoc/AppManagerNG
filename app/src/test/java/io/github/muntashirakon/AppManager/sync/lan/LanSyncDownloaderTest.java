// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static io.github.muntashirakon.AppManager.sync.lan.ApkSignatureGateTest.DNSFILTER_SIGNER;
import static io.github.muntashirakon.AppManager.sync.lan.ApkSignatureGateTest.TEXTLAUNCHER;
import static io.github.muntashirakon.AppManager.sync.lan.ApkSignatureGateTest.TEXTLAUNCHER_SIGNER;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private ExecutorService mExecutor;
    private LocalIdentity mHostIdentity;
    private LocalIdentity mClientIdentity;
    private SyncFixtures.Catalog mCatalog;
    private File mDir;
    private File mApk;
    private byte[] mApkBytes;
    private Future<Void> mHost;
    private SyncClientSession mClient;

    @Before
    public void setUp() throws GeneralSecurityException, IOException {
        mExecutor = Executors.newFixedThreadPool(2);
        mHostIdentity = LocalIdentity.generate(new SeededRandom(51));
        mClientIdentity = LocalIdentity.generate(new SeededRandom(52));
        mCatalog = new SyncFixtures.Catalog();
        mDir = new File(mTemp.getRoot(), LanSyncDownloader.DIR_NAME);
        mApk = SyncFixtures.resource(TEXTLAUNCHER);
        mApkBytes = Files.readAllBytes(mApk.toPath());
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
    public void singleApkArrivesAsAnApk() throws Exception {
        mCatalog.add(listed(TEXTLAUNCHER_SIGNER, "base.apk"), mApk);
        SyncApp remote = connectAndList();
        AtomicLong done = new AtomicLong();
        AtomicLong total = new AtomicLong();

        LanSyncDownloader.Result result = LanSyncDownloader.pull(mClient, remote, null, mDir,
                ApkSigners.VERIFIED_FILE_READER, (d, t) -> {
                    done.set(d);
                    total.set(t);
                });

        assertEquals(LanSyncDownloader.MIME_APK, result.mimeType);
        assertTrue(result.file.getName().endsWith(".apk"));
        assertArrayEquals(mApkBytes, Files.readAllBytes(result.file.toPath()));
        assertEquals(Collections.singletonList(result.file.getName()), dirContents());
        assertEquals(mApkBytes.length, done.get());
        assertEquals(mApkBytes.length, total.get());
    }

    @Test
    public void splitsArriveAsAStoredApksBundle() throws Exception {
        mCatalog.add(listed(TEXTLAUNCHER_SIGNER, "base.apk", "split_config.en.apk"), mApk, mApk);
        SyncApp remote = connectAndList();
        SyncApp installed = SyncFixtures.app(PKG, 1, TEXTLAUNCHER_SIGNER);

        LanSyncDownloader.Result result = LanSyncDownloader.pull(mClient, remote, installed, mDir,
                ApkSigners.VERIFIED_FILE_READER, null);

        assertEquals(LanSyncDownloader.MIME_APKS, result.mimeType);
        assertEquals("Only the bundle is left once the parts are packed",
                Collections.singletonList(result.file.getName()), dirContents());
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
    }

    @Test
    public void hostLyingAboutTheSignerIsStoppedBeforeTheInstaller() throws Exception {
        // The host claims the key the installed app has, but the file is signed by another one
        mCatalog.add(listed(DNSFILTER_SIGNER, "base.apk"), mApk);
        SyncApp remote = connectAndList();
        SyncApp installed = SyncFixtures.app(PKG, 1, DNSFILTER_SIGNER);

        assertThrows(ApkSignatureGate.MismatchException.class, () -> LanSyncDownloader.pull(mClient, remote,
                installed, mDir, ApkSigners.VERIFIED_FILE_READER, null));
        assertTrue("A rejected download must leave nothing behind", dirContents().isEmpty());
    }

    @Test
    public void appThatIsNotNewerIsNeverRequested() throws Exception {
        mCatalog.add(listed(TEXTLAUNCHER_SIGNER, "base.apk"), mApk);
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
        assertTrue(dirContents().isEmpty());
    }

    @Test
    public void purgeStaleRemovesOnlyOldFiles() throws IOException {
        assertTrue(mDir.mkdirs());
        File old = new File(mDir, "old.apk");
        File fresh = new File(mDir, "fresh.apk");
        Files.write(old.toPath(), new byte[1]);
        Files.write(fresh.toPath(), new byte[1]);
        assertTrue(old.setLastModified(1_000_000L));
        assertTrue(fresh.setLastModified(9_000_000L));

        LanSyncDownloader.purgeStale(mDir, 5_000_000L, 10_000_000L);

        assertEquals(Collections.singletonList("fresh.apk"), dirContents());
    }

    // Helpers

    @NonNull
    private SyncApp listed(@NonNull String signer, @NonNull String... names) {
        List<SyncApp.ApkFileInfo> files = new ArrayList<>();
        for (String name : names) {
            files.add(new SyncApp.ApkFileInfo(name, mApk.length()));
        }
        return new SyncApp(PKG, "Text Launcher", 5, "5", false, Collections.singletonList(signer),
                Collections.<String>emptyList(), files);
    }

    @NonNull
    private SyncApp connectAndList() throws IOException {
        TestPipe.Duplex duplex = new TestPipe.Duplex();
        FrameIo hostIo = duplex.endpointA();
        mHost = mExecutor.submit(() -> {
            new HostSession(hostIo, HostSession.NO_TIMEOUTS, "Host", mHostIdentity, new SeededRandom(61),
                    new SyncFixtures.MemoryTrust(), session -> ApprovalGate.Decision.ALLOW, mCatalog, null).run();
            return null;
        });
        mClient = SyncClientSession.connect(duplex.endpointB(), HostSession.NO_TIMEOUTS, "Client", mClientIdentity,
                new SeededRandom(62), null);
        List<SyncApp> apps = mClient.list(false);
        assertEquals(1, apps.size());
        return apps.get(0);
    }

    @NonNull
    private List<String> dirContents() {
        String[] names = mDir.list();
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
}
