// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shizuku;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;

import androidx.annotation.AnyThread;

import rikka.shizuku.Shizuku;

public final class ShizukuBridge {
    public static final int MIN_USER_SERVICE_VERSION = 10;
    public static final int REQUEST_PERMISSION_CODE = 0x5348;

    private ShizukuBridge() {
    }

    @AnyThread
    public static boolean isBinderAlive() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11();
        } catch (Throwable e) {
            return false;
        }
    }

    @AnyThread
    public static boolean supportsUserService() {
        try {
            return isBinderAlive() && Shizuku.getVersion() >= MIN_USER_SERVICE_VERSION;
        } catch (Throwable e) {
            return false;
        }
    }

    @AnyThread
    public static boolean hasPermission() {
        try {
            return supportsUserService()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }

    @AnyThread
    public static boolean shouldShowPermissionRationale() {
        try {
            return supportsUserService() && Shizuku.shouldShowRequestPermissionRationale();
        } catch (Throwable e) {
            return false;
        }
    }

    @AnyThread
    public static boolean isUsable() {
        return supportsUserService() && hasPermission();
    }

    @AnyThread
    public static int getUidOrSelf() {
        try {
            return isBinderAlive() ? Shizuku.getUid() : Process.myUid();
        } catch (Throwable e) {
            return Process.myUid();
        }
    }
}
