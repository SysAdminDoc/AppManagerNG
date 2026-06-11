// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * Manifest-declared receiver for {@link Intent#ACTION_PACKAGE_REPLACED}. Fires
 * the {@link PermissionChangeMonitor} on a background thread.
 *
 * <p>Gated by {@link Prefs.Privacy#isPermissionChangeMonitorEnabled()} so the
 * default is OFF — users opt in from Settings -> Privacy. While off the
 * receiver still runs (Android requires the manifest declaration to be live to
 * receive the broadcast at all) but returns immediately after the preference
 * check, leaving no state behind.
 */
public class PermissionChangeReceiver extends BroadcastReceiver {
    public static final String TAG = "PermissionChangeReceiver";

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        if (!Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())) return;
        String packageName = extractPackageName(intent);
        if (packageName == null || packageName.isEmpty()) return;
        boolean permMonitorOn = Prefs.Privacy.isPermissionChangeMonitorEnabled();
        boolean certMonitorOn = Prefs.Privacy.isSigningCertChangeMonitorEnabled();
        boolean componentMonitorOn = Prefs.Privacy.isAppChangeAuditorEnabled();
        if (!permMonitorOn && !certMonitorOn && !componentMonitorOn) return;
        final Context appContext = context.getApplicationContext();
        final String pkg = packageName;
        // Use goAsync() so the broadcast result stays alive across the
        // off-main-thread snapshot + notification work.
        final PendingResult pending = goAsync();
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                if (permMonitorOn) {
                    try {
                        PermissionChangeMonitor.onPackageReplaced(appContext, pkg);
                    } catch (Throwable t) {
                        Log.w(TAG, "Permission change monitor failed for " + pkg, t);
                    }
                }
                if (certMonitorOn) {
                    try {
                        SigningCertChangeMonitor.onPackageReplaced(appContext, pkg);
                    } catch (Throwable t) {
                        Log.w(TAG, "Signing-cert change monitor failed for " + pkg, t);
                    }
                }
                if (componentMonitorOn) {
                    try {
                        ComponentChangeMonitor.onPackageReplaced(appContext, pkg);
                    } catch (Throwable t) {
                        Log.w(TAG, "Component change monitor failed for " + pkg, t);
                    }
                }
            } finally {
                pending.finish();
            }
        });
    }

    @Nullable
    private static String extractPackageName(@NonNull Intent intent) {
        Uri data = intent.getData();
        if (data == null) return null;
        String ssp = data.getSchemeSpecificPart();
        if (ssp != null && !ssp.isEmpty()) return ssp;
        return data.getEncodedSchemeSpecificPart();
    }
}
