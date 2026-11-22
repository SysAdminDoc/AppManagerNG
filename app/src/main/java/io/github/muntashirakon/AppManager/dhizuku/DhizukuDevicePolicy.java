// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

/**
 * The device policy calls NG runs as the Dhizuku owner. Each one passes the owner's admin
 * component, so callers only name the package. {@link DhizukuDevicePolicyImpl} forwards them to
 * a {@code DevicePolicyManager} on the Dhizuku binder; tests pass a fake.
 */
@WorkerThread
public interface DhizukuDevicePolicy {
    /**
     * @return {@code true} when Android changed the hidden state
     */
    boolean setApplicationHidden(@NonNull String packageName, boolean hidden);

    boolean isApplicationHidden(@NonNull String packageName);

    /**
     * @return the packages Android could not change
     */
    @RequiresApi(Build.VERSION_CODES.N)
    @Nullable
    String[] setPackagesSuspended(@NonNull String[] packageNames, boolean suspended);

    @RequiresApi(Build.VERSION_CODES.N)
    boolean isPackageSuspended(@NonNull String packageName) throws PackageManager.NameNotFoundException;

    /**
     * @param grantState one of {@code DevicePolicyManager.PERMISSION_GRANT_STATE_*}
     * @return {@code false} when Android refused, for example for an app that targets Android 5.1
     * or lower
     */
    boolean setPermissionGrantState(@NonNull String packageName, @NonNull String permission, int grantState);

    int getPermissionGrantState(@NonNull String packageName, @NonNull String permission);

    /**
     * Android answers on {@code callback} once the data is gone, or failed to go.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    void clearApplicationUserData(@NonNull String packageName, @NonNull ClearDataCallback callback);

    interface ClearDataCallback {
        void onCleared(@NonNull String packageName, boolean succeeded);
    }
}
