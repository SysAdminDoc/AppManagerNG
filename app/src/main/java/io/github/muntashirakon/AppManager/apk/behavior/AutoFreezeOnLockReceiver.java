// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.settings.Prefs;

public class AutoFreezeOnLockReceiver extends BroadcastReceiver {
    static final String UNIQUE_WORK_NAME = "auto-freeze-on-screen-lock";
    static final String WORK_TAG = "auto-freeze";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
            cancel(context);
            return;
        }
        if (Intent.ACTION_SCREEN_OFF.equals(action) && Prefs.Blocking.isAutoFreezeOnLockEnabled()) {
            schedule(context, Prefs.Blocking.getAutoFreezeDelaySeconds());
        }
    }

    static void schedule(@NonNull Context context, int delaySeconds) {
        int boundedDelay = AutoFreezeOnLockWorker.sanitizeDelaySeconds(delaySeconds);
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(AutoFreezeOnLockWorker.class)
                .addTag(WORK_TAG);
        if (boundedDelay > 0) {
            builder.setInitialDelay(boundedDelay, TimeUnit.SECONDS);
        }
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, builder.build());
    }

    public static void cancel(@NonNull Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(UNIQUE_WORK_NAME);
    }
}
