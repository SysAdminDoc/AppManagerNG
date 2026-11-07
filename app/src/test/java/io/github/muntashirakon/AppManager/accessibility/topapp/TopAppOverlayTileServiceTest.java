// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.shadows.ShadowTileService;
import org.robolectric.shadows.ShadowToast;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.FeatureController;

@RunWith(RobolectricTestRunner.class)
public class TopAppOverlayTileServiceTest {
    private static class RecordingTile extends TopAppOverlayTileService {
        Intent launched;

        RecordingTile(@NonNull Context base) {
            attachBaseContext(base);
        }

        @Override
        void launch(@NonNull Intent intent) {
            launched = intent;
        }
    }

    private Application mApp;
    private RecordingTile mTile;
    private boolean mUsageFeatureBefore;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        mTile = new RecordingTile(mApp);
        mUsageFeatureBefore = FeatureController.isUsageAccessEnabled();
    }

    @After
    public void tearDown() {
        TopAppOverlayService.setRunningForTesting(false);
        FeatureController.getInstance().modifyState(FeatureController.FEAT_USAGE_ACCESS, mUsageFeatureBefore);
        ShadowSettings.setCanDrawOverlays(false);
    }

    private void turnAccessibilityOn() {
        Settings.Secure.putString(mApp.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                mApp.getPackageName() + "/.accessibility.NoRootAccessibilityService");
    }

    private void click() {
        mTile.onClick();
        ShadowLooper.idleMainLooper();
    }

    @Test
    public void theTileShowsWhetherTheBubbleIsRunning() {
        TopAppOverlayService.setRunningForTesting(true);
        mTile.onStartListening();
        assertEquals(Tile.STATE_ACTIVE, mTile.getQsTile().getState());

        TopAppOverlayService.setRunningForTesting(false);
        mTile.onStartListening();
        assertEquals(Tile.STATE_INACTIVE, mTile.getQsTile().getState());
    }

    @Test
    public void aMissingOverlayPermissionGoesStraightToTheSystemScreenAndSaysWhy() {
        ShadowSettings.setCanDrawOverlays(false);
        turnAccessibilityOn();

        click();

        assertNotNull(mTile.launched);
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, mTile.launched.getAction());
        assertEquals("package:" + mApp.getPackageName(), mTile.launched.getDataString());
        assertTrue((mTile.launched.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertEquals(mApp.getString(R.string.top_app_overlay_needs_overlay), ShadowToast.getTextOfLatestToast());
        assertNull(shadowOf(mApp).getNextStartedService());
        assertEquals(Tile.STATE_INACTIVE, mTile.getQsTile().getState());
    }

    @Test
    public void noSourceWithUsageAccessOffGoesToTheAccessibilityScreen() {
        ShadowSettings.setCanDrawOverlays(true);
        FeatureController.getInstance().modifyState(FeatureController.FEAT_USAGE_ACCESS, false);

        click();

        assertNotNull(mTile.launched);
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, mTile.launched.getAction());
        assertEquals(mApp.getString(R.string.top_app_overlay_needs_accessibility), ShadowToast.getTextOfLatestToast());
        assertNull(shadowOf(mApp).getNextStartedService());
    }

    @Test
    public void aReadyTileStartsTheServiceAndTurnsOn() {
        ShadowSettings.setCanDrawOverlays(true);
        turnAccessibilityOn();

        click();

        assertNull(mTile.launched);
        Intent started = shadowOf(mApp).getNextStartedService();
        assertNotNull(started);
        assertEquals(new ComponentName(mApp, TopAppOverlayService.class), started.getComponent());
        assertEquals(Tile.STATE_ACTIVE, mTile.getQsTile().getState());
    }

    @Test
    public void aRunningTileStopsTheServiceAndTurnsOff() {
        TopAppOverlayService.setRunningForTesting(true);

        click();

        assertNull(mTile.launched);
        Intent stopped = shadowOf(mApp).getNextStoppedService();
        assertNotNull(stopped);
        assertEquals(new ComponentName(mApp, TopAppOverlayService.class), stopped.getComponent());
        assertEquals(Tile.STATE_INACTIVE, mTile.getQsTile().getState());
    }

    @Test
    public void aLockedPhoneUnlocksBeforeTheTileActs() {
        ShadowSettings.setCanDrawOverlays(true);
        turnAccessibilityOn();
        ShadowTileService shadow = Shadow.extract(mTile);
        shadow.setLocked(true);

        click();

        assertFalse(mTile.isLocked());
        assertNotNull(shadowOf(mApp).getNextStartedService());
    }

    @Test
    public void aSettingsScreenThatWontOpenTellsTheUser() {
        ShadowSettings.setCanDrawOverlays(false);
        RecordingTile tile = new RecordingTile(mApp) {
            @Override
            void launch(@NonNull Intent intent) {
                throw new IllegalStateException("no such screen");
            }
        };

        tile.onClick();
        ShadowLooper.idleMainLooper();

        assertEquals(mApp.getString(R.string.top_app_overlay_failed), ShadowToast.getTextOfLatestToast());
    }
}
