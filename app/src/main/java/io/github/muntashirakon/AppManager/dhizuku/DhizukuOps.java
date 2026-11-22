// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import android.annotation.UserIdInt;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.util.AndroidException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.AppManager.dhizuku.DhizukuCapabilities.Operation;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * The app operations NG runs through Dhizuku. Each works on the current user only: a device
 * owner's policy calls act on its own user.
 */
public final class DhizukuOps {
    private static final long CLEAR_DATA_TIMEOUT_SECONDS = 60;

    private DhizukuOps() {
    }

    /**
     * Whether {@code operation} can go through Dhizuku for {@code userId}, judged from the last
     * check. Cheap enough for the main thread.
     */
    @AnyThread
    public static boolean canRun(@NonNull Operation operation, @UserIdInt int userId) {
        return userId == UserHandleHidden.myUserId()
                && DhizukuCapabilities.canRun(DhizukuSession.getCachedState(), Build.VERSION.SDK_INT, operation);
    }

    @WorkerThread
    public static void setHidden(@NonNull String packageName, @UserIdInt int userId, boolean hidden)
            throws RemoteException {
        DhizukuDevicePolicy policy = requirePolicy(Operation.HIDE_APPS, userId);
        try {
            // false means nothing changed, which is fine when the app was already in that state
            if (!policy.setApplicationHidden(packageName, hidden) && policy.isApplicationHidden(packageName) != hidden) {
                throw remoteException("Android did not " + (hidden ? "hide " : "unhide ") + packageName
                        + " through Dhizuku.", null);
            }
        } catch (RuntimeException e) {
            throw remoteException("Dhizuku stopped answering.", e);
        }
    }

    @WorkerThread
    public static boolean isHidden(@NonNull String packageName, @UserIdInt int userId) throws RemoteException {
        DhizukuDevicePolicy policy = requirePolicy(Operation.HIDE_APPS, userId);
        try {
            return policy.isApplicationHidden(packageName);
        } catch (RuntimeException e) {
            throw remoteException("Dhizuku stopped answering.", e);
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @WorkerThread
    public static void setSuspended(@NonNull String packageName, @UserIdInt int userId, boolean suspended)
            throws RemoteException {
        DhizukuDevicePolicy policy = requirePolicy(Operation.SUSPEND_APPS, userId);
        String[] failed;
        try {
            failed = policy.setPackagesSuspended(new String[]{packageName}, suspended);
        } catch (RuntimeException e) {
            throw remoteException("Dhizuku stopped answering.", e);
        }
        if (failed != null) {
            for (String failedPackage : failed) {
                if (packageName.equals(failedPackage)) {
                    throw remoteException("Android did not " + (suspended ? "suspend " : "unsuspend ")
                            + packageName + " through Dhizuku.", null);
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @WorkerThread
    public static boolean isSuspended(@NonNull String packageName, @UserIdInt int userId) throws RemoteException {
        DhizukuDevicePolicy policy = requirePolicy(Operation.SUSPEND_APPS, userId);
        try {
            return policy.isPackageSuspended(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            throw remoteException(packageName + " is not installed.", e);
        } catch (RuntimeException e) {
            throw remoteException("Dhizuku stopped answering.", e);
        }
    }

    /**
     * Grant or revoke a runtime permission the way a root or ADB grant does.
     *
     * <p>A device owner's grant or denial also locks the permission, so the user can't change it
     * in Settings any more. Setting the state back to default right after takes the lock away and
     * keeps the grant or the denial.
     */
    @WorkerThread
    public static void setRuntimePermission(@NonNull String packageName, @NonNull String permission,
                                            @UserIdInt int userId, boolean grant) throws RemoteException {
        DhizukuDevicePolicy policy = requirePolicy(Operation.RUNTIME_PERMISSIONS, userId);
        Context context = ContextUtils.getContext();
        String action = (grant ? "grant " : "revoke ") + permission + " for " + packageName;
        try {
            if (!policy.setPermissionGrantState(packageName, permission, grant
                    ? DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    : DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED)) {
                // Android refuses apps that target 5.1 or lower, and sensor permissions for some owners
                throw remoteException("Android did not let Dhizuku " + action + ".", null);
            }
            policy.setPermissionGrantState(packageName, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        } catch (RuntimeException e) {
            throw remoteException("Dhizuku stopped answering.", e);
        }
        if (DhizukuSession.isGranted(context, permission, packageName) != grant) {
            throw remoteException("Dhizuku could not " + action + ".", null);
        }
    }

    /**
     * Clear an app's data and wait for Android to report the result.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    @WorkerThread
    public static void clearAppData(@NonNull String packageName, @UserIdInt int userId) throws AndroidException {
        DhizukuDevicePolicy policy = requirePolicy(Operation.CLEAR_APP_DATA, userId);
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean succeeded = new AtomicBoolean(false);
        try {
            policy.clearApplicationUserData(packageName, (clearedPackage, success) -> {
                succeeded.set(success);
                done.countDown();
            });
            if (!done.await(CLEAR_DATA_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AndroidException("Android did not report whether the data of " + packageName
                        + " was cleared.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AndroidException(e);
        } catch (RuntimeException e) {
            throw remoteException("Dhizuku stopped answering.", e);
        }
        if (!succeeded.get()) {
            throw new AndroidException("Android could not clear the data of " + packageName + " through Dhizuku.");
        }
    }

    @WorkerThread
    @NonNull
    private static DhizukuDevicePolicy requirePolicy(@NonNull Operation operation, @UserIdInt int userId)
            throws RemoteException {
        if (userId != UserHandleHidden.myUserId()) {
            throw remoteException("Dhizuku can only change apps in the current user.", null);
        }
        if (Build.VERSION.SDK_INT < operation.minSdk) {
            throw remoteException("This needs a newer Android version through Dhizuku.", null);
        }
        DhizukuDevicePolicy policy = DhizukuSession.policy(ContextUtils.getContext());
        if (policy == null) {
            throw remoteException("Dhizuku isn't available. Allow AppManagerNG in Dhizuku, then try again.", null);
        }
        return policy;
    }

    @NonNull
    private static RemoteException remoteException(@NonNull String message, @Nullable Throwable cause) {
        RemoteException exception = new RemoteException(message);
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }
}
