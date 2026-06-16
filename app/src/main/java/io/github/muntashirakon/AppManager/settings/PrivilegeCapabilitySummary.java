// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.self.SelfPermissions;

final class PrivilegeCapabilitySummary {
    private PrivilegeCapabilitySummary() {
    }

    @NonNull
    static Snapshot probe() {
        return new Snapshot(
                SelfPermissions.canInstallExistingPackages(),
                SelfPermissions.canModifyAppOpMode(),
                SelfPermissions.canModifyPermissions(),
                SelfPermissions.canFreezeUnfreezePackages(),
                SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)
                        || SelfPermissions.canKillUid(),
                SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CLEAR_APP_USER_DATA),
                SelfPermissions.canClearAppCache(),
                SelfPermissions.canWriteToDataData(),
                SelfPermissions.canBlockByIFW());
    }

    @NonNull
    static CharSequence buildSummary(@NonNull Context context, @NonNull Snapshot snapshot) {
        List<String> available = new ArrayList<>();
        List<String> limited = new ArrayList<>();
        addCapability(context, available, limited, snapshot.canInstallExistingPackages,
                R.string.privilege_health_capability_feature_install);
        addCapability(context, available, limited, snapshot.canModifyAppOps,
                R.string.privilege_health_capability_feature_app_ops);
        addCapability(context, available, limited, snapshot.canModifyRuntimePermissions,
                R.string.privilege_health_capability_feature_runtime_permissions);
        addCapability(context, available, limited, snapshot.canFreezeUnfreezePackages,
                R.string.privilege_health_capability_feature_freeze);
        addCapability(context, available, limited, snapshot.canForceStopPackages,
                R.string.privilege_health_capability_feature_force_stop);
        addCapability(context, available, limited, snapshot.canClearAppData,
                R.string.privilege_health_capability_feature_clear_data);
        addCapability(context, available, limited, snapshot.canClearAppCache,
                R.string.privilege_health_capability_feature_clear_cache);
        addCapability(context, available, limited, snapshot.canReadPrivateAppData,
                R.string.privilege_health_capability_feature_private_data);
        addCapability(context, available, limited, snapshot.canWriteIfwRules,
                R.string.privilege_health_capability_feature_ifw_rules);
        return context.getString(R.string.privilege_health_capability_summary,
                joinOrNone(context, available),
                joinOrNone(context, limited),
                getNextStep(context, snapshot));
    }

    private static void addCapability(@NonNull Context context,
                                      @NonNull List<String> available,
                                      @NonNull List<String> limited,
                                      boolean enabled,
                                      int labelRes) {
        if (enabled) {
            available.add(context.getString(labelRes));
        } else {
            limited.add(context.getString(labelRes));
        }
    }

    @NonNull
    private static String joinOrNone(@NonNull Context context, @NonNull List<String> values) {
        return values.isEmpty() ? context.getString(R.string.none) : android.text.TextUtils.join(", ", values);
    }

    @NonNull
    private static String getNextStep(@NonNull Context context, @NonNull Snapshot snapshot) {
        if (snapshot.isFullPower()) {
            return context.getString(R.string.privilege_health_capability_next_full_power);
        }
        if (!snapshot.canReadPrivateAppData || !snapshot.canWriteIfwRules) {
            return context.getString(R.string.privilege_health_capability_next_root);
        }
        return context.getString(R.string.privilege_health_capability_next_reconnect);
    }

    static final class Snapshot {
        final boolean canInstallExistingPackages;
        final boolean canModifyAppOps;
        final boolean canModifyRuntimePermissions;
        final boolean canFreezeUnfreezePackages;
        final boolean canForceStopPackages;
        final boolean canClearAppData;
        final boolean canClearAppCache;
        final boolean canReadPrivateAppData;
        final boolean canWriteIfwRules;

        Snapshot(boolean canInstallExistingPackages,
                 boolean canModifyAppOps,
                 boolean canModifyRuntimePermissions,
                 boolean canFreezeUnfreezePackages,
                 boolean canForceStopPackages,
                 boolean canClearAppData,
                 boolean canClearAppCache,
                 boolean canReadPrivateAppData,
                 boolean canWriteIfwRules) {
            this.canInstallExistingPackages = canInstallExistingPackages;
            this.canModifyAppOps = canModifyAppOps;
            this.canModifyRuntimePermissions = canModifyRuntimePermissions;
            this.canFreezeUnfreezePackages = canFreezeUnfreezePackages;
            this.canForceStopPackages = canForceStopPackages;
            this.canClearAppData = canClearAppData;
            this.canClearAppCache = canClearAppCache;
            this.canReadPrivateAppData = canReadPrivateAppData;
            this.canWriteIfwRules = canWriteIfwRules;
        }

        boolean isFullPower() {
            return canInstallExistingPackages
                    && canModifyAppOps
                    && canModifyRuntimePermissions
                    && canFreezeUnfreezePackages
                    && canForceStopPackages
                    && canClearAppData
                    && canClearAppCache
                    && canReadPrivateAppData
                    && canWriteIfwRules;
        }
    }
}
