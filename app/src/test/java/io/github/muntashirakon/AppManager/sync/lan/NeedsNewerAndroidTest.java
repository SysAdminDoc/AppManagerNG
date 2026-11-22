// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Build;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.chip.Chip;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowNsdManager;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.test.TestApks;

/**
 * An app built for a newer Android than this phone runs shows as needing that Android, never as
 * badly signed, and isn't offered for download (#22). Revision 4 of the sync messages carries each
 * app's minimum Android; an older host doesn't, and then the downloaded file says it.
 */
@RunWith(RobolectricTestRunner.class)
public class NeedsNewerAndroidTest {
    private static final String PKG = "com.example.walkie";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private ExecutorService mExecutor;
    private LocalIdentity mHostIdentity;
    private LocalIdentity mClientIdentity;
    private SyncFixtures.Catalog mCatalog;
    private Future<Void> mHost;
    private SyncClientSession mClient;
    private ActivityController<LanSyncActivity> mController;

    @Before
    public void setUp() throws Exception {
        mExecutor = Executors.newFixedThreadPool(2);
        mHostIdentity = LocalIdentity.generate(new SeededRandom(71));
        mClientIdentity = LocalIdentity.generate(new SeededRandom(72));
        mCatalog = new SyncFixtures.Catalog();
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (mClient != null) {
                mClient.close();
                mHost.get(10, TimeUnit.SECONDS);
            }
            if (mController != null) {
                mController.pause().stop().destroy();
                Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), false);
                ShadowNsdManager.reset();
            }
        } finally {
            mExecutor.shutdownNow();
            LanSyncInstallTracker.resetForTesting();
        }
    }

    @Test
    public void revisionFourListsEachAppsMinimumAndroid() throws Exception {
        mCatalog.apps.add(app(32, 1));

        List<SyncApp> apps = connectAndList(SyncMessages.REVISION);

        assertEquals(32, apps.get(0).minSdk);
        assertEquals(Collections.singletonList("CN=Walkie"), apps.get(0).signerSubjects);
    }

    @Test
    public void olderHostsStillListTheirAppsWithoutAMinimum() throws Exception {
        for (int revision = 1; revision < SyncMessages.REVISION_MIN_SDK; ++revision) {
            mCatalog.apps.clear();
            mCatalog.apps.add(app(32, 1));

            List<SyncApp> apps = connectAndList(revision);

            assertEquals("Revision " + revision, PKG, apps.get(0).packageName);
            assertEquals("Revision " + revision, 0, apps.get(0).minSdk);
            assertEquals(revision >= SyncMessages.REVISION_SIGNER_SUBJECTS,
                    !apps.get(0).signerSubjects.isEmpty());
            mClient.close();
            mHost.get(10, TimeUnit.SECONDS);
            mClient = null;
        }
    }

    @Test
    public void aRevisionThreeClientGetsTheListItCanRead() throws Exception {
        mCatalog.apps.add(app(32, 1));
        startHost(SyncMessages.REVISION);
        SyncClientSession client = connect();
        try {
            SecureChannel channel = client.getPairedSession().getChannel();
            channel.send(SyncMessages.revisionQuery(3));
            channel.receive(SyncMessages.MAX_CONTROL);
            channel.send(SyncMessages.listRequest(false));
            byte[] reply = channel.receive(SyncMessages.MAX_LIST);
            // What a revision 3 build runs, which refuses trailing bytes
            List<SyncApp> apps = SyncMessages.parseList(reply, true);
            assertEquals(PKG, apps.get(0).packageName);
        } finally {
            client.getPairedSession().getChannel().close();
        }
        mHost.cancel(true);
    }

    @Test
    public void theMinimumSurvivesTheListAndOnlyComesWhenAskedFor() throws Exception {
        List<SyncApp> apps = Collections.singletonList(app(Build.VERSION.SDK_INT + 1, 1));

        List<SyncApp> parsed = SyncMessages.parseList(SyncMessages.listResponse(apps, true, true), true, true);

        assertEquals(Build.VERSION.SDK_INT + 1, parsed.get(0).minSdk);
        assertThrows(IOException.class, () -> SyncMessages.parseList(SyncMessages.listResponse(apps, true, true),
                true));
        assertEquals(0, SyncMessages.parseList(SyncMessages.listResponse(apps, true), true).get(0).minSdk);
    }

    @Test
    public void aListedAppForANewerAndroidIsNeverDownloaded() throws Exception {
        File apk = signedApk(Build.VERSION.SDK_INT + 1);
        SyncApp listed = app(Build.VERSION.SDK_INT + 1, apk.length());
        mCatalog.add(listed, apk);
        SyncApp remote = connectAndList(SyncMessages.REVISION).get(0);
        File dir = new File(mTemp.getRoot(), LanSyncDownloader.DIR_NAME);

        LanSyncDownloader.NeedsNewerAndroidException e = assertThrows(
                LanSyncDownloader.NeedsNewerAndroidException.class,
                () -> LanSyncDownloader.pull(mClient, remote, null, dir, ApkSigners.VERIFIED_FILE_READER, null));

        assertEquals(Build.VERSION.SDK_INT + 1, e.minSdk);
        assertTrue("Nothing was asked for", mCatalog.fileLookups.isEmpty());
    }

    @Test
    public void fromAnOlderHostTheFileSaysItNeedsANewerAndroidAndStaysInReceivedApks() throws Exception {
        File apk = signedApk(Build.VERSION.SDK_INT + 1);
        mCatalog.add(app(0, apk.length()), apk);
        SyncApp remote = connectAndList(SyncMessages.REVISION_MIN_SDK - 1).get(0);
        assertEquals(0, remote.minSdk);
        File dir = new File(mTemp.getRoot(), LanSyncDownloader.DIR_NAME);

        LanSyncDownloader.NeedsNewerAndroidException e = assertThrows(
                LanSyncDownloader.NeedsNewerAndroidException.class,
                () -> LanSyncDownloader.pull(mClient, remote, null, dir, ApkSigners.VERIFIED_FILE_READER, null));

        assertEquals(Build.VERSION.SDK_INT + 1, e.minSdk);
        List<ReceivedApkStore.Entry> received = new ReceivedApkStore(dir).list();
        assertEquals("It can still be saved and moved to another phone", 1, received.size());
        assertTrue(received.get(0).complete);
        assertTrue(received.get(0).hasAllFiles());
    }

    @Test
    public void theRowSaysWhichAndroidItNeedsAndOffersNoDownload() throws Exception {
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), true);
        mController = Robolectric.buildActivity(LanSyncActivity.class).setup();
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        int newer = Build.VERSION.SDK_INT + 1;
        File apk = signedApk(newer);
        SyncFixtures.Catalog catalog = new SyncFixtures.Catalog();
        SyncApp there = new SyncApp(PKG, "Walkie-Talkie", 5, "2.0.3", false, Collections.singletonList(signer()),
                Collections.<String>emptyList(), Collections.singletonList(new SyncApp.ApkFileInfo("base.apk",
                apk.length())), Collections.<String>emptyList(), newer);
        catalog.add(there, apk);
        SyncApp here = new SyncApp(PKG, "Walkie-Talkie", 3, "1.19.7", false, Collections.singletonList(signer()),
                Collections.<String>emptyList(), Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", 1)));
        viewModel.setLocalIndex(() -> new HashMap<>(Collections.singletonMap(PKG, here)));
        viewModel.setInstalledLookup(packageName -> PKG.equals(packageName) ? here : null);
        LanSyncHost host = LanSyncScreen.allowingHost(catalog, 75);
        try {
            LanSyncViewModel.AppRow row = LanSyncScreen.connectAndWaitForRow(viewModel, host.start());
            assertEquals(SyncComparator.Status.NEWER, row.status);
            assertTrue(row.needsNewerAndroid);
            ((Chip) activity.findViewById(R.id.chip_newer)).performClick();
            shadowOf(Looper.getMainLooper()).idle();

            LanSyncAppsAdapter.ViewHolder holder = LanSyncScreen.firstRow(activity);
            String needs = activity.getString(R.string.lan_sync_needs_newer_android, AndroidReleases.describe(newer),
                    AndroidReleases.describe(Build.VERSION.SDK_INT));
            assertEquals(needs, holder.note.getText().toString());
            assertEquals(View.GONE, holder.pull.getVisibility());
            assertEquals("Update all leaves it out", View.GONE,
                    activity.findViewById(R.id.update_all).getVisibility());

            viewModel.pullAllNewer();
            viewModel.pull(row);
            String toast = LanSyncScreen.waitFor(ShadowToast::getTextOfLatestToast);
            assertEquals(activity.getString(R.string.lan_sync_pull_failed, "Walkie-Talkie", needs), toast);
            assertTrue("Nothing was downloaded", catalog.fileLookups.isEmpty());
        } finally {
            host.stop();
        }
    }

    @Test
    public void androidVersionsAreNamedTheWayPeopleKnowThem() {
        assertEquals("12L (API 32)", AndroidReleases.describe(32));
        assertEquals("11 (API 30)", AndroidReleases.describe(30));
        assertEquals("5.0 (API 21)", AndroidReleases.describe(21));
        assertEquals("16 (API 36)", AndroidReleases.describe(36));
        assertEquals("API 99", AndroidReleases.describe(99));
    }

    // Helpers

    @NonNull
    private static String signer() throws Exception {
        return DigestUtils.getHexDigest(DigestUtils.SHA_256, key().getCertificate().getEncoded());
    }

    private static KeyPair sKey;

    @NonNull
    private static synchronized KeyPair key() throws Exception {
        if (sKey == null) {
            sKey = TestApks.newKey("Walkie");
        }
        return sKey;
    }

    @NonNull
    private File signedApk(int minSdk) throws Exception {
        File dir = mTemp.newFolder();
        return TestApks.signed(new File(dir, "base.apk"), PKG, minSdk, minSdk, key(), false, false, true, null);
    }

    @NonNull
    private static SyncApp app(int minSdk, long size) throws Exception {
        return new SyncApp(PKG, "Walkie-Talkie", 5, "2.0.3", false, Collections.singletonList(signer()),
                Collections.<String>emptyList(), Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", size)),
                Collections.singletonList("CN=Walkie"), minSdk);
    }

    private void startHost(int revision) {
        TestPipe.Duplex duplex = new TestPipe.Duplex();
        mDuplex = duplex;
        FrameIo io = duplex.endpointA();
        mHost = mExecutor.submit(() -> {
            HostSession session = new HostSession(io, HostSession.NO_TIMEOUTS, "Host", mHostIdentity,
                    new SeededRandom(73), new SyncFixtures.MemoryTrust(), s -> ApprovalGate.Decision.ALLOW, mCatalog,
                    null);
            session.setRevision(revision);
            session.run();
            return null;
        });
    }

    private TestPipe.Duplex mDuplex;

    @NonNull
    private SyncClientSession connect() throws IOException {
        return SyncClientSession.connect(mDuplex.endpointB(), HostSession.NO_TIMEOUTS, "Client", mClientIdentity,
                new SeededRandom(74), null);
    }

    @NonNull
    private List<SyncApp> connectAndList(int revision) throws IOException {
        startHost(revision);
        mClient = connect();
        List<SyncApp> apps = mClient.list(false);
        assertNotNull(apps);
        assertEquals(1, apps.size());
        return apps;
    }
}
