// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * The security-relevant manifest facts of a single component, as recorded in a snapshot.
 *
 * <p>The component set alone cannot express update-time privilege expansion: an app can keep the
 * exact same components across an update and still open one of them to the world, or drop the
 * permission that used to guard it. Those are the transitions this record makes diffable.
 */
public final class ComponentRecord {
    public static final String TYPE_UNKNOWN = "unknown";
    public static final String TYPE_ACTIVITY = "activity";
    public static final String TYPE_SERVICE = "service";
    public static final String TYPE_RECEIVER = "receiver";
    public static final String TYPE_PROVIDER = "provider";

    @NonNull
    public final String type;
    /** Effective exported state, i.e. what the platform will honour, not just the manifest bit. */
    public final boolean exported;
    public final boolean enabled;
    /** The permission a caller must hold, or {@code null} when the component is unguarded. */
    @Nullable
    public final String permission;

    public ComponentRecord(@NonNull String type, boolean exported, boolean enabled,
                           @Nullable String permission) {
        this.type = type;
        this.exported = exported;
        this.enabled = enabled;
        this.permission = permission != null && !permission.isEmpty() ? permission : null;
    }

    /**
     * A component that is reachable by other apps: exported, enabled, and — for the alerting
     * purposes of the auditor — that is the state worth watching for.
     */
    public boolean isReachable() {
        return exported && enabled;
    }

    @NonNull
    public static ComponentRecord unknown() {
        return new ComponentRecord(TYPE_UNKNOWN, false, true, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComponentRecord)) return false;
        ComponentRecord other = (ComponentRecord) o;
        return exported == other.exported
                && enabled == other.enabled
                && type.equals(other.type)
                && Objects.equals(permission, other.permission);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, exported, enabled, permission);
    }

    @NonNull
    @Override
    public String toString() {
        return type + (exported ? "/exported" : "/internal") + (enabled ? "" : "/disabled")
                + (permission != null ? "/" + permission : "");
    }
}
