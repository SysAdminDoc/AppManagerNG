// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure-function diff between two {@link PermissionSnapshot}s. Split out from
 * the receiver so the diff policy is unit-testable without Android.
 *
 * <p>"Added dangerous permission" means the permission name is present in
 * {@code after.dangerousPermissions} but not in {@code before.dangerousPermissions}.
 * Removed permissions are reported separately so a future surface can show
 * the negative-delta case too, but the {@link Result#isInteresting} signal
 * fires only on additions — losing a permission is rarely the user-alarming
 * direction.
 */
public final class PermissionChangeDiff {

    public static final class Result {
        @NonNull
        public final String packageName;
        public final long beforeVersionCode;
        public final long afterVersionCode;
        @NonNull
        public final Set<String> added;
        @NonNull
        public final Set<String> removed;

        Result(@NonNull String packageName, long beforeVersionCode, long afterVersionCode,
               @NonNull Set<String> added, @NonNull Set<String> removed) {
            this.packageName = packageName;
            this.beforeVersionCode = beforeVersionCode;
            this.afterVersionCode = afterVersionCode;
            this.added = new TreeSet<>(added);
            this.removed = new TreeSet<>(removed);
        }

        /** True when there is at least one newly-added dangerous permission. */
        public boolean isInteresting() {
            return !added.isEmpty();
        }
    }

    private PermissionChangeDiff() {
    }

    @VisibleForTesting
    @NonNull
    public static Result compute(@NonNull String packageName,
                                 @NonNull PermissionSnapshot before,
                                 @NonNull PermissionSnapshot after) {
        Set<String> added = new LinkedHashSet<>(after.dangerousPermissions);
        added.removeAll(before.dangerousPermissions);
        Set<String> removed = new LinkedHashSet<>(before.dangerousPermissions);
        removed.removeAll(after.dangerousPermissions);
        return new Result(packageName, before.versionCode, after.versionCode, added, removed);
    }
}
