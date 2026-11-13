// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserHandleHidden;
import android.provider.Settings;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.IntentUtils;

/**
 * Special app access the Permission Inspector lists next to the runtime permission groups.
 * These aren't ordinary permissions: some are app ops, some are system lists (battery,
 * data saver, enabled services), so each entry has its own probe that knows how to read the
 * state, whether App Manager can change it directly with its current access, and which
 * Settings pages to open when it can't.
 */
public final class SpecialAccessCatalog {
    private static final String TAG = SpecialAccessCatalog.class.getSimpleName();

    /** {@code ActivityInfo.FLAG_SUPPORTS_PICTURE_IN_PICTURE}, hidden from apps. */
    static final int FLAG_SUPPORTS_PICTURE_IN_PICTURE = 0x400000;
    /** App op codes are stable across Android versions. */
    static final int OP_ACTIVATE_VPN = 47;
    static final int OP_PICTURE_IN_PICTURE = 67;

    private SpecialAccessCatalog() {
    }

    public static final class Entry {
        @NonNull
        public final String id;
        @StringRes
        public final int labelRes;
        @StringRes
        public final int summaryRes;
        @StringRes
        public final int sectionRes;
        @DrawableRes
        public final int iconRes;
        @StringRes
        public final int grantedRes;
        @StringRes
        public final int deniedRes;
        public final int minSdk;
        @NonNull
        final Probe probe;

        Entry(@NonNull String id, @StringRes int labelRes, @StringRes int summaryRes, @StringRes int sectionRes,
              @DrawableRes int iconRes, @StringRes int grantedRes, @StringRes int deniedRes, int minSdk,
              @NonNull Probe probe) {
            this.id = id;
            this.labelRes = labelRes;
            this.summaryRes = summaryRes;
            this.sectionRes = sectionRes;
            this.iconRes = iconRes;
            this.grantedRes = grantedRes;
            this.deniedRes = deniedRes;
            this.minSdk = minSdk;
            this.probe = probe;
        }

        /** Extra {@link PackageManager} flags the probe needs beyond the permissions. */
        int packageFlags() {
            return probe.packageFlags();
        }

        boolean appliesTo(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return pi.applicationInfo != null && probe.appliesTo(pi, s);
        }

        @SpecialAccess.Status
        int status(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            try {
                return probe.status(pi, s);
            } catch (Exception e) {
                Log.w(TAG, "Could not read %s for %s.", e, id, pi.packageName);
                return SpecialAccess.UNKNOWN;
            }
        }

        boolean canChange(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return probe.canChange(pi, s);
        }

        @WorkerThread
        boolean set(@NonNull PackageInfo pi, boolean allow, @NonNull SpecialAccessSnapshot s) {
            try {
                return probe.set(pi, allow, s);
            } catch (Exception e) {
                Log.w(TAG, "Could not change %s for %s.", e, id, pi.packageName);
                return false;
            }
        }

        /** Settings pages to try, best first. The app's info page is always last. */
        @NonNull
        List<Intent> settings(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            List<Intent> intents = new ArrayList<>(probe.settings(pi, s));
            intents.add(IntentUtils.getAppDetailsSettings(pi.packageName));
            return intents;
        }
    }

    private static List<Entry> sAll;

