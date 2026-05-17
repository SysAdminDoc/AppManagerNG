// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.revert;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.github.muntashirakon.AppManager.R;

final class DozeAllowlistDiagnostics {
    private static final String SETTINGS_DEVICE_IDLE_CONSTANTS = "device_idle_constants";
    private static final String DEVICE_CONFIG_DEVICE_IDLE_NAMESPACE = "device_idle";

    @VisibleForTesting
    static final int PACKAGE_KIND_UNKNOWN = 0;
    @VisibleForTesting
    static final int PACKAGE_KIND_USER = 1;
    @VisibleForTesting
    static final int PACKAGE_KIND_SYSTEM = 2;

    private static final int MAX_CHANGED_KEYS = 3;
    private static final int MAX_SUMMARY_KEYS = 4;

    private DozeAllowlistDiagnostics() {
    }

    @NonNull
    static Snapshot snapshot(@NonNull Context context, @NonNull String packageName) {
        Context appContext = context.getApplicationContext();
        return new Snapshot(
                Settings.Global.getString(appContext.getContentResolver(), SETTINGS_DEVICE_IDLE_CONSTANTS),
                readDeviceIdleDeviceConfig(),
                getPackageKind(appContext, packageName),
                Build.MANUFACTURER);
    }

    @NonNull
    static String buildHint(@NonNull Context context,
                            @NonNull Snapshot before,
                            @NonNull Snapshot after) {
        return context.getString(R.string.os_revert_doze_detail_with_diff,
                buildOneLineDiff(before, after),
                context.getString(getPolicyHintRes(after)));
    }

    @StringRes
    private static int getPolicyHintRes(@NonNull Snapshot snapshot) {
        String manufacturer = snapshot.getManufacturer().toLowerCase(Locale.ROOT);
        if (snapshot.getPackageKind() == PACKAGE_KIND_USER && manufacturer.contains("samsung")) {
            return R.string.os_revert_doze_policy_hint_samsung;
        }
        if (snapshot.getPackageKind() == PACKAGE_KIND_USER) {
            return R.string.os_revert_doze_policy_hint_user;
        }
        if (snapshot.getPackageKind() == PACKAGE_KIND_SYSTEM) {
            return R.string.os_revert_doze_policy_hint_system;
        }
        return R.string.os_revert_doze_policy_hint_unknown;
    }

    @NonNull
    @VisibleForTesting
    static String buildOneLineDiff(@NonNull Snapshot before, @NonNull Snapshot after) {
        String globalDiff = buildMapDiff(
                parseKeyValueList(before.getDeviceIdleConstants()),
                parseKeyValueList(after.getDeviceIdleConstants()),
                "device_idle_constants");
        if (globalDiff != null) {
            return globalDiff;
        }
        if (!safeEquals(normalizeSetting(before.getDeviceIdleConstants()),
                normalizeSetting(after.getDeviceIdleConstants()))) {
            return "device_idle_constants raw changed";
        }
        String deviceConfigDiff = buildMapDiff(
                before.getDeviceConfigValues(),
                after.getDeviceConfigValues(),
                "DeviceConfig device_idle");
        if (deviceConfigDiff != null) {
            return describeUnchangedGlobal(after) + "; " + deviceConfigDiff;
        }
        if (isBlank(after.getDeviceIdleConstants()) && !after.getDeviceConfigValues().isEmpty()) {
            return "device_idle_constants empty; DeviceConfig device_idle unchanged ("
                    + summarizeMap(after.getDeviceConfigValues()) + ")";
        }
        return describeUnchangedGlobal(after);
    }

    @Nullable
    private static String buildMapDiff(@NonNull Map<String, String> before,
                                       @NonNull Map<String, String> after,
                                       @NonNull String label) {
        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        List<String> changes = new ArrayList<>();
        for (String key : keys) {
            String beforeValue = before.get(key);
            String afterValue = after.get(key);
            if (safeEquals(beforeValue, afterValue)) {
                continue;
            }
            changes.add(key + ": " + valueOrMissing(beforeValue) + " -> " + valueOrMissing(afterValue));
        }
        if (changes.isEmpty()) {
            return null;
        }
        int shown = Math.min(MAX_CHANGED_KEYS, changes.size());
        StringBuilder sb = new StringBuilder(label).append(" changed ");
        for (int i = 0; i < shown; ++i) {
            if (i > 0) sb.append(", ");
            sb.append(changes.get(i));
        }
        int hidden = changes.size() - shown;
        if (hidden > 0) {
            sb.append(" (+").append(hidden).append(" more)");
        }
        return sb.toString();
    }

