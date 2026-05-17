// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shizuku;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;

import rikka.shizuku.Shizuku;

public final class ShizukuBridge {
    private static final int ANDROID_17_SDK_INT = 37;

    public static final String PACKAGE_NAME = "moe.shizuku.privileged.api";
    public static final String AUTO_START_ACTIVITY = PACKAGE_NAME + ".AUTO_START";
    public static final String PROVIDER_CLASS_NAME = "rikka.shizuku.ShizukuProvider";
    public static final String MIN_RECOMMENDED_MANAGER_VERSION = "13.6.0";
    @Nullable
    public static final String MIN_ANDROID_17_COMPATIBLE_VERSION = null;
    public static final int MIN_USER_SERVICE_VERSION = 10;
    public static final int REQUEST_PERMISSION_CODE = 0x5348;

    private ShizukuBridge() {
    }

    @AnyThread
    public static boolean isBinderAlive() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11();
        } catch (Throwable e) {
            return false;
        }
    }

    @AnyThread
    public static boolean supportsUserService() {
        try {
            return isBinderAlive() && Shizuku.getVersion() >= MIN_USER_SERVICE_VERSION;
        } catch (Throwable e) {
            return false;
        }
    }

    @AnyThread
    public static int getVersionOrZero() {
        try {
            return isBinderAlive() ? Shizuku.getVersion() : 0;
        } catch (Throwable e) {
            return 0;
        }
    }

    @AnyThread
    public static boolean hasPermission() {
        try {
            return supportsUserService()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }

    @AnyThread
    public static boolean wasPermissionRevokedAfterClearData(boolean hadPermissionBeforeClearData) {
        return wasPermissionRevokedAfterClearData(hadPermissionBeforeClearData, hasPermission());
    }

    @VisibleForTesting
    static boolean wasPermissionRevokedAfterClearData(boolean hadPermissionBeforeClearData,
                                                     boolean hasPermissionAfterClearData) {
        return hadPermissionBeforeClearData && !hasPermissionAfterClearData;
    }

    @AnyThread
    public static boolean shouldShowPermissionRationale() {
        try {
            return supportsUserService() && Shizuku.shouldShowRequestPermissionRationale();
        } catch (Throwable e) {
            return false;
        }
    }

    @AnyThread
    public static boolean isUsable() {
        return supportsUserService() && hasPermission();
    }

    @AnyThread
    public static int getUidOrSelf() {
        try {
            return isBinderAlive() ? Shizuku.getUid() : Process.myUid();
        } catch (Throwable e) {
            return Process.myUid();
        }
    }

    @AnyThread
    @Nullable
    public static String getInstalledVersionName(@NonNull Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            return packageInfo.versionName;
        } catch (Throwable e) {
            return null;
        }
    }

    @AnyThread
    public static boolean isRecommendedManagerVersion(@NonNull Context context) {
        String versionName = getInstalledVersionName(context);
        return versionName != null && compareVersion(versionName, MIN_RECOMMENDED_MANAGER_VERSION) >= 0;
    }

    @AnyThread
    @StringRes
    public static int getClearDataAuthorizationWarning(@NonNull Context context, @NonNull String packageName) {
        return getClearDataAuthorizationWarning(context, packageName, null);
    }

    @AnyThread
    @StringRes
    public static int getClearDataAuthorizationWarning(@NonNull Context context, @NonNull String packageName,
                                                       @Nullable PackageInfo packageInfo) {
        int warning = getClearDataAuthorizationWarning(context.getPackageName(), packageName, false);
        if (warning != 0) {
            return warning;
        }
        return getClearDataAuthorizationWarning(context.getPackageName(), packageName,
                declaresShizukuProvider(packageInfo) || declaresShizukuProvider(context, packageName));
    }

    @AnyThread
    public static boolean hasClearDataAuthorizationWarning(@NonNull Context context,
                                                          @NonNull Iterable<String> packageNames) {
        for (String packageName : packageNames) {
            if (getClearDataAuthorizationWarning(context, packageName) != 0) {
                return true;
            }
        }
        return false;
    }

    @AnyThread
    public static boolean supportsTrustedWlanAutoStart(@NonNull Context context) {
        return supportsTrustedWlanAutoStart(Build.VERSION.SDK_INT, getInstalledVersionName(context));
    }

    @AnyThread
    public static boolean shouldOfferTrustedWlanAutoStart(@NonNull Context context) {
        return shouldOfferTrustedWlanAutoStart(Build.VERSION.SDK_INT, getInstalledVersionName(context), isBinderAlive());
    }

    @AnyThread
    @NonNull
    public static Intent getTrustedWlanAutoStartIntent(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        // Roadmap tracks the explicit auto-start component; Shizuku v13.6.0
        // source does not expose it in every build, so fall back gracefully.
        Intent autoStartIntent = new Intent()
                .setComponent(new ComponentName(PACKAGE_NAME, AUTO_START_ACTIVITY))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (autoStartIntent.resolveActivity(pm) != null) {
            return autoStartIntent;
        }
        Intent launchIntent = pm.getLaunchIntentForPackage(PACKAGE_NAME);
        if (launchIntent != null) {
            return launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + PACKAGE_NAME))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @AnyThread
    public static boolean hasAndroid17CompatibilityRisk(@NonNull Context context) {
        return hasAndroid17CompatibilityRisk(Build.VERSION.SDK_INT, getInstalledVersionName(context),
                MIN_ANDROID_17_COMPATIBLE_VERSION);
    }

    @VisibleForTesting
    static boolean hasAndroid17CompatibilityRisk(int sdkInt, @Nullable String versionName,
                                                 @Nullable String compatibleVersionName) {
        if (sdkInt < ANDROID_17_SDK_INT) {
            return false;
        }
        if (compatibleVersionName == null) {
            return true;
        }
        return versionName == null || compareVersion(versionName, compatibleVersionName) < 0;
    }

    @VisibleForTesting
    static boolean supportsTrustedWlanAutoStart(int sdkInt, @Nullable String versionName) {
        return sdkInt >= Build.VERSION_CODES.TIRAMISU
                && versionName != null
                && compareVersion(versionName, MIN_RECOMMENDED_MANAGER_VERSION) >= 0;
    }

    @VisibleForTesting
    static boolean shouldOfferTrustedWlanAutoStart(int sdkInt, @Nullable String versionName, boolean binderAlive) {
        return !binderAlive && supportsTrustedWlanAutoStart(sdkInt, versionName);
    }

    @VisibleForTesting
    @StringRes
    static int getClearDataAuthorizationWarning(@NonNull String appPackageName, @NonNull String targetPackageName,
                                                boolean declaresShizukuProvider) {
        if (Objects.equals(appPackageName, targetPackageName)) {
            return R.string.shizuku_clear_data_self_warning;
        }
        if (Objects.equals(PACKAGE_NAME, targetPackageName)) {
            return R.string.shizuku_clear_data_manager_warning;
        }
        if (declaresShizukuProvider) {
            return R.string.shizuku_clear_data_client_warning;
        }
        return 0;
    }

    private static boolean declaresShizukuProvider(@NonNull Context context, @NonNull String packageName) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName,
                    PackageManager.GET_PROVIDERS);
            return declaresShizukuProvider(packageInfo);
        } catch (Throwable e) {
            return false;
        }
    }

    @VisibleForTesting
    static boolean declaresShizukuProvider(@Nullable PackageInfo packageInfo) {
        if (packageInfo == null || packageInfo.providers == null) {
            return false;
        }
        for (ProviderInfo providerInfo : packageInfo.providers) {
            if (providerInfo != null && isShizukuProviderName(providerInfo.name)) {
                return true;
            }
        }
        return false;
    }

    @VisibleForTesting
    static boolean isShizukuProviderName(@Nullable String providerName) {
        return Objects.equals(PROVIDER_CLASS_NAME, providerName);
    }

    @VisibleForTesting
    static int compareVersion(@NonNull String versionName, @NonNull String requiredVersionName) {
        int[] version = parseVersionPrefix(versionName);
        int[] required = parseVersionPrefix(requiredVersionName);
        for (int i = 0; i < Math.max(version.length, required.length); ++i) {
            int current = i < version.length ? version[i] : 0;
            int expected = i < required.length ? required[i] : 0;
            if (current != expected) {
                return current - expected;
            }
        }
        return 0;
    }

    @NonNull
    private static int[] parseVersionPrefix(@NonNull String versionName) {
        String normalized = versionName.startsWith("v") ? versionName.substring(1) : versionName;
        String[] parts = normalized.split("[^0-9]+");
        int[] version = new int[Math.min(parts.length, 3)];
        int count = 0;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            try {
                version[count++] = Integer.parseInt(part);
            } catch (NumberFormatException ignore) {
                version[count++] = 0;
            }
            if (count == version.length) break;
        }
        if (count == version.length) {
            return version;
        }
        int[] compact = new int[count];
        System.arraycopy(version, 0, compact, 0, count);
        return compact;
    }
}
