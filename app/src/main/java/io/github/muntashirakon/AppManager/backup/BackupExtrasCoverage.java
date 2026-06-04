// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.magisk.MagiskDenyList;
import io.github.muntashirakon.AppManager.magisk.MagiskHide;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * Documents and audits the rule subtypes stored in the backup "Extras" file.
 */
public final class BackupExtrasCoverage {
    public static final int MAX_AUDIT_WARNINGS = 64;

    private BackupExtrasCoverage() {
    }

    @NonNull
    public static RestoreCapabilities fromCurrentMode(boolean probeMagiskTools) {
        boolean canWriteSystemStores = Ops.isWorkingUidRoot() || Ops.isSystem() || SelfPermissions.canWriteToDataData();
        boolean canRestoreMagisk = Ops.isWorkingUidRoot();
        if (probeMagiskTools && canRestoreMagisk) {
            canRestoreMagisk = MagiskHide.available() || MagiskDenyList.available();
        }
        return new RestoreCapabilities(
                SelfPermissions.canModifyPermissions(),
                SelfPermissions.canModifyAppOpMode(),
                SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY),
                SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.DEVICE_POWER),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
                        && SelfPermissions.checkNotificationListenerAccess(),
                canWriteSystemStores,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && canWriteSystemStores,
                canRestoreMagisk);
    }

    @NonNull
    public static List<ExtraItem> getKnownExtras() {
        ArrayList<ExtraItem> items = new ArrayList<>();
        items.add(new ExtraItem(RuleType.PERMISSION, R.string.backup_extras_item_permissions));
        items.add(new ExtraItem(RuleType.APP_OP, R.string.backup_extras_item_app_ops));
        items.add(new ExtraItem(RuleType.NET_POLICY, R.string.backup_extras_item_network_policy));
        items.add(new ExtraItem(RuleType.BATTERY_OPT, R.string.backup_extras_item_battery));
        items.add(new ExtraItem(RuleType.MAGISK_HIDE, R.string.backup_extras_item_magisk));
        items.add(new ExtraItem(RuleType.NOTIFICATION, R.string.backup_extras_item_notifications));
        items.add(new ExtraItem(RuleType.URI_GRANT, R.string.backup_extras_item_uri_grants));
        items.add(new ExtraItem(RuleType.SSAID, R.string.backup_extras_item_ssaid));
        items.add(new ExtraItem(RuleType.FREEZE, R.string.backup_extras_item_freeze));
        return Collections.unmodifiableList(items);
    }

    @NonNull
    public static CharSequence formatDialogDetails(@NonNull Context context,
                                                   @NonNull RestoreCapabilities capabilities) {
        ArrayList<String> included = new ArrayList<>();
        ArrayList<String> restorable = new ArrayList<>();
        ArrayList<String> skipped = new ArrayList<>();
        for (ExtraItem item : getKnownExtras()) {
            String label = context.getString(item.labelRes);
            included.add(label);
            int skipReason = getSkipReason(item.ruleType, capabilities);
            if (skipReason == 0) {
                restorable.add(label);
            } else {
                skipped.add(context.getString(R.string.backup_extras_skip_dialog_item,
                        label, context.getString(skipReason)));
            }
        }
        String none = context.getString(R.string.none);
        return context.getString(R.string.backup_extras_includes, TextUtils.join(", ", included))
                + "\n" + context.getString(R.string.backup_extras_can_restore_now,
                restorable.isEmpty() ? none : TextUtils.join(", ", restorable))
                + "\n" + context.getString(R.string.backup_extras_will_skip_now,
                skipped.isEmpty() ? none : TextUtils.join("; ", skipped));
    }

    @StringRes
    public static int getSkipReason(@NonNull RuleType ruleType,
                                    @NonNull RestoreCapabilities capabilities) {
        switch (ruleType) {
            case PERMISSION:
                return capabilities.canRestorePermissions ? 0
                        : R.string.backup_extras_skip_runtime_permissions;
            case APP_OP:
                return capabilities.canRestoreAppOps ? 0
                        : R.string.backup_extras_skip_app_ops;
            case NET_POLICY:
                return capabilities.canRestoreNetworkPolicy ? 0
                        : R.string.backup_extras_skip_network_policy;
            case BATTERY_OPT:
                return capabilities.canRestoreBatteryOptimization ? 0
                        : R.string.backup_extras_skip_battery;
            case MAGISK_HIDE:
            case MAGISK_DENY_LIST:
                return capabilities.canRestoreMagisk ? 0
                        : R.string.backup_extras_skip_magisk;
            case NOTIFICATION:
                return capabilities.canRestoreNotificationListeners ? 0
                        : R.string.backup_extras_skip_notifications;
            case URI_GRANT:
                return capabilities.canRestoreUriGrants ? 0
                        : R.string.backup_extras_skip_uri_grants;
            case SSAID:
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    return R.string.backup_extras_skip_ssaid_android_version;
                }
                return capabilities.canRestoreSsaid ? 0
                        : R.string.backup_extras_skip_ssaid_store;
            case FREEZE:
                return 0;
            default:
                return R.string.backup_extras_skip_unknown_type;
        }
    }

    @NonNull
    public static String formatAuditWarning(@NonNull RuleEntry entry,
                                            @NonNull RestoreCapabilities capabilities) {
        int reason = getSkipReason(entry.type, capabilities);
        String detail = reason != 0 ? getAuditReason(reason) : "unsupported";
        return formatAuditWarning(entry, detail);
    }

    @NonNull
    public static String formatAuditWarning(@NonNull RuleEntry entry, @Nullable Throwable error) {
        String message = error != null ? error.getMessage() : null;
        return formatAuditWarning(entry, message != null && !message.isEmpty()
                ? "failed: " + abbreviate(message, 120)
                : "failed during restore");
    }

    @NonNull
    public static String formatAuditWarning(@NonNull RuleEntry entry, @NonNull String reason) {
        String name = RuleEntry.STUB.equals(entry.name) ? "" : " " + entry.name;
        return String.format(Locale.ROOT, "%s%s: %s", entry.type.name(), name, reason);
    }

    @NonNull
    private static String getAuditReason(@StringRes int reason) {
        if (reason == R.string.backup_extras_skip_runtime_permissions) {
            return "runtime permission grant/revoke unavailable";
        }
        if (reason == R.string.backup_extras_skip_app_ops) {
            return "app-op mode control unavailable";
        }
        if (reason == R.string.backup_extras_skip_network_policy) {
            return "network policy control unavailable";
        }
        if (reason == R.string.backup_extras_skip_battery) {
            return "device power control unavailable";
        }
        if (reason == R.string.backup_extras_skip_magisk) {
            return "MagiskHide/DenyList unavailable";
        }
        if (reason == R.string.backup_extras_skip_notifications) {
            return "notification listener management unavailable";
        }
        if (reason == R.string.backup_extras_skip_uri_grants) {
            return "system URI grant store unavailable";
        }
        if (reason == R.string.backup_extras_skip_ssaid_android_version) {
            return "SSAID restore requires Android 8.0+";
        }
        if (reason == R.string.backup_extras_skip_ssaid_store) {
            return "SSAID settings store unavailable";
        }
        return "unsupported extra type";
    }

    @NonNull
    private static String abbreviate(@NonNull String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "...";
    }

    public static final class RestoreCapabilities {
        public final boolean canRestorePermissions;
        public final boolean canRestoreAppOps;
        public final boolean canRestoreNetworkPolicy;
        public final boolean canRestoreBatteryOptimization;
        public final boolean canRestoreNotificationListeners;
        public final boolean canRestoreUriGrants;
        public final boolean canRestoreSsaid;
        public final boolean canRestoreMagisk;

        public RestoreCapabilities(boolean canRestorePermissions,
                                   boolean canRestoreAppOps,
                                   boolean canRestoreNetworkPolicy,
                                   boolean canRestoreBatteryOptimization,
                                   boolean canRestoreNotificationListeners,
                                   boolean canRestoreUriGrants,
                                   boolean canRestoreSsaid,
                                   boolean canRestoreMagisk) {
            this.canRestorePermissions = canRestorePermissions;
            this.canRestoreAppOps = canRestoreAppOps;
            this.canRestoreNetworkPolicy = canRestoreNetworkPolicy;
            this.canRestoreBatteryOptimization = canRestoreBatteryOptimization;
            this.canRestoreNotificationListeners = canRestoreNotificationListeners;
            this.canRestoreUriGrants = canRestoreUriGrants;
            this.canRestoreSsaid = canRestoreSsaid;
            this.canRestoreMagisk = canRestoreMagisk;
        }
    }

    public static final class ExtraItem {
        @NonNull
        public final RuleType ruleType;
        @StringRes
        public final int labelRes;

        private ExtraItem(@NonNull RuleType ruleType, @StringRes int labelRes) {
            this.ruleType = ruleType;
            this.labelRes = labelRes;
        }
    }
}
