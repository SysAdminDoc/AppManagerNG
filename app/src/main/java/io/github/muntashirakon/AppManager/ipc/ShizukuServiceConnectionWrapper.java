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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import rikka.shizuku.Shizuku;

class ShizukuServiceConnectionWrapper {
    private static final String TAG = ShizukuServiceConnectionWrapper.class.getSimpleName();

    // Both fields are written from the Shizuku binder-callback thread
    // (ServiceConnectionImpl) and read from worker threads (getService /
    // isBinderActive / bindService). They must be volatile so a worker observes
    // the callback's write — otherwise a worker can see a stale null after
    // connect (spurious "Binder not running") or a stale dead binder after
    // disconnect.
    @Nullable
    private volatile IBinder mIBinder;
    @Nullable
    private volatile CountDownLatch mServiceBoundWatcher;

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
            // Read once into a local: the field can be nulled/replaced by a
            // concurrent bind on another thread.
            CountDownLatch watcher = mServiceBoundWatcher;
            if (watcher != null) {
                watcher.countDown();
            }
            // A framework binder callback can legitimately arrive outside an
            // active bind window — a delayed or duplicate onServiceDisconnected/
            // onBindingDied, or a callback for a previous bind whose await()
            // already timed out. That is not a programming error, so never
            // throw here: this runs on a binder thread and would crash the
            // whole process.
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
        IBinder binder = mIBinder;
        if (binder == null || !binder.pingBinder()) {
            throw new RemoteException("Binder not running.");
        }
        return binder;
    }

    @NonNull
    public IBinder bindService() throws RemoteException {
        if (!isBinderActive()) {
            startDaemon();
        }
        return getService();
    }

    @MainThread
    public void unbindService() {
        synchronized (mServiceConnection) {
            unbindService(false);
        }
    }

    @WorkerThread
    private void startDaemon() throws RemoteException {
        CountDownLatch watcher;
        synchronized (mServiceConnection) {
            if (isBinderActive()) {
                Log.d(TAG, "Binder is already active?");
                return;
            }
            watcher = mServiceBoundWatcher;
            if (watcher == null) {
                // Nobody is binding yet. A second caller waits for this bind instead of starting its own.
                if (!ShizukuBridge.supportsUserService()) {
                    throw new RemoteException("Shizuku UserService is unavailable.");
                }
                if (!ShizukuBridge.hasPermission()) {
                    throw new RemoteException("Shizuku permission not granted.");
                }
                watcher = new CountDownLatch(1);
                mServiceBoundWatcher = watcher;
                Log.d(TAG, "Launching Shizuku user service...");
                try {
                    Shizuku.bindUserService(getUserServiceArgs(), mServiceConnection);
                } catch (Throwable e) {
                    mServiceBoundWatcher = null;
                    throw asRemoteException("Could not bind Shizuku user service.", e);
                }
            }
        }
        // Wait without the lock: unbindService() takes it on the main thread, and stopDaemon()
        // takes it to cancel a bind that never gets an answer.
        try {
            if (!watcher.await(45, TimeUnit.SECONDS) || !isBinderActive()) {
                throw new RemoteException("Shizuku user service was not bound.");
            }
        } catch (InterruptedException e) {
            throw asRemoteException("Could not bind Shizuku user service.", e);
        } finally {
            synchronized (mServiceConnection) {
                if (mServiceBoundWatcher == watcher) {
                    mServiceBoundWatcher = null;
                }
            }
        }
    }

    @WorkerThread
    public void stopDaemon() {
        CountDownLatch watcher;
        synchronized (mServiceConnection) {
            unbindService(true);
            mIBinder = null;
            watcher = mServiceBoundWatcher;
            mServiceBoundWatcher = null;
        }
        if (watcher != null) {
            // Release a bind still waiting for the service
            watcher.countDown();
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
        IBinder binder = mIBinder;
        return binder != null && binder.pingBinder();
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
