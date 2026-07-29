// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Per-package snapshot of permission state. Immutable value type.
 *
 * <p>Schema 2 records more than the dangerous set: every requested permission (so a newly
 * requested custom or signature permission is visible), and every custom permission the package
 * declares with its protection level and owner signer (so the auditor can spot an unrelated
 * signer taking over a permission name, or a name being requested with nobody declaring it).
 */
public final class PermissionSnapshot {
    public final long versionCode;
    @NonNull
    public final Set<String> dangerousPermissions;
    /** Every permission the package requests, dangerous or not. */
    @NonNull
    public final Set<String> requestedPermissions;
    /** Custom permissions declared by this package, keyed by name. */
    @NonNull
    public final Map<String, DeclaredPermission> declaredPermissions;

    public PermissionSnapshot(long versionCode, @NonNull Set<String> dangerousPermissions) {
        this(versionCode, dangerousPermissions, dangerousPermissions, Collections.emptyMap());
    }

    public PermissionSnapshot(long versionCode,
                              @NonNull Set<String> dangerousPermissions,
                              @NonNull Set<String> requestedPermissions,
                              @NonNull Map<String, DeclaredPermission> declaredPermissions) {
        this.versionCode = versionCode;
        // Normalize to sorted, immutable collections so equals() and the on-disk representation
        // are deterministic regardless of insertion order.
        this.dangerousPermissions = Collections.unmodifiableSet(new TreeSet<>(dangerousPermissions));
        this.requestedPermissions = Collections.unmodifiableSet(new TreeSet<>(requestedPermissions));
        this.declaredPermissions = Collections.unmodifiableMap(new TreeMap<>(declaredPermissions));
    }

    @VisibleForTesting
    public static PermissionSnapshot of(long versionCode, @NonNull String... perms) {
        Set<String> set = new LinkedHashSet<>();
        for (String p : perms) if (p != null && !p.isEmpty()) set.add(p);
        return new PermissionSnapshot(versionCode, set);
    }
}
