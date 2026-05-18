// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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

    @VisibleForTesting
    @NonNull
    static String normalizeComponentName(@NonNull String packageName, @NonNull String componentName) {
        String trimmedComponent = componentName.trim();
        int slash = trimmedComponent.indexOf('/');
        if (slash >= 0 && slash < trimmedComponent.length() - 1) {
            String className = trimmedComponent.substring(slash + 1);
            return normalizeComponentName(packageName, className);
        }
        if (trimmedComponent.startsWith(".")) {
            return packageName + trimmedComponent;
        }
        if (trimmedComponent.indexOf('.') == -1) {
            return packageName + "." + trimmedComponent;
        }
        return trimmedComponent;
    }
}
