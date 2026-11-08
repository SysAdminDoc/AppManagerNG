// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowTileService;
import org.robolectric.shadows.ShadowToast;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.main.SplashActivity;

@RunWith(RobolectricTestRunner.class)
public class ForceStopTileServiceTest {
    private static final class RecordingTile extends ForceStopTileService {
        Intent launched;

        RecordingTile(@NonNull Context base) {
            attachBaseContext(base);
        }

        @Override
        void launch(@NonNull Intent intent) {
            launched = intent;
        }
    }

    @Test
    public void supportsRequestAddTileOnlyOnTiramisuPlus() {
        assertFalse(ForceStopTileService.supportsRequestAddTile(Build.VERSION_CODES.S_V2));
        assertTrue(ForceStopTileService.supportsRequestAddTile(Build.VERSION_CODES.TIRAMISU));
        assertTrue(ForceStopTileService.supportsRequestAddTile(Build.VERSION_CODES.UPSIDE_DOWN_CAKE));
    }

    @Test
    public void tileComponentTargetsForceStopTileService() {
        Context context = ApplicationProvider.getApplicationContext();

        assertEquals(ForceStopTileService.class.getName(),
                ForceStopTileService.getTileComponentName(context).getClassName());
    }

    // Android 14 and later drop unlockAndRun's callback once the panel closes for the PIN, so a tap on the lock screen
    // did nothing (#25). The tap now starts a screen the system holds back until the PIN is in.
    @Test
    public void aLockedTapActsOnlyOnceThePinIsIn() {
        Context context = ApplicationProvider.getApplicationContext();
        ForceStopTileController.clearSelectedTarget();
        RecordingTile tile = new RecordingTile(context);
        ShadowTileService shadow = Shadow.extract(tile);
        shadow.setLocked(true);

        tile.onClick();
        ShadowLooper.idleMainLooper();

        assertTrue("the click must not go through unlockAndRun", tile.isLocked());
        assertNull("nothing happens before the PIN", ShadowToast.getLatestToast());
        assertNotNull(tile.launched);
        assertEquals(new ComponentName(context, TileActionActivity.class), tile.launched.getComponent());

        TileActionActivity unlocked = Robolectric.buildActivity(TileActionActivity.class, tile.launched).create().get();
        ShadowLooper.idleMainLooper();

        assertEquals(context.getString(R.string.force_stop_tile_no_app), ShadowToast.getTextOfLatestToast());
        Intent opened = shadowOf(unlocked).getNextStartedActivity();
        assertNotNull(opened);
        assertEquals(new ComponentName(context, SplashActivity.class), opened.getComponent());
        assertTrue(unlocked.isFinishing());
    }
}
