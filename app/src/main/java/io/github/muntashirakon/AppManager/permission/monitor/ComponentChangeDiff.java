// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure-function diff between component snapshots.
 */
public final class ComponentChangeDiff {
    public static final class Result {
        @NonNull
        public final String packageName;
        public final long beforeVersionCode;
        public final long afterVersionCode;
        @NonNull
        public final Set<String> addedComponents;
        @NonNull
        public final Set<String> removedComponents;
        @NonNull
        public final Set<String> addedTrackers;
        @NonNull
        public final Set<String> removedTrackers;

        Result(@NonNull String packageName, long beforeVersionCode, long afterVersionCode,
               @NonNull Set<String> addedComponents, @NonNull Set<String> removedComponents,
               @NonNull Set<String> addedTrackers, @NonNull Set<String> removedTrackers) {
            this.packageName = packageName;
            this.beforeVersionCode = beforeVersionCode;
            this.afterVersionCode = afterVersionCode;
            this.addedComponents = new TreeSet<>(addedComponents);
            this.removedComponents = new TreeSet<>(removedComponents);
            this.addedTrackers = new TreeSet<>(addedTrackers);
            this.removedTrackers = new TreeSet<>(removedTrackers);
        }

        public boolean isInteresting() {
            return !addedComponents.isEmpty() || !removedComponents.isEmpty()
                    || !addedTrackers.isEmpty() || !removedTrackers.isEmpty();
        }
    }

    private ComponentChangeDiff() {
    }

    @VisibleForTesting
    @NonNull
    public static Result compute(@NonNull String packageName,
                                 @NonNull ComponentSnapshot before,
                                 @NonNull ComponentSnapshot after) {
        Set<String> addedComponents = new LinkedHashSet<>(after.components);
        addedComponents.removeAll(before.components);
        Set<String> removedComponents = new LinkedHashSet<>(before.components);
        removedComponents.removeAll(after.components);
        Set<String> addedTrackers = new LinkedHashSet<>(after.trackerComponents);
        addedTrackers.removeAll(before.trackerComponents);
        Set<String> removedTrackers = new LinkedHashSet<>(before.trackerComponents);
        removedTrackers.removeAll(after.trackerComponents);
        return new Result(packageName, before.versionCode, after.versionCode,
                addedComponents, removedComponents, addedTrackers, removedTrackers);
    }
}
