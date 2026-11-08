// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.quicksettings.TileService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.topapp.TopAppOverlayTileService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.QuickFreezeTileService;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/**
 * Runs a Quick Settings tile's action after a locked phone is unlocked.
 * <p>
 * From Android 14 the system drops the callback given to {@link TileService#unlockAndRun} once the tile stops
 * listening, and it stops as soon as the panel closes for the PIN, so a tap on the lock screen did nothing (#25). A tile
 * tapped on a locked phone starts this screen through {@link TileService#startActivityAndCollapse(PendingIntent)}
 * instead. The system asks for the PIN before starting it, and it runs the action and closes without showing anything.
 * Only tiles start it, and tiles need Android 7.
 */
@RequiresApi(Build.VERSION_CODES.N)
public class TileActionActivity extends Activity {
    private static final String TAG = TileActionActivity.class.getSimpleName();
    private static final String EXTRA_ACTION = "action";

    public enum Action {
        TOP_APP_BUBBLE,
        QUICK_FREEZE,
        FORCE_STOP,
    }

    /**
     * Whether {@link TileService#unlockAndRun} still runs its callback after the PIN. A pending intent started through
     * {@link QuickOpenTileService#startAndCollapse} waits for the PIN on every version that has it, so that route takes
     * over from Android 14.
     */
    public static boolean unlockAndRunWorks() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    /** Runs a tile's action now if the phone is unlocked, or once the user unlocks it. */
    public static <T extends TileService & TileActionHost> void run(@NonNull T tile, @NonNull Action action,
                                                                    @NonNull Runnable runner) {
        if (!tile.isLocked()) {
            runner.run();
            return;
        }
        if (unlockAndRunWorks()) {
            tile.unlockAndRun(runner);
            return;
        }
        try {
            tile.openScreen(getIntent(tile, action));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not ask for an unlock for " + action, e);
            UIUtils.displayShortToast(R.string.failed);
        }
    }

    @NonNull
    @VisibleForTesting
    static Intent getIntent(@NonNull Context context, @NonNull Action action) {
        return new Intent(context, TileActionActivity.class)
                // Tiles share a pending intent request code, so the action keeps theirs apart.
                .setAction(action.name())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Action action = parse(getIntent());
            if (action == null) {
                Log.w(TAG, "Ignoring unknown tile action " + getIntent().getAction());
                return;
            }
            Context app = getApplicationContext();
            switch (action) {
                case TOP_APP_BUBBLE:
                    TopAppOverlayTileService.toggle(this,
                            host(() -> TopAppOverlayTileService.requestTileStateUpdate(app)));
                    break;
                case QUICK_FREEZE:
                    QuickFreezeTileService.runSelectedFreezeProfile(this,
                            host(() -> QuickFreezeTileService.requestTileStateUpdate(app)));
                    break;
                case FORCE_STOP:
                    ForceStopTileService.runSelectedTarget(this,
                            host(() -> ForceStopTileService.requestTileStateUpdate(app)));
                    break;
            }
        } finally {
            // Theme.NoDisplay has to finish before onResume.
            finish();
        }
    }

    @NonNull
    private TileActionHost host(@NonNull Runnable refreshTile) {
        return new TileActionHost() {
            @Override
            public void showTileState(int state) {
                // The panel is closed, so the tile only needs to read its state again the next time it opens.
                refreshTile.run();
            }

            @Override
            public void openScreen(@NonNull Intent intent) {
                startActivity(intent);
            }
        };
    }

    @Nullable
    private static Action parse(@Nullable Intent intent) {
        String name = intent != null ? intent.getAction() : null;
        if (name == null) {
            return null;
        }
        try {
            return Action.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