    /** Every entry, whatever this device's Android version. */
    @NonNull
    static synchronized List<Entry> all() {
        if (sAll != null) return sAll;
        List<Entry> e = new ArrayList<>();
        int display = R.string.perm_section_display;
        e.add(new Entry("overlay", R.string.special_access_overlay, R.string.special_access_overlay_summary,
                display, R.drawable.ic_view_agenda, R.string.perm_app_status_allowed,
                R.string.perm_app_status_not_allowed, Build.VERSION_CODES.M,
                new AppOpPermissionProbe(Manifest.permission.SYSTEM_ALERT_WINDOW,
                        "android.settings.action.MANAGE_OVERLAY_PERMISSION")));
        e.add(new Entry("write_settings", R.string.special_access_write_settings,
                R.string.special_access_write_settings_summary, display, R.drawable.ic_tune,
                R.string.perm_app_status_allowed, R.string.perm_app_status_not_allowed, Build.VERSION_CODES.M,
                new AppOpPermissionProbe(Manifest.permission.WRITE_SETTINGS,
                        "android.settings.action.MANAGE_WRITE_SETTINGS")));
        e.add(new Entry("pip", R.string.special_access_pip, R.string.special_access_pip_summary, display,
                R.drawable.ic_tab, R.string.perm_app_status_allowed, R.string.perm_app_status_not_allowed,
                Build.VERSION_CODES.O, new PictureInPictureProbe()));
        e.add(new Entry("full_screen_intent", R.string.special_access_full_screen,
                R.string.special_access_full_screen_summary, display, R.drawable.ic_presentation,
                R.string.perm_app_status_allowed, R.string.perm_app_status_not_allowed,
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new AppOpPermissionProbe("android.permission.USE_FULL_SCREEN_INTENT",
                        "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT")));
        e.add(new Entry("accessibility", R.string.special_access_accessibility,
                R.string.special_access_accessibility_summary, display, R.drawable.ic_touch_app,
                R.string.on, R.string.off, Build.VERSION_CODES.LOLLIPOP,
                new AccessibilityProbe()));
        e.add(new Entry("notification_listener", R.string.notification_filter_access_title,
                R.string.special_access_notification_listener_summary, display, R.drawable.ic_eye,
                R.string.perm_app_status_allowed, R.string.perm_app_status_not_allowed,
                Build.VERSION_CODES.LOLLIPOP, new NotificationListenerProbe()));

        int background = R.string.perm_section_background;
        e.add(new Entry("battery_optimization", R.string.battery_optimization,
                R.string.special_access_battery_summary, background, R.drawable.ic_battery_plus,
                R.string.perm_app_status_not_optimized, R.string.pref_backup_schedule_battery_optimized,
                Build.VERSION_CODES.M, new BatteryOptimizationProbe()));
        e.add(new Entry("background_activity", R.string.special_access_background,
                R.string.special_access_background_summary, background, R.drawable.ic_autorenew,
                R.string.perm_app_status_allowed, R.string.perm_app_status_restricted,
                Build.VERSION_CODES.O, new BackgroundActivityProbe()));
        e.add(new Entry("exact_alarms", R.string.special_access_exact_alarms,
                R.string.special_access_exact_alarms_summary, background, R.drawable.ic_history,
                R.string.perm_app_status_allowed, R.string.perm_app_status_not_allowed, Build.VERSION_CODES.S,
                new ExactAlarmProbe()));

        int network = R.string.perm_section_network;
        e.add(new Entry("unrestricted_data", R.string.special_access_unrestricted_data,
                R.string.special_access_unrestricted_data_summary, network, R.drawable.ic_data_usage,
                R.string.perm_app_status_allowed, R.string.perm_app_status_not_allowed, Build.VERSION_CODES.N,
                new NetworkPolicyProbe(true)));
        e.add(new Entry("background_data", R.string.special_access_background_data,
                R.string.special_access_background_data_summary, network, R.drawable.ic_sync,
                R.string.perm_app_status_allowed, R.string.perm_app_status_blocked, Build.VERSION_CODES.N,
                new NetworkPolicyProbe(false)));

        int files = R.string.perm_section_files;
        e.add(new Entry("install_unknown", R.string.special_access_install_unknown,
                R.string.special_access_install_unknown_summary, files, R.drawable.ic_get_app,
                R.string.perm_app_status_allowed, R.string.perm_app_status_not_allowed, Build.VERSION_CODES.O,
                new AppOpPermissionProbe("android.permission.REQUEST_INSTALL_PACKAGES",
                        "android.settings.MANAGE_UNKNOWN_APP_SOURCES")));
        e.add(new Entry("all_files", R.string.special_access_all_files, R.string.special_access_all_files_summary,
                files, R.drawable.ic_file_document_multiple, R.string.perm_app_status_allowed,
                R.string.perm_app_status_not_allowed, Build.VERSION_CODES.R,
                new AppOpPermissionProbe("android.permission.MANAGE_EXTERNAL_STORAGE",
                        "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION")));
        e.add(new Entry("manage_media", R.string.special_access_manage_media,
                R.string.special_access_manage_media_summary, files, R.drawable.ic_image,
                R.string.perm_app_status_allowed, R.string.perm_app_status_not_allowed, Build.VERSION_CODES.S,
                new AppOpPermissionProbe("android.permission.MANAGE_MEDIA",
                        "android.settings.REQUEST_MANAGE_MEDIA")));
        e.add(new Entry("usage_access", R.string.usage_access, R.string.special_access_usage_summary,
                files, R.drawable.ic_list_status, R.string.perm_app_status_allowed,
                R.string.perm_app_status_not_allowed, Build.VERSION_CODES.LOLLIPOP,
                // Settings denies usage access with "ignore", not "error"
                new AppOpPermissionProbe("android.permission.PACKAGE_USAGE_STATS",
                        Settings.ACTION_USAGE_ACCESS_SETTINGS, AppOpsManager.MODE_IGNORED)));

        int system = R.string.perm_section_system;
        e.add(new Entry("dnd_access", R.string.special_access_dnd, R.string.special_access_dnd_summary, system,
                R.drawable.ic_notifications_paused, R.string.perm_app_status_allowed,
                R.string.perm_app_status_not_allowed, Build.VERSION_CODES.M, new DndAccessProbe()));
        e.add(new Entry("device_admin", R.string.special_access_device_admin,
                R.string.special_access_device_admin_summary, system, R.drawable.ic_security,
                R.string.perm_app_status_active, R.string.perm_app_status_not_active,
                Build.VERSION_CODES.LOLLIPOP, new DeviceAdminProbe()));
        e.add(new Entry("vpn", R.string.special_access_vpn, R.string.special_access_vpn_summary, system,
                R.drawable.ic_security_network, R.string.perm_app_status_allowed,
                R.string.perm_app_status_not_allowed, Build.VERSION_CODES.LOLLIPOP, new VpnProbe()));
        sAll = Collections.unmodifiableList(e);
        return sAll;
    }

