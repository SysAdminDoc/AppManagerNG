// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

class ServiceConnectionWrapper {
    public static final String TAG = ServiceConnectionWrapper.class.getSimpleName();

    // RootService callbacks can arrive on a binder thread while callers are
    // waiting from worker threads. Keep these fields visible across both sides.
    @Nullable
    private volatile IBinder mIBinder;
    @Nullable
    private volatile CountDownLatch mServiceBoundWatcher;
    // Set by stopDaemon(): a connection that arrives after it belongs to a bind nobody wants
    private boolean mStopped;

    private class ServiceConnectionImpl implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "service onServiceConnected: %s", name);
            boolean stopped;
            synchronized (this) {
                stopped = mStopped;
                if (!stopped) {
                    mIBinder = service;
                }
            }
            if (stopped) {
                Intent intent = new Intent().setComponent(mComponentName);
                ThreadUtils.postOnMainThread(() -> RootService.stop(intent));
                return;
            }
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
            // Framework/service callbacks can legitimately arrive outside an
            // active bind wait, for example after an earlier wait timed out or
            // after the service was explicitly stopped. This callback runs on a
            // binder thread, so never throw here.
        }
    }

    @NonNull
    private final ComponentName mComponentName;
    @NonNull
    private final ServiceConnectionImpl mServiceConnection;

    public ServiceConnectionWrapper(@NonNull String pkgName, @NonNull String className) {
        this(new ComponentName(pkgName, className));
    }

    public ServiceConnectionWrapper(@NonNull ComponentName cn) {
        mComponentName = cn;
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
    @NoOps(used = true)
    public IBinder bindService() throws RemoteException {
        if (!isBinderActive()) {
            startDaemon();
        }
        return getService();
    }

    @MainThread
    public void unbindService() {
        synchronized (mServiceConnection) {
            RootService.unbind(mServiceConnection);
        }
    }

    @WorkerThread
    private void startDaemon() {
        CountDownLatch watcher;
        synchronized (mServiceConnection) {
            if (isBinderActive()) {
                Log.d(TAG, "Binder is already active?");
                return;
            }
            watcher = mServiceBoundWatcher;
            if (watcher == null) {
                // Nobody is binding yet. A second caller waits for this bind instead of starting its own.
                watcher = new CountDownLatch(1);
                mServiceBoundWatcher = watcher;
                mStopped = false;
                Log.d(TAG, "Launching service...");
                Intent intent = new Intent();
                intent.setComponent(mComponentName);
                ThreadUtils.postOnMainThread(() -> {
                    if (mIBinder != null) {
                        RootService.stop(intent);
                    }
                    RootService.bind(intent, mServiceConnection);
                });
            }
        }
        // Wait without the lock: unbindService() takes it on the main thread, and stopDaemon()
        // takes it to cancel a bind that never gets an answer.
        try {
            watcher.await(45, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Service watcher interrupted.");
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
        Intent intent = new Intent();
        intent.setComponent(mComponentName);
        CountDownLatch watcher;
        synchronized (mServiceConnection) {
            mStopped = true;
            mIBinder = null;
            watcher = mServiceBoundWatcher;
            mServiceBoundWatcher = null;
            // Posted under the lock, so a bind that starts after this stop also reaches the main
            // thread after it. Posted outside, a new bind could run first and then be stopped.
            ThreadUtils.postOnMainThread(() -> RootService.stop(intent));
        }
        if (watcher != null) {
            // Release a bind still waiting for the service
            watcher.countDown();
        }
    }

    boolean isBinderActive() {
        IBinder binder = mIBinder;
        return binder != null && binder.pingBinder();
    }
}
