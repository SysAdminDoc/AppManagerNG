// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

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
import android.provider.Settings;
import android.service.quicksettings.TileService;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowTileService;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class QuietModeTileServiceTest {
    private static final String ENABLED_LISTENERS = "enabled_notification_listeners";

    private static final class RecordingTile extends QuietModeTileService {
        Intent launched;

        RecordingTile(@NonNull Context base) {
            attachBaseContext(base);
        }

        @Override
        void launch(@NonNull Intent intent) {
            launched = intent;
        }
    }

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        NotificationFilterSettings.resetForTesting();
        revokeAccess();
    }

    @After
    public void tearDown() {
        revokeAccess();
        NotificationFilterSettings.resetForTesting();
    }

    /**
     * Android writes an empty list when the last listener goes. NotificationManagerCompat keeps its last answer while
     * the setting is null, so writing null here would leave access granted for the next test.
     */
    private void revokeAccess() {
        Settings.Secure.putString(mContext.getContentResolver(), ENABLED_LISTENERS, "");
    }

    private void grantAccess() {
        Settings.Secure.putString(mContext.getContentResolver(), ENABLED_LISTENERS,
                NotificationListenerAccess.component(mContext).flattenToString());
    }

    private boolean quietModeOn() {
        return NotificationFilterSettings.getInstance(mContext).isQuietModeActive(System.currentTimeMillis());
    }

    @Test
    public void withoutAccessItOpensTheFilterScreen() {
        RecordingTile tile = new RecordingTile(mContext);

        tile.onClick();

        assertNotNull(tile.launched);
        assertEquals(new ComponentName(mContext, NotificationFilterActivity.class), tile.launched.getComponent());
        assertTrue((tile.launched.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertFalse(quietModeOn());
    }

    @Test
    public void withAccessItTurnsQuietModeOnAndOff() {
        grantAccess();
        assertTrue(NotificationListenerAccess.isGranted(mContext));
        RecordingTile tile = new RecordingTile(mContext);

        tile.onClick();
        assertTrue(quietModeOn());

        tile.onClick();
        assertFalse(quietModeOn());
        assertNull(tile.launched);
    }

    @Test
    public void itWorksOnALockedPhoneWithoutAnUnlock() {
        grantAccess();
        RecordingTile tile = new RecordingTile(mContext);
        ShadowTileService shadow = Shadow.extract(tile);
        shadow.setLocked(true);

        tile.onClick();

        assertTrue(quietModeOn());
        assertNull(tile.launched);
    }

    @Test
    public void tileIsDeclaredBehindTheBindPermissionAndToggles() {
        PackageManager pm = mContext.getPackageManager();
        List<ResolveInfo> tiles = pm.queryIntentServices(new Intent(TileService.ACTION_QS_TILE)
                .setPackage(mContext.getPackageName()), PackageManager.GET_META_DATA);
        ResolveInfo found = null;
        for (ResolveInfo info : tiles) {
            if (QuietModeTileService.class.getName().equals(info.serviceInfo.name)) {
                found = info;
            }
        }

        assertNotNull(found);
        assertTrue(found.serviceInfo.exported);
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", found.serviceInfo.permission);
        assertNotNull(found.serviceInfo.metaData);
        assertTrue(found.serviceInfo.metaData.getBoolean(TileService.META_DATA_TOGGLEABLE_TILE));
    }
}
