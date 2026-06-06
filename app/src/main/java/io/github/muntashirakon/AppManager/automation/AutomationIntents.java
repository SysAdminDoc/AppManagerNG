// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;

public final class AutomationIntents {
    public static final String PERMISSION_AUTOMATION = BuildConfig.APPLICATION_ID + ".permission.AUTOMATION";

    public static final String ACTION_PREFIX = BuildConfig.APPLICATION_ID + ".action.";
    public static final String ACTION_FREEZE = ACTION_PREFIX + "FREEZE";
    public static final String ACTION_UNFREEZE = ACTION_PREFIX + "UNFREEZE";
    public static final String ACTION_FORCE_STOP = ACTION_PREFIX + "FORCE_STOP";
    public static final String ACTION_CLEAR_CACHE = ACTION_PREFIX + "CLEAR_CACHE";
    public static final String ACTION_CLEAR_DATA = ACTION_PREFIX + "CLEAR_DATA";
    public static final String ACTION_UNINSTALL = ACTION_PREFIX + "UNINSTALL";
    public static final String ACTION_BACKUP = ACTION_PREFIX + "BACKUP";
    public static final String ACTION_RESTORE = ACTION_PREFIX + "RESTORE";
    public static final String ACTION_DISABLE_COMPONENT = ACTION_PREFIX + "DISABLE_COMPONENT";
    public static final String ACTION_ENABLE_COMPONENT = ACTION_PREFIX + "ENABLE_COMPONENT";
    public static final String ACTION_RUN_PROFILE = ACTION_PREFIX + "RUN_PROFILE";
    public static final String ACTION_INSTALL_FROM_URI = ACTION_PREFIX + "INSTALL_FROM_URI";
    public static final String ACTION_SCAN_TRACKERS = ACTION_PREFIX + "SCAN_TRACKERS";

    public static final String EXTRA_PACKAGE = "EXTRA_PACKAGE";
    public static final String EXTRA_PACKAGES = "EXTRA_PACKAGES";
    public static final String EXTRA_USER = "EXTRA_USER";
    public static final String EXTRA_USERS = "EXTRA_USERS";
    public static final String EXTRA_COMPONENT = "EXTRA_COMPONENT";
    public static final String EXTRA_PROFILE_ID = "EXTRA_PROFILE_ID";
    public static final String EXTRA_PROFILE_STATE = "EXTRA_PROFILE_STATE";
    public static final String EXTRA_PROFILE_OVERRIDES = "EXTRA_PROFILE_OVERRIDES";
    public static final String EXTRA_BACKUP_NAME = "EXTRA_BACKUP_NAME";
    public static final String EXTRA_BACKUP_FLAGS = "EXTRA_BACKUP_FLAGS";
    public static final String EXTRA_DRY_RUN = "EXTRA_DRY_RUN";
    public static final String EXTRA_URI = "EXTRA_URI";

