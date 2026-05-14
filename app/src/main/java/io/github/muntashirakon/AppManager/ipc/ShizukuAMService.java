// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.content.Context;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.logs.Log;

public class ShizukuAMService extends AMService.IAMServiceImpl {
    private static final String TAG = ShizukuAMService.class.getSimpleName();
    private static final int TRANSACTION_DESTROY = 16777115;

    public ShizukuAMService() {
        Log.d(TAG, "constructor");
    }

    @Keep
    public ShizukuAMService(@NonNull Context context) {
        Log.d(TAG, "constructor with context");
    }

    public void destroy() {
        Log.d(TAG, "destroy");
        System.exit(0);
    }

    @Override
    public boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags)
            throws RemoteException {
        if (code == TRANSACTION_DESTROY) {
            destroy();
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }
}
