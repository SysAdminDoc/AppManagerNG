// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Hands the watched apps to {@link PermissionWatchRelay} in the privileged process. Called whenever the privileged
 * services bind and after each check, which runs whenever a rule is saved.
 */
public final class PermissionWatchRelayClient {
    private static final String TAG = "PermissionWatchRelay";

    interface UidResolver {
        /** @return the app's uid, or {@code null} when it isn't installed for that user */
        @Nullable
        Integer uidOf(@NonNull String packageName, int userId);
    }

    static final class Targets {
        @NonNull
        final int[] uids;
        @NonNull
        final String[] packageNames;

        Targets(@NonNull int[] uids, @NonNull String[] packageNames) {
            this.uids = uids;
            this.packageNames = packageNames;
        }
    }

    private PermissionWatchRelayClient() {
    }

    @WorkerThread
    public static void push(@NonNull Context context) {
        // Without a rules database nothing was ever handed over, so there is nothing to clear either
        if (!LocalServices.alive() || !PermissionWatchDb.mightHaveRules(context)) {
            return;
        }
        try {
            List<WatchRule> rules = PermissionWatchDb.getInstance(context).ruleDao().getAll();
            Targets targets = targetsFor(rules, PermissionWatchRelayClient::installedUid);
            LocalServices.getAmService().setPermissionWatchTargets(targets.uids, targets.packageNames);
        } catch (Exception e) {
            // The scheduled check and the other check points still run without the live watch
            Log.w(TAG, "Could not hand the watched apps to the privileged process.", e);
        }
    }

    /** One entry per app and user with a rule that acts on a change; "Don't alert" rules act on nothing. */
    @VisibleForTesting
    @NonNull
    static Targets targetsFor(@NonNull List<WatchRule> rules, @NonNull UidResolver resolver) {
        Map<String, Integer> uidByApp = new LinkedHashMap<>();
        Map<String, String> packageByApp = new LinkedHashMap<>();
        for (WatchRule rule : rules) {
            if (rule.state == WatchRule.STATE_IGNORE) {
                continue;
            }
            String key = rule.packageName + ':' + rule.userId;
            if (packageByApp.containsKey(key)) {
                continue;
            }
            packageByApp.put(key, rule.packageName);
            Integer uid = resolver.uidOf(rule.packageName, rule.userId);
            if (uid != null) {
                uidByApp.put(key, uid);
            }
        }
        List<Integer> uids = new ArrayList<>();
        List<String> packageNames = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : uidByApp.entrySet()) {
            uids.add(entry.getValue());
            packageNames.add(packageByApp.get(entry.getKey()));
        }
        int[] uidArray = new int[uids.size()];
        for (int i = 0; i < uidArray.length; ++i) {
            uidArray[i] = uids.get(i);
        }
        return new Targets(uidArray, packageNames.toArray(new String[0]));
    }

    @Nullable
    private static Integer installedUid(@NonNull String packageName, int userId) {
        try {
            ApplicationInfo info = PackageManagerCompat.getApplicationInfo(packageName, 0, userId);
            return info != null ? info.uid : null;
        } catch (Exception e) {
            return null;
        }
    }
}
