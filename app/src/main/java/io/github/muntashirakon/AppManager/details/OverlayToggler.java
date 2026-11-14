// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Turns overlays on or off and reads the real state back from the overlay manager afterwards,
 * whether the change went through or not, so a switch never shows a state Android doesn't have.
 * A change to an overlay that's still being changed is refused.
 */
final class OverlayToggler {
    interface Applier {
        @WorkerThread
        boolean setEnabled(@NonNull String overlayPackage, boolean enable) throws RemoteException;
    }

    interface Callback {
        @WorkerThread
        void onFinished(boolean success);
    }

    private static final String TAG = OverlayToggler.class.getSimpleName();

    @NonNull
    private final Executor mExecutor;
    @NonNull
    private final Applier mApplier;
    @NonNull
    private final Runnable mReload;
    private final Set<String> mChanging = new HashSet<>();

    /**
     * @param reload reads every overlay's state from the overlay manager and shows it
     */
    OverlayToggler(@NonNull Executor executor, @NonNull Applier applier, @NonNull Runnable reload) {
        mExecutor = executor;
        mApplier = applier;
        mReload = reload;
    }

    /**
     * @return {@code false} when this overlay is still being changed, in which case nothing is done
     */
    @AnyThread
    boolean setEnabled(@NonNull String overlayPackage, boolean enable, @NonNull Callback callback) {
        synchronized (mChanging) {
            if (!mChanging.add(overlayPackage)) {
                return false;
            }
        }
        mExecutor.execute(() -> {
            boolean success = false;
            try {
                success = mApplier.setEnabled(overlayPackage, enable);
            } catch (RemoteException | RuntimeException | LinkageError e) {
                // LinkageError: the hidden overlay manager API isn't what this build expects.
                Log.e(TAG, "Couldn't change overlay %s", e, overlayPackage);
            } finally {
                // Cleared before the reload, so the rows it shows have their switches back.
                synchronized (mChanging) {
                    mChanging.remove(overlayPackage);
                }
            }
            mReload.run();
            callback.onFinished(success);
        });
        return true;
    }

    @AnyThread
    boolean isChanging(@NonNull String overlayPackage) {
        synchronized (mChanging) {
            return mChanging.contains(overlayPackage);
        }
    }
}
