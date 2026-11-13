// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import aosp.android.content.pm.ParceledListSlice;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.IRemoteProcess;
import io.github.muntashirakon.AppManager.IRemoteShell;
import io.github.muntashirakon.AppManager.ipc.ps.ProcessEntry;
import io.github.muntashirakon.AppManager.ipc.ps.Ps;
import io.github.muntashirakon.AppManager.permission.watcher.PermissionWatchRelay;
import io.github.muntashirakon.AppManager.server.common.IRootServiceManager;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.compat.android16.Android16BinderCompat;
import io.github.muntashirakon.compat.os.ParcelCompat2;

public class AMService extends RootService {
    static class IAMServiceImpl extends IAMService.Stub {
        /** A context of this package in the privileged process; {@code null} where the host gave none. */
        @Nullable
        private final Context mContext;

        IAMServiceImpl(@Nullable Context context) {
            mContext = context;
        }

        /**
         * To get {@link Process}, wrap it using {@link RemoteProcess}. Since the streams are piped,
         * I/O operations may have to be done in different threads.
         */
        @Override
        public IRemoteProcess newProcess(String[] cmd, String[] env, String dir) throws RemoteException {
            Process process;
            try {
                process = Runtime.getRuntime().exec(cmd, env, dir != null ? new File(dir) : null);
            } catch (Exception e) {
                throw new RemoteException(e.getMessage());
            }
            return new RemoteProcessImpl(process);
        }

        @Override
        public IRemoteShell getShell(String[] cmd) {
            return new RemoteShellImpl(cmd);
        }

        @Override
        public ParceledListSlice<ProcessEntry> getRunningProcesses() {
            Ps ps = new Ps();
            ps.loadProcesses();
            return new ParceledListSlice<>(ps.getProcesses());
        }

        @Override
        public int getUid() {
            return android.os.Process.myUid();
        }

        @Override
        public void symlink(String file, String link) throws RemoteException {
            try {
                Os.symlink(file, link);
            } catch (ErrnoException e) {
                throw new RemoteException(e.getMessage());
            }
        }

        @Override
        public IBinder getService(String serviceName) throws RemoteException {
            return ServiceManager.getService(serviceName);
        }

        @Override
        public void setPermissionWatchTargets(int[] uids, String[] packageNames) {
            Context context = mContext != null ? mContext : ContextUtils.rootContext;
            if (context == null) {
                Log.w(TAG, "No context to reach the app from, so permission changes aren't relayed.");
                return;
            }
            PermissionWatchRelay.getInstance(context).setTargets(Binder.getCallingUid(), uids, packageNames);
        }

        @Override
        public boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
            if (code == ProxyBinder.PROXY_BINDER_TRANSACTION) {
                data.enforceInterface(IRootServiceManager.class.getName());
                transactRemote(data, reply);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        /**
         * Call target Binder received through {@link ProxyBinder}.
         *
         * @author Rikka
         */
        private void transactRemote(@NonNull Parcel data, @Nullable Parcel reply) throws RemoteException {
            IBinder targetBinder = data.readStrongBinder();
            int targetCode = data.readInt();
            int targetFlags = data.readInt();

            Parcel newData = ParcelCompat2.obtain(targetBinder);
            try {
                newData.appendFrom(data, data.dataPosition(), data.dataAvail());
                long id = Binder.clearCallingIdentity();
                try {
                    Android16BinderCompat.transact(targetBinder, targetCode, newData, reply, targetFlags);
                } finally {
                    Binder.restoreCallingIdentity(id);
                }
            } catch (RemoteException e) {
                throw e;
            } catch (Throwable th) {
                throw (RemoteException) new RemoteException(th.getMessage()).initCause(th);
            } finally {
                newData.recycle();
            }
        }
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Log.d(TAG, "AMService: onBind");
        return new IAMServiceImpl(this);
    }

    @Override
    public void onRebind(@NonNull Intent intent) {
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(@NonNull Intent intent) {
        return true;
    }
}
