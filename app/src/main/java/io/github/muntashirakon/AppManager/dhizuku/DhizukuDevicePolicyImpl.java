// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.ServiceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * A {@link DevicePolicyManager} whose binder runs every call inside the Dhizuku server.
 *
 * <p>It is a new manager built on the owner's package context, never the app's cached one: the
 * manager sends its context's package name along with the admin component, and Android checks
 * that name against the calling uid, which is Dhizuku's.
 */
@RequiresApi(Build.VERSION_CODES.M)
final class DhizukuDevicePolicyImpl implements DhizukuDevicePolicy {
    @NonNull
    private final DevicePolicyManager mManager;
    @NonNull
    private final ComponentName mAdmin;

    @NonNull
    static DhizukuDevicePolicyImpl create(@NonNull Context context, @NonNull DhizukuClient client)
            throws ReflectiveOperationException, PackageManager.NameNotFoundException {
        ComponentName admin = client.getOwnerComponent();
        Context ownerContext = context.getApplicationContext().createPackageContext(admin.getPackageName(), 0);
        IBinder service = ServiceManager.getService(Context.DEVICE_POLICY_SERVICE);
        if (service == null) {
            throw new IllegalStateException("The device policy service is missing.");
        }
        return new DhizukuDevicePolicyImpl(newManager(ownerContext, client.wrap(service)), admin);
    }

    private DhizukuDevicePolicyImpl(@NonNull DevicePolicyManager manager, @NonNull ComponentName admin) {
        mManager = manager;
        mAdmin = admin;
    }

    // IDevicePolicyManager isn't in the hidden API stubs, so it's reached by name. The manager's
    // own methods keep the AIDL signature differences between Android versions out of NG.
    @SuppressLint({"PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi"})
    @NonNull
    private static DevicePolicyManager newManager(@NonNull Context ownerContext, @NonNull IBinder binder)
            throws ReflectiveOperationException {
        Class<?> serviceClass = Class.forName("android.app.admin.IDevicePolicyManager");
        Object service = Class.forName("android.app.admin.IDevicePolicyManager$Stub")
                .getMethod("asInterface", IBinder.class)
                .invoke(null, binder);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Constructor<DevicePolicyManager> constructor =
                        DevicePolicyManager.class.getDeclaredConstructor(Context.class, serviceClass);
                constructor.setAccessible(true);
                return constructor.newInstance(ownerContext, service);
            } catch (NoSuchMethodException ignore) {
                // Use the field below
            }
        }
        // Android 6.0 has no constructor that takes the service. A package context caches its
        // own manager, so changing this one leaves the app's manager alone.
        DevicePolicyManager manager = (DevicePolicyManager) ownerContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("The owner context has no device policy manager.");
        }
        Field field = DevicePolicyManager.class.getDeclaredField("mService");
        field.setAccessible(true);
        field.set(manager, service);
        return manager;
    }

    @Override
    public boolean setApplicationHidden(@NonNull String packageName, boolean hidden) {
        return mManager.setApplicationHidden(mAdmin, packageName, hidden);
    }

    @Override
    public boolean isApplicationHidden(@NonNull String packageName) {
        return mManager.isApplicationHidden(mAdmin, packageName);
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @Nullable
    @Override
    public String[] setPackagesSuspended(@NonNull String[] packageNames, boolean suspended) {
        return mManager.setPackagesSuspended(mAdmin, packageNames, suspended);
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @Override
    public boolean isPackageSuspended(@NonNull String packageName) throws PackageManager.NameNotFoundException {
        return mManager.isPackageSuspended(mAdmin, packageName);
    }

    @Override
    public boolean setPermissionGrantState(@NonNull String packageName, @NonNull String permission, int grantState) {
        return mManager.setPermissionGrantState(mAdmin, packageName, permission, grantState);
    }

    @Override
    public int getPermissionGrantState(@NonNull String packageName, @NonNull String permission) {
        return mManager.getPermissionGrantState(mAdmin, packageName, permission);
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Override
    public void clearApplicationUserData(@NonNull String packageName, @NonNull ClearDataCallback callback) {
        mManager.clearApplicationUserData(mAdmin, packageName, Runnable::run, callback::onCleared);
    }
}
