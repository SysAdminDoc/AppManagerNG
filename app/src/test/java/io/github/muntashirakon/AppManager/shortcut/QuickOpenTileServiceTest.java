// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowTileService;
import org.robolectric.shadows.ShadowToast;

import java.util.List;

import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.profiles.ProfilesActivity;
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.usage.AppUsageActivity;

@RunWith(RobolectricTestRunner.class)
public class QuickOpenTileServiceTest {
    private static final class RecordingTile extends QuickOpenTileService {
        private final Target mTarget;
        Intent launched;
        RuntimeException launchFailure;

        RecordingTile(@NonNull Context base, @NonNull Target target) {
            attachBaseContext(base);
            mTarget = target;
        }

        @NonNull
        @Override
        Target getTarget() {
            return mTarget;
        }

        @Override
        void launch(@NonNull Intent intent) {
            if (launchFailure != null) {
                throw launchFailure;
            }
            launched = intent;
        }
    }

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @After
    public void restoreFeatures() {
        FeatureController.getInstance().modifyState(FeatureController.FEAT_USAGE_ACCESS, true);
        FeatureController.getInstance().modifyState(FeatureController.FEAT_LOG_VIEWER, true);
    }

    @Test
    public void eachTileOpensItsOwnScreenInANewTask() {
        assertRoutesTo(new QuickOpenTileService.RunningApps(), RunningAppsActivity.class);
        assertRoutesTo(new QuickOpenTileService.AppUsage(), AppUsageActivity.class);
        assertRoutesTo(new QuickOpenTileService.Profiles(), ProfilesActivity.class);
        assertRoutesTo(new QuickOpenTileService.LogViewer(), LogViewerActivity.class);
    }

    @Test
    public void everyTileIsDeclaredAsAQuickSettingsTileBehindTheBindPermission() {
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> tiles = pm.queryIntentServices(new Intent(TileService.ACTION_QS_TILE)
                .setPackage(mContext.getPackageName()), PackageManager.GET_META_DATA);
        for (Class<?> tile : new Class<?>[]{QuickOpenTileService.RunningApps.class,
                QuickOpenTileService.AppUsage.class, QuickOpenTileService.Profiles.class,
                QuickOpenTileService.LogViewer.class}) {
            ServiceInfo found = null;
            for (ResolveInfo info : tiles) {
                if (tile.getName().equals(info.serviceInfo.name)) {
                    found = info.serviceInfo;
                }
            }
            assertNotNull(tile.getName() + " must be declared in the manifest", found);
            assertTrue(tile.getName() + " must be exported for System UI", found.exported);
            assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", found.permission);
        }
    }

    @Test
    public void clickOpensTheScreen() {
        RecordingTile tile = new RecordingTile(mContext, QuickOpenTileService.Target.PROFILES);

        tile.onClick();

        assertEquals(new ComponentName(mContext, ProfilesActivity.class), tile.launched.getComponent());
    }

    @Test
    public void clickOnALockedDeviceGoesThroughUnlock() {
        RecordingTile tile = new RecordingTile(mContext, QuickOpenTileService.Target.RUNNING_APPS);
        ShadowTileService shadow = Shadow.extract(tile);
        shadow.setLocked(true);

        tile.onClick();

        // Only the shadow's unlockAndRun clears the locked state, so this proves the click went through it.
        assertFalse(tile.isLocked());
        assertEquals(new ComponentName(mContext, RunningAppsActivity.class), tile.launched.getComponent());
    }

    @Test
    public void usageTileIsUnavailableAndOpensNothingWhenTheFeatureIsOff() {
        FeatureController.getInstance().modifyState(FeatureController.FEAT_USAGE_ACCESS, false);
        RecordingTile tile = new RecordingTile(mContext, QuickOpenTileService.Target.APP_USAGE);

        tile.onStartListening();
        assertEquals(Tile.STATE_UNAVAILABLE, tile.getQsTile().getState());

        tile.onClick();
        assertNull(tile.launched);
    }

    @Test
    public void logViewerTileFollowsItsFeatureGate() {
        RecordingTile tile = new RecordingTile(mContext, QuickOpenTileService.Target.LOG_VIEWER);

        FeatureController.getInstance().modifyState(FeatureController.FEAT_LOG_VIEWER, true);
        tile.onStartListening();
        assertEquals(Tile.STATE_INACTIVE, tile.getQsTile().getState());

        FeatureController.getInstance().modifyState(FeatureController.FEAT_LOG_VIEWER, false);
        tile.onStartListening();
        assertEquals(Tile.STATE_UNAVAILABLE, tile.getQsTile().getState());
    }

    @Test
    public void aFailedLaunchIsShownInsteadOfCrashingSystemUiBinding() {
        RecordingTile tile = new RecordingTile(mContext, QuickOpenTileService.Target.RUNNING_APPS);
        tile.launchFailure = new IllegalStateException("not allowed");

        tile.onClick();

        ShadowLooper.idleMainLooper();
        assertNotNull(ShadowToast.getLatestToast());
    }

    private void assertRoutesTo(@NonNull QuickOpenTileService tile, @NonNull Class<?> activity) {
        Intent intent = tile.getTarget().getIntent(mContext);
        assertEquals(new ComponentName(mContext, activity), intent.getComponent());
        assertTrue("A tile starts outside any task", (intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }
}
