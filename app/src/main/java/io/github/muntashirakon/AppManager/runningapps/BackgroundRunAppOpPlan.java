// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.app.AppOpsManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;

final class BackgroundRunAppOpPlan {
    private BackgroundRunAppOpPlan() {
    }

    @NonNull
    static int[] getAppOpsForSdk(int sdkInt) {
        if (sdkInt < Build.VERSION_CODES.N) {
            return new int[0];
        }
        if (sdkInt < Build.VERSION_CODES.P) {
            return new int[]{AppOpsManagerCompat.OP_RUN_IN_BACKGROUND};
        }
        return new int[]{
                AppOpsManagerCompat.OP_RUN_IN_BACKGROUND,
                AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND,
        };
    }

    static boolean canRunInBackground(@NonNull int[] currentModes) {
        if (currentModes.length == 0) {
            return true;
        }
        for (int mode : currentModes) {
            if (!isRestricted(mode)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasBackgroundRestriction(@NonNull int[] currentModes) {
        for (int mode : currentModes) {
            if (isRestricted(mode)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    static List<OpModeChange> createRestorePlan(@NonNull int[] appOps, @NonNull int[] currentModes,
                                                @Nullable int[] previousModes) {
        if (appOps.length != currentModes.length) {
            throw new IllegalArgumentException("appOps and currentModes must have the same length.");
        }
        if (previousModes != null && previousModes.length != appOps.length) {
            throw new IllegalArgumentException("previousModes must match appOps length.");
        }
        List<OpModeChange> changes = new ArrayList<>();
        for (int i = 0; i < appOps.length; ++i) {
            int currentMode = currentModes[i];
            if (!isRestricted(currentMode)) {
                continue;
            }
            Integer previousMode = previousModes != null ? previousModes[i] : null;
            changes.add(new OpModeChange(appOps[i], currentMode, getRestoreMode(previousMode)));
        }
        return changes;
    }

    static boolean isRestricted(int mode) {
        return mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED;
    }

    @AppOpsManagerCompat.Mode
    private static int getRestoreMode(@Nullable Integer previousMode) {
        if (previousMode == null || isRestricted(previousMode)) {
            return AppOpsManager.MODE_DEFAULT;
        }
        return previousMode;
    }

    static final class OpModeChange {
        final int op;
        final int previousMode;
        @AppOpsManagerCompat.Mode
        final int restoreMode;

        OpModeChange(int op, int previousMode, @AppOpsManagerCompat.Mode int restoreMode) {
            this.op = op;
            this.previousMode = previousMode;
            this.restoreMode = restoreMode;
        }
    }
}