    @NonNull
    private static String describeUnchangedGlobal(@NonNull Snapshot snapshot) {
        Map<String, String> globalValues = parseKeyValueList(snapshot.getDeviceIdleConstants());
        if (globalValues.isEmpty()) {
            return "device_idle_constants unchanged (empty)";
        }
        return "device_idle_constants unchanged (" + summarizeMap(globalValues) + ")";
    }

    @NonNull
    private static String summarizeMap(@NonNull Map<String, String> values) {
        if (values.isEmpty()) {
            return "empty";
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            if (index >= MAX_SUMMARY_KEYS) break;
            entries.add(entry.getKey() + "=" + entry.getValue());
            ++index;
        }
        int hidden = values.size() - entries.size();
        if (hidden > 0) {
            entries.add("+" + hidden + " more");
        }
        return join(entries, ", ");
    }

    @NonNull
    @VisibleForTesting
    static Map<String, String> parseKeyValueList(@Nullable String value) {
        String normalized = normalizeSetting(value);
        if (normalized == null) {
            return Collections.emptyMap();
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        String[] parts = normalized.split(",");
        for (String part : parts) {
            int separator = part.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = part.substring(0, separator).trim();
            String val = part.substring(separator + 1).trim();
            if (!key.isEmpty()) {
                parsed.put(key, val);
            }
        }
        return parsed;
    }

    @Nullable
    private static String normalizeSetting(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private static boolean isBlank(@Nullable String value) {
        return normalizeSetting(value) == null;
    }

    private static boolean safeEquals(@Nullable String a, @Nullable String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    @NonNull
    private static String valueOrMissing(@Nullable String value) {
        return value == null ? "<missing>" : value;
    }

    private static int getPackageKind(@NonNull Context context, @NonNull String packageName) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            int systemFlags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
            return (info.flags & systemFlags) != 0 ? PACKAGE_KIND_SYSTEM : PACKAGE_KIND_USER;
        } catch (PackageManager.NameNotFoundException e) {
            return PACKAGE_KIND_UNKNOWN;
        }
    }

    @NonNull
    private static Map<String, String> readDeviceIdleDeviceConfig() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Collections.emptyMap();
        }
        try {
            Class<?> deviceConfigClass = Class.forName("android.provider.DeviceConfig");
            Method getProperties = deviceConfigClass.getMethod("getProperties", String.class, String[].class);
            Object properties = getProperties.invoke(null, DEVICE_CONFIG_DEVICE_IDLE_NAMESPACE, (Object) new String[0]);
            if (properties == null) {
                return Collections.emptyMap();
            }
            Method getKeyset = properties.getClass().getMethod("getKeyset");
            Method getString = properties.getClass().getMethod("getString", String.class, String.class);
            Object keysetObj = getKeyset.invoke(properties);
            if (!(keysetObj instanceof Set<?>)) {
                return Collections.emptyMap();
            }
            Map<String, String> values = new TreeMap<>();
            for (Object keyObj : (Set<?>) keysetObj) {
                if (!(keyObj instanceof String)) {
                    continue;
                }
                String key = (String) keyObj;
                Object valueObj = getString.invoke(properties, key, (Object) null);
                if (valueObj instanceof String) {
                    values.put(key, (String) valueObj);
                }
            }
            return values;
        } catch (Throwable ignored) {
            return Collections.emptyMap();
        }
    }

    @NonNull
    private static String join(@NonNull List<String> values, @NonNull String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); ++i) {
            if (i > 0) sb.append(separator);
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    static final class Snapshot {
        @Nullable
        private final String mDeviceIdleConstants;
        @NonNull
        private final Map<String, String> mDeviceConfigValues;
        private final int mPackageKind;
        @NonNull
        private final String mManufacturer;

        Snapshot(@Nullable String deviceIdleConstants,
                 @NonNull Map<String, String> deviceConfigValues,
                 int packageKind,
                 @Nullable String manufacturer) {
            mDeviceIdleConstants = deviceIdleConstants;
            mDeviceConfigValues = new TreeMap<>(deviceConfigValues);
            mPackageKind = packageKind;
            mManufacturer = manufacturer == null ? "" : manufacturer;
        }

        @Nullable
        String getDeviceIdleConstants() {
            return mDeviceIdleConstants;
        }

        @NonNull
        Map<String, String> getDeviceConfigValues() {
            return mDeviceConfigValues;
        }

        int getPackageKind() {
            return mPackageKind;
        }

        @NonNull
        String getManufacturer() {
            return mManufacturer;
        }
    }
}
