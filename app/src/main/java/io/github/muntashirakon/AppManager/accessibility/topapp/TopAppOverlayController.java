// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.BaseAccessibilityService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/**
 * What a tile or button does when pressed: stop a running bubble, start one, or send the user to the one system screen
 * standing in the way.
 */
public final class TopAppOverlayController {
    private static final String TAG = TopAppOverlayController.class.getSimpleName();

    public static final class Outcome {
        /** Whether the bubble is on its way up, for a caller that shows a state before the service reports it. */
        public final boolean running;
        /** A screen the caller has to open, or {@code null}. Opening it differs between a tile and an activity. */
        @Nullable
        public final Intent settingsIntent;

        Outcome(boolean running, @Nullable Intent settingsIntent) {
            this.running = running;
            this.settingsIntent = settingsIntent;
        }
    }

    private TopAppOverlayController() {
    }

    @MainThread
    @NonNull
    public static Outcome toggle(@NonNull Context context) {
        if (TopAppOverlayService.isRunning()) {
            TopAppOverlayService.stop(context);
            UIUtils.displayShortToast(R.string.top_app_overlay_stopped);
            return new Outcome(false, null);
        }
        TopAppOverlayPolicy.StartStep step = TopAppOverlayPolicy.nextStep(canDrawOverlays(context),
                BaseAccessibilityService.isAccessibilityEnabled(context), SelfPermissions.checkUsageStatsPermission(),
                FeatureController.isUsageAccessEnabled());
        if (step != TopAppOverlayPolicy.StartStep.START) {
            UIUtils.displayLongToast(whyRes(step));
            return new Outcome(false, settingsIntent(context, step));
        }
        try {
            TopAppOverlayService.start(context);
            UIUtils.displayShortToast(R.string.top_app_overlay_started);
            return new Outcome(true, null);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not start the top app bubble.", e);
            UIUtils.displayLongToast(R.string.top_app_overlay_failed);
            return new Outcome(false, null);
        }
    }

    /** For a screen that can start activities itself. A tile has to go through its own collapsing route instead. */
    @MainThread
    public static void toggleAndOpenSettings(@NonNull Context context) {
        Intent settings = toggle(context).settingsIntent;
        if (settings == null) {
            return;
        }
        try {
            context.startActivity(settings);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not open the settings screen.", e);
            UIUtils.displayLongToast(R.string.top_app_overlay_failed);
        }
    }

    static boolean canDrawOverlays(@NonNull Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    /** Respects the user's own switch for usage access, not only the system grant. */
    static boolean hasUsageAccess() {
        return FeatureController.isUsageAccessEnabled() && SelfPermissions.checkUsageStatsPermission();
    }

    @StringRes
    private static int whyRes(@NonNull TopAppOverlayPolicy.StartStep step) {
        switch (step) {
            case OVERLAY_PERMISSION:
                return R.string.top_app_overlay_needs_overlay;
            case USAGE_ACCESS:
                return R.string.top_app_overlay_needs_usage_access;
            default:
                return R.string.top_app_overlay_needs_accessibility;
        }
    }

    @NonNull
    private static Intent settingsIntent(@NonNull Context context, @NonNull TopAppOverlayPolicy.StartStep step) {
        Intent intent;
        switch (step) {
            case OVERLAY_PERMISSION:
                // Below Android 6 the permission is granted at install, so this step never comes up there.
                intent = new Intent(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                        : Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.getPackageName()));
                break;
            case USAGE_ACCESS:
                intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                break;
            default:
                intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                break;
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
