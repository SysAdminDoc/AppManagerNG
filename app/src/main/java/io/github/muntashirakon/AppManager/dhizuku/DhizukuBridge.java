// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Objects;

public final class DhizukuBridge {
    public static final String PACKAGE_NAME = "com.rosan.dhizuku";
    public static final String DEVICE_ADMIN_CLASS_NAME = PACKAGE_NAME + ".server.DhizukuDAReceiver";
    public static final String PERMISSION_API = PACKAGE_NAME + ".permission.API";
    public static final String PROVIDER_AUTHORITY = PACKAGE_NAME + ".server.provider";
    public static final String ACTION_REQUEST_PERMISSION = PACKAGE_NAME + ".action.request.permission";
    public static final String ACTION_REQUEST_PERMISSION_COMPAT = PACKAGE_NAME + ".action.REQUEST_DHIZUKU_PERMISSION";
    public static final String ACTIVATION_COMMAND =
            "adb shell dpm set-device-owner --user 0 " + PACKAGE_NAME + "/.server.DhizukuDAReceiver";

    public static final int MIN_SUPPORTED_SDK = Build.VERSION_CODES.O;
    public static final int MAX_DECLARED_SUPPORTED_SDK = 36;

    private DhizukuBridge() {
    }

    @AnyThread
    @NonNull
    public static Result probe(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        String installedVersionName = getInstalledVersionName(appContext);
        ComponentName ownerComponent = getOwnerComponent(appContext);
        boolean providerVisible = isProviderVisible(appContext);
        boolean apiPermissionGranted = hasApiPermission(appContext);
        return new Result(Build.VERSION.SDK_INT, installedVersionName, ownerComponent,
                providerVisible, apiPermissionGranted);
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
    @Nullable
    public static ComponentName getOwnerComponent(@NonNull Context context) {
        try {
            DevicePolicyManager manager =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            return manager != null ? getOwnerComponent(manager) : null;
        } catch (Throwable e) {
            return null;
        }
    }

    @AnyThread
    @Nullable
    public static ComponentName getOwnerComponent(@NonNull DevicePolicyManager manager) {
        List<ComponentName> admins;
        try {
            admins = manager.getActiveAdmins();
        } catch (Throwable e) {
            return null;
        }
        if (admins == null) {
            return null;
        }
        for (ComponentName admin : admins) {
            if (admin == null) {
                continue;
            }
            String packageName = admin.getPackageName();
            try {
                if (manager.isDeviceOwnerApp(packageName)) {
                    return admin;
                }
                if (manager.isProfileOwnerApp(packageName)) {
                    return admin;
                }
            } catch (Throwable ignore) {
            }
        }
        return null;
    }

    @AnyThread
    public static boolean isProviderVisible(@NonNull Context context) {
        try {
            ProviderInfo providerInfo = context.getPackageManager().resolveContentProvider(PROVIDER_AUTHORITY, 0);
            return providerInfo != null;
        } catch (Throwable e) {
            return false;
        }
    }

    @AnyThread
    public static boolean hasApiPermission(@NonNull Context context) {
        try {
            return ContextCompat.checkSelfPermission(context, PERMISSION_API)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }

    @AnyThread
    @NonNull
    public static Intent getSettingsIntent(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(PACKAGE_NAME);
        if (launchIntent != null) {
            return launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + PACKAGE_NAME))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @VisibleForTesting
    public static boolean isBelowMinimumSupportedAndroidVersion(int sdk) {
        return sdk < MIN_SUPPORTED_SDK;
    }

    @VisibleForTesting
    public static boolean isAboveDeclaredSupportedAndroidVersion(int sdk) {
        return sdk > MAX_DECLARED_SUPPORTED_SDK;
    }

    @VisibleForTesting
    public static boolean isOfficialOwnerComponent(@Nullable String packageName, @Nullable String className) {
        return Objects.equals(PACKAGE_NAME, packageName)
                && (Objects.equals(DEVICE_ADMIN_CLASS_NAME, className)
                || Objects.equals(".server.DhizukuDAReceiver", className));
    }

    @VisibleForTesting
    @NonNull
    public static String getProviderAuthorityName(@NonNull String packageName) {
        if (Objects.equals(PACKAGE_NAME, packageName)) {
            return PROVIDER_AUTHORITY;
        }
        return packageName + ".dhizuku_server.provider";
    }

    @VisibleForTesting
    @NonNull
    public static String getActionRequestPermission(@NonNull String packageName) {
        if (Objects.equals(PACKAGE_NAME, packageName)) {
            return ACTION_REQUEST_PERMISSION;
        }
        return packageName + ".action.REQUEST_DHIZUKU_PERMISSION";
    }

    public static final class Result {
        public final int sdk;
        @Nullable
        public final String installedVersionName;
        @Nullable
        public final ComponentName ownerComponent;
        public final boolean providerVisible;
        public final boolean apiPermissionGranted;

        private Result(int sdk, @Nullable String installedVersionName, @Nullable ComponentName ownerComponent,
                       boolean providerVisible, boolean apiPermissionGranted) {
            this.sdk = sdk;
            this.installedVersionName = installedVersionName;
            this.ownerComponent = ownerComponent;
            this.providerVisible = providerVisible;
            this.apiPermissionGranted = apiPermissionGranted;
        }

        public boolean isInstalled() {
            return installedVersionName != null;
        }

        public boolean isOfficialOwner() {
            return ownerComponent != null
                    && isOfficialOwnerComponent(ownerComponent.getPackageName(), ownerComponent.getClassName());
        }

        @NonNull
        public String ownerLabel() {
            return ownerComponent != null ? ownerComponent.flattenToShortString() : "none";
        }
    }
}
