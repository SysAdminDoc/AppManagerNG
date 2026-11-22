// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.R;

/**
 * The words Settings uses for a permission, so a row can show "Music and audio" under
 * android.permission.READ_MEDIA_AUDIO. Each lookup asks the package manager, so call these off the main thread.
 */
public final class PermissionTexts {
    private static final String UNDEFINED_GROUP = "android.permission-group.UNDEFINED";

    /**
     * The special permissions, named the way Settings' Special app access page names them.
     */
    private static final Map<String, Integer> SPECIAL_LABELS = new HashMap<>();
    /**
     * What each special permission allows, for when Android describes it with nothing.
     */
    private static final Map<String, Integer> SPECIAL_DESCRIPTIONS = new HashMap<>();
    /**
     * The runtime permissions and the group Settings files each under. Since Android 10 the permission itself names
     * no group, and the mapping lives in the permission controller.
     */
    private static final Map<String, String> PLATFORM_GROUPS = new HashMap<>();

    static {
        special("android.permission.MANAGE_EXTERNAL_STORAGE", R.string.special_access_all_files,
                R.string.perm_desc_manage_external_storage);
        special("android.permission.SYSTEM_ALERT_WINDOW", R.string.special_access_overlay,
                R.string.perm_desc_system_alert_window);
        special("android.permission.REQUEST_INSTALL_PACKAGES", R.string.special_access_install_unknown,
                R.string.perm_desc_request_install_packages);
        special("android.permission.WRITE_SETTINGS", R.string.special_access_write_settings,
                R.string.perm_desc_write_settings);
        special("android.permission.PACKAGE_USAGE_STATS", R.string.usage_access,
                R.string.perm_desc_package_usage_stats);
        special("android.permission.BIND_ACCESSIBILITY_SERVICE", R.string.special_access_accessibility,
                R.string.perm_desc_bind_accessibility_service);
        special("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", R.string.notification_filter_access_title,
                R.string.perm_desc_bind_notification_listener_service);
        special("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", R.string.battery_optimization,
                R.string.perm_desc_request_ignore_battery_optimizations);
        special("android.permission.SCHEDULE_EXACT_ALARM", R.string.special_access_exact_alarms,
                R.string.perm_desc_schedule_exact_alarm);
        special("android.permission.USE_EXACT_ALARM", R.string.special_access_exact_alarms,
                R.string.perm_desc_use_exact_alarm);

        group("android.permission-group.CALENDAR", "READ_CALENDAR", "WRITE_CALENDAR");
        group("android.permission-group.CAMERA", "CAMERA");
        group("android.permission-group.CONTACTS", "READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS");
        group("android.permission-group.LOCATION", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
                "ACCESS_BACKGROUND_LOCATION");
        group("android.permission-group.MICROPHONE", "RECORD_AUDIO");
        group("android.permission-group.PHONE", "READ_PHONE_STATE", "READ_PHONE_NUMBERS", "CALL_PHONE",
                "ANSWER_PHONE_CALLS", "ADD_VOICEMAIL", "USE_SIP", "ACCEPT_HANDOVER");
        group("android.permission-group.CALL_LOG", "READ_CALL_LOG", "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS");
        group("android.permission-group.SENSORS", "BODY_SENSORS", "BODY_SENSORS_BACKGROUND");
        group("android.permission-group.ACTIVITY_RECOGNITION", "ACTIVITY_RECOGNITION");
        group("android.permission-group.SMS", "SEND_SMS", "RECEIVE_SMS", "READ_SMS", "RECEIVE_WAP_PUSH",
                "RECEIVE_MMS");
        group("android.permission-group.STORAGE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE");
        group("android.permission-group.READ_MEDIA_AURAL", "READ_MEDIA_AUDIO");
        group("android.permission-group.READ_MEDIA_VISUAL", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO",
                "READ_MEDIA_VISUAL_USER_SELECTED");
        group("android.permission-group.NEARBY_DEVICES", "BLUETOOTH_CONNECT", "BLUETOOTH_SCAN",
                "BLUETOOTH_ADVERTISE", "NEARBY_WIFI_DEVICES", "UWB_RANGING");
        group("android.permission-group.NOTIFICATIONS", "POST_NOTIFICATIONS");
    }

    private PermissionTexts() {
    }

    /**
     * The short name Settings shows: a special permission's page name, a runtime permission's group (in the
     * system's language), the group the permission names, or the permission's own label. {@code null} when there is
     * nothing better than the name the row already shows.
     */
    @Nullable
    @WorkerThread
    public static CharSequence shortLabel(@NonNull Context context, @NonNull PermissionInfo info) {
        Integer special = SPECIAL_LABELS.get(info.name);
        if (special != null) {
            return context.getString(special);
        }
        PackageManager pm = context.getPackageManager();
        CharSequence label = groupLabel(pm, PLATFORM_GROUPS.get(info.name));
        if (label == null && info.group != null && !UNDEFINED_GROUP.equals(info.group)) {
            label = groupLabel(pm, info.group);
        }
        if (label == null) {
            CharSequence own = info.loadLabel(pm);
            if (!TextUtils.isEmpty(own) && !TextUtils.equals(own, info.name)) {
                label = own;
            }
        }
        return label;
    }

    /**
     * The system's description of the permission, or ours for a special permission the system leaves undescribed.
     */
    @Nullable
    @WorkerThread
    public static CharSequence description(@NonNull Context context, @NonNull PermissionInfo info) {
        CharSequence description = info.loadDescription(context.getPackageManager());
        if (description != null && !TextUtils.isEmpty(description.toString().trim())) {
            return description;
        }
        Integer fallback = SPECIAL_DESCRIPTIONS.get(info.name);
        return fallback != null ? context.getString(fallback) : null;
    }

    @Nullable
    private static CharSequence groupLabel(@NonNull PackageManager pm, @Nullable String group) {
        if (group == null) {
            return null;
        }
        try {
            PermissionGroupInfo groupInfo = pm.getPermissionGroupInfo(group, 0);
            CharSequence label = groupInfo.loadLabel(pm);
            return TextUtils.isEmpty(label) || TextUtils.equals(label, group) ? null : label;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    private static void special(@NonNull String permission, @StringRes int label, @StringRes int description) {
        SPECIAL_LABELS.put(permission, label);
        SPECIAL_DESCRIPTIONS.put(permission, description);
    }

    private static void group(@NonNull String group, @NonNull String... permissions) {
        for (String permission : permissions) {
            PLATFORM_GROUPS.put("android.permission." + permission, group);
        }
    }
}
