// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Starts or stops log recording when the recording widget is tapped. It isn't exported, so only the
 * widget's own PendingIntent (which runs as this app) reaches it. The widget provider has to be
 * exported for the launcher's widget broadcasts, and while it handled this too, any installed app
 * could stop a recording by sending it the action.
 */
// Copyright 2012 Nolan Lawson
public class RecordingWidgetActionReceiver extends BroadcastReceiver {
    public static final String TAG = RecordingWidgetActionReceiver.class.getSimpleName();

    public static final String ACTION_RECORD_OR_STOP = BuildConfig.APPLICATION_ID + ".action.RECORD_OR_STOP";

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        Log.d(TAG, "onReceive called with intent %s", intent);
        if (!ACTION_RECORD_OR_STOP.equals(intent.getAction())) {
            return;
        }
        // Start or stop recording as necessary
        synchronized (RecordingWidgetActionReceiver.class) {
            boolean alreadyRunning = ServiceHelper.checkIfServiceIsRunning(context, LogcatRecordingService.class);
            if (alreadyRunning) {
                // stop the current recording process
                ServiceHelper.stopBackgroundServiceIfRunning(context);
            } else {
                // start a new recording process
                Intent targetIntent = new Intent();
                targetIntent.setClass(context, RecordLogDialogActivity.class);
                targetIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

                context.startActivity(targetIntent);
            }
        }
    }
}
