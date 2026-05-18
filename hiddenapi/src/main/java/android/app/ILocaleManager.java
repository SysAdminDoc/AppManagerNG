// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.LocaleList;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public interface ILocaleManager extends IInterface {
    abstract class Stub extends Binder implements ILocaleManager {
        public static ILocaleManager asInterface(IBinder binder) {
            return HiddenUtil.throwUOE(binder);
        }
    }

    void setApplicationLocales(String appPackageName, int userId, LocaleList locales, boolean fromDelegate)
            throws RemoteException;

    LocaleList getApplicationLocales(String appPackageName, int userId) throws RemoteException;
}
