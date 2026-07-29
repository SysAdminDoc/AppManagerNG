// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.LinkedHashSet;
import java.util.Map;
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
        /**
         * Components that already existed and became reachable by other apps — the update-time
         * privilege expansion a bare component-name diff cannot see.
         */
        @NonNull
        public final Set<String> newlyExported;
        /**
         * Reachable components whose guard permission was dropped or swapped for a different one.
         */
        @NonNull
        public final Set<String> weakenedGuards;

        Result(@NonNull String packageName, long beforeVersionCode, long afterVersionCode,
               @NonNull Set<String> addedComponents, @NonNull Set<String> removedComponents,
               @NonNull Set<String> addedTrackers, @NonNull Set<String> removedTrackers,
               @NonNull Set<String> newlyExported, @NonNull Set<String> weakenedGuards) {
            this.packageName = packageName;
            this.beforeVersionCode = beforeVersionCode;
            this.afterVersionCode = afterVersionCode;
            this.addedComponents = new TreeSet<>(addedComponents);
            this.removedComponents = new TreeSet<>(removedComponents);
            this.addedTrackers = new TreeSet<>(addedTrackers);
            this.removedTrackers = new TreeSet<>(removedTrackers);
            this.newlyExported = new TreeSet<>(newlyExported);
            this.weakenedGuards = new TreeSet<>(weakenedGuards);
        }

        public boolean isInteresting() {
            return !addedComponents.isEmpty() || !removedComponents.isEmpty()
                    || !addedTrackers.isEmpty() || !removedTrackers.isEmpty()
                    || !newlyExported.isEmpty() || !weakenedGuards.isEmpty();
        }

        /** True when the update widened the package's attack surface. */
        public boolean isEscalation() {
            return !newlyExported.isEmpty() || !weakenedGuards.isEmpty() || !addedTrackers.isEmpty();
        }
    }

    private ComponentChangeDiff() {
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @NonNull
    static Result compute(@NonNull String packageName,
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
        Set<String> newlyExported = new LinkedHashSet<>();
        Set<String> weakenedGuards = new LinkedHashSet<>();
        for (Map.Entry<String, ComponentRecord> entry : after.records.entrySet()) {
            ComponentRecord previous = before.records.get(entry.getKey());
            if (previous == null) {
                // A component that arrives already reachable is covered by addedComponents.
                continue;
            }
            ComponentRecord current = entry.getValue();
            if (!previous.isReachable() && current.isReachable()) {
                newlyExported.add(entry.getKey());
            }
            if (current.isReachable() && previous.permission != null
                    && !previous.permission.equals(current.permission)) {
                weakenedGuards.add(entry.getKey());
            }
        }
        return new Result(packageName, before.versionCode, after.versionCode,
                addedComponents, removedComponents, addedTrackers, removedTrackers,
                newlyExported, weakenedGuards);
    }
}
