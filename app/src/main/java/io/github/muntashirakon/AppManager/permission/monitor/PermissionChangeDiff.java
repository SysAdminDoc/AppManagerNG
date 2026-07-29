// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import android.content.pm.PermissionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
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
        /** Permissions newly requested that are not dangerous — custom and signature ones. */
        @NonNull
        public final Set<String> newlyRequested;
        /** Custom permissions this package now declares that it did not declare before. */
        @NonNull
        public final Set<String> newlyDeclared;
        /**
         * Declared permissions whose protection level dropped, i.e. a name that used to be
         * signature-guarded and is now obtainable more cheaply.
         */
        @NonNull
        public final Set<String> weakenedDeclarations;
        /** Permissions the package requests that no installed package declares. */
        @NonNull
        public final Set<String> orphanedRequests;
        /** Declared names that an unrelated signer also claims. */
        @NonNull
        public final Set<String> contestedOwnership;

        Result(@NonNull String packageName, long beforeVersionCode, long afterVersionCode,
               @NonNull Set<String> added, @NonNull Set<String> removed,
               @NonNull Set<String> newlyRequested, @NonNull Set<String> newlyDeclared,
               @NonNull Set<String> weakenedDeclarations, @NonNull Set<String> orphanedRequests,
               @NonNull Set<String> contestedOwnership) {
            this.packageName = packageName;
            this.beforeVersionCode = beforeVersionCode;
            this.afterVersionCode = afterVersionCode;
            this.added = new TreeSet<>(added);
            this.removed = new TreeSet<>(removed);
            this.newlyRequested = new TreeSet<>(newlyRequested);
            this.newlyDeclared = new TreeSet<>(newlyDeclared);
            this.weakenedDeclarations = new TreeSet<>(weakenedDeclarations);
            this.orphanedRequests = new TreeSet<>(orphanedRequests);
            this.contestedOwnership = new TreeSet<>(contestedOwnership);
        }

        /** True when there is at least one newly-added dangerous permission. */
        public boolean isInteresting() {
            return !added.isEmpty();
        }

        /** True when the update expanded privilege in any of the tracked ways. */
        public boolean isEscalation() {
            return !added.isEmpty() || !newlyRequested.isEmpty() || !weakenedDeclarations.isEmpty()
                    || !contestedOwnership.isEmpty();
        }
    }

    private PermissionChangeDiff() {
    }

    @VisibleForTesting
    @NonNull
    public static Result compute(@NonNull String packageName,
                                 @NonNull PermissionSnapshot before,
                                 @NonNull PermissionSnapshot after) {
        return compute(packageName, before, after, Collections.emptyMap(), Collections.emptySet());
    }

    /**
     * @param otherDeclarations declared custom permissions owned by the <em>other</em> installed
     *                          packages, keyed by permission name.
     * @param knownPermissions  every permission name the platform can currently resolve; a
     *                          requested name outside this set is orphaned.
     */
    @VisibleForTesting
    @NonNull
    public static Result compute(@NonNull String packageName,
                                 @NonNull PermissionSnapshot before,
                                 @NonNull PermissionSnapshot after,
                                 @NonNull Map<String, DeclaredPermission> otherDeclarations,
                                 @NonNull Set<String> knownPermissions) {
        Set<String> added = new LinkedHashSet<>(after.dangerousPermissions);
        added.removeAll(before.dangerousPermissions);
        Set<String> removed = new LinkedHashSet<>(before.dangerousPermissions);
        removed.removeAll(after.dangerousPermissions);

        Set<String> newlyRequested = new LinkedHashSet<>(after.requestedPermissions);
        newlyRequested.removeAll(before.requestedPermissions);
        // Dangerous additions are already reported on their own; do not double-count them.
        newlyRequested.removeAll(after.dangerousPermissions);

        Set<String> newlyDeclared = new LinkedHashSet<>(after.declaredPermissions.keySet());
        newlyDeclared.removeAll(before.declaredPermissions.keySet());

        Set<String> weakenedDeclarations = new LinkedHashSet<>();
        Set<String> contestedOwnership = new LinkedHashSet<>();
        for (Map.Entry<String, DeclaredPermission> entry : after.declaredPermissions.entrySet()) {
            DeclaredPermission current = entry.getValue();
            DeclaredPermission previous = before.declaredPermissions.get(entry.getKey());
            if (previous != null && isWeakerThan(current.protectionLevel, previous.protectionLevel)) {
                weakenedDeclarations.add(entry.getKey());
            }
            DeclaredPermission foreign = otherDeclarations.get(entry.getKey());
            if (foreign != null && current.isForeignOwnerOf(foreign)) {
                contestedOwnership.add(entry.getKey());
            }
        }

        Set<String> orphanedRequests = new LinkedHashSet<>();
        if (!knownPermissions.isEmpty()) {
            for (String requested : after.requestedPermissions) {
                if (!knownPermissions.contains(requested)
                        && !after.declaredPermissions.containsKey(requested)) {
                    orphanedRequests.add(requested);
                }
            }
        }

        return new Result(packageName, before.versionCode, after.versionCode, added, removed,
                newlyRequested, newlyDeclared, weakenedDeclarations, orphanedRequests,
                contestedOwnership);
    }

    /**
     * Protection ordering used for the weakening check: signature is the strongest, then internal,
     * then dangerous, then normal. Unknown levels ({@code -1}) never count as a weakening.
     */
    @VisibleForTesting
    static boolean isWeakerThan(int current, int previous) {
        return current >= 0 && previous >= 0 && rank(current) < rank(previous);
    }

    private static int rank(int protection) {
        switch (protection) {
            case PermissionInfo.PROTECTION_SIGNATURE:
                return 3;
            case PROTECTION_INTERNAL:
                return 2;
            case PermissionInfo.PROTECTION_DANGEROUS:
                return 1;
            case PermissionInfo.PROTECTION_NORMAL:
            default:
                return 0;
        }
    }

    /** {@code PermissionInfo.PROTECTION_INTERNAL}, inlined for the API 21 floor. */
    private static final int PROTECTION_INTERNAL = 4;
}
