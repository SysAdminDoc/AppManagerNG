// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

/**
 * Package broadcasts from the manifest. Android 8 and later never deliver them here, so {@link PackageEventSync}
 * also looks on its own. Below Android 8 this starts a sync right away.
 */
public class PackageChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action) || Intent.ACTION_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            PackageEventSync.requestSync(context, goAsync());
        }
    }
}
