// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.test.shadows;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

@Implements(PackageManagerCompat.class)
public class ShadowPackageManagerCompat {
    private static int sClearApplicationUserDataCalls;

    @Implementation
    @NonNull
    public static PackageInfo getPackageInfo(@NonNull String packageName, int flags, int userId)
            throws RemoteException, PackageManager.NameNotFoundException {
        return ContextUtils.getContext().getPackageManager().getPackageInfo(packageName, flags);
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
