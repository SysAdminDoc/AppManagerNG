// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_A;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_B;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowNsdManager;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.SelfReplaceGuard;
import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * Replacing uninstalls the copy here before the installer starts, which for this app would end the
 * app halfway and leave neither copy (#45). Its own row explains what to do instead.
 */
@RunWith(RobolectricTestRunner.class)
public class LanSyncSelfReplaceTest {
    private ActivityController<LanSyncActivity> mController;

    @Before
    public void setUp() {
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), true);
        mController = Robolectric.buildActivity(LanSyncActivity.class).setup();
    }

    @After
    public void tearDown() {
        mController.pause().stop().destroy();
        new LanSyncSettings(ApplicationProvider.getApplicationContext()).setAllowReplace(false);
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), false);
        ShadowNsdManager.reset();
        LanSyncInstallTracker.resetForTesting();
    }

    @Test
    public void thisAppIsNeverUninstalledToTakeAnotherCopy() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        String self = activity.getPackageName();
        List<String> uninstalled = Collections.synchronizedList(new ArrayList<>());
        viewModel.setUninstaller((packageName, label) -> {
            uninstalled.add(packageName);
            return null;
        }, Runnable::run);
        // The other phone has the other build, signed by another key
        SyncFixtures.Catalog catalog = catalogWith(self, 40, SIGNER_A);
        installHere(viewModel, self, 34, SIGNER_B);
        LanSyncHost host = LanSyncScreen.allowingHost(catalog, 301);
        try {
            LanSyncViewModel.AppRow row = LanSyncScreen.connectAndWaitForRow(viewModel, host.start());
            assertEquals(SyncComparator.Status.SIGNATURE_DIFFERS, row.status);
            ((Chip) activity.findViewById(R.id.chip_signature)).performClick();
            MaterialSwitch allowReplace = activity.findViewById(R.id.allow_replace);
            allowReplace.performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(new LanSyncSettings(activity).isAllowReplace());

            LanSyncAppsAdapter.ViewHolder holder = LanSyncScreen.firstRow(activity);
            String why = SelfReplaceGuard.explanation(activity);
            assertEquals("No Replace button for this app", View.GONE, holder.pull.getVisibility());
            assertEquals(why, holder.note.getText().toString());

            // Asked for anyway, it neither downloads nor uninstalls
            viewModel.replace(row);
            String toast = LanSyncScreen.waitFor(ShadowToast::getTextOfLatestToast);
            assertEquals(why, toast);
            LanSyncScreen.waitFor(() -> viewModel.getTask().getValue() == null ? Boolean.TRUE : null);
            assertTrue("This app stays installed", uninstalled.isEmpty());
            assertTrue("Nothing was downloaded", catalog.fileLookups.isEmpty());
            assertNull("Nothing goes to the installer", shadowOf(activity).getNextStartedActivity());
        } finally {
            host.stop();
        }
    }

    @Test
    public void anUpdateOfThisAppSignedByTheSameKeyIsStillOffered() throws Exception {
        LanSyncActivity activity = mController.get();
        LanSyncViewModel viewModel = new ViewModelProvider(activity).get(LanSyncViewModel.class);
        String self = activity.getPackageName();
        SyncFixtures.Catalog catalog = catalogWith(self, 40, SIGNER_A);
        installHere(viewModel, self, 34, SIGNER_A);
        new LanSyncSettings(activity).setAllowReplace(true);
        LanSyncHost host = LanSyncScreen.allowingHost(catalog, 311);
        try {
            LanSyncViewModel.AppRow row = LanSyncScreen.connectAndWaitForRow(viewModel, host.start());
            assertEquals(SyncComparator.Status.NEWER, row.status);

            LanSyncAppsAdapter.ViewHolder holder = LanSyncScreen.firstRow(activity);
            assertEquals(View.VISIBLE, holder.pull.getVisibility());
            assertEquals(activity.getString(R.string.lan_sync_get), holder.pull.getText().toString());
        } finally {
            host.stop();
        }
    }

    @NonNull
    private static SyncFixtures.Catalog catalogWith(@NonNull String packageName, long versionCode,
                                                    @NonNull String signer) {
        File apk = SyncFixtures.resource(ApkSignatureGateTest.TEXTLAUNCHER);
        return new SyncFixtures.Catalog().add(new SyncApp(packageName, "AppManagerNG", versionCode, "0.9.0", false,
                Collections.singletonList(signer), Collections.<String>emptyList(),
                Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", apk.length()))), apk);
    }

    private static void installHere(@NonNull LanSyncViewModel viewModel, @NonNull String packageName,
                                    long versionCode, @NonNull String signer) {
        SyncApp here = SyncFixtures.app(packageName, versionCode, signer);
        viewModel.setLocalIndex(() -> new HashMap<>(Collections.singletonMap(packageName, here)));
        viewModel.setInstalledLookup(name -> packageName.equals(name) ? here : null);
    }
}
