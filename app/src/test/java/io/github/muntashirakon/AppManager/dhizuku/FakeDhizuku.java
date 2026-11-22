// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Dhizuku server, binder wrapper and device policy manager in one, recording every call.
 * {@link #install()} puts it behind {@link DhizukuSession}.
 */
public final class FakeDhizuku implements DhizukuSession.Environment, DhizukuClient, DhizukuDevicePolicy {
    public static final ComponentName OWNER =
            new ComponentName(DhizukuBridge.PACKAGE_NAME, DhizukuBridge.DEVICE_ADMIN_CLASS_NAME);

    public int sdk = Build.VERSION.SDK_INT;
    public boolean installed = true;
    public boolean owner = true;
    public boolean serverAnswers = true;
    public boolean permitted = true;
    public boolean grantOnRequest = true;
    public boolean refuseGrantState;
    public boolean grantsTakeEffect = true;
    public boolean clearDataSucceeds = true;

    public final List<String> calls = new ArrayList<>();
    public final Set<String> hidden = new HashSet<>();
    public final Set<String> suspended = new HashSet<>();
    private final Map<String, Boolean> mGrants = new HashMap<>();
    /** Each wrapped binder, keyed by the binder that wraps it */
    public final Map<IBinder, IBinder> wrapped = new HashMap<>();

    @NonNull
    public static FakeDhizuku install() {
        FakeDhizuku fake = new FakeDhizuku();
        DhizukuSession.setEnvironmentForTesting(fake);
        return fake;
    }

    public static void uninstall() {
        DhizukuSession.setEnvironmentForTesting(null);
    }

    public boolean isGranted(@NonNull String packageName, @NonNull String permission) {
        return Boolean.TRUE.equals(mGrants.get(packageName + "/" + permission));
    }

    // Environment

    @NonNull
    @Override
    public DhizukuBridge.Result probe(@NonNull Context context) {
        return DhizukuBridge.Result.forTesting(sdk, installed ? "2.13.0" : null, owner ? OWNER : null);
    }

    @Nullable
    @Override
    public DhizukuClient client() {
        return sdk >= Build.VERSION_CODES.M ? this : null;
    }

    @NonNull
    @Override
    public DhizukuDevicePolicy newPolicy(@NonNull Context context, @NonNull DhizukuClient client) {
        return this;
    }

    @Override
    public int checkPermission(@NonNull Context context, @NonNull String permission, @NonNull String packageName) {
        return isGranted(packageName, permission) ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
    }

    // Client

    @Override
    public boolean connect(@NonNull Context context) {
        calls.add("connect");
        return serverAnswers && owner;
    }

    @Override
    public boolean isPermissionGranted() {
        return permitted;
    }

    @Override
    public void requestPermission(@NonNull PermissionCallback callback) {
        calls.add("requestPermission");
        permitted = grantOnRequest;
        callback.onResult(permitted);
    }

    @NonNull
    @Override
    public ComponentName getOwnerComponent() {
        return OWNER;
    }

    @NonNull
    @Override
    public IBinder wrap(@NonNull IBinder binder) {
        IBinder wrapper = new Binder();
        wrapped.put(wrapper, binder);
        return wrapper;
    }

    // Device policy

    @Override
    public boolean setApplicationHidden(@NonNull String packageName, boolean hide) {
        calls.add("setApplicationHidden " + packageName + " " + hide);
        return hide ? hidden.add(packageName) : hidden.remove(packageName);
    }

    @Override
    public boolean isApplicationHidden(@NonNull String packageName) {
        return hidden.contains(packageName);
    }

    @Nullable
    @Override
    public String[] setPackagesSuspended(@NonNull String[] packageNames, boolean suspend) {
        calls.add("setPackagesSuspended " + Arrays.toString(packageNames) + " " + suspend);
        for (String packageName : packageNames) {
            if (suspend) suspended.add(packageName);
            else suspended.remove(packageName);
        }
        return new String[0];
    }

    @Override
    public boolean isPackageSuspended(@NonNull String packageName) {
        return suspended.contains(packageName);
    }

    @Override
    public boolean setPermissionGrantState(@NonNull String packageName, @NonNull String permission, int grantState) {
        calls.add("setPermissionGrantState " + packageName + " " + permission + " " + grantState);
        if (refuseGrantState) {
            return false;
        }
        if (grantsTakeEffect) {
            if (grantState == DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED) {
                mGrants.put(packageName + "/" + permission, true);
            } else if (grantState == DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED) {
                mGrants.put(packageName + "/" + permission, false);
            }
        }
        return true;
    }

    @Override
    public int getPermissionGrantState(@NonNull String packageName, @NonNull String permission) {
        return DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
    }

    @Override
    public void clearApplicationUserData(@NonNull String packageName, @NonNull ClearDataCallback callback) {
        calls.add("clearApplicationUserData " + packageName);
        callback.onCleared(packageName, clearDataSucceeds);
    }
}
