// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/**
 * What the terminal and App Info show when Termux won't run a command for them.
 */
public final class TermuxDialogs {
    private static final String TAG = TermuxDialogs.class.getSimpleName();

    private TermuxDialogs() {
    }

    /**
     * Termux runs nothing from other apps until its permission is granted here and allow-external-apps is set
     * on its side, and neither can be done from this app.
     */
    public static void showSetupSteps(@NonNull Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.termux_permission_title)
                .setMessage(R.string.termux_permission_steps)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.app_info, (dialog, which) -> {
                    try {
                        context.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.getPackageName(), null)));
                    } catch (ActivityNotFoundException e) {
                        Log.w(TAG, "Could not open App info", e);
                    }
                })
                .show();
    }

    /**
     * Shows Termux's refusals while {@code owner} is started: the setup steps when allow-external-apps is off,
     * a toast for anything else.
     */
    public static void observeRefusals(@NonNull LifecycleOwner owner, @NonNull Context context) {
        TermuxBridge.getRefusals().observe(owner, refusal -> {
            if (refusal == null) {
                return;
            }
            TermuxBridge.getRefusals().setValue(null);
            if (TermuxBridge.isExternalAppsOff(refusal)) {
                showSetupSteps(context);
            } else {
                UIUtils.displayLongToast(R.string.termux_run_command_failed);
            }
        });
    }
}
