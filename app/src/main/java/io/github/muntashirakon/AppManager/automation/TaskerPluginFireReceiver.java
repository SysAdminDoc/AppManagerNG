// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.logs.Log;

public class TaskerPluginFireReceiver extends BroadcastReceiver {
    private static final String TAG = TaskerPluginFireReceiver.class.getSimpleName();

    @Override
    public void onReceive(@NonNull Context context, @Nullable Intent intent) {
        Intent automationIntent = TaskerPluginBroker.getSignedAutomationIntent(context, intent);
        if (automationIntent == null) {
            Log.w(TAG, "Rejected unsigned or invalid Tasker plug-in fire request.");
            if (isOrderedBroadcast()) {
                setResultCode(Activity.RESULT_CANCELED);
            }
            return;
        }
        context.sendBroadcast(automationIntent);
        if (isOrderedBroadcast()) {
            setResultCode(Activity.RESULT_OK);
        }
    }
}
