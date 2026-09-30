// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.os.Process;
import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.FileSystemManager;
import io.github.muntashirakon.io.ShizukuFileSystemService;

public class LocalServices {
    private static final Object sBindLock = new Object();
    private static final MutableLiveData<Boolean> sState = new MutableLiveData<>(false);

    @NonNull
    private static final ServiceConnectionWrapper sFileSystemServiceConnectionWrapper
            = new ServiceConnectionWrapper(BuildConfig.APPLICATION_ID, FileSystemService.class.getName());
    @NonNull
    private static final ShizukuServiceConnectionWrapper sShizukuFileSystemServiceConnectionWrapper
            = new ShizukuServiceConnectionWrapper(BuildConfig.APPLICATION_ID,
            ShizukuFileSystemService.class.getName(), "filesystem", "shizuku_fs");

    @WorkerThread
    public static void bindServicesIfNotAlready() throws RemoteException {
        // Checked under the bind lock: a caller that sees the services down while another thread
        // is still binding them would otherwise unbind that fresh binding and start over, and
        // whoever was using it gets a DeadObjectException.
        synchronized (sBindLock) {
            if (!activeServicesAlive()) {
                bindServices();
            }
        }
    }

    @WorkerThread
    public static void bindServices() throws RemoteException {
        synchronized (sBindLock) {
            unbindServicesIfRunning();
            try {
                if (Ops.isShizuku()) {
                    bindShizukuAmService();
                    bindShizukuFileSystemManager();
                } else {
                    bindAmService();
                    bindFileSystemManager();
                }
                // Both binders have to be up before anything relies on either
                if (!getAmService().asBinder().pingBinder() || !activeServicesAlive()) {
                    throw new RemoteException("A required service binder is not running.");
                }
                getFileSystemManager();
                // Update UID
                Ops.setWorkingUid(getAmService().getUid());
                sState.postValue(true);
            } catch (RemoteException | RuntimeException e) {
                // Don't leave one service running on its own
                stopServices();
                throw e;
            }
        }
    }

    /**
     * Whether the privileged services are bound, updated when they bind, stop or unbind.
     */
    @NonNull
    public static LiveData<Boolean> state() {
        return sState;
    }

    public static boolean alive() {
        return (sAMServiceConnectionWrapper.isBinderActive() && sFileSystemServiceConnectionWrapper.isBinderActive())
                || (sShizukuAMServiceConnectionWrapper.isBinderActive()
                && sShizukuFileSystemServiceConnectionWrapper.isBinderActive());
    }

    private static boolean activeServicesAlive() {
        if (Ops.isShizuku()) {
            return sShizukuAMServiceConnectionWrapper.isBinderActive()
                    && sShizukuFileSystemServiceConnectionWrapper.isBinderActive();
        }
        return sAMServiceConnectionWrapper.isBinderActive() && sFileSystemServiceConnectionWrapper.isBinderActive();
    }

    // The bind methods wait up to 45 seconds for a service and must not hold the wrapper's monitor
    // while they do: unbindServices() takes the same monitors on the main thread.
    @WorkerThread
    @NoOps(used = true)
    private static void bindFileSystemManager() throws RemoteException {
        sFileSystemServiceConnectionWrapper.bindService();
    }

    @WorkerThread
    @NoOps(used = true)
    private static void bindShizukuFileSystemManager() throws RemoteException {
        sShizukuFileSystemServiceConnectionWrapper.bindService();
    }

    @AnyThread
    @NonNull
    @NoOps(used = true)
    public static FileSystemManager getFileSystemManager() throws RemoteException {
        if (Ops.isShizuku() && sShizukuFileSystemServiceConnectionWrapper.isBinderActive()) {
            synchronized (sShizukuFileSystemServiceConnectionWrapper) {
                try {
                    return FileSystemManager.getRemote(sShizukuFileSystemServiceConnectionWrapper.getService());
                } finally {
                    sShizukuFileSystemServiceConnectionWrapper.notifyAll();
                }
            }
        }
        synchronized (sFileSystemServiceConnectionWrapper) {
            try {
                return FileSystemManager.getRemote(sFileSystemServiceConnectionWrapper.getService());
            } finally {
                sFileSystemServiceConnectionWrapper.notifyAll();
            }
        }
    }

    @NonNull
    private static final ServiceConnectionWrapper sAMServiceConnectionWrapper
            = new ServiceConnectionWrapper(BuildConfig.APPLICATION_ID, AMService.class.getName());
    @NonNull
    private static final ShizukuServiceConnectionWrapper sShizukuAMServiceConnectionWrapper
            = new ShizukuServiceConnectionWrapper(BuildConfig.APPLICATION_ID,
            ShizukuAMService.class.getName(), "am", "shizuku_am");

    @WorkerThread
    @NoOps(used = true)
    private static void bindAmService() throws RemoteException {
        sAMServiceConnectionWrapper.bindService();
    }

    @WorkerThread
    @NoOps(used = true)
    private static void bindShizukuAmService() throws RemoteException {
        sShizukuAMServiceConnectionWrapper.bindService();
    }

    @AnyThread
    @NonNull
    @NoOps(used = true)
    public static IAMService getAmService() throws RemoteException {
        if (Ops.isShizuku() && sShizukuAMServiceConnectionWrapper.isBinderActive()) {
            synchronized (sShizukuAMServiceConnectionWrapper) {
                try {
                    return IAMService.Stub.asInterface(sShizukuAMServiceConnectionWrapper.getService());
                } finally {
                    sShizukuAMServiceConnectionWrapper.notifyAll();
                }
            }
        }
        synchronized (sAMServiceConnectionWrapper) {
            try {
                return IAMService.Stub.asInterface(sAMServiceConnectionWrapper.getService());
            } finally {
                sAMServiceConnectionWrapper.notifyAll();
            }
        }
    }

    @WorkerThread
    @NoOps(used = true)
    public static void stopServices() {
        synchronized (sAMServiceConnectionWrapper) {
            sAMServiceConnectionWrapper.stopDaemon();
        }
        synchronized (sFileSystemServiceConnectionWrapper) {
            sFileSystemServiceConnectionWrapper.stopDaemon();
        }
        synchronized (sShizukuAMServiceConnectionWrapper) {
            sShizukuAMServiceConnectionWrapper.stopDaemon();
        }
        synchronized (sShizukuFileSystemServiceConnectionWrapper) {
            sShizukuFileSystemServiceConnectionWrapper.stopDaemon();
        }
        Ops.setWorkingUid(Process.myUid());
        sState.postValue(false);
    }

    @MainThread
    public static void unbindServices() {
        synchronized (sAMServiceConnectionWrapper) {
            sAMServiceConnectionWrapper.unbindService();
        }
        synchronized (sFileSystemServiceConnectionWrapper) {
            sFileSystemServiceConnectionWrapper.unbindService();
        }
        synchronized (sShizukuAMServiceConnectionWrapper) {
            sShizukuAMServiceConnectionWrapper.unbindService();
        }
        synchronized (sShizukuFileSystemServiceConnectionWrapper) {
            sShizukuFileSystemServiceConnectionWrapper.unbindService();
        }
        Ops.setWorkingUid(Process.myUid());
        sState.postValue(false);
    }

    @WorkerThread
    private static void unbindServicesIfRunning() {
        // Basically unregister the services so that we can open another connection
        CountDownLatch unbindWatcher = new CountDownLatch(1);
        ThreadUtils.postOnMainThread(() -> {
            try {
                unbindServices();
            } finally {
                unbindWatcher.countDown();
            }
        });
        try {
            unbindWatcher.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException ignore) {
        }
    }
}
