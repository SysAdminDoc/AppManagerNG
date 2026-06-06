// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.main.ApplicationItem;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public final class AppActionShortcutPublisher {
    private static final String TAG = AppActionShortcutPublisher.class.getSimpleName();

    private static final int STATIC_LAUNCHER_SHORTCUT_COUNT = 3;
    private static final int MAX_DYNAMIC_APP_ACTIONS = 6;

    private AppActionShortcutPublisher() {
    }

    public static void publishDynamicShortcuts(@NonNull Context context,
                                               @NonNull List<ApplicationItem> applicationItems) {
        int maxShortcutCount = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context);
        int maxDynamicActions = Math.min(MAX_DYNAMIC_APP_ACTIONS,
                Math.max(0, maxShortcutCount - STATIC_LAUNCHER_SHORTCUT_COUNT));
        List<TargetAction> targetActions = selectTargetActions(applicationItems, maxDynamicActions,
                SelfPermissions.canFreezeUnfreezePackages(),
                SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES),
                SelfPermissions.canClearAppCache());
        List<ShortcutInfoCompat> shortcuts = new ArrayList<>(targetActions.size());
        for (int i = 0; i < targetActions.size(); ++i) {
            shortcuts.add(toShortcut(context, targetActions.get(i), i));
        }
        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not update app action shortcuts.", e);
        }
    }

    @NonNull
    @VisibleForTesting
    static List<TargetAction> selectTargetActions(@NonNull List<ApplicationItem> applicationItems,
                                                  int maxDynamicActions,
                                                  boolean canFreeze,
                                                  boolean canForceStop,
                                                  boolean canClearCache) {
        if (maxDynamicActions <= 0 || (!canFreeze && !canForceStop && !canClearCache)) {
            return Collections.emptyList();
        }
        List<ApplicationItem> candidates = new ArrayList<>();
        for (ApplicationItem item : applicationItems) {
            if (!isEligible(item)) {
                continue;
            }
            candidates.add(item);
        }
        candidates.sort(APP_SHORTCUT_COMPARATOR);
        List<TargetAction> actions = new ArrayList<>(Math.min(maxDynamicActions, candidates.size() * 3));
        for (ApplicationItem item : candidates) {
            int userId = item.getUserId();
            if (userId < 0) {
                continue;
            }
            if (canFreeze && !item.isDisabled) {
                actions.add(new TargetAction(item.packageName, userId, getLabel(item),
                        AppActionShortcutInfo.ACTION_FREEZE,
                        R.string.shortcut_freeze_app,
                        R.drawable.ic_snowflake));
            }
            if (canForceStop && !item.isStopped) {
                actions.add(new TargetAction(item.packageName, userId, getLabel(item),
                        AppActionShortcutInfo.ACTION_FORCE_STOP,
                        R.string.shortcut_force_stop_app,
                        R.drawable.ic_stop));
            }
            if (canClearCache) {
                actions.add(new TargetAction(item.packageName, userId, getLabel(item),
                        AppActionShortcutInfo.ACTION_CLEAR_CACHE,
                        R.string.shortcut_clear_cache_app,
                        R.drawable.ic_clear_cache));
            }
            if (actions.size() >= maxDynamicActions) {
                break;
            }
        }
        if (actions.size() <= maxDynamicActions) {
            return actions;
        }
        return new ArrayList<>(actions.subList(0, maxDynamicActions));
    }

    private static final Comparator<ApplicationItem> APP_SHORTCUT_COMPARATOR = (o1, o2) -> {
        int byScore = Long.compare(getShortcutScore(o2), getShortcutScore(o1));
        if (byScore != 0) {
            return byScore;
        }
        int byOpenCount = Integer.compare(o2.openCount, o1.openCount);
        if (byOpenCount != 0) {
            return byOpenCount;
        }
        return o1.packageName.compareTo(o2.packageName);
    };

    private static boolean isEligible(@NonNull ApplicationItem item) {
        return item.isInstalled
                && item.userIds.length > 0
                && PackageUtils.validateName(item.packageName)
                && !BuildConfig.APPLICATION_ID.equals(item.packageName);
    }

    private static long getShortcutScore(@NonNull ApplicationItem item) {
        long lastUsageTime = item.lastUsageTime != null ? item.lastUsageTime : 0;
        long lastActionTime = item.lastActionTime != null ? item.lastActionTime : 0;
        long lastUpdateTime = item.lastUpdateTime != null ? item.lastUpdateTime : 0;
        return Math.max(Math.max(lastUsageTime, lastActionTime), lastUpdateTime);
    }

    @NonNull
    private static String getLabel(@NonNull ApplicationItem item) {
        return item.label != null ? item.label : item.packageName;
    }

    @NonNull
    private static ShortcutInfoCompat toShortcut(@NonNull Context context,
                                                 @NonNull TargetAction targetAction,
                                                 int rank) {
        AppActionShortcutInfo shortcutInfo = new AppActionShortcutInfo(
                targetAction.packageName, targetAction.userId, targetAction.action);
        String label = context.getString(targetAction.labelRes, targetAction.appLabel);
        Intent intent = shortcutInfo.toShortcutIntent(context);
        intent.setAction(Intent.ACTION_CREATE_SHORTCUT);
        return new ShortcutInfoCompat.Builder(context, shortcutInfo.getId())
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(IconCompat.createWithResource(context, targetAction.iconRes))
                .setIntent(intent)
                .setRank(rank)
                .build();
    }

    @VisibleForTesting
    static final class TargetAction {
        @NonNull
        final String packageName;
        final int userId;
        @NonNull
        final String appLabel;
        @NonNull
        @AppActionShortcutInfo.ShortcutAction
        final String action;
        @StringRes
        final int labelRes;
        @DrawableRes
        final int iconRes;

        TargetAction(@NonNull String packageName,
                     int userId,
                     @NonNull String appLabel,
                     @NonNull @AppActionShortcutInfo.ShortcutAction String action,
                     @StringRes int labelRes,
                     @DrawableRes int iconRes) {
            this.packageName = packageName;
            this.userId = userId;
            this.appLabel = appLabel;
            this.action = action;
            this.labelRes = labelRes;
            this.iconRes = iconRes;
        }
    }
}
