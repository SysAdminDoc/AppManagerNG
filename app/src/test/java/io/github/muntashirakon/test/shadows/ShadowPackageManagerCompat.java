// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.test.shadows;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

@Implements(PackageManagerCompat.class)
public class ShadowPackageManagerCompat {
    private static int sClearApplicationUserDataCalls;
    private static final List<String> sPackageInfoLookups = new ArrayList<>();
    private static final Map<String, PackageInfo> sUserPackages = new HashMap<>();

    @Implementation
    @NonNull
    public static PackageInfo getPackageInfo(@NonNull String packageName, int flags, int userId)
            throws RemoteException, PackageManager.NameNotFoundException {
        String key = packageName + "/" + userId;
        sPackageInfoLookups.add(key);
        if (sUserPackages.containsKey(key)) {
            PackageInfo packageInfo = sUserPackages.get(key);
            if (packageInfo == null) {
                throw new PackageManager.NameNotFoundException(key);
            }
            return packageInfo;
        }
        // Robolectric's package manager has one user, so every other user sees the same packages
        return ContextUtils.getContext().getPackageManager().getPackageInfo(packageName, flags);
    }

    /**
     * Makes getPackageInfo answer with this package info for this user only, or report the package
     * missing there when it's null. Call {@link #resetPackageInfo()} before and after the test: the
     * shadow's lists outlive a test class, and Robolectric runs no resetter for this module's shadows.
     */
    public static void setPackageInfoForUser(@NonNull String packageName, int userId, @Nullable PackageInfo packageInfo) {
        sUserPackages.put(packageName + "/" + userId, packageInfo);
    }

    /** Every getPackageInfo call since the last reset, as "package/user". */
    @NonNull
    public static List<String> getPackageInfoLookups() {
        return new ArrayList<>(sPackageInfoLookups);
    }

    public static void resetPackageInfo() {
        sPackageInfoLookups.clear();
        sUserPackages.clear();
    }

    @Implementation
    @NonNull
    public static ApplicationInfo getApplicationInfo(String packageName, int flags, int userId)
            throws RemoteException, PackageManager.NameNotFoundException {
        // The real call asks the system's package service, which Robolectric doesn't provide.
        // Robolectric's package manager knows the packages a test installs. Packages only the
        // apps database knows about aren't on the test device.
        return ContextUtils.getContext().getPackageManager().getApplicationInfo(packageName, flags);
    }

    @Implementation
    public static boolean clearApplicationUserData(@NonNull String packageName, int userId) {
        ++sClearApplicationUserDataCalls;
        // App data may have been cleaned already depending on how it was handled in unit tests
        return true;
    }

    @Implementation
    public static String getInstallerPackageName(@NonNull String packageName, int userId) {
        return BuildConfig.APPLICATION_ID;
    }

    public static void resetClearApplicationUserDataCalls() {
        sClearApplicationUserDataCalls = 0;
    }

    public static int getClearApplicationUserDataCalls() {
        return sClearApplicationUserDataCalls;
    }
}
