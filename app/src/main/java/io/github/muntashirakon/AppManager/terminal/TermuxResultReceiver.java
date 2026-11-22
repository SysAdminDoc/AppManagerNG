// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Where Termux reports how a command handed to it went (see {@link TermuxBridge#withResult}). Not exported:
 * Termux reaches it only through the PendingIntent this app gave it.
 */
public class TermuxResultReceiver extends BroadcastReceiver {
    private static final String TAG = TermuxResultReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String refusal = TermuxBridge.getRefusal(intent.getBundleExtra(TermuxBridge.EXTRA_RESULT_BUNDLE));
        if (refusal == null) {
            return;
        }
        Log.w(TAG, "Termux didn't run the command: %s", refusal);
        TermuxBridge.reportRefusal(context, refusal);
    }
}