    private static final Set<String> ACTIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ACTION_FREEZE,
            ACTION_UNFREEZE,
            ACTION_FORCE_STOP,
            ACTION_CLEAR_CACHE,
            ACTION_CLEAR_DATA,
            ACTION_UNINSTALL,
            ACTION_BACKUP,
            ACTION_RESTORE,
            ACTION_DISABLE_COMPONENT,
            ACTION_ENABLE_COMPONENT,
            ACTION_RUN_PROFILE,
            ACTION_INSTALL_FROM_URI,
            ACTION_SCAN_TRACKERS
    )));

    private AutomationIntents() {
    }

    public static boolean isAutomationAction(@Nullable String action) {
        return action != null && ACTIONS.contains(action);
    }

    @VisibleForTesting
    @Nullable
    static Integer getBatchOpForAction(@NonNull String action) {
        switch (action) {
            case ACTION_FREEZE:
                return BatchOpsManager.OP_FREEZE;
            case ACTION_UNFREEZE:
                return BatchOpsManager.OP_UNFREEZE;
            case ACTION_FORCE_STOP:
                return BatchOpsManager.OP_FORCE_STOP;
            case ACTION_CLEAR_CACHE:
                return BatchOpsManager.OP_CLEAR_CACHE;
            case ACTION_CLEAR_DATA:
                return BatchOpsManager.OP_CLEAR_DATA;
            case ACTION_UNINSTALL:
                return BatchOpsManager.OP_UNINSTALL;
            case ACTION_BACKUP:
                return BatchOpsManager.OP_BACKUP;
            case ACTION_RESTORE:
                return BatchOpsManager.OP_RESTORE_BACKUP;
            case ACTION_DISABLE_COMPONENT:
                return BatchOpsManager.OP_BLOCK_COMPONENTS;
            case ACTION_ENABLE_COMPONENT:
                return BatchOpsManager.OP_UNBLOCK_COMPONENTS;
            default:
                return null;
        }
    }

    /**
     * Read an intent extra as a value of any wire type and return it.
     *
     * <p>Automation front-ends (Tasker, Automate, {@code adb shell am broadcast},
     * {@code am://} deep links) very commonly deliver extras as <em>strings</em>
     * even when the semantic type is boolean/int. {@link Intent#getBooleanExtra}
     * and {@link Intent#getIntExtra} silently ignore a string-typed extra and
     * return the fallback, which for {@code EXTRA_DRY_RUN} means a dry-run
     * request executes for real. The {@link #coerceBoolean}/{@link #coerceInt}
     * helpers below mirror {@code AutomationRequest}'s coercion so the broadcast
     * receiver and the URI/activity path agree on how a raw extra is read.
     */
    @Nullable
    static Object getExtra(@NonNull Intent intent, @NonNull String name) {
        Bundle extras = intent.getExtras();
        return extras != null ? extras.get(name) : null;
    }

    /** Read {@code name} as a boolean, accepting boolean or string ("true"/"1"/"yes"/"on"...) values. */
    static boolean readBooleanExtra(@NonNull Intent intent, @NonNull String name, boolean fallback) {
        return coerceBoolean(getExtra(intent, name), fallback);
    }

    /** Read {@code name} as an int, accepting numeric or string ("7") values. */
    static int readIntExtra(@NonNull Intent intent, @NonNull String name, int fallback) {
        return coerceInt(getExtra(intent, name), fallback);
    }

    static boolean coerceBoolean(@Nullable Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized)
                || "on".equals(normalized)) {
            return true;
        }
        if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized)
                || "off".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    static int coerceInt(@Nullable Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignore) {
            return fallback;
        }
    }

    /**
     * Split a comma/newline-separated string extra into trimmed, non-empty
     * tokens. Mirrors {@code AutomationRequest.addSplitValues} so a
     * {@code EXTRA_PACKAGES="a,b,c"} string extra (a common automation-tool
     * form) is honoured by the broadcast receiver, not silently dropped.
     */
    @NonNull
    static List<String> splitValues(@Nullable String value) {
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return out;
        }
        for (String part : normalized.split("[,\\n]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    @VisibleForTesting
    @NonNull
    static String normalizeComponentName(@NonNull String packageName, @NonNull String componentName) {
        String trimmedComponent = componentName.trim();
        if (trimmedComponent.isEmpty()) {
            throw new IllegalArgumentException("Empty component name");
        }
        int slash = trimmedComponent.indexOf('/');
        if (slash >= 0) {
            if (slash != trimmedComponent.lastIndexOf('/') || slash == trimmedComponent.length() - 1) {
                throw new IllegalArgumentException("Invalid component name: " + componentName);
            }
            String className = trimmedComponent.substring(slash + 1);
            return normalizeComponentName(packageName, className);
        }
        String normalizedComponent;
        if (trimmedComponent.startsWith(".")) {
            normalizedComponent = packageName + trimmedComponent;
        } else if (trimmedComponent.indexOf('.') == -1) {
            normalizedComponent = packageName + "." + trimmedComponent;
        } else {
            normalizedComponent = trimmedComponent;
        }
        if (!isPlausibleComponentClassName(normalizedComponent)) {
            throw new IllegalArgumentException("Invalid component name: " + componentName);
        }
        return normalizedComponent;
    }

    private static boolean isPlausibleComponentClassName(@NonNull String className) {
        boolean front = true;
        for (int i = 0; i < className.length(); ++i) {
            char c = className.charAt(i);
            if (c == '.') {
                if (front) {
                    return false;
                }
                front = true;
                continue;
            }
            if (front) {
                if (!Character.isJavaIdentifierStart(c)) {
                    return false;
                }
                front = false;
            } else if (!Character.isJavaIdentifierPart(c)) {
                return false;
            }
        }
        return !front;
    }
}
