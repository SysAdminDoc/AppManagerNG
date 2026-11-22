// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

/**
 * The calls NG makes into a Dhizuku server. {@link DhizukuApiClient} talks to the real one through
 * Dhizuku-API; tests pass a fake.
 */
public interface DhizukuClient {
    /**
     * Ask the device or profile owner for its Dhizuku binder, or check that the one already held is
     * alive.
     *
     * @return {@code false} when no Dhizuku server answered
     */
    @WorkerThread
    boolean connect(@NonNull Context context);

    /**
     * Whether Dhizuku allowed this app. Dhizuku keeps that answer in its own database, so the
     * platform permission check on {@link DhizukuBridge#PERMISSION_API} can't tell.
     */
    @WorkerThread
    boolean isPermissionGranted();

    /**
     * Open Dhizuku's prompt that asks the user to allow this app. The callback can run on any
     * thread.
     */
    void requestPermission(@NonNull PermissionCallback callback);

    /**
     * The admin component of the owner that answered {@link #connect(Context)}.
     */
    @NonNull
    ComponentName getOwnerComponent();

    /**
     * Wrap a system service binder so every call on it runs inside the Dhizuku server, as the
     * device owner.
     */
    @NonNull
    IBinder wrap(@NonNull IBinder binder);

    interface PermissionCallback {
        void onResult(boolean granted);
    }
}
