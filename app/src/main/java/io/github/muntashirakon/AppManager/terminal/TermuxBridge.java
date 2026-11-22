// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.MutableLiveData;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.Utils;

/**
 * Hands commands to Termux through its RUN_COMMAND service, the documented way for another app
 * to run something there. Termux needs two things first: this app holding its RUN_COMMAND
 * permission, and {@code allow-external-apps = true} in its own termux.properties.
 */
public final class TermuxBridge {
    public static final String PACKAGE_NAME = "com.termux";
    public static final String PERMISSION_RUN_COMMAND = ManifestCompat.permission.TERMUX_RUN_COMMAND;
    private static final String TAG = TermuxBridge.class.getSimpleName();

    static final String RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    static final String EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT";
    // Termux's result bundle: err is Activity.RESULT_OK when the command ran, errmsg says why it didn't
    static final String EXTRA_RESULT_BUNDLE = "result";
    static final String RESULT_ERR = "err";
    static final String RESULT_ERRMSG = "errmsg";
    private static final MutableLiveData<String> REFUSALS = new MutableLiveData<>();
    static final String ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
    static final String EXTRA_PATH = "com.termux.RUN_COMMAND_PATH";
    static final String EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
    static final String EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
    static final String EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
    static final String EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION";
    /**
     * Make the new session Termux's current one without opening its screen. Termux can open its screen from
     * the background only with Display over other apps allowed, so {@link #send} opens it instead.
     */
    static final String SESSION_ACTION_SWITCH_DONT_OPEN = "2";
    /** How long Termux gets to turn a command down before its screen is brought forward. */
    static final long OPEN_DELAY_MS = 1000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    @Nullable
    private static Runnable sPendingOpen;
    static final String HOME = "/data/data/com.termux/files/home";
    // Termux's login exports SHELL; sh is there in every Termux install should it be missing
    private static final String KEEP_SESSION = "\nexec \"${SHELL:-/data/data/com.termux/files/usr/bin/sh}\" -l";
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
     * <p>
     * Termux closes a session as soon as its command ends (right away when, like here, the sender asked
     * for the result), so the output of a short command was gone before anyone could read it. The
     * session carries on in the user's shell instead. The hand-off sits on its own line so a trailing
     * {@code #} comment can't swallow it, and it skips {@code login}, which would print the welcome text
     * over the output.
     */
    @NonNull
    public static Intent buildRunCommandIntent(@NonNull String command) {
        return new Intent(ACTION_RUN_COMMAND)
                .setClassName(PACKAGE_NAME, RUN_COMMAND_SERVICE)
                .putExtra(EXTRA_PATH, Utils.TERMUX_LOGIN_PATH)
                .putExtra(EXTRA_ARGUMENTS, new String[]{"-c", command + KEEP_SESSION})
                .putExtra(EXTRA_WORKDIR, HOME)
                .putExtra(EXTRA_BACKGROUND, false)
                .putExtra(EXTRA_SESSION_ACTION, SESSION_ACTION_SWITCH_DONT_OPEN);
    }

    /**
     * Hands the command to Termux and brings Termux to the front. Termux opens its own screen from the
     * background only with Display over other apps allowed, so without it the command ran in a session
     * nobody saw. This app is in front when the user taps, so it may open Termux itself. It waits a moment
     * first: Termux turns a command down within that time when allow-external-apps is off, and then the
     * setup steps show here instead of the user landing in Termux with nothing run.
     *
     * @throws RuntimeException when Android refuses to start Termux's service
     */
    @MainThread
    public static void send(@NonNull Context context, @NonNull Intent command) {
        ContextCompat.startForegroundService(context, withResult(context, command));
        Context app = context.getApplicationContext();
        cancelPendingOpen();
        sPendingOpen = () -> {
            sPendingOpen = null;
            try {
                app.startActivity(getLaunchIntent(app));
            } catch (RuntimeException e) {
                // ActivityNotFoundException if Termux went away, SecurityException if Android blocks the start
                Log.w(TAG, "Could not bring Termux forward", e);
            }
        };
        MAIN.postDelayed(sPendingOpen, OPEN_DELAY_MS);
    }

    @MainThread
    private static void cancelPendingOpen() {
        if (sPendingOpen != null) {
            MAIN.removeCallbacks(sPendingOpen);
            sPendingOpen = null;
        }
    }

    /**
     * Asks Termux to report back. Without this, a command Termux turns down (allow-external-apps off, the
     * usual first-run case) only reaches Termux's own log and a notice Android may hide, so nothing happens
     * on screen. Termux adds its result to the intent as extras, so on Android 12 and later the
     * PendingIntent has to be mutable, and it names the receiver explicitly as Android 14 requires.
     */
    @NonNull
    public static Intent withResult(@NonNull Context context, @NonNull Intent command) {
        Intent result = new Intent(context, TermuxResultReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return command.putExtra(EXTRA_PENDING_INTENT, PendingIntent.getBroadcast(context, 0, result, flags));
    }

    /**
     * Termux's reason for not running a command, or {@code null} when it ran (or the result isn't Termux's).
     */
    @Nullable
    static String getRefusal(@Nullable Bundle result) {
        if (result == null) {
            return null;
        }
        int err = result.getInt(RESULT_ERR, Activity.RESULT_OK);
        if (err == Activity.RESULT_OK) {
            return null;
        }
        String errmsg = result.getString(RESULT_ERRMSG);
        return errmsg != null ? errmsg : "";
    }

    static boolean isExternalAppsOff(@NonNull String refusal) {
        return refusal.contains("allow-external-apps");
    }

    /**
     * Refusals for the screen that sent the command. A screen that shows one sets the value back to null.
     */
    @NonNull
    public static MutableLiveData<String> getRefusals() {
        return REFUSALS;
    }

    /**
     * Hands a refusal to the screen that's open, or says it in a toast when none is listening.
     */
    @MainThread
    static void reportRefusal(@NonNull Context context, @NonNull String refusal) {
        // Nothing ran, so there's nothing to see in Termux
        cancelPendingOpen();
        if (REFUSALS.hasActiveObservers()) {
            REFUSALS.setValue(refusal);
            return;
        }
        Toast.makeText(context, isExternalAppsOff(refusal) ? R.string.termux_external_apps_off
                : R.string.termux_run_command_failed, Toast.LENGTH_LONG).show();
    }

    /**
     * Where to get Termux, or {@code null} in builds that keep every online link out.
     */
    @Nullable
    public static String getInstallUrl(boolean optionalNetworkFeatures) {
        return optionalNetworkFeatures ? INSTALL_URL : null;
    }
}
