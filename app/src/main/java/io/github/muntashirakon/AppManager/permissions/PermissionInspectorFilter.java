// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-function chip-row filter for the Permission Inspector catalog (EI-04).
 *
 * <p>The inspector lists one row per curated dangerous-permission group with a
 * requested-count and granted-count. The toolbar chip row narrows that list:
 * <ul>
 *   <li>{@link Filter#ALL} - every group.</li>
 *   <li>{@link Filter#REQUESTED} - groups at least one app requests.</li>
 *   <li>{@link Filter#GRANTED} - groups at least one app has granted.</li>
 *   <li>{@link Filter#NEEDS_ATTENTION} - groups where some requesting app has
 *       <em>not</em> granted it ({@code requested > granted}); the actionable
 *       "review me" set.</li>
 * </ul>
 *
 * <p>{@link #matches(Filter, int, int)} is the JVM-unit-testable core (plain
 * ints, no Android types); {@link #apply(List, Filter)} is the trivial glue the
 * Activity calls.
 */
public final class PermissionInspectorFilter {

    public enum Filter {
        ALL,
        REQUESTED,
        GRANTED,
        NEEDS_ATTENTION
    }

    private PermissionInspectorFilter() {
    }

    /** Whether a row with the given counts is kept under {@code filter}. */
    @VisibleForTesting
    public static boolean matches(@NonNull Filter filter, int requestedCount, int grantedCount) {
        switch (filter) {
            case REQUESTED:
                return requestedCount > 0;
            case GRANTED:
                return grantedCount > 0;
            case NEEDS_ATTENTION:
                return requestedCount > grantedCount;
            case ALL:
            default:
                return true;
        }
    }

    /**
     * Return the subset of {@code rows} kept under {@code filter}. {@code ALL}
     * (or a null list) returns the input unchanged; otherwise a new filtered
     * list in the original order.
     */
    @NonNull
    public static List<PermissionInspectorViewModel.Row> apply(
            @Nullable List<PermissionInspectorViewModel.Row> rows, @NonNull Filter filter) {
        if (rows == null) {
            return new ArrayList<>(0);
        }
        if (filter == Filter.ALL) {
            return rows;
        }
        List<PermissionInspectorViewModel.Row> out = new ArrayList<>(rows.size());
        for (PermissionInspectorViewModel.Row row : rows) {
            if (row != null && matches(filter, row.requestedCount, row.grantedCount)) {
                out.add(row);
            }
        }
        return out;
    }
}
