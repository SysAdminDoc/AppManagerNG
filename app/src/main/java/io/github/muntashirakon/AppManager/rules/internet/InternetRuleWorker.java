// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.internet;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Ops;

/** Puts saved internet rules back: every one when the input names no package, otherwise that app's. */
public class InternetRuleWorker extends Worker {
    private static final String TAG = "InternetRuleWorker";

    public static final String INPUT_PACKAGE = "package";
    public static final String INPUT_USER = "user";

    public InternetRuleWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String packageName = getInputData().getString(INPUT_PACKAGE);
        int userId = getInputData().getInt(INPUT_USER, 0);
        // Don't bring up root or ADB after every restart for nothing
        if (packageName == null && !InternetAccess.hasAnyRule()) {
            return Result.success();
        }
        int status = Ops.init(getApplicationContext(), false);
        if (status != Ops.STATUS_SUCCESS) {
            // Binding the privileged mode later puts every rule back
            Log.w(TAG, "Privileged mode isn't ready (%d), so internet rules wait for it.", status);
        }
        if (packageName == null) {
            InternetAccess.applyAll();
        } else {
            InternetAccess.Status result = InternetAccess.apply(packageName, userId);
            if (!result.inForce) {
                Log.w(TAG, "Internet rule for %s isn't in force: %s", packageName, result);
            }
        }
        return Result.success();
    }
}
