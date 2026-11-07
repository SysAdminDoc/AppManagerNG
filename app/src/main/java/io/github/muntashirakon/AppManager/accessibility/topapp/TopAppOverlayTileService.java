// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

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

/** Turns the top-app bubble on and off, and shows which of the two it is. */
@RequiresApi(Build.VERSION_CODES.N)
public class TopAppOverlayTileService extends TileService {
    private static final String TAG = TopAppOverlayTileService.class.getSimpleName();

    static void requestTileStateUpdate(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            TileService.requestListeningState(context, new ComponentName(context, TopAppOverlayTileService.class));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not request a top app bubble tile update.", e);
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile(TopAppOverlayService.isRunning());
    }

    @Override
    public void onClick() {
        super.onClick();
        if (isLocked()) {
            unlockAndRun(this::toggle);
            return;
        }
        toggle();
    }

    private void toggle() {
        TopAppOverlayController.Outcome outcome = TopAppOverlayController.toggle(this);
        if (outcome.settingsIntent == null) {
            // The service reports its real state through requestTileStateUpdate once it's up or down.
            updateTile(outcome.running);
            return;
        }
        try {
            launch(outcome.settingsIntent);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not open the settings screen.", e);
            UIUtils.displayLongToast(R.string.top_app_overlay_failed);
        }
    }

    @VisibleForTesting
    void launch(@NonNull Intent intent) {
        QuickOpenTileService.startAndCollapse(this, intent);
    }

    private void updateTile(boolean running) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setLabel(getString(R.string.top_app_overlay_title));
        tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
