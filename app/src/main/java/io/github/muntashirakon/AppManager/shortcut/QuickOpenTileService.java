// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.PendingIntentCompat;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.toast.ToastHistoryActivity;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.ProfilesActivity;
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.usage.AppUsageActivity;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/**
 * A Quick Settings tile that opens one screen of the app. Each screen takes no argument, so the tile has no state of
 * its own beyond whether the feature behind it is turned on.
 */
@RequiresApi(Build.VERSION_CODES.N)
public abstract class QuickOpenTileService extends TileService {
    private static final String TAG = QuickOpenTileService.class.getSimpleName();

    @VisibleForTesting
    enum Target {
        RUNNING_APPS(RunningAppsActivity.class, R.string.running_apps),
        APP_USAGE(AppUsageActivity.class, R.string.app_usage),
        PROFILES(ProfilesActivity.class, R.string.profiles),
        LOG_VIEWER(LogViewerActivity.class, R.string.log_viewer),
        TOAST_HISTORY(ToastHistoryActivity.class, R.string.toast_history_title);

        @NonNull
        final Class<? extends Activity> activityClass;
        @StringRes
        final int labelRes;

        Target(@NonNull Class<? extends Activity> activityClass, @StringRes int labelRes) {
            this.activityClass = activityClass;
            this.labelRes = labelRes;
        }

        /** The same gates that hide these screens from the main menu. */
        boolean isEnabled() {
            switch (this) {
                case APP_USAGE:
                    return FeatureController.isUsageAccessEnabled();
                case LOG_VIEWER:
                    return FeatureController.isLogViewerEnabled();
                default:
                    return true;
            }
        }

        @NonNull
        Intent getIntent(@NonNull Context context) {
            return new Intent(context, activityClass)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
    }

    public static final class RunningApps extends QuickOpenTileService {
        @NonNull
        @Override
        Target getTarget() {
            return Target.RUNNING_APPS;
        }
    }

    public static final class AppUsage extends QuickOpenTileService {
        @NonNull
        @Override
        Target getTarget() {
            return Target.APP_USAGE;
        }
    }

    public static final class Profiles extends QuickOpenTileService {
        @NonNull
        @Override
        Target getTarget() {
            return Target.PROFILES;
        }
    }

    public static final class LogViewer extends QuickOpenTileService {
        @NonNull
        @Override
        Target getTarget() {
            return Target.LOG_VIEWER;
        }
    }

    public static final class ToastHistory extends QuickOpenTileService {
        @NonNull
        @Override
        Target getTarget() {
            return Target.TOAST_HISTORY;
        }
    }

    /**
     * Starts an activity from a tile and closes the panel. {@link #startActivityAndCollapse(Intent)} throws for apps
     * targeting API 34 or later, where only the {@link PendingIntent} form works. Below API 34 that form doesn't exist,
     * so the deprecated call stays, behind the version check that lint can't see.
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    public static void startAndCollapse(@NonNull TileService service, @NonNull Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntentCompat.getActivity(service, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT, false);
            service.startActivityAndCollapse(pendingIntent);
            return;
        }
        service.startActivityAndCollapse(intent);
    }

    @NonNull
    abstract Target getTarget();

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        // From Android 14 the pending intent that open() starts waits for the PIN by itself, and unlockAndRun's
        // callback gets dropped once the panel closes for it.
        if (isLocked() && TileActionActivity.unlockAndRunWorks()) {
            unlockAndRun(this::open);
            return;
        }
        open();
    }

    private void open() {
        Target target = getTarget();
        if (!target.isEnabled()) {
            // A tile's state is only as fresh as the last time the panel opened.
            updateTile();
            UIUtils.displayShortToast(R.string.quick_open_tile_feature_off);
            return;
        }
        try {
            launch(target.getIntent(this));
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not open " + target + " from its tile.", e);
            UIUtils.displayLongToast(R.string.quick_open_tile_failed, getString(target.labelRes));
        }
    }

    @VisibleForTesting
    void launch(@NonNull Intent intent) {
        startAndCollapse(this, intent);
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        Target target = getTarget();
        boolean enabled = target.isEnabled();
        tile.setLabel(getString(target.labelRes));
        tile.setState(enabled ? Tile.STATE_INACTIVE : Tile.STATE_UNAVAILABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(enabled ? null : getString(R.string.quick_open_tile_feature_off));
        }
        tile.updateTile();
    }
}
