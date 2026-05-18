// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shizuku;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
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

import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.SystemProperties;

import rikka.shizuku.Shizuku;

public final class ShizukuBridge {
    private static final int ANDROID_15_SDK_INT = 35;
    private static final int ANDROID_16_SDK_INT = 36;
    private static final int ANDROID_17_SDK_INT = 37;
    private static final int ROOT_UID = 0;
    private static final int SHELL_UID = 2000;
    private static final int SHIZUKU_13_6_API_VERSION = 13;

    public static final String PACKAGE_NAME = "moe.shizuku.privileged.api";
    public static final String API_PERMISSION = "moe.shizuku.manager.permission.API_V23";
    public static final String LEGACY_SERVICE_PERMISSION = "moe.shizuku.api.SHIZUKU_SERVICE";
    private static final String AUTO_START_ACTIVITY_SUFFIX = ".AUTO_START";
    public static final String AUTO_START_ACTIVITY = PACKAGE_NAME + AUTO_START_ACTIVITY_SUFFIX;
    public static final String PROVIDER_CLASS_NAME = "rikka.shizuku.ShizukuProvider";
    public static final String MIN_RECOMMENDED_MANAGER_VERSION = "13.6.0";
    public static final String PINNED_SAFE_MANAGER_VERSION = "13.5.4";
    public static final String PINNED_SAFE_MANAGER_ARCHIVE_URL =
            "https://apt.izzysoft.de/fdroid/repo/moe.shizuku.privileged.api_1049.apk";
    @Nullable
    public static final String MIN_ANDROID_17_COMPATIBLE_VERSION = null;
    public static final int MIN_USER_SERVICE_VERSION = 10;
    public static final int REQUEST_PERMISSION_CODE = 0x5348;

    private static final String PROP_TRANSSION_VERSION = "ro.transsion.version";
    private static final String PROP_PRODUCT_PLATFORM = "ro.product.platform";
    private static final String PROP_BOARD_PLATFORM = "ro.board.platform";
    private static final String PROP_HARDWARE = "ro.hardware";
    private static final String PROP_SOC_MANUFACTURER = "ro.soc.manufacturer";
    private static final String PROP_SOC_MODEL = "ro.soc.model";

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
    public static boolean isRootBacked() {
        try {
            return isBinderAlive() && isRootBackedUid(Shizuku.getUid());
        } catch (Throwable e) {
            return false;
        }
    }

    @AnyThread
    public static boolean isAdbBacked() {
        try {
            return isBinderAlive() && Shizuku.getUid() == SHELL_UID;
        } catch (Throwable e) {
            return false;
        }
    }

    @AnyThread
    public static boolean shouldAvoidRootBackedShizukuInAuto(boolean adbAvailable) {
        return shouldAvoidRootBackedShizukuInAuto(isUsable(), getUidOrSelf(), adbAvailable);
    }

    @AnyThread
    @Nullable
    public static String getInstalledVersionName(@NonNull Context context) {
        String managerPackageName = getManagerPackageName(context);
        String versionName = getInstalledVersionName(context, managerPackageName);
        if (versionName != null || Objects.equals(PACKAGE_NAME, managerPackageName)) {
            return versionName;
        }
        return getInstalledVersionName(context, PACKAGE_NAME);
    }

    @AnyThread
    @NonNull
    public static String getManagerPackageName(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        return getManagerPackageName(getPermissionInfo(pm, API_PERMISSION),
                getPermissionInfo(pm, LEGACY_SERVICE_PERMISSION));
    }

