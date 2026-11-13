// SPDX-License-Identifier: Apache-2.0

package android.permission;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

@RequiresApi(Build.VERSION_CODES.R)
public interface IOnPermissionsChangeListener extends IInterface {
    void onPermissionsChanged(int uid) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // 14 r50
    void onPermissionsChanged(int uid, String persistentDeviceId) throws RemoteException;

    abstract class Stub extends Binder implements IOnPermissionsChangeListener {
        public static IOnPermissionsChangeListener asInterface(IBinder binder) {
            return HiddenUtil.throwUOE(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
