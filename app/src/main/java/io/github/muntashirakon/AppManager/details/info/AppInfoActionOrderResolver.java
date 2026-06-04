// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;

public final class AppInfoActionOrderResolver {
    public static final String ACTION_LAUNCH = "launch";
    public static final String ACTION_FREEZE = "freeze";
    public static final String ACTION_FORCE_STOP = "force_stop";
    public static final String ACTION_CLEAR_CACHE = "clear_cache";
    public static final String ACTION_MANIFEST = "manifest";
    public static final String ACTION_SCANNER = "scanner";
    public static final String ACTION_HIDE = "hide";
    public static final String ACTION_ARCHIVE = "archive";
    public static final String ACTION_INSTALL = "install";
    public static final String ACTION_WHATS_NEW = "whats_new";
    public static final String ACTION_SETTINGS = "settings";
    public static final String ACTION_UNINSTALL = "uninstall";
    public static final String ACTION_CLEAR_DATA = "clear_data";
    public static final String ACTION_SHARED_PREFS = "shared_prefs";
    public static final String ACTION_DATABASES = "databases";
    public static final String ACTION_FDROID = "fdroid";
    public static final String ACTION_AURORA_STORE = "aurora_store";

    private static final String[] DEFAULT_ORDER = {
            ACTION_LAUNCH,
            ACTION_FREEZE,
            ACTION_FORCE_STOP,
            ACTION_CLEAR_CACHE,
            ACTION_MANIFEST,
            ACTION_SCANNER,
            ACTION_HIDE,
            ACTION_ARCHIVE,
            ACTION_INSTALL,
            ACTION_WHATS_NEW,
            ACTION_SETTINGS,
            ACTION_UNINSTALL,
            ACTION_CLEAR_DATA,
            ACTION_SHARED_PREFS,
            ACTION_DATABASES,
            ACTION_FDROID,
            ACTION_AURORA_STORE,
    };

    private AppInfoActionOrderResolver() {
    }

    @NonNull
    public static String[] getCustomizableActionIds() {
        return DEFAULT_ORDER.clone();
    }

    @StringRes
    public static int getLabelRes(@NonNull String actionId) {
        switch (actionId) {
            case ACTION_LAUNCH:
                return R.string.launch_app;
            case ACTION_FREEZE:
                return R.string.freeze;
            case ACTION_FORCE_STOP:
                return R.string.force_stop;
            case ACTION_CLEAR_CACHE:
                return R.string.clear_cache;
            case ACTION_MANIFEST:
                return R.string.manifest;
            case ACTION_SCANNER:
                return R.string.scanner;
            case ACTION_HIDE:
                return R.string.quick_hide_app;
            case ACTION_ARCHIVE:
                return R.string.archive_app;
            case ACTION_INSTALL:
                return R.string.install;
            case ACTION_WHATS_NEW:
                return R.string.whats_new;
            case ACTION_SETTINGS:
                return R.string.view_in_settings;
            case ACTION_UNINSTALL:
                return R.string.uninstall;
            case ACTION_CLEAR_DATA:
                return R.string.clear_data;
            case ACTION_SHARED_PREFS:
                return R.string.shared_prefs;
            case ACTION_DATABASES:
                return R.string.databases;
            case ACTION_FDROID:
                return R.string.fdroid;
            case ACTION_AURORA_STORE:
                return R.string.open_in_aurora_store;
            default:
                return R.string.state_unknown;
        }
    }

    @NonNull
    static List<ActionItem> resolve(@NonNull List<ActionItem> availableActions,
                                    @Nullable List<String> prioritizedActionIds) {
        List<String> sanitizedPriority = sanitizePriority(prioritizedActionIds);
        List<ActionItem> orderedActions = new ArrayList<>(availableActions);
        orderedActions.sort((left, right) -> Integer.compare(
                getResolvedRank(left.getActionId(), sanitizedPriority),
                getResolvedRank(right.getActionId(), sanitizedPriority)));
        return orderedActions;
    }

    @NonNull
    public static List<String> parsePriority(@Nullable String serializedPriority) {
        List<String> priority = new ArrayList<>();
        if (serializedPriority == null || serializedPriority.trim().isEmpty()) {
            return priority;
        }
        String[] actionIds = serializedPriority.split(",");
        for (String actionId : actionIds) {
            priority.add(actionId.trim());
        }
        return sanitizePriority(priority);
    }

    @NonNull
    public static String serializePriority(@Nullable List<String> prioritizedActionIds) {
        List<String> sanitizedPriority = sanitizePriority(prioritizedActionIds);
        StringBuilder builder = new StringBuilder();
        for (String actionId : sanitizedPriority) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(actionId);
        }
        return builder.toString();
    }

    @NonNull
    public static List<String> sanitizePriority(@Nullable List<String> prioritizedActionIds) {
        List<String> sanitizedPriority = new ArrayList<>();
        if (prioritizedActionIds == null) {
            return sanitizedPriority;
        }
        Set<String> seen = new HashSet<>();
        for (String actionId : prioritizedActionIds) {
            if (!isKnownAction(actionId) || seen.contains(actionId)) {
                continue;
            }
            sanitizedPriority.add(actionId);
            seen.add(actionId);
        }
        return sanitizedPriority;
    }

    private static int getResolvedRank(@NonNull String actionId, @NonNull List<String> prioritizedActionIds) {
        int priorityRank = prioritizedActionIds.indexOf(actionId);
        if (priorityRank >= 0) {
            return priorityRank;
        }
        return DEFAULT_ORDER.length + getDefaultRank(actionId);
    }

    private static int getDefaultRank(@NonNull String actionId) {
        for (int i = 0; i < DEFAULT_ORDER.length; ++i) {
            if (DEFAULT_ORDER[i].equals(actionId)) {
                return i;
            }
        }
        return DEFAULT_ORDER.length;
    }

    private static boolean isKnownAction(@Nullable String actionId) {
        if (actionId == null) {
            return false;
        }
        for (String knownActionId : DEFAULT_ORDER) {
            if (knownActionId.equals(actionId)) {
                return true;
            }
        }
        return false;
    }
}
