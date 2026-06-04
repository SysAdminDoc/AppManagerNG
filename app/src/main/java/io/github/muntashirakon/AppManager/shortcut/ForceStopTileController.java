// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Objects;

import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public final class ForceStopTileController {
    private static final char TARGET_SEPARATOR = ':';

    private ForceStopTileController() {
    }

    public static void setSelectedTarget(@NonNull String packageName, int userId) {
        Prefs.AppActions.setForceStopTileTarget(encodeTarget(packageName, userId));
    }

    public static void clearSelectedTarget() {
        Prefs.AppActions.setForceStopTileTarget(null);
    }

    @Nullable
    public static Target getSelectedTarget() {
        return parseTarget(Prefs.AppActions.getForceStopTileTarget());
    }

    public static boolean isSelectedTarget(@NonNull String packageName, int userId) {
        Target target = getSelectedTarget();
        return target != null && userId == target.userId && packageName.equals(target.packageName);
    }

    @NonNull
    @VisibleForTesting
    static String encodeTarget(@NonNull String packageName, int userId) {
        return packageName + TARGET_SEPARATOR + userId;
    }

    @Nullable
    @VisibleForTesting
    static Target parseTarget(@Nullable String encodedTarget) {
        if (encodedTarget == null || encodedTarget.isEmpty()) {
            return null;
        }
        int separatorIndex = encodedTarget.lastIndexOf(TARGET_SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex == encodedTarget.length() - 1) {
            return null;
        }
        String packageName = encodedTarget.substring(0, separatorIndex);
        if (!PackageUtils.validateName(packageName)) {
            return null;
        }
        try {
            int userId = Integer.parseInt(encodedTarget.substring(separatorIndex + 1));
            if (userId < 0) {
                return null;
            }
            return new Target(packageName, userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static final class Target {
        @NonNull
        public final String packageName;
        public final int userId;

        private Target(@NonNull String packageName, int userId) {
            this.packageName = packageName;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Target)) return false;
            Target target = (Target) o;
            return userId == target.userId && packageName.equals(target.packageName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageName, userId);
        }
    }
}
