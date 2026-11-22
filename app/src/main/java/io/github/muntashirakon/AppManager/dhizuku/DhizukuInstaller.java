// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import android.content.Context;
import android.content.pm.IPackageInstaller;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;

/**
 * Package installer calls made as the Dhizuku owner. Android lets a device owner install and
 * uninstall without asking the user.
 *
 * <p>Android records the uid that creates a session as its owner, and only that uid may write
 * to it. Through Dhizuku that's the owner's uid, so the session binder has to be wrapped too
 * (see {@link #wrapSession(Context, IBinder)}), and the installer package name passed along has
 * to be the owner's own.
 */
public final class DhizukuInstaller {
    private DhizukuInstaller() {
    }

    /**
     * Whether an install or uninstall should go through Dhizuku: only when the current mode can't
     * install by itself, Dhizuku is ready, and the target is the current user alone.
     *
     * @param allUsers the caller asked for every user
     */
    public static boolean shouldUse(boolean privileged, boolean dhizukuReady, int targetUserId,
                                    boolean allUsers, int myUserId, int userCount) {
        if (privileged || !dhizukuReady || targetUserId != myUserId) {
            return false;
        }
        return !allUsers || userCount == 1;
    }

    /**
     * Android's package installer with every call made by the Dhizuku owner.
     */
    @WorkerThread
    @NonNull
    public static IPackageInstaller getPackageInstaller(@NonNull Context context) throws RemoteException {
        DhizukuClient client = requireClient(context);
        IBinder installer = PackageManagerCompat.getUnprivilegedPackageManager().getPackageInstaller().asBinder();
        try {
            return IPackageInstaller.Stub.asInterface(client.wrap(installer));
        } catch (RuntimeException e) {
            throw remoteException(e);
        }
    }

    /**
     * Wrap the binder {@code openSession()} returned, so writing and committing run as the owner
     * that created the session.
     */
    @WorkerThread
    @NonNull
    public static IBinder wrapSession(@NonNull Context context, @NonNull IBinder session) throws RemoteException {
        DhizukuClient client = requireClient(context);
        try {
            return client.wrap(session);
        } catch (RuntimeException e) {
            throw remoteException(e);
        }
    }

    /**
     * The package Android checks the calling uid against when Dhizuku creates a session or
     * uninstalls.
     */
    @WorkerThread
    @NonNull
    public static String getOwnerPackageName(@NonNull Context context) throws RemoteException {
        DhizukuClient client = requireClient(context);
        try {
            return client.getOwnerComponent().getPackageName();
        } catch (RuntimeException e) {
            throw remoteException(e);
        }
    }

    @WorkerThread
    @NonNull
    private static DhizukuClient requireClient(@NonNull Context context) throws RemoteException {
        DhizukuClient client = DhizukuSession.permittedClient(context);
        if (client == null) {
            throw new RemoteException("Dhizuku isn't available. Allow AppManagerNG in Dhizuku, then try again.");
        }
        return client;
    }

    @NonNull
    private static RemoteException remoteException(@NonNull RuntimeException cause) {
        RemoteException exception = new RemoteException("Dhizuku stopped answering.");
        exception.initCause(cause);
        return exception;
    }
}
