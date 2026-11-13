// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import android.content.Context;
import android.content.pm.IPackageManager;
import android.os.Build;
import android.os.IInterface;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.permission.IPermissionManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.internal.app.IAppOpsCallback;
import com.android.internal.app.IAppOpsService;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The system listeners behind {@link PermissionWatchRelay}. Only the privileged process creates this: the calls need
 * {@code OBSERVE_GRANT_REVOKE_PERMISSIONS} and {@code WATCH_APPOPS}, which the shell user and root hold.
 */
final class PermissionWatchListeners implements PermissionWatchRelay.Listeners {
    /** {@code AppOpsManager.OP_NONE}: with a package name, every op of that package. */
    private static final int OP_NONE = -1;

    @Nullable
    private IInterface mPermissionListener;
    @Nullable
    private IAppOpsCallback.Stub mAppOpsCallback;
    @NonNull
    private Set<String> mAppOpPackages = Collections.emptySet();

    @Override
    public synchronized void watch(@NonNull PermissionWatchRelay relay, @NonNull Set<String> packages)
            throws Exception {
        Exception failure = null;
        if (mPermissionListener == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mPermissionListener = addPermissionListener(relay);
            } catch (Exception e) {
                failure = e;
            }
        }
        if (!packages.equals(mAppOpPackages)) {
            try {
                IAppOpsService appOps = appOpsService();
                if (mAppOpsCallback == null) {
                    mAppOpsCallback = new IAppOpsCallback.Stub() {
                        @Override
                        public void opChanged(int op, int uid, String packageName) {
                            relay.onChanged(uid, packageName);
                        }

                        @Override
                        public void opChanged(int op, int uid, String packageName, String persistentDeviceId) {
                            relay.onChanged(uid, packageName);
                        }
                    };
                } else {
                    appOps.stopWatchingMode(mAppOpsCallback);
                }
                mAppOpPackages = Collections.emptySet();
                for (String packageName : packages) {
                    appOps.startWatchingMode(OP_NONE, packageName, mAppOpsCallback);
                }
                mAppOpPackages = new HashSet<>(packages);
            } catch (Exception e) {
                failure = e;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public synchronized void unwatch() {
        if (mPermissionListener != null) {
            try {
                removePermissionListener(mPermissionListener);
            } catch (Exception ignore) {
                // The listener goes with this process anyway
            }
            mPermissionListener = null;
        }
        if (mAppOpsCallback != null) {
            try {
                appOpsService().stopWatchingMode(mAppOpsCallback);
            } catch (Exception ignore) {
            }
            mAppOpsCallback = null;
        }
        mAppOpPackages = Collections.emptySet();
    }

    @NonNull
    private static IAppOpsService appOpsService() {
        return IAppOpsService.Stub.asInterface(ServiceManager.getService(Context.APP_OPS_SERVICE));
    }

    @SuppressWarnings("deprecation")
    @RequiresApi(Build.VERSION_CODES.M)
    @NonNull
    private static IInterface addPermissionListener(@NonNull PermissionWatchRelay relay) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.permission.IOnPermissionsChangeListener listener =
                    new android.permission.IOnPermissionsChangeListener.Stub() {
                        @Override
                        public void onPermissionsChanged(int uid) {
                            relay.onChanged(uid, null);
                        }

                        @Override
                        public void onPermissionsChanged(int uid, String persistentDeviceId) {
                            relay.onChanged(uid, null);
                        }
                    };
            IPermissionManager.Stub.asInterface(ServiceManager.getService("permissionmgr"))
                    .addOnPermissionsChangeListener(listener);
            return listener;
        }
        android.content.pm.IOnPermissionsChangeListener listener =
                new android.content.pm.IOnPermissionsChangeListener.Stub() {
                    @Override
                    public void onPermissionsChanged(int uid) {
                        relay.onChanged(uid, null);
                    }
                };
        IPackageManager.Stub.asInterface(ServiceManager.getService("package"))
                .addOnPermissionsChangeListener(listener);
        return listener;
    }

    @SuppressWarnings("deprecation")
    private static void removePermissionListener(@NonNull IInterface listener) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            IPermissionManager.Stub.asInterface(ServiceManager.getService("permissionmgr"))
                    .removeOnPermissionsChangeListener((android.permission.IOnPermissionsChangeListener) listener);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            IPackageManager.Stub.asInterface(ServiceManager.getService("package"))
                    .removeOnPermissionsChangeListener((android.content.pm.IOnPermissionsChangeListener) listener);
        }
    }
}
