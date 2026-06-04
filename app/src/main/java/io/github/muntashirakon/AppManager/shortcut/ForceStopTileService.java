// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.PendingIntentCompat;
import androidx.core.content.ContextCompat;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.main.SplashActivity;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class ForceStopTileService extends TileService {
    private static final String TAG = ForceStopTileService.class.getSimpleName();

    public static void requestTileStateUpdate(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            TileService.requestListeningState(context, getTileComponentName(context));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not request force-stop tile state update.", e);
        }
    }

    public static void requestAddTile(@NonNull Context context) {
        if (!supportsRequestAddTile(Build.VERSION.SDK_INT)) {
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
                    context.getString(R.string.force_stop_tile_label),
                    Icon.createWithResource(context, R.drawable.ic_stop),
                    ContextCompat.getMainExecutor(context),
                    result -> requestTileStateUpdate(context));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not request force-stop tile installation.", e);
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
        return new ComponentName(context, ForceStopTileService.class);
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
            unlockAndRun(this::runSelectedTarget);
            return;
        }
        runSelectedTarget();
    }

    private void runSelectedTarget() {
        ForceStopTileController.Target target = ForceStopTileController.getSelectedTarget();
        if (target == null) {
            updateTile(Tile.STATE_UNAVAILABLE);
            UIUtils.displayShortToast(R.string.force_stop_tile_no_app);
            openLauncherAndCollapse();
            return;
        }
        if (!SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
            updateTile(Tile.STATE_UNAVAILABLE);
            UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode);
            return;
        }
        updateTile(Tile.STATE_ACTIVE);
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                PackageManagerCompat.forceStopPackage(target.packageName, target.userId);
                String label = PackageUtils.getPackageLabel(getPackageManager(), target.packageName, target.userId)
                        .toString();
                ThreadUtils.postOnMainThread(() -> {
                    updateTile(Tile.STATE_INACTIVE);
                    UIUtils.displayShortToast(R.string.force_stop_tile_done, label);
                });
            } catch (Throwable th) {
                Log.e(TAG, th);
                ThreadUtils.postOnMainThread(() -> {
                    updateTile(Tile.STATE_INACTIVE);
                    UIUtils.displayShortToast(R.string.failed);
                });
            }
        });
    }

    private void updateTile(int configuredState) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setLabel(getString(R.string.force_stop_tile_label));
        ForceStopTileController.Target target = ForceStopTileController.getSelectedTarget();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(target != null ? target.packageName : null);
        }
        if (target == null || !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            tile.setState(configuredState);
        }
        tile.updateTile();
    }

    @SuppressWarnings("deprecation")
    private void openLauncherAndCollapse() {
        Intent intent = new Intent(this, SplashActivity.class)
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
