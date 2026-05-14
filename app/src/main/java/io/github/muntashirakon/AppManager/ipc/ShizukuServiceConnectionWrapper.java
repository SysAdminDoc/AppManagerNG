// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import rikka.shizuku.Shizuku;

class ShizukuServiceConnectionWrapper {
    private static final String TAG = ShizukuServiceConnectionWrapper.class.getSimpleName();

    @Nullable
    private IBinder mIBinder;
    @Nullable
    private CountDownLatch mServiceBoundWatcher;

    private class ServiceConnectionImpl implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "service onServiceConnected: %s", name);
            mIBinder = service;
            onResponseReceived();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "service onServiceDisconnected: %s", name);
            mIBinder = null;
            onResponseReceived();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.d(TAG, "service onBindingDied: %s", name);
            mIBinder = null;
            onResponseReceived();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.d(TAG, "service onNullBinding: %s", name);
            mIBinder = null;
            onResponseReceived();
        }

        private void onResponseReceived() {
            if (mServiceBoundWatcher != null) {
                mServiceBoundWatcher.countDown();
            } else throw new RuntimeException("Service watcher should never be null!");
        }
    }

    @NonNull
    private final ComponentName mComponentName;
    @NonNull
    private final String mTag;
    @NonNull
    private final String mProcessNameSuffix;
    @NonNull
    private final ServiceConnectionImpl mServiceConnection;

    ShizukuServiceConnectionWrapper(@NonNull String pkgName, @NonNull String className,
                                    @NonNull String tag, @NonNull String processNameSuffix) {
        mComponentName = new ComponentName(pkgName, className);
        mTag = tag;
        mProcessNameSuffix = processNameSuffix;
        mServiceConnection = new ServiceConnectionImpl();
    }

    @NonNull
    public IBinder getService() throws RemoteException {
        if (!isBinderActive()) {
            throw new RemoteException("Binder not running.");
        }
        return Objects.requireNonNull(mIBinder);
    }

    @NonNull
    public IBinder bindService() throws RemoteException {
        synchronized (mServiceConnection) {
            if (!isBinderActive()) {
                startDaemon();
            }
            return getService();
        }
    }

    @MainThread
    public void unbindService() {
        synchronized (mServiceConnection) {
            unbindService(false);
        }
    }

    @WorkerThread
    private void startDaemon() throws RemoteException {
        synchronized (mServiceConnection) {
            if (isBinderActive()) {
                Log.d(TAG, "Binder is already active?");
                return;
            }
            if (!ShizukuBridge.supportsUserService()) {
                throw new RemoteException("Shizuku UserService is unavailable.");
            }
            if (!ShizukuBridge.hasPermission()) {
                throw new RemoteException("Shizuku permission not granted.");
            }
            mServiceBoundWatcher = new CountDownLatch(1);
            Log.d(TAG, "Launching Shizuku user service...");
            try {
                Shizuku.bindUserService(getUserServiceArgs(), mServiceConnection);
                if (!mServiceBoundWatcher.await(45, TimeUnit.SECONDS) || !isBinderActive()) {
                    throw new RemoteException("Shizuku user service was not bound.");
                }
            } catch (RemoteException e) {
                throw e;
            } catch (Throwable e) {
                throw asRemoteException("Could not bind Shizuku user service.", e);
            }
        }
    }

    @WorkerThread
    public void stopDaemon() {
        synchronized (mServiceConnection) {
            unbindService(true);
            mIBinder = null;
        }
    }

    private void unbindService(boolean remove) {
        if (!ShizukuBridge.isBinderAlive()) {
            mIBinder = null;
            return;
        }
        try {
            Shizuku.unbindUserService(getUserServiceArgs(), mServiceConnection, remove);
        } catch (Throwable e) {
            Log.e(TAG, "Could not unbind Shizuku user service.", e);
        } finally {
            mIBinder = null;
        }
    }

    boolean isBinderActive() {
        return mIBinder != null && mIBinder.pingBinder();
    }

    @NonNull
    private Shizuku.UserServiceArgs getUserServiceArgs() {
        return new Shizuku.UserServiceArgs(mComponentName)
                .daemon(false)
                .debuggable(BuildConfig.DEBUG)
                .processNameSuffix(mProcessNameSuffix)
                .tag(mTag)
                .version(BuildConfig.VERSION_CODE);
    }

    @NonNull
    private static RemoteException asRemoteException(@NonNull String message, @NonNull Throwable cause) {
        RemoteException exception = new RemoteException(message + " " + cause.getMessage());
        exception.initCause(cause);
        return exception;
    }
}
