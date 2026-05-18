// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Per-package snapshot of dangerous-permission state. Immutable value type.
 * Used by {@link PermissionSnapshotStore} to detect new dangerous-permission
 * additions across app updates.
 */
public final class PermissionSnapshot {
    public final long versionCode;
    @NonNull
    public final Set<String> dangerousPermissions;

    public PermissionSnapshot(long versionCode, @NonNull Set<String> dangerousPermissions) {
        this.versionCode = versionCode;
        // Normalize to a sorted, immutable set so equals() and on-disk
        // representation are deterministic regardless of insertion order.
        this.dangerousPermissions = Collections.unmodifiableSet(new TreeSet<>(dangerousPermissions));
    }

    @VisibleForTesting
    public static PermissionSnapshot of(long versionCode, @NonNull String... perms) {
        Set<String> set = new LinkedHashSet<>();
        for (String p : perms) if (p != null && !p.isEmpty()) set.add(p);
        return new PermissionSnapshot(versionCode, set);
    }
}
