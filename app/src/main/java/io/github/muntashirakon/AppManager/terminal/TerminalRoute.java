// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;

final class TerminalRoute {
    enum Type {
        LOCAL,
        ROOT,
        SHIZUKU,
        ADB
    }

    @NonNull
    private final Type mType;
    @Nullable
    private final Type mUnavailablePrivilegedType;

    private TerminalRoute(@NonNull Type type, @Nullable Type unavailablePrivilegedType) {
        mType = type;
        mUnavailablePrivilegedType = unavailablePrivilegedType;
    }

    @NonNull
    static TerminalRoute resolve(boolean localServicesAlive, boolean directRoot, boolean shizuku,
                                 boolean adb, int uid) {
        Type expectedType = expectedType(directRoot, shizuku, adb, uid);
        if (localServicesAlive && expectedType != Type.LOCAL) {
            return new TerminalRoute(expectedType, null);
        }
        if (expectedType != Type.LOCAL) {
            return new TerminalRoute(Type.LOCAL, expectedType);
        }
        return new TerminalRoute(Type.LOCAL, null);
    }

    @NonNull
    TerminalRoute withLocalFallback() {
        if (mType == Type.LOCAL) {
            return this;
        }
        return new TerminalRoute(Type.LOCAL, mType);
    }

    boolean usesLocalProcess() {
        return mType == Type.LOCAL;
    }

    boolean isLocalFallback() {
        return mType == Type.LOCAL && mUnavailablePrivilegedType != null;
    }

    @NonNull
    Type getType() {
        return mType;
    }

    @Nullable
    Type getUnavailablePrivilegedType() {
        return mUnavailablePrivilegedType;
    }

    @NonNull
    String getLabel(@NonNull Context context) {
        return context.getString(labelRes(mType));
    }

    @NonNull
    String getUnavailableLabel(@NonNull Context context) {
        Type type = mUnavailablePrivilegedType != null ? mUnavailablePrivilegedType : mType;
        return context.getString(labelRes(type));
    }

    @NonNull
    String getStatusText(@NonNull Context context) {
        if (isLocalFallback()) {
            return context.getString(R.string.terminal_route_status_fallback, getUnavailableLabel(context));
        }
        return context.getString(R.string.terminal_route_status, getLabel(context));
    }

    @NonNull
    String getProcessEndedText(@NonNull Context context, int exitCode) {
        return context.getString(R.string.terminal_process_ended, getLabel(context), exitCode);
    }

    @NonNull
    static String getThrowableMessage(@NonNull Throwable throwable) {
        String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getSimpleName();
    }

    @NonNull
    private static Type expectedType(boolean directRoot, boolean shizuku, boolean adb, int uid) {
        if (shizuku) {
            return Type.SHIZUKU;
        }
        if (directRoot || uid == Ops.ROOT_UID) {
            return Type.ROOT;
        }
        if (adb || uid == Ops.SHELL_UID) {
            return Type.ADB;
        }
        return Type.LOCAL;
    }

    @StringRes
    private static int labelRes(@NonNull Type type) {
        switch (type) {
            case ROOT:
                return R.string.terminal_route_root;
            case SHIZUKU:
                return R.string.terminal_route_shizuku;
            case ADB:
                return R.string.terminal_route_adb;
            case LOCAL:
            default:
                return R.string.terminal_route_local;
        }
    }
}
