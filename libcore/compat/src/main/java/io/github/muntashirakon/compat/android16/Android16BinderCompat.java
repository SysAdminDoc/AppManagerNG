// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat.android16;

import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class Android16BinderCompat {
    private static final TransactInvoker DIRECT_TRANSACT = new TransactInvoker() {
        @Override
        public boolean transact(@NonNull IBinder binder, int code, @NonNull Parcel data,
                                @Nullable Parcel reply, int flags) throws RemoteException {
            return binder.transact(code, data, reply, flags);
        }
    };
    private static final TransactInvoker REFLECTIVE_TRANSACT = new TransactInvoker() {
        @Override
        public boolean transact(@NonNull IBinder binder, int code, @NonNull Parcel data,
                                @Nullable Parcel reply, int flags) throws RemoteException {
            return transactReflectively(binder, code, data, reply, flags);
        }
    };

    @NonNull
    private static TransactInvoker sDirectTransact = DIRECT_TRANSACT;
    @NonNull
    private static TransactInvoker sReflectiveTransact = REFLECTIVE_TRANSACT;

    private Android16BinderCompat() {
    }

    public static boolean transact(@NonNull IBinder binder, int code, @NonNull Parcel data,
                                   @Nullable Parcel reply, int flags) throws RemoteException {
        return transactForSdk(Build.VERSION.SDK_INT, binder, code, data, reply, flags);
    }

    @VisibleForTesting
    static boolean transactForSdk(int sdkInt, @NonNull IBinder binder, int code, @NonNull Parcel data,
                                  @Nullable Parcel reply, int flags) throws RemoteException {
        try {
            return sDirectTransact.transact(binder, code, data, reply, flags);
        } catch (RuntimeException e) {
            return fallbackOrThrow(sdkInt, binder, code, data, reply, flags, e);
        } catch (LinkageError e) {
            return fallbackOrThrow(sdkInt, binder, code, data, reply, flags, e);
        }
    }

    @VisibleForTesting
    static boolean shouldAttemptReflectiveFallback(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.BAKLAVA;
    }

    private static boolean fallbackOrThrow(int sdkInt, @NonNull IBinder binder, int code,
                                           @NonNull Parcel data, @Nullable Parcel reply, int flags,
                                           @NonNull Throwable directFailure) throws RemoteException {
        if (!shouldAttemptReflectiveFallback(sdkInt)) {
            rethrow(directFailure);
        }
        try {
            return sReflectiveTransact.transact(binder, code, data, reply, flags);
        } catch (RemoteException e) {
            e.addSuppressed(directFailure);
            throw e;
        } catch (RuntimeException e) {
            e.addSuppressed(directFailure);
            throw e;
        } catch (LinkageError e) {
            e.addSuppressed(directFailure);
            throw e;
        }
    }

    private static void rethrow(@NonNull Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new AssertionError(throwable);
    }

    private static boolean transactReflectively(@NonNull IBinder binder, int code, @NonNull Parcel data,
                                                @Nullable Parcel reply, int flags) throws RemoteException {
        try {
            Method transact = IBinder.class.getMethod("transact", int.class, Parcel.class, Parcel.class, int.class);
            Object result = transact.invoke(binder, code, data, reply, flags);
            return Boolean.TRUE.equals(result);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RemoteException) {
                throw (RemoteException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            RemoteException remoteException = new RemoteException(cause != null ? cause.getMessage() : e.getMessage());
            remoteException.initCause(cause != null ? cause : e);
            throw remoteException;
        } catch (ReflectiveOperationException e) {
            RemoteException remoteException = new RemoteException(e.getMessage());
            remoteException.initCause(e);
            throw remoteException;
        }
    }

    @VisibleForTesting
    static void setTransactInvokersForTesting(@NonNull TransactInvoker direct,
                                              @NonNull TransactInvoker reflective) {
        sDirectTransact = direct;
        sReflectiveTransact = reflective;
    }

    @VisibleForTesting
    static void resetTransactInvokersForTesting() {
        sDirectTransact = DIRECT_TRANSACT;
        sReflectiveTransact = REFLECTIVE_TRANSACT;
    }

    @VisibleForTesting
    interface TransactInvoker {
        boolean transact(@NonNull IBinder binder, int code, @NonNull Parcel data,
                         @Nullable Parcel reply, int flags) throws RemoteException;
    }
}
