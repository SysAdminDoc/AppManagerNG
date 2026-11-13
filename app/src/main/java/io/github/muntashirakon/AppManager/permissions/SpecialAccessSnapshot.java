// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.Manifest;
import android.app.INotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;

/**
 * System state the special app access entries read, gathered once per load so each app doesn't
 * cost a round of service queries.
 */
final class SpecialAccessSnapshot {
    private static final String TAG = SpecialAccessSnapshot.class.getSimpleName();

    static final String ACCESSIBILITY_SERVICE = "android.accessibilityservice.AccessibilityService";
    static final String NOTIFICATION_LISTENER_SERVICE = "android.service.notification.NotificationListenerService";
    static final String VPN_SERVICE = "android.net.VpnService";
    static final String DEVICE_ADMIN_ENABLED = "android.app.action.DEVICE_ADMIN_ENABLED";
    private static final String DND_ACCESS_PACKAGES = "enabled_notification_policy_access_packages";

    @NonNull
    final Context context;
    @NonNull
    final AppOpsManagerCompat appOps;
    final boolean canModifyAppOps;
    final boolean canChangeBatteryOptimization;
    final boolean canUseNetworkPolicy;
    /** Package name to its accessibility services. */
    @NonNull
    final Map<String, List<ComponentName>> accessibilityServices;
    @NonNull
    final Map<String, List<ComponentName>> notificationListeners;
    @NonNull
    final Map<String, List<ComponentName>> vpnServices;
    @NonNull
    final Map<String, List<ComponentName>> deviceAdmins;
    @NonNull
    final Set<String> enabledAccessibility;
    @NonNull
    final Set<String> enabledListeners;
    @NonNull
    final Set<String> activeAdmins;
    /**
     * Packages with Do Not Disturb access according to the secure setting, or {@code null} when
     * that setting can't be trusted. See {@link #dndAccessFromSetting(int, String)}.
     */
    @Nullable
    final Set<String> dndAccess;
    /**
     * Asks the notification service itself, which needs root, system or the status bar
     * permission (ADB has it from Android 12). {@code null} without that access.
     */
    @Nullable
    final INotificationManager notificationPolicy;

