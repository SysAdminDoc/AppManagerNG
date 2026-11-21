// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.utils.Utils;

/**
 * Hands commands to Termux through its RUN_COMMAND service, the documented way for another app
 * to run something there. Termux needs two things first: this app holding its RUN_COMMAND
 * permission, and {@code allow-external-apps = true} in its own termux.properties.
 */
public final class TermuxBridge {
    public static final String PACKAGE_NAME = "com.termux";
    public static final String PERMISSION_RUN_COMMAND = ManifestCompat.permission.TERMUX_RUN_COMMAND;

    static final String RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    static final String ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
    static final String EXTRA_PATH = "com.termux.RUN_COMMAND_PATH";
    static final String EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
    static final String EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
    static final String EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
    static final String EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION";
    /** Open the new session and bring Termux to the front, so the output can be read. */
    static final String SESSION_ACTION_SWITCH_AND_OPEN = "0";
    static final String HOME = "/data/data/com.termux/files/home";
    private static final String MAIN_ACTIVITY = "com.termux.app.TermuxActivity";
    private static final String INSTALL_URL = "https://f-droid.org/packages/com.termux/";

    @IntDef({STATE_NOT_INSTALLED, STATE_NO_PERMISSION, STATE_READY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }

    public static final int STATE_NOT_INSTALLED = 0;
    public static final int STATE_NO_PERMISSION = 1;
    public static final int STATE_READY = 2;

    private TermuxBridge() {
    }

    @State
    public static int getState(@NonNull Context context) {
        if (!isInstalled(context)) {
            return STATE_NOT_INSTALLED;
        }
        return hasRunCommandPermission(context) ? STATE_READY : STATE_NO_PERMISSION;
    }

    public static boolean isInstalled(@NonNull Context context) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(PACKAGE_NAME, 0);
            return info.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean hasRunCommandPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, PERMISSION_RUN_COMMAND)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Opens Termux's own terminal. Needs no permission.
     */
    @NonNull
    public static Intent getLaunchIntent(@NonNull Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(PACKAGE_NAME);
        if (intent == null) {
            intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(PACKAGE_NAME, MAIN_ACTIVITY);
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /**
     * Runs the command in a new Termux session through the user's login shell, so their shell
     * choice and profile apply. The command goes over as one {@code -c} argument: quotes, pipes
     * and {@code &&} reach the shell untouched.
     */
    @NonNull
    public static Intent buildRunCommandIntent(@NonNull String command) {
        return new Intent(ACTION_RUN_COMMAND)
                .setClassName(PACKAGE_NAME, RUN_COMMAND_SERVICE)
                .putExtra(EXTRA_PATH, Utils.TERMUX_LOGIN_PATH)
                .putExtra(EXTRA_ARGUMENTS, new String[]{"-c", command})
                .putExtra(EXTRA_WORKDIR, HOME)
                .putExtra(EXTRA_BACKGROUND, false)
                .putExtra(EXTRA_SESSION_ACTION, SESSION_ACTION_SWITCH_AND_OPEN);
    }

    /**
     * Where to get Termux, or {@code null} in builds that keep every online link out.
     */
    @Nullable
    public static String getInstallUrl(boolean optionalNetworkFeatures) {
        return optionalNetworkFeatures ? INSTALL_URL : null;
    }
}
