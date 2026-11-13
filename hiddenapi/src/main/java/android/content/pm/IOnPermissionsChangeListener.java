// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import misc.utils.HiddenUtil;

/**
 * @deprecated Replaced in API 30 (Android R) with {@link android.permission.IOnPermissionsChangeListener}
 */
@Deprecated
public interface IOnPermissionsChangeListener extends IInterface {
    void onPermissionsChanged(int uid) throws RemoteException;

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
