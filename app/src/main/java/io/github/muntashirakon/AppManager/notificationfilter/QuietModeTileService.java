// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.shortcut.QuickOpenTileService;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/**
 * Turns quiet mode on and off. Like the system's Do Not Disturb tile it works on a locked phone, since it only changes
 * how new notifications arrive. Without notification access it opens the filter screen, which says what's missing.
 */
@RequiresApi(Build.VERSION_CODES.N)
public class QuietModeTileService extends TileService {
    private static final String TAG = QuietModeTileService.class.getSimpleName();

    /** Callers check the version first: below N this class can't even load, since TileService doesn't exist. */
    @RequiresApi(Build.VERSION_CODES.O)
    public static void requestTileStateUpdate(@NonNull Context context) {
        try {
            TileService.requestListeningState(context, new ComponentName(context, QuietModeTileService.class));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not request a quiet mode tile update.", e);
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile(NotificationFilterSettings.getInstance(this).isQuietModeActive(System.currentTimeMillis()));
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!NotificationListenerAccess.isGranted(this)) {
            try {
                launch(new Intent(this, NotificationFilterActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not open the notification filter.", e);
                UIUtils.displayShortToast(R.string.failed);
            }
            return;
        }
        boolean on = NotificationFilterSettings.getInstance(this).toggleQuietMode(System.currentTimeMillis());
        updateTile(on);
    }

    @VisibleForTesting
    void launch(@NonNull Intent intent) {
        QuickOpenTileService.startAndCollapse(this, intent);
    }

    private void updateTile(boolean on) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setLabel(getString(R.string.notification_filter_quiet_mode));
        tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
