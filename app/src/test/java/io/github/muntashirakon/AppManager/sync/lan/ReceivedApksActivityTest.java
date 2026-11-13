// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.widget.RecyclerView;

@RunWith(RobolectricTestRunner.class)
public class ReceivedApksActivityTest {
    private static final long WAIT_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final String PKG = "ademar.textlauncher";

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private final Map<String, SyncApp> mInstalled = new HashMap<>();
    private Context mContext;
    private ReceivedApkStore mStore;
    private ReceivedApkStore.Entry mEntry;
    private ActivityController<ReceivedApksActivity> mController;
    private ReceivedApksActivity mActivity;
    private ReceivedApksViewModel mViewModel;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        Ops.setAuthenticated(mContext, true);
        File dir = new File(mTemp.getRoot(), LanSyncDownloader.DIR_NAME);
        mStore = new ReceivedApkStore(dir);
        File apk = SyncFixtures.resource(ApkSignatureGateTest.TEXTLAUNCHER);
        SyncApp app = SyncFixtures.app(PKG, 1, Collections.singletonList(ApkSignatureGateTest.TEXTLAUNCHER_SIGNER),
                Collections.<String>emptyList(), Collections.singletonList(new SyncApp.ApkFileInfo("base.apk",
                        apk.length())));
        mEntry = mStore.open(app, System.currentTimeMillis());
        Files.copy(apk.toPath(), mEntry.file("base.apk").toPath());
        mEntry = mStore.markComplete(mEntry, System.currentTimeMillis());

        mController = Robolectric.buildActivity(ReceivedApksActivity.class).setup();
        mActivity = mController.get();
        // An AndroidViewModel holds the Application of the first test in the run, so its own
        // cache folder isn't this test's: point it at the folder seeded above.
        mViewModel = new ViewModelProvider(mActivity).get(ReceivedApksViewModel.class);
        mViewModel.setUpForTesting(dir, () -> mInstalled, ApkSigners.VERIFIED_FILE_READER);
    }

    @After
    public void tearDown() {
        mController.pause().stop().destroy();
        mViewModel.getSettings().setRetention(LanSyncSettings.RETENTION_24_HOURS);
        mViewModel.getSettings().setKeepAfterInstall(false);
        Ops.setAuthenticated(mContext, false);
    }

    @Test
    public void aReceivedApkInstallsWithNoOtherDeviceAround() throws Exception {
        View row = loadRow();
        View install = row.findViewById(R.id.install);
        assertEquals(View.VISIBLE, install.getVisibility());

        install.performClick();

        Intent started = waitFor(() -> shadowOf(mActivity).getNextStartedActivity());
        assertNotNull(started.getComponent());
        assertEquals(PackageInstallerActivity.class.getName(), started.getComponent().getClassName());
        assertTrue("The APK stays until the app is in", mEntry.file("base.apk").isFile());
    }

    @Test
    public void anAppSignedByAnotherKeyIsNotHandedOn() throws Exception {
        mInstalled.put(PKG, SyncFixtures.app(PKG, 1, SyncFixtures.SIGNER_B));
        View row = loadRow();

        row.findViewById(R.id.install).performClick();

        String toast = waitFor(ShadowToast::getTextOfLatestToast);
        assertTrue(toast, toast.contains(PKG));
        assertNull(shadowOf(mActivity).getNextStartedActivity());
        assertTrue("The files are kept for later", mEntry.file("base.apk").isFile());
    }

    @Test
    public void deleteRemovesTheApkAndShowsTheEmptyList() throws Exception {
        View row = loadRow();

        row.findViewById(R.id.delete).performClick();

        waitFor(ShadowToast::getTextOfLatestToast);
        assertFalse(mEntry.dir.exists());
        assertNull(mStore.find(mEntry.key));
        RecyclerView list = mActivity.findViewById(android.R.id.list);
        waitFor(() -> list.getAdapter().getItemCount() == 0 ? Boolean.TRUE : null);
    }

    @Test
    public void anUnfinishedDownloadCannotBeInstalled() throws Exception {
        SyncApp app = mEntry.app;
        mStore.delete(mEntry);
        mEntry = mStore.open(app, System.currentTimeMillis());
        Files.write(mEntry.part("base.apk").toPath(), new byte[100]);

        View row = loadRow();

        assertEquals(View.GONE, row.findViewById(R.id.install).getVisibility());
        String detail = ((TextView) row.findViewById(R.id.detail)).getText().toString();
        assertTrue(detail, detail.startsWith("Unfinished"));
    }

    @Test
    public void anInstalledAppTakesItsApkWithItUnlessKept() throws Exception {
        loadRow();
        View keep = mActivity.findViewById(R.id.keep_installed);
        RecyclerView list = mActivity.findViewById(android.R.id.list);

        // Installed elsewhere while the screen shows, with "keep" turned on first
        keep.performClick();
        assertTrue(mViewModel.getSettings().isKeepAfterInstall());
        mInstalled.put(PKG, mEntry.app);
        Object before = mViewModel.getItems().getValue();
        mViewModel.load();
        waitFor(() -> mViewModel.getItems().getValue() != before ? Boolean.TRUE : null);
        assertTrue("Kept on request", mEntry.dir.exists());

        keep.performClick();

        waitFor(() -> list.getAdapter().getItemCount() == 0 ? Boolean.TRUE : null);
        assertFalse(mViewModel.getSettings().isKeepAfterInstall());
        assertFalse(mEntry.dir.exists());
    }

    @Test
    public void theRetentionChoiceIsSaved() throws Exception {
        loadRow();

        mActivity.findViewById(R.id.retention_7_days).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(LanSyncSettings.RETENTION_7_DAYS, mViewModel.getSettings().getRetention());
    }

    @NonNull
    private View loadRow() throws Exception {
        mViewModel.load();
        RecyclerView list = mActivity.findViewById(android.R.id.list);
        return waitFor(() -> {
            if (list.getAdapter() == null || list.getAdapter().getItemCount() == 0) {
                return null;
            }
            list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
            list.layout(0, 0, 1080, 1920);
            return list.getChildCount() > 0 ? list.getChildAt(0) : null;
        });
    }

    @NonNull
    private static <T> T waitFor(@NonNull Callable<T> probe) throws Exception {
        long deadline = System.nanoTime() + WAIT_NANOS;
        while (true) {
            shadowOf(Looper.getMainLooper()).idle();
            T value = probe.call();
            if (value != null) {
                return value;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting");
            }
            Thread.sleep(20);
        }
    }
}
