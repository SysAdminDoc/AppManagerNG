// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.content.Context;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ShizukuFileSystemService extends FileSystemService {
    private static final int TRANSACTION_DESTROY = 16777115;

    public ShizukuFileSystemService() {
    }

    @Keep
    public ShizukuFileSystemService(@NonNull Context context) {
    }

    public void destroy() {
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
