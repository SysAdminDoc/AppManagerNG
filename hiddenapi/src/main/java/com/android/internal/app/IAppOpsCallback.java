// SPDX-License-Identifier: Apache-2.0

package com.android.internal.app;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

public interface IAppOpsCallback extends IInterface {
    void opChanged(int op, int uid, String packageName) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    void opChanged(int op, int uid, String packageName, String persistentDeviceId) throws RemoteException;

    abstract class Stub extends Binder implements IAppOpsCallback {
        public static IAppOpsCallback asInterface(IBinder binder) {
            return HiddenUtil.throwUOE(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