    @Nullable
    private static String getInstalledVersionName(@NonNull Context context, @NonNull String packageName) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
            return packageInfo.versionName;
        } catch (Throwable e) {
            return null;
        }
    }

    @Nullable
    private static PermissionInfo getPermissionInfo(@NonNull PackageManager pm, @NonNull String permissionName) {
        try {
            return pm.getPermissionInfo(permissionName, 0);
        } catch (Throwable e) {
            return null;
        }
    }

    @VisibleForTesting
    @NonNull
    static String getManagerPackageName(@Nullable PermissionInfo apiPermission,
                                        @Nullable PermissionInfo legacyServicePermission) {
        String packageName = getPermissionOwnerPackageName(apiPermission);
        if (packageName != null) {
            return packageName;
        }
        packageName = getPermissionOwnerPackageName(legacyServicePermission);
        return packageName != null ? packageName : PACKAGE_NAME;
    }

    @Nullable
    private static String getPermissionOwnerPackageName(@Nullable PermissionInfo permissionInfo) {
        if (permissionInfo == null || permissionInfo.packageName == null || permissionInfo.packageName.isEmpty()) {
            return null;
        }
        return permissionInfo.packageName;
    }

    /**
     * Best-effort version label for display. Falls back to the binder-reported
     * API version when the Shizuku Manager package is hidden from
     * {@code PackageManager} queries (the v13.6.0+ "Hide Shizuku" feature only
     * removes the package from visibility — the binder remains reachable for
     * authorized callers).
     *
     * <p>Output shapes:
     * <ul>
     *   <li>{@code "13.6.0"} — package query succeeded</li>
     *   <li>{@code "api 14 (hidden)"} — package query failed, binder is alive</li>
     *   <li>{@code null} — Shizuku is neither installed nor running</li>
     * </ul>
     */
    @AnyThread
    @Nullable
    public static String getDisplayVersion(@NonNull Context context) {
        return getDisplayVersion(getInstalledVersionName(context), getVersionOrZero(), isBinderAlive());
    }

    @VisibleForTesting
    @Nullable
    static String getDisplayVersion(@Nullable String installedVersionName,
                                    int binderApiVersion,
                                    boolean binderAlive) {
        if (installedVersionName != null && !installedVersionName.isEmpty()) {
            return installedVersionName;
        }
        if (binderAlive && binderApiVersion > 0) {
            return "api " + binderApiVersion + " (hidden)";
        }
        return null;
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
        String managerPackageName = getManagerPackageName(context);
        int warning = getClearDataAuthorizationWarning(context.getPackageName(), packageName,
                managerPackageName, false);
        if (warning != 0) {
            return warning;
        }
        return getClearDataAuthorizationWarning(context.getPackageName(), packageName, managerPackageName,
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
    @Nullable
    public static OemCompatibilityWarning getOemCompatibilityWarning(@NonNull Context context) {
        return getOemCompatibilityWarning(Build.MANUFACTURER, Build.MODEL, Build.DEVICE, Build.PRODUCT,
                Build.VERSION.SDK_INT, getInstalledVersionName(context), getVersionOrZero(),
                SystemProperties.get(PROP_TRANSSION_VERSION, ""),
                SystemProperties.get(PROP_PRODUCT_PLATFORM, ""),
                SystemProperties.get(PROP_BOARD_PLATFORM, ""),
                SystemProperties.get(PROP_HARDWARE, ""),
                SystemProperties.get(PROP_SOC_MANUFACTURER, ""),
                SystemProperties.get(PROP_SOC_MODEL, ""));
    }

    @AnyThread
    @NonNull
    public static Intent getPinnedSafeManagerArchiveIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(PINNED_SAFE_MANAGER_ARCHIVE_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @AnyThread
    @NonNull
    public static Intent getTrustedWlanAutoStartIntent(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        String managerPackageName = getManagerPackageName(context);
        // Roadmap tracks the explicit auto-start component; Shizuku v13.6.0
        // source does not expose it in every build, so fall back gracefully.
        Intent autoStartIntent = new Intent()
                .setComponent(new ComponentName(managerPackageName, managerPackageName + AUTO_START_ACTIVITY_SUFFIX))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (autoStartIntent.resolveActivity(pm) != null) {
            return autoStartIntent;
        }
        Intent launchIntent = pm.getLaunchIntentForPackage(managerPackageName);
        if (launchIntent != null) {
            return launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + managerPackageName))
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
    static boolean shouldAvoidRootBackedShizukuInAuto(boolean shizukuUsable,
                                                      int shizukuUid,
                                                      boolean adbAvailable) {
        return shizukuUsable && isRootBackedUid(shizukuUid) && adbAvailable;
    }

    @VisibleForTesting
    static boolean isRootBackedUid(int uid) {
        return uid == ROOT_UID;
    }

    @VisibleForTesting
    @Nullable
    static OemCompatibilityWarning getOemCompatibilityWarning(@Nullable String manufacturer,
                                                              @Nullable String model,
                                                              @Nullable String device,
                                                              @Nullable String product,
                                                              int sdkInt,
                                                              @Nullable String managerVersionName,
                                                              int shizukuApiVersion,
                                                              @Nullable String transsionVersion,
                                                              @Nullable String productPlatform,
                                                              @Nullable String boardPlatform,
                                                              @Nullable String hardware,
                                                              @Nullable String socManufacturer,
                                                              @Nullable String socModel) {
        if (!isShizuku13_6Runtime(managerVersionName, shizukuApiVersion)) {
            return null;
        }
        String maker = lower(manufacturer);
        if (sdkInt >= ANDROID_15_SDK_INT
                && (isTranssionManufacturer(maker) || !lower(transsionVersion).isEmpty())) {
            return new OemCompatibilityWarning("transsion",
                    R.string.shizuku_oem_compat_banner_transsion,
                    R.string.shizuku_oem_compat_summary_transsion);
        }
        if (sdkInt >= ANDROID_16_SDK_INT && "google".equals(maker)
                && containsAny(lower(model) + " " + lower(device) + " " + lower(product),
                "pixel 9", "pixel9")) {
            return new OemCompatibilityWarning("pixel9",
                    R.string.shizuku_oem_compat_banner_pixel9,
                    R.string.shizuku_oem_compat_summary_pixel9);
        }
        String platform = lower(productPlatform) + " " + lower(boardPlatform) + " " + lower(hardware)
                + " " + lower(socManufacturer) + " " + lower(socModel);
        if (sdkInt >= ANDROID_15_SDK_INT && isMediatekPlatform(platform)) {
            return new OemCompatibilityWarning("mediatek",
                    R.string.shizuku_oem_compat_banner_mediatek,
                    R.string.shizuku_oem_compat_summary_mediatek);
        }
        return null;
    }

    @VisibleForTesting
    @StringRes
    static int getClearDataAuthorizationWarning(@NonNull String appPackageName, @NonNull String targetPackageName,
                                                boolean declaresShizukuProvider) {
        return getClearDataAuthorizationWarning(appPackageName, targetPackageName, PACKAGE_NAME,
                declaresShizukuProvider);
    }

    @VisibleForTesting
    @StringRes
    static int getClearDataAuthorizationWarning(@NonNull String appPackageName, @NonNull String targetPackageName,
                                                @NonNull String managerPackageName,
                                                boolean declaresShizukuProvider) {
        if (Objects.equals(appPackageName, targetPackageName)) {
            return R.string.shizuku_clear_data_self_warning;
        }
        if (Objects.equals(PACKAGE_NAME, targetPackageName) || Objects.equals(managerPackageName, targetPackageName)) {
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

    private static boolean isShizuku13_6Runtime(@Nullable String managerVersionName, int shizukuApiVersion) {
        if (managerVersionName != null) {
            return compareVersion(managerVersionName, MIN_RECOMMENDED_MANAGER_VERSION) >= 0;
        }
        return shizukuApiVersion >= SHIZUKU_13_6_API_VERSION;
    }

    private static boolean isTranssionManufacturer(@NonNull String manufacturer) {
        return containsAny(manufacturer, "transsion", "infinix", "tecno", "itel");
    }

    private static boolean isMediatekPlatform(@NonNull String platform) {
        return containsAny(platform, "mediatek", "mtk", "mt6", "mt7", "mt8", "mt9", "dimensity", "helio");
    }

    private static boolean containsAny(@NonNull String haystack, @NonNull String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String lower(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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

    public static final class OemCompatibilityWarning {
        @NonNull
        public final String reasonCode;
        @StringRes
        public final int bannerTextRes;
        @StringRes
        public final int summaryTextRes;
        @NonNull
        public final String fallbackVersion;
        @NonNull
        public final String archiveUrl;

        private OemCompatibilityWarning(@NonNull String reasonCode, @StringRes int bannerTextRes,
                                        @StringRes int summaryTextRes) {
            this.reasonCode = reasonCode;
            this.bannerTextRes = bannerTextRes;
            this.summaryTextRes = summaryTextRes;
            this.fallbackVersion = PINNED_SAFE_MANAGER_VERSION;
            this.archiveUrl = PINNED_SAFE_MANAGER_ARCHIVE_URL;
        }
    }
}
