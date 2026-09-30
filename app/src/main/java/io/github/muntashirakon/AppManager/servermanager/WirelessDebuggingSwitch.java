// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.provider.SettingsHidden;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.adb.AdbUtils;

/**
 * The Wireless debugging setting as the reconnect after a boot sees it, so a test can stand in for
 * the system setting.
 */
@RequiresApi(Build.VERSION_CODES.R)
interface WirelessDebuggingSwitch {
    boolean isOn();

    /**
     * Switches Wireless debugging on if it's off.
     *
     * @return {@code false} when it couldn't
     */
    @WorkerThread
    boolean switchOn();

    /**
     * Calls {@code onChange} on the main thread each time the setting changes, until
     * {@link #stopObserving()}.
     */
    @MainThread
    void observe(@NonNull Runnable onChange);

    @MainThread
    void stopObserving();

    @RequiresApi(Build.VERSION_CODES.R)
    final class SystemSetting implements WirelessDebuggingSwitch {
        @NonNull
        private final Context mContext;
        @NonNull
        private final Handler mHandler;
        @Nullable
        private ContentObserver mObserver;

        SystemSetting(@NonNull Context context, @NonNull Handler handler) {
            mContext = context;
            mHandler = handler;
        }

        @Override
        public boolean isOn() {
            return !AdbUtils.isWirelessDebuggingOff(mContext);
        }

        @Override
        public boolean switchOn() {
            return AdbUtils.enableWirelessDebugging(mContext);
        }

        @Override
        public void observe(@NonNull Runnable onChange) {
            if (mObserver != null) {
                return;
            }
            mObserver = new ContentObserver(mHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    onChange.run();
                }
            };
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(SettingsHidden.Global.ADB_WIFI_ENABLED), false, mObserver);
        }

        @Override
        public void stopObserving() {
            if (mObserver != null) {
                mContext.getContentResolver().unregisterContentObserver(mObserver);
                mObserver = null;
            }
        }
    }
}