    private SpecialAccessSnapshot(@NonNull Context context, @NonNull AppOpsManagerCompat appOps) {
        this.context = context;
        this.appOps = appOps;
        canModifyAppOps = SelfPermissions.canModifyAppOpMode();
        canChangeBatteryOptimization = SelfPermissions.checkSelfOrRemotePermission(
                ManifestCompat.permission.DEVICE_POWER);
        canUseNetworkPolicy = SelfPermissions.checkSelfOrRemotePermission(
                ManifestCompat.permission.MANAGE_NETWORK_POLICY);
        PackageManager pm = context.getPackageManager();
        accessibilityServices = services(pm, ACCESSIBILITY_SERVICE, Manifest.permission.BIND_ACCESSIBILITY_SERVICE);
        notificationListeners = services(pm, NOTIFICATION_LISTENER_SERVICE,
                Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE);
        vpnServices = services(pm, VPN_SERVICE, Manifest.permission.BIND_VPN_SERVICE);
        deviceAdmins = receivers(pm, DEVICE_ADMIN_ENABLED, Manifest.permission.BIND_DEVICE_ADMIN);
        ContentResolver resolver = context.getContentResolver();
        enabledAccessibility = packagesOf(readSecure(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
        enabledListeners = enabledListeners(context);
        activeAdmins = activeAdmins(context);
        dndAccess = dndAccess(resolver);
        notificationPolicy = notificationPolicy();
    }

    @WorkerThread
    @NonNull
    static SpecialAccessSnapshot take(@NonNull Context context, @NonNull AppOpsManagerCompat appOps) {
        return new SpecialAccessSnapshot(context, appOps);
    }

    @NonNull
    private static Map<String, List<ComponentName>> services(@NonNull PackageManager pm, @NonNull String action,
                                                             @NonNull String bindPermission) {
        try {
            return byPackage(pm.queryIntentServices(new Intent(action), 0), bindPermission);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not list services for %s.", e, action);
            return Collections.emptyMap();
        }
    }

    @NonNull
    private static Map<String, List<ComponentName>> receivers(@NonNull PackageManager pm, @NonNull String action,
                                                              @NonNull String bindPermission) {
        try {
            return byPackage(pm.queryBroadcastReceivers(new Intent(action), 0), bindPermission);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not list receivers for %s.", e, action);
            return Collections.emptyMap();
        }
    }

    /**
     * Android only binds a service or admin receiver that is guarded by the matching permission,
     * so components without it don't count.
     */
    @NonNull
    private static Map<String, List<ComponentName>> byPackage(@Nullable List<ResolveInfo> infos,
                                                              @NonNull String bindPermission) {
        Map<String, List<ComponentName>> map = new HashMap<>();
        if (infos == null) return map;
        for (ResolveInfo info : infos) {
            ComponentInfo ci = info.serviceInfo != null ? info.serviceInfo : info.activityInfo;
            if (ci == null) continue;
            String permission = info.serviceInfo != null ? info.serviceInfo.permission : info.activityInfo.permission;
            if (!bindPermission.equals(permission)) continue;
            List<ComponentName> list = map.get(ci.packageName);
            if (list == null) {
                list = new ArrayList<>(1);
                map.put(ci.packageName, list);
            }
            list.add(new ComponentName(ci.packageName, ci.name));
        }
        return map;
    }

    @Nullable
    private static String readSecure(@NonNull ContentResolver resolver, @NonNull String key) {
        try {
            return Settings.Secure.getString(resolver, key);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not read %s.", e, key);
            return null;
        }
    }

    /** Package names from a colon-separated list of components, as Settings stores them. */
    @NonNull
    static Set<String> packagesOf(@Nullable String flattenedComponents) {
        Set<String> packages = new HashSet<>();
        if (TextUtils.isEmpty(flattenedComponents)) return packages;
        for (String item : flattenedComponents.split(":")) {
            if (item.isEmpty()) continue;
            int slash = item.indexOf('/');
            packages.add(slash > 0 ? item.substring(0, slash) : item);
        }
        return packages;
    }

    @NonNull
    private static Set<String> enabledListeners(@NonNull Context context) {
        try {
            return new HashSet<>(NotificationManagerCompat.getEnabledListenerPackages(context));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not read notification listeners.", e);
            return Collections.emptySet();
        }
    }

    @NonNull
    private static Set<String> activeAdmins(@NonNull Context context) {
        Set<String> packages = new HashSet<>();
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            List<ComponentName> admins = dpm != null ? dpm.getActiveAdmins() : null;
            if (admins != null) {
                for (ComponentName admin : admins) packages.add(admin.getPackageName());
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not read device admins.", e);
        }
        return packages;
    }

    @Nullable
    private static Set<String> dndAccess(@NonNull ContentResolver resolver) {
        try {
            return dndAccessFromSetting(Build.VERSION.SDK_INT,
                    Settings.Secure.getString(resolver, DND_ACCESS_PACKAGES));
        } catch (RuntimeException e) {
            // Newer Android hides this list from apps
            return null;
        }
    }

    /**
     * Android 12 stopped writing Do Not Disturb access to the secure setting (only the
     * notification listener list is still mirrored there), so from then on the setting is empty
     * or left over from before an upgrade and says nothing about the real state.
     */
    @Nullable
    static Set<String> dndAccessFromSetting(int sdk, @Nullable String value) {
        if (sdk >= Build.VERSION_CODES.S) return null;
        return packagesOf(value);
    }

    @Nullable
    private static INotificationManager notificationPolicy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
        if (!SelfPermissions.isSystemOrRoot()
                && !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.STATUS_BAR_SERVICE)) {
            return null;
        }
        try {
            return INotificationManager.Stub.asInterface(ProxyBinder.getService(Context.NOTIFICATION_SERVICE));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not reach the notification service.", e);
            return null;
        }
    }
}
