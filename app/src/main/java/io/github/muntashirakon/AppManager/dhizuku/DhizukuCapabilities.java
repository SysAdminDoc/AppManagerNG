// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

/**
 * What runs through Dhizuku. A device owner reaches Android's device policy and package installer
 * APIs, nothing more: it can't run shell commands, force stop apps, clear caches, block
 * components, change app ops or disable apps. Those stay with root, ADB and Shizuku.
 */
public final class DhizukuCapabilities {
    public enum Operation {
        /** {@code DevicePolicyManager#setApplicationHidden} */
        HIDE_APPS(Build.VERSION_CODES.M, R.string.dhizuku_capability_hide_apps),
        /** {@code DevicePolicyManager#setPackagesSuspended} */
        SUSPEND_APPS(Build.VERSION_CODES.N, R.string.dhizuku_capability_suspend_apps),
        /** {@code DevicePolicyManager#setPermissionGrantState} */
        RUNTIME_PERMISSIONS(Build.VERSION_CODES.M, R.string.dhizuku_capability_runtime_permissions),
        /** {@code DevicePolicyManager#clearApplicationUserData} */
        CLEAR_APP_DATA(Build.VERSION_CODES.P, R.string.dhizuku_capability_clear_app_data),
        /** A package installer session opened as the owner */
        INSTALL_APPS(Build.VERSION_CODES.M, R.string.dhizuku_capability_install_apps),
        /** {@code PackageInstaller#uninstall} called as the owner */
        UNINSTALL_APPS(Build.VERSION_CODES.M, R.string.dhizuku_capability_uninstall_apps);

        public final int minSdk;
        @StringRes
        public final int labelRes;

        Operation(int minSdk, @StringRes int labelRes) {
            this.minSdk = minSdk;
            this.labelRes = labelRes;
        }
    }

    private DhizukuCapabilities() {
    }

    /**
     * The operations that run through Dhizuku right now. Empty unless Dhizuku allowed this app.
     */
    @NonNull
    public static List<Operation> runnable(@Nullable DhizukuState state, int sdk) {
        if (state != DhizukuState.PERMITTED) {
            return Collections.emptyList();
        }
        List<Operation> operations = new ArrayList<>();
        for (Operation operation : Operation.values()) {
            if (sdk >= operation.minSdk) {
                operations.add(operation);
            }
        }
        return Collections.unmodifiableList(operations);
    }

    public static boolean canRun(@Nullable DhizukuState state, int sdk, @NonNull Operation operation) {
        return state == DhizukuState.PERMITTED && sdk >= operation.minSdk;
    }
}
