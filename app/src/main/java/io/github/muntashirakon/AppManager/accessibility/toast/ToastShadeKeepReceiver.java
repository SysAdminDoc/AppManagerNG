// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.muntashirakon.AppManager.settings.Prefs;

/**
 * Gets the toast notification's delete intent. Android 14 and later let the user swipe away an ongoing notification,
 * and with Keep in the shade on, it goes straight back, once for each time it's swiped.
 */
public class ToastShadeKeepReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Settings may have changed since the notification went up: only put it back if it's still wanted.
        if (ToastShadeNotification.isWanted() && Prefs.Privacy.isToastHistoryShadeKeepEnabled()) {
            ToastHistory.getInstance(context).refreshShade();
        }
    }
}
