// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.app.PendingIntentCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.PackageEventSync;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

/**
 * The "Always deny" and "Always allow" buttons on a permission change alert. Saves a rule for each permission the
 * alert named, dismisses it, and checks the app right away so the rule takes effect.
 */
public class WatchRuleActionReceiver extends BroadcastReceiver {
    private static final String TAG = "WatchRuleActionReceiver";

    public static final String ACTION_SAVE_RULES = "io.github.muntashirakon.AppManager.action.SAVE_WATCH_RULES";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_PERMISSIONS = "permissions";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_NOTIFICATION_TAG = "notification_tag";

    private static final AtomicInteger sRequestCode = new AtomicInteger();

    @NonNull
    public static PendingIntent buildPendingIntent(@NonNull Context context, @NonNull String packageName, int userId,
                                                   @NonNull Collection<String> permissions,
                                                   @WatchRule.State int state, @NonNull String notificationTag) {
        Intent intent = new Intent(context, WatchRuleActionReceiver.class)
                .setAction(ACTION_SAVE_RULES)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_USER, userId)
                .putExtra(EXTRA_PERMISSIONS, permissions.toArray(new String[0]))
                .putExtra(EXTRA_STATE, state)
                .putExtra(EXTRA_NOTIFICATION_TAG, notificationTag);
        // Intents that differ only in extras share a PendingIntent, so each button needs its own request code.
        return PendingIntentCompat.getBroadcast(context, sRequestCode.incrementAndGet(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT, false);
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        if (!ACTION_SAVE_RULES.equals(intent.getAction())) {
            return;
        }
        Context appContext = context.getApplicationContext();
        PendingResult pending = goAsync();
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                boolean saved = handle(appContext, intent);
                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(saved
                        ? R.string.permission_watcher_rule_saved : R.string.permission_watcher_rule_save_failed));
            } finally {
                if (pending != null) {
                    pending.finish();
                }
            }
        });
    }

    /** @return whether the rules were saved */
    @VisibleForTesting
    @WorkerThread
    static boolean handle(@NonNull Context context, @NonNull Intent intent) {
        List<WatchRule> rules = rulesFrom(intent);
        if (rules.isEmpty()) {
            return false;
        }
        try {
            PermissionWatchDb.getInstance(context).ruleDao().upsertAll(rules);
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not save the watcher rules.", e);
            return false;
        }
        String tag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG);
        if (tag != null) {
            NotificationUtils.cancelHighPriorityNotification(context, tag);
        }
        WatchRule first = rules.get(0);
        PermissionWatchScheduler.checkSoon(context, first.packageName, first.userId);
        PackageEventSync.scheduleOrCancel(context);
        return true;
    }

    @VisibleForTesting
    @NonNull
    static List<WatchRule> rulesFrom(@NonNull Intent intent) {
        List<WatchRule> rules = new ArrayList<>();
        String packageName = intent.getStringExtra(EXTRA_PACKAGE);
        String[] permissions = intent.getStringArrayExtra(EXTRA_PERMISSIONS);
        int state = intent.getIntExtra(EXTRA_STATE, -1);
        if (packageName == null || packageName.isEmpty() || permissions == null || !isState(state)) {
            return rules;
        }
        int userId = intent.getIntExtra(EXTRA_USER, 0);
        for (String permission : permissions) {
            if (permission != null && !permission.isEmpty()) {
                rules.add(WatchRule.forPermission(packageName, userId, permission, state));
            }
        }
        return rules;
    }

    private static boolean isState(int state) {
        return state == WatchRule.STATE_ALLOW || state == WatchRule.STATE_DENY || state == WatchRule.STATE_IGNORE;
    }
}
