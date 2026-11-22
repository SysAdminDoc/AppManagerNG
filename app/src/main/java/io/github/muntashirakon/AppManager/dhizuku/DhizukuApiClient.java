// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.rosan.dhizuku.api.Dhizuku;
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * The only class that touches Dhizuku-API. The library declares minSdk 23, so nothing loads this
 * class below Android 6.0 (see docs/policy/minsdk-21-ceiling.md).
 */
@RequiresApi(Build.VERSION_CODES.M)
final class DhizukuApiClient implements DhizukuClient {
    private static final String TAG = DhizukuApiClient.class.getSimpleName();

    @Override
    public boolean connect(@NonNull Context context) {
        try {
            return Dhizuku.init(context.getApplicationContext());
        } catch (Throwable e) {
            Log.w(TAG, "Could not reach a Dhizuku server.", e);
            return false;
        }
    }

    @Override
    public boolean isPermissionGranted() {
        try {
            return Dhizuku.isPermissionGranted();
        } catch (RuntimeException e) {
            // The server went away between connect() and this call
            Log.w(TAG, "Could not ask Dhizuku whether this app is allowed.", e);
            return false;
        }
    }

    @Override
    public void requestPermission(@NonNull PermissionCallback callback) {
        Dhizuku.requestPermission(new DhizukuRequestPermissionListener() {
            @Override
            public void onRequestPermission(int grantResult) {
                callback.onResult(grantResult == PackageManager.PERMISSION_GRANTED);
            }
        });
    }

    @NonNull
    @Override
    public ComponentName getOwnerComponent() {
        return Dhizuku.getOwnerComponent();
    }

    @NonNull
    @Override
    public IBinder wrap(@NonNull IBinder binder) {
        return Dhizuku.binderWrapper(binder);
    }
}
