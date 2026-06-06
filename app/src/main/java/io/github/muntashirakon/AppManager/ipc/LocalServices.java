// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.os.Process;
import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

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

    @NonNull
    private static final ServiceConnectionWrapper sFileSystemServiceConnectionWrapper
            = new ServiceConnectionWrapper(BuildConfig.APPLICATION_ID, FileSystemService.class.getName());
    @NonNull
    private static final ShizukuServiceConnectionWrapper sShizukuFileSystemServiceConnectionWrapper
            = new ShizukuServiceConnectionWrapper(BuildConfig.APPLICATION_ID,
            ShizukuFileSystemService.class.getName(), "filesystem", "shizuku_fs");

    @WorkerThread
    public static void bindServicesIfNotAlready() throws RemoteException {
        if (!activeServicesAlive()) {
            bindServices();
        }
    }

    @WorkerThread
    public static void bindServices() throws RemoteException {
        synchronized (sBindLock) {
            unbindServicesIfRunning();
            if (Ops.isShizuku()) {
                bindShizukuAmService();
                bindShizukuFileSystemManager();
            } else {
                bindAmService();
                bindFileSystemManager();
            }
            // Verify binding
            if (!getAmService().asBinder().pingBinder()) {
                throw new RemoteException("IAmService not running.");
            }
            getFileSystemManager();
            // Update UID
            Ops.setWorkingUid(getAmService().getUid());
        }
    }

    public static boolean alive() {
        synchronized (sAMServiceConnectionWrapper) {
            if (sAMServiceConnectionWrapper.isBinderActive()) {
                return true;
            }
        }
        synchronized (sShizukuAMServiceConnectionWrapper) {
            return sShizukuAMServiceConnectionWrapper.isBinderActive();
        }
    }

    private static boolean activeServicesAlive() {
        if (Ops.isShizuku()) {
            synchronized (sShizukuAMServiceConnectionWrapper) {
                return sShizukuAMServiceConnectionWrapper.isBinderActive();
            }
        }
        synchronized (sAMServiceConnectionWrapper) {
            return sAMServiceConnectionWrapper.isBinderActive();
        }
    }

    @WorkerThread
    @NoOps(used = true)
    private static void bindFileSystemManager() throws RemoteException {
        synchronized (sFileSystemServiceConnectionWrapper) {
            try {
                sFileSystemServiceConnectionWrapper.bindService();
            } finally {
                sFileSystemServiceConnectionWrapper.notifyAll();
            }
        }
    }

    @WorkerThread
    @NoOps(used = true)
    private static void bindShizukuFileSystemManager() throws RemoteException {
        synchronized (sShizukuFileSystemServiceConnectionWrapper) {
            try {
                sShizukuFileSystemServiceConnectionWrapper.bindService();
            } finally {
                sShizukuFileSystemServiceConnectionWrapper.notifyAll();
            }
        }
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
        synchronized (sAMServiceConnectionWrapper) {
            try {
                sAMServiceConnectionWrapper.bindService();
            } finally {
                sAMServiceConnectionWrapper.notifyAll();
            }
        }
    }

    @WorkerThread
    @NoOps(used = true)
    private static void bindShizukuAmService() throws RemoteException {
        synchronized (sShizukuAMServiceConnectionWrapper) {
            try {
                sShizukuAMServiceConnectionWrapper.bindService();
            } finally {
                sShizukuAMServiceConnectionWrapper.notifyAll();
            }
        }
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
