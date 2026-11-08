// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import org.json.JSONException;

import java.io.IOException;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.shortcut.QuickOpenTileService;
import io.github.muntashirakon.AppManager.shortcut.TileActionActivity;
import io.github.muntashirakon.AppManager.shortcut.TileActionHost;
import io.github.muntashirakon.AppManager.utils.UIUtils;

@RequiresApi(Build.VERSION_CODES.N)
public class QuickFreezeTileService extends TileService implements TileActionHost {
    private static final String TAG = QuickFreezeTileService.class.getSimpleName();

    public static void requestTileStateUpdate(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            TileService.requestListeningState(context, getTileComponentName(context));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not request quick freeze tile state update.", e);
        }
    }

    public static void requestAddTile(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            requestTileStateUpdate(context);
            return;
        }
        try {
            StatusBarManager statusBarManager = context.getSystemService(StatusBarManager.class);
            if (statusBarManager == null) {
                requestTileStateUpdate(context);
                return;
            }
            statusBarManager.requestAddTileService(getTileComponentName(context),
                    context.getString(R.string.quick_freeze_tile_label),
                    Icon.createWithResource(context, R.drawable.ic_snowflake),
                    ContextCompat.getMainExecutor(context),
                    result -> requestTileStateUpdate(context));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not request quick freeze tile installation.", e);
            requestTileStateUpdate(context);
        }
    }

    @VisibleForTesting
    static boolean supportsRequestAddTile(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.TIRAMISU;
    }

    @NonNull
    @VisibleForTesting
    static ComponentName getTileComponentName(@NonNull Context context) {
        return new ComponentName(context, QuickFreezeTileService.class);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile(Tile.STATE_INACTIVE);
    }

    @Override
    public void onClick() {
        super.onClick();
        TileActionActivity.run(this, TileActionActivity.Action.QUICK_FREEZE,
                () -> runSelectedFreezeProfile(this, this));
    }

    /** What a tap does, run by the tile or, after an unlock, by {@link TileActionActivity}. */
    public static void runSelectedFreezeProfile(@NonNull Context context, @NonNull TileActionHost host) {
        BaseProfile profile;
        try {
            profile = QuickFreezeTileController.loadSelectedProfile();
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Could not load quick freeze profile.", e);
            host.showTileState(Tile.STATE_UNAVAILABLE);
            UIUtils.displayShortToast(R.string.quick_freeze_tile_profile_missing);
            openProfiles(context, host);
            return;
        }
        if (profile == null) {
            host.showTileState(Tile.STATE_UNAVAILABLE);
            UIUtils.displayShortToast(R.string.quick_freeze_tile_no_profile);
            openProfiles(context, host);
            return;
        }
        if (!QuickFreezeTileController.isProfileEligible(profile)) {
            host.showTileState(Tile.STATE_UNAVAILABLE);
            UIUtils.displayShortToast(R.string.quick_freeze_tile_requires_freeze);
            openProfiles(context, host);
            return;
        }
        host.showTileState(Tile.STATE_ACTIVE);
        Intent intent = ProfileApplierService.getIntent(context,
                ProfileQueueItem.fromProfile(profile, BaseProfile.STATE_ON), true);
        ContextCompat.startForegroundService(context, intent);
        UIUtils.displayShortToast(R.string.quick_freeze_tile_started, profile.name);
    }

    private static void openProfiles(@NonNull Context context, @NonNull TileActionHost host) {
        host.openScreen(new Intent(context, ProfilesActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    @Override
    public void showTileState(int state) {
        updateTile(state);
    }

    @Override
    public void openScreen(@NonNull Intent intent) {
        launch(intent);
    }

    @VisibleForTesting
    void launch(@NonNull Intent intent) {
        QuickOpenTileService.startAndCollapse(this, intent);
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
}
