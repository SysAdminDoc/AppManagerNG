// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** A pin's Unpin button, its timer before Android 8, and its delete intent when the user swipes it away. */
public class NotificationPinReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String tag = intent.getStringExtra(NotificationPins.EXTRA_TAG);
        if (tag == null) {
            return;
        }
        if (NotificationPins.ACTION_UNPIN.equals(intent.getAction())) {
            NotificationPins.unpin(context, tag);
        } else if (NotificationPins.ACTION_DISMISSED.equals(intent.getAction())) {
            NotificationPins.repostRemembered(context, tag, System.currentTimeMillis());
        }
    }
}
