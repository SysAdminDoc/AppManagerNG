// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.fm.FmProvider;

@RunWith(RobolectricTestRunner.class)
public class ReceivedApkExportTest {
    private static final String PKG = "ademar.textlauncher";
    private static final String SYSTEM_INSTALLER = "com.android.packageinstaller";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private Context mContext;
    private File mApk;
    private ReceivedApkStore mStore;
    private File mDownloads;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mApk = SyncFixtures.resource(ApkSignatureGateTest.TEXTLAUNCHER);
        mStore = new ReceivedApkStore(mTemp.newFolder("received"));
        mDownloads = new File(mTemp.getRoot(), "Download");
    }

    @Test
    public void aSingleApkIsCopiedUnderItsLabelAndVersion() throws Exception {
        ReceivedApkStore.Entry entry = received("base.apk");

        String name = ReceivedApkExport.save(entry, new ReceivedApkExport.FolderDestination(mDownloads));

        assertEquals(PKG + "_v1.apk", name);
        assertArrayEquals(Files.readAllBytes(mApk.toPath()), Files.readAllBytes(new File(mDownloads, name).toPath()));
    }

    @Test
    public void splitsBecomeOneApksFileWithEveryPart() throws Exception {
        ReceivedApkStore.Entry entry = received("base.apk", "split_config.en.apk");

        String name = ReceivedApkExport.save(entry, new ReceivedApkExport.FolderDestination(mDownloads));

        assertEquals(PKG + "_v1.apks", name);
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(new File(mDownloads, name))) {
            for (ZipEntry zipEntry : Collections.list(zip.entries())) {
                names.add(zipEntry.getName());
                assertEquals("Stored, the way the installer reads it", ZipEntry.STORED, zipEntry.getMethod());
                try (InputStream in = zip.getInputStream(zipEntry)) {
                    assertArrayEquals(Files.readAllBytes(mApk.toPath()), readAll(in));
                }
            }
        }
        assertEquals(Arrays.asList("base.apk", "split_config.en.apk"), names);
    }

    @Test
    public void aNameAlreadyInDownloadsGetsANumber() throws Exception {
        ReceivedApkStore.Entry entry = received("base.apk");

        String first = ReceivedApkExport.save(entry, new ReceivedApkExport.FolderDestination(mDownloads));
        String second = ReceivedApkExport.save(entry, new ReceivedApkExport.FolderDestination(mDownloads));

        assertEquals(PKG + "_v1 (1).apk", second);
        assertTrue(new File(mDownloads, first).isFile());
        assertTrue(new File(mDownloads, second).isFile());
    }

    @Test
    public void aSaveThatBreaksOffLeavesNothingBehind() throws Exception {
        ReceivedApkStore.Entry entry = received("base.apk");
        ReceivedApkExport.FolderDestination folder = new ReceivedApkExport.FolderDestination(mDownloads);
        ReceivedApkExport.Destination full = new ReceivedApkExport.Destination() {
            @NonNull
            @Override
            public OutputStream open(@NonNull String displayName, @NonNull String mimeType) throws IOException {
                return new FilterOutputStream(folder.open(displayName, mimeType)) {
                    private int mWritten;

                    @Override
                    public void write(@NonNull byte[] b, int off, int len) throws IOException {
                        if ((mWritten += len) > 1024) {
                            throw new IOException("No space left on device");
                        }
                        out.write(b, off, len);
                    }
                };
            }

            @NonNull
            @Override
            public String commit() throws IOException {
                return folder.commit();
            }

            @Override
            public void abort() {
                folder.abort();
            }
        };

        try {
            ReceivedApkExport.save(entry, full);
            fail("A full disk must not count as saved");
        } catch (IOException e) {
            assertEquals("No space left on device", e.getMessage());
        }

        String[] left = mDownloads.list();
        assertNotNull(left);
        assertEquals("No half-written APK stays in Downloads", 0, left.length);
    }

    @Test
    public void anUnfinishedDownloadIsNotSaved() throws Exception {
        SyncApp app = app("base.apk");
        ReceivedApkStore.Entry entry = mStore.open(app, System.currentTimeMillis());
        Files.write(entry.part("base.apk").toPath(), new byte[100]);

        try {
            ReceivedApkExport.save(entry, new ReceivedApkExport.FolderDestination(mDownloads));
            fail("Half an APK must not be saved");
        } catch (IOException expected) {
            // Nothing to save yet
        }
        assertFalse(mDownloads.exists());
    }

    @Test
    public void fileNamesKeepOnlyCharactersAFileNameCanHold() {
        SyncApp sneaky = new SyncApp("com.example.app", "../..:Evil*/App?", 3, "1.0/beta", false,
                Collections.singletonList(SyncFixtures.SIGNER_A), Collections.<String>emptyList(),
                Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", 1)));
        String name = ReceivedApkExport.fileNameFor(sneaky, false);
        assertFalse(name, name.contains("/"));
        assertFalse(name, name.contains(":"));
        assertFalse(name, name.startsWith("."));
        assertTrue(name, name.endsWith("1.0_beta.apk"));

        SyncApp unnamed = new SyncApp("com.example.app", "  ", 3, "", false,
                Collections.singletonList(SyncFixtures.SIGNER_A), Collections.<String>emptyList(),
                Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", 1)));
        assertEquals("com.example.app.apks", ReceivedApkExport.fileNameFor(unnamed, true));

        char[] longLabel = new char[300];
        Arrays.fill(longLabel, 'a');
        SyncApp verbose = new SyncApp("com.example.app", new String(longLabel), 3, "1", false,
                Collections.singletonList(SyncFixtures.SIGNER_A), Collections.<String>emptyList(),
                Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", 1)));
        assertTrue(ReceivedApkExport.fileNameFor(verbose, false).length() <= 100);
    }

    @Test
    public void androidsOwnInstallerOpensTheApkNotThisAppOrAnother() throws Exception {
        ReceivedApkStore.Entry entry = received("base.apk");
        Uri uri = FmProvider.getContentUri(entry.file("base.apk"));
        Intent query = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, LanSyncDownloader.MIME_APK);
        // This app handles APKs too, and so does a store someone installed
        handles(query, mContext.getPackageName(), false);
        handles(query, "com.example.store", false);
        handles(query, SYSTEM_INSTALLER, true);

        Intent intent = ReceivedApkExport.systemInstallerIntent(mContext, entry);

        assertNotNull(intent);
        assertEquals(new ComponentName(SYSTEM_INSTALLER, SYSTEM_INSTALLER + ".InstallStart"), intent.getComponent());
        assertEquals(uri, intent.getData());
        assertEquals(LanSyncDownloader.MIME_APK, intent.getType());
        assertTrue("Android's installer may read the file",
                (intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertNotNull(intent.getClipData());
        assertEquals(uri, intent.getClipData().getItemAt(0).getUri());
    }

    @Test
    public void thereIsNoAndroidInstallerForSplitsOrWithoutOne() throws Exception {
        ReceivedApkStore.Entry single = received("base.apk");
        Uri uri = FmProvider.getContentUri(single.file("base.apk"));
        handles(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, LanSyncDownloader.MIME_APK),
                "com.example.store", false);
        assertNull("Only a store that isn't part of Android", ReceivedApkExport.systemInstallerIntent(mContext, single));

        ReceivedApkStore.Entry bundle = received("base.apk", "split_config.en.apk");
        assertNull(ReceivedApkExport.systemInstallerIntent(mContext, bundle));
    }

    private void handles(@NonNull Intent query, @NonNull String packageName, boolean system) {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageName;
        applicationInfo.flags = system ? ApplicationInfo.FLAG_SYSTEM : 0;
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = packageName;
        activityInfo.name = packageName + ".InstallStart";
        activityInfo.applicationInfo = applicationInfo;
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;
        shadowOf(mContext.getPackageManager()).addResolveInfoForIntent(query, resolveInfo);
    }

    @NonNull
    private ReceivedApkStore.Entry received(@NonNull String... names) throws IOException {
        ReceivedApkStore.Entry entry = mStore.open(app(names), System.currentTimeMillis());
        for (String name : names) {
            Files.copy(mApk.toPath(), entry.file(name).toPath());
        }
        return mStore.markComplete(entry, System.currentTimeMillis());
    }

    @NonNull
    private SyncApp app(@NonNull String... names) {
        List<SyncApp.ApkFileInfo> files = new ArrayList<>();
        for (String name : names) {
            files.add(new SyncApp.ApkFileInfo(name, mApk.length()));
        }
        return SyncFixtures.app(PKG, 1, Collections.singletonList(ApkSignatureGateTest.TEXTLAUNCHER_SIGNER),
                Collections.<String>emptyList(), files);
    }

    @NonNull
    private static byte[] readAll(@NonNull InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