    /** The entries this Android version supports, in display order. */
    @NonNull
    public static List<Entry> available(int sdk) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : all()) {
            if (sdk >= entry.minSdk) out.add(entry);
        }
        return out;
    }

    @NonNull
    public static List<Entry> available() {
        return available(Build.VERSION.SDK_INT);
    }

    @Nullable
    public static Entry findById(@Nullable String id) {
        if (id == null) return null;
        for (Entry entry : available()) {
            if (entry.id.equals(id)) return entry;
        }
        return null;
    }

    /** {@link PackageManager} flags that cover every available entry. */
    static int packageFlags(@NonNull List<Entry> entries) {
        int flags = PackageManager.GET_PERMISSIONS;
        for (Entry entry : entries) flags |= entry.packageFlags();
        return flags;
    }

    // Probes

    abstract static class Probe {
        int packageFlags() {
            return 0;
        }

        abstract boolean appliesTo(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s);

        @SpecialAccess.Status
        abstract int status(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) throws Exception;

        boolean canChange(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return false;
        }

        @WorkerThread
        boolean set(@NonNull PackageInfo pi, boolean allow, @NonNull SpecialAccessSnapshot s) throws Exception {
            return false;
        }

        @NonNull
        abstract List<Intent> settings(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s);
    }

    /** Access behind an app op that has its own permission, like display over other apps. */
    static class AppOpPermissionProbe extends Probe {
        @NonNull
        final String permission;
        @NonNull
        final String settingsAction;
        final int denyMode;

        AppOpPermissionProbe(@NonNull String permission, @NonNull String settingsAction) {
            this(permission, settingsAction, AppOpsManager.MODE_ERRORED);
        }

        AppOpPermissionProbe(@NonNull String permission, @NonNull String settingsAction, int denyMode) {
            this.permission = permission;
            this.settingsAction = settingsAction;
            this.denyMode = denyMode;
        }

        int op() {
            return AppOpsManagerCompat.permissionToOpCode(permission);
        }

        @Override
        boolean appliesTo(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return requests(pi, permission);
        }

        @Override
        int status(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) throws Exception {
            int op = op();
            if (op == AppOpsManagerCompat.OP_NONE) return SpecialAccess.UNKNOWN;
            int mode = s.appOps.checkOperation(op, pi.applicationInfo.uid, pi.packageName);
            return SpecialAccess.fromAppOpMode(mode, isGranted(pi, permission));
        }

        @Override
        boolean canChange(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return s.canModifyAppOps && op() != AppOpsManagerCompat.OP_NONE;
        }

        @Override
        boolean set(@NonNull PackageInfo pi, boolean allow, @NonNull SpecialAccessSnapshot s) throws Exception {
            s.appOps.setMode(op(), pi.applicationInfo.uid, pi.packageName,
                    allow ? AppOpsManager.MODE_ALLOWED : denyMode);
            return true;
        }

        @NonNull
        @Override
        List<Intent> settings(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            List<Intent> intents = new ArrayList<>(2);
            intents.add(IntentUtils.getSettings(settingsAction, pi.packageName));
            intents.add(IntentUtils.getSettings(settingsAction, null));
            return intents;
        }
    }

    /**
     * Alarms and reminders. An app holding USE_EXACT_ALARM always has it, so only
     * SCHEDULE_EXACT_ALARM can be switched.
     */
    static final class ExactAlarmProbe extends AppOpPermissionProbe {
        static final String USE_EXACT_ALARM = "android.permission.USE_EXACT_ALARM";

        ExactAlarmProbe() {
            super("android.permission.SCHEDULE_EXACT_ALARM", "android.settings.REQUEST_SCHEDULE_EXACT_ALARM");
        }

        @Override
        boolean appliesTo(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return requests(pi, permission) || requests(pi, USE_EXACT_ALARM);
        }

        @Override
        int status(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) throws Exception {
            if (isGranted(pi, USE_EXACT_ALARM)) return SpecialAccess.GRANTED;
            if (!requests(pi, permission)) return SpecialAccess.DENIED;
            return super.status(pi, s);
        }

        @Override
        boolean canChange(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return !isGranted(pi, USE_EXACT_ALARM) && requests(pi, permission) && super.canChange(pi, s);
        }
    }

    static final class PictureInPictureProbe extends Probe {
        @Override
        int packageFlags() {
            return PackageManager.GET_ACTIVITIES;
        }

        @Override
        boolean appliesTo(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            if (pi.activities == null) return false;
            for (ActivityInfo activity : pi.activities) {
                if ((activity.flags & FLAG_SUPPORTS_PICTURE_IN_PICTURE) != 0) return true;
            }
            return false;
        }

        @Override
        int status(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) throws Exception {
            // Allowed unless someone turned it off
            return SpecialAccess.fromAppOpMode(s.appOps.checkOperation(OP_PICTURE_IN_PICTURE,
                    pi.applicationInfo.uid, pi.packageName), true);
        }

        @Override
        boolean canChange(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return s.canModifyAppOps;
        }

        @Override
        boolean set(@NonNull PackageInfo pi, boolean allow, @NonNull SpecialAccessSnapshot s) throws Exception {
            s.appOps.setMode(OP_PICTURE_IN_PICTURE, pi.applicationInfo.uid, pi.packageName,
                    allow ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_ERRORED);
            return true;
        }

        @NonNull
        @Override
        List<Intent> settings(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            List<Intent> intents = new ArrayList<>(2);
            intents.add(IntentUtils.getSettings("android.settings.PICTURE_IN_PICTURE_SETTINGS", pi.packageName));
            intents.add(IntentUtils.getSettings("android.settings.PICTURE_IN_PICTURE_SETTINGS", null));
            return intents;
        }
    }

    /**
     * Whether Android may restrict the app in the background (the Restricted battery setting on
     * Android 12 and later).
     */
    static final class BackgroundActivityProbe extends Probe {
        static int op() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return AppOpsManagerCompat.OP_RUN_ANY_IN_BACKGROUND;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return AppOpsManagerCompat.OP_RUN_IN_BACKGROUND;
            return AppOpsManagerCompat.OP_NONE;
        }

        @Override
        boolean appliesTo(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            if (!UserHandleHidden.isApp(pi.applicationInfo.uid)) return false;
            // System apps only when someone restricted them, or the list drowns in them
            return !isSystem(pi) || safeStatus(this, pi, s) == SpecialAccess.DENIED;
        }

        @Override
        int status(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) throws Exception {
            return SpecialAccess.fromBackgroundMode(s.appOps.checkOperation(op(), pi.applicationInfo.uid,
                    pi.packageName));
        }

        @Override
        boolean canChange(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return s.canModifyAppOps;
        }

        @Override
        boolean set(@NonNull PackageInfo pi, boolean allow, @NonNull SpecialAccessSnapshot s) throws Exception {
            s.appOps.setMode(op(), pi.applicationInfo.uid, pi.packageName,
                    allow ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_IGNORED);
            return true;
        }

        @NonNull
        @Override
        List<Intent> settings(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return Collections.singletonList(IntentUtils.getBatteryOptSettings(pi.packageName));
            }
            return Collections.emptyList();
        }
    }

    /** Whether the app is exempt from battery optimization. */
    static final class BatteryOptimizationProbe extends Probe {
        @Override
        boolean appliesTo(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            if (!UserHandleHidden.isApp(pi.applicationInfo.uid)) return false;
            return !isSystem(pi) || safeStatus(this, pi, s) == SpecialAccess.GRANTED;
        }

        @Override
        int status(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return DeviceIdleManagerCompat.isBatteryOptimizedApp(pi.packageName)
                    ? SpecialAccess.DENIED : SpecialAccess.GRANTED;
        }

        @Override
        boolean canChange(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return s.canChangeBatteryOptimization;
        }

        @Override
        boolean set(@NonNull PackageInfo pi, boolean allow, @NonNull SpecialAccessSnapshot s) {
            return allow ? DeviceIdleManagerCompat.disableBatteryOptimization(pi.packageName)
                    : DeviceIdleManagerCompat.enableBatteryOptimization(pi.packageName);
        }

        @NonNull
        @Override
        List<Intent> settings(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            List<Intent> intents = new ArrayList<>(2);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intents.add(IntentUtils.getBatteryOptSettings(pi.packageName));
            }
            intents.add(IntentUtils.getSettings("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS", null));
            return intents;
        }
    }

    /** Data Saver and background data, both stored in the app's network policy. */
    static final class NetworkPolicyProbe extends Probe {
        final boolean unrestricted;

        NetworkPolicyProbe(boolean unrestricted) {
            this.unrestricted = unrestricted;
        }

        @Override
        boolean appliesTo(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return UserHandleHidden.isApp(pi.applicationInfo.uid) && requests(pi, Manifest.permission.INTERNET);
        }

        @Override
        int status(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            // Android only shows the policy to apps that may manage it
            if (!s.canUseNetworkPolicy) return SpecialAccess.UNKNOWN;
            int policy = NetworkPolicyManagerCompat.getUidPolicy(pi.applicationInfo.uid);
            return unrestricted ? SpecialAccess.unrestrictedDataStatus(policy)
                    : SpecialAccess.backgroundDataStatus(policy);
        }

        @Override
        boolean canChange(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return s.canUseNetworkPolicy;
        }

        @Override
        boolean set(@NonNull PackageInfo pi, boolean allow, @NonNull SpecialAccessSnapshot s) {
            int uid = pi.applicationInfo.uid;
            int policy = NetworkPolicyManagerCompat.getUidPolicy(uid);
            NetworkPolicyManagerCompat.setUidPolicy(uid, unrestricted
                    ? SpecialAccess.withUnrestrictedData(policy, allow)
                    : SpecialAccess.withBackgroundData(policy, allow));
            return true;
        }

        @NonNull
        @Override
        List<Intent> settings(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return Collections.emptyList();
            return Collections.singletonList(IntentUtils.getNetPolicySettings(pi.packageName));
        }
    }

    static final class AccessibilityProbe extends Probe {
        @Override
        boolean appliesTo(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return s.accessibilityServices.containsKey(pi.packageName);
        }

        @Override
        int status(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return s.enabledAccessibility.contains(pi.packageName) ? SpecialAccess.GRANTED : SpecialAccess.DENIED;
        }

        @NonNull
        @Override
        List<Intent> settings(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return Collections.singletonList(IntentUtils.getSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS, null));
        }
    }

    static final class NotificationListenerProbe extends Probe {
        @Override
        boolean appliesTo(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return s.notificationListeners.containsKey(pi.packageName);
        }

        @Override
        int status(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return s.enabledListeners.contains(pi.packageName) ? SpecialAccess.GRANTED : SpecialAccess.DENIED;
        }

        @NonNull
        @Override
        List<Intent> settings(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            List<Intent> intents = new ArrayList<>(2);
            List<ComponentName> listeners = s.notificationListeners.get(pi.packageName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && listeners != null && !listeners.isEmpty()) {
                intents.add(IntentUtils.getSettings("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS", null)
                        .putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME",
                                listeners.get(0).flattenToString()));
            }
            intents.add(IntentUtils.getSettings("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS", null));
            return intents;
        }
    }

    static final class DndAccessProbe extends Probe {
        @Override
        boolean appliesTo(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return requests(pi, "android.permission.ACCESS_NOTIFICATION_POLICY");
        }

        @Override
        int status(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) throws Exception {
            // Notification access comes with Do Not Disturb access
            if (s.enabledListeners.contains(pi.packageName)) return SpecialAccess.GRANTED;
            if (s.notificationPolicy != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return s.notificationPolicy.isNotificationPolicyAccessGrantedForPackage(pi.packageName)
                        ? SpecialAccess.GRANTED : SpecialAccess.DENIED;
            }
            if (s.dndAccess == null) return SpecialAccess.UNKNOWN;
            return s.dndAccess.contains(pi.packageName) ? SpecialAccess.GRANTED : SpecialAccess.DENIED;
        }

        @Override
        boolean canChange(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            // A notification listener keeps Do Not Disturb access until notification access goes
            return s.notificationPolicy != null && !s.enabledListeners.contains(pi.packageName);
        }

        @WorkerThread
        @Override
        boolean set(@NonNull PackageInfo pi, boolean allow, @NonNull SpecialAccessSnapshot s) throws Exception {
            if (s.notificationPolicy == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
            s.notificationPolicy.setNotificationPolicyAccessGranted(pi.packageName, allow);
            return true;
        }

        @NonNull
        @Override
        List<Intent> settings(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            List<Intent> intents = new ArrayList<>(2);
            intents.add(IntentUtils.getSettings("android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS",
                    pi.packageName));
            intents.add(IntentUtils.getSettings("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS", null));
            return intents;
        }
    }

    static final class DeviceAdminProbe extends Probe {
        @Override
        boolean appliesTo(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return s.deviceAdmins.containsKey(pi.packageName);
        }

        @Override
        int status(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return s.activeAdmins.contains(pi.packageName) ? SpecialAccess.GRANTED : SpecialAccess.DENIED;
        }

        @NonNull
        @Override
        List<Intent> settings(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            List<Intent> intents = new ArrayList<>(2);
            intents.add(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("com.android.settings",
                    "com.android.settings.Settings$DeviceAdminSettingsActivity")));
            intents.add(IntentUtils.getSettings(Settings.ACTION_SECURITY_SETTINGS, null));
            return intents;
        }
    }

    /** VPN apps, granted once the user has allowed them to set up a connection. */
    static final class VpnProbe extends Probe {
        @Override
        boolean appliesTo(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return s.vpnServices.containsKey(pi.packageName);
        }

        @Override
        int status(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) throws Exception {
            return SpecialAccess.fromAppOpMode(s.appOps.checkOperation(OP_ACTIVATE_VPN, pi.applicationInfo.uid,
                    pi.packageName), false);
        }

        @NonNull
        @Override
        List<Intent> settings(@NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
            return Collections.singletonList(IntentUtils.getSettings("android.settings.VPN_SETTINGS", null));
        }
    }

    // Helpers

    static boolean requests(@NonNull PackageInfo pi, @NonNull String permission) {
        return indexOf(pi, permission) >= 0;
    }

    static boolean isGranted(@NonNull PackageInfo pi, @NonNull String permission) {
        int i = indexOf(pi, permission);
        return i >= 0 && pi.requestedPermissionsFlags != null && i < pi.requestedPermissionsFlags.length
                && (pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
    }

    private static int indexOf(@NonNull PackageInfo pi, @NonNull String permission) {
        if (pi.requestedPermissions == null) return -1;
        for (int i = 0; i < pi.requestedPermissions.length; i++) {
            if (permission.equals(pi.requestedPermissions[i])) return i;
        }
        return -1;
    }

    static boolean isSystem(@NonNull PackageInfo pi) {
        return (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    @SpecialAccess.Status
    private static int safeStatus(@NonNull Probe probe, @NonNull PackageInfo pi, @NonNull SpecialAccessSnapshot s) {
        try {
            return probe.status(pi, s);
        } catch (Exception e) {
            return SpecialAccess.UNKNOWN;
        }
    }
}
