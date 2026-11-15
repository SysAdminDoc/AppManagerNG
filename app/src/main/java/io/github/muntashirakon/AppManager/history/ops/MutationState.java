// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

/**
 * The state a mutation image records, as plain strings that compare exactly. Every reader answers null when the
 * state can't be read, so "unknown" never passes for a real value.
 * <ul>
 * <li>{@link #KIND_FREEZE}: {@link #UNFROZEN}, or the methods holding the app frozen joined by {@code +} in the order
 * disabled, suspended, hidden.
 * <li>{@link #KIND_PERMISSION}: {@link #GRANTED} or {@link #DENIED}; the target is the permission name.
 * <li>{@link #KIND_APP_OP}: the op's effective mode as a number, which is its default when nothing is set (the
 * service drops an entry set back to its default, so this round-trips); the target is the op number.
 * <li>{@link #KIND_NET_POLICY}: the UID policy flags as a number.
 * </ul>
 */
public final class MutationState {
    public static final String KIND_FREEZE = "freeze";
    public static final String KIND_PERMISSION = "permission";
    public static final String KIND_APP_OP = "app_op";
    public static final String KIND_NET_POLICY = "net_policy";

    public static final String UNFROZEN = "unfrozen";
    public static final String DISABLED = "disabled";
    public static final String SUSPENDED = "suspended";
    public static final String HIDDEN = "hidden";
    public static final String GRANTED = "granted";
    public static final String DENIED = "denied";

    /** Reads the current state of one target. */
    public interface Reader {
        @WorkerThread
        @Nullable
        String read(@NonNull String kind, @NonNull String target, @NonNull String packageName, int userId);
    }

    /** Reads from the system services, the same ones the writes go through. */
    public static final Reader SYSTEM = MutationState::readFromSystem;

    @WorkerThread
    @Nullable
    static String readFromSystem(@NonNull String kind, @NonNull String target, @NonNull String packageName,
                                 int userId) {
        try {
            switch (kind) {
                case KIND_FREEZE:
                    return freezeValue(applicationInfo(packageName, userId));
                case KIND_PERMISSION: {
                    int result = PermissionCompat.checkPermission(target, packageName, userId);
                    return result == PackageManager.PERMISSION_GRANTED ? GRANTED : DENIED;
                }
                case KIND_APP_OP: {
                    int op = Integer.parseInt(target);
                    ApplicationInfo info = applicationInfo(packageName, userId);
                    List<AppOpsManagerCompat.OpEntry> entries = AppOpsManagerCompat.getOpsForPackageOrNull(info.uid,
                            packageName);
                    if (entries == null) {
                        return null;
                    }
                    return String.valueOf(AppOpsManagerCompat.getModeFromOpEntriesOrDefault(op, entries));
                }
                case KIND_NET_POLICY:
                    return String.valueOf(NetworkPolicyManagerCompat.getUidPolicy(
                            applicationInfo(packageName, userId).uid));
                default:
                    return null;
            }
        } catch (Throwable th) {
            // Any failure to read means the state is unknown, never that it's off
            return null;
        }
    }

    @NonNull
    private static ApplicationInfo applicationInfo(@NonNull String packageName, int userId) throws Exception {
        return PackageManagerCompat.getApplicationInfo(packageName, PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES
                | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
    }

    @NonNull
    static String freezeValue(@NonNull ApplicationInfo info) {
        return freezeValue(!info.enabled, ApplicationInfoCompat.isSuspended(info), ApplicationInfoCompat.isHidden(info));
    }

    @NonNull
    static String freezeValue(boolean disabled, boolean suspended, boolean hidden) {
        List<String> parts = new ArrayList<>(3);
        if (disabled) parts.add(DISABLED);
        if (suspended) parts.add(SUSPENDED);
        if (hidden) parts.add(HIDDEN);
        return parts.isEmpty() ? UNFROZEN : TextUtils.join("+", parts);
    }

    /** The value a freeze with this method asks for. A method the app can fall back from still names what was asked. */
    @NonNull
    public static String requestedFreezeValue(@FreezeUtils.FreezeMethod int method) {
        switch (method) {
            case FreezeUtils.FREEZE_HIDE:
                return HIDDEN;
            case FreezeUtils.FREEZE_SUSPEND:
            case FreezeUtils.FREEZE_ADV_SUSPEND:
                return SUSPENDED;
            case FreezeUtils.FREEZE_DISABLE:
            default:
                return DISABLED;
        }
    }

    /** The freeze method that puts an app back to a frozen value held by one method, or null for any other value. */
    @Nullable
    public static Integer freezeMethodFor(@NonNull String value) {
        switch (value) {
            case DISABLED:
                return FreezeUtils.FREEZE_DISABLE;
            case SUSPENDED:
                return FreezeUtils.FREEZE_SUSPEND;
            case HIDDEN:
                return FreezeUtils.FREEZE_HIDE;
            default:
                return null;
        }
    }

    private MutationState() {
    }
}
