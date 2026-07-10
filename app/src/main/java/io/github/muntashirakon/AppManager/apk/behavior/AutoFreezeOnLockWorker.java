// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.struct.FreezeRule;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

public class AutoFreezeOnLockWorker extends Worker {
    private static final String TAG = AutoFreezeOnLockWorker.class.getSimpleName();

    public AutoFreezeOnLockWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean interactive = powerManager == null || powerManager.isInteractive();
        if (!shouldFreeze(Prefs.Blocking.isAutoFreezeOnLockEnabled(), interactive)) {
            return Result.success();
        }
        int status = Ops.init(context, false);
        if (status != Ops.STATUS_SUCCESS) {
            Log.w(TAG, "Auto-freeze skipped because privileged mode initialization returned %d.", status);
            return Result.failure();
        }
        try {
            List<FreezeRule> rules = RulesStorageManager.getAllFreezeRules();
            int frozen = 0;
            int failed = 0;
            for (FreezeRule rule : rules) {
                if (isStopped() || powerManager == null || powerManager.isInteractive()) {
                    Log.i(TAG, "Auto-freeze cancelled after %d/%d packages.", frozen, rules.size());
                    return Result.success();
                }
                try {
                    FreezeUtils.freeze(rule.packageName, rule.getFreezeType());
                    ++frozen;
                } catch (Exception e) {
                    ++failed;
                    Log.w(TAG, "Failed to auto-freeze %s.", e, rule.packageName);
                }
            }
            Log.i(TAG, "Auto-freeze on lock finished: %d frozen, %d failed, %d total.",
                    frozen, failed, rules.size());
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Auto-freeze on lock failed.", e);
            return Result.failure();
        }
    }

    @VisibleForTesting
    static boolean shouldFreeze(boolean enabled, boolean interactive) {
        return enabled && !interactive;
    }

    @VisibleForTesting
    static int sanitizeDelaySeconds(int delaySeconds) {
        return Math.max(0, Math.min(Prefs.Blocking.MAX_AUTO_FREEZE_DELAY_SECONDS, delaySeconds));
    }
}
