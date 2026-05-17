// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.NonNull;
import androidx.core.app.PendingIntentCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;

import java.io.IOException;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class QuickFreezeTileService extends TileService {
    private static final String TAG = QuickFreezeTileService.class.getSimpleName();

    public static void requestTileStateUpdate(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            TileService.requestListeningState(context,
                    new ComponentName(context, QuickFreezeTileService.class));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not request quick freeze tile state update.", e);
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile(Tile.STATE_INACTIVE);
    }

    @Override
    public void onClick() {
        super.onClick();
        if (isLocked()) {
            unlockAndRun(this::runSelectedFreezeProfile);
            return;
        }
        runSelectedFreezeProfile();
    }

    private void runSelectedFreezeProfile() {
        BaseProfile profile;
        try {
            profile = QuickFreezeTileController.loadSelectedProfile();
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Could not load quick freeze profile.", e);
            updateTile(Tile.STATE_UNAVAILABLE);
            UIUtils.displayShortToast(R.string.quick_freeze_tile_profile_missing);
            openProfilesAndCollapse();
            return;
        }
        if (profile == null) {
            updateTile(Tile.STATE_UNAVAILABLE);
            UIUtils.displayShortToast(R.string.quick_freeze_tile_no_profile);
            openProfilesAndCollapse();
            return;
        }
        if (!QuickFreezeTileController.isProfileEligible(profile)) {
            updateTile(Tile.STATE_UNAVAILABLE);
            UIUtils.displayShortToast(R.string.quick_freeze_tile_requires_freeze);
            openProfilesAndCollapse();
            return;
        }
        updateTile(Tile.STATE_ACTIVE);
        Intent intent = ProfileApplierService.getIntent(this,
                ProfileQueueItem.fromProfile(profile, BaseProfile.STATE_ON), true);
        ContextCompat.startForegroundService(this, intent);
        UIUtils.displayShortToast(R.string.quick_freeze_tile_started, profile.name);
    }

    private void updateTile(int configuredState) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setLabel(getString(R.string.quick_freeze_tile_label));
        if (QuickFreezeTileController.getSelectedProfileId() == null) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            tile.setState(configuredState);
        }
        tile.updateTile();
    }

    @SuppressWarnings("deprecation")
    private void openProfilesAndCollapse() {
        Intent intent = new Intent(this, ProfilesActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntentCompat.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT, false);
            startActivityAndCollapse(pendingIntent);
            return;
        }
        startActivityAndCollapse(intent);
    }
}
