// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.Manifest;
import android.annotation.UserIdInt;
import android.app.ILocaleManager;
import android.content.Context;
import android.os.Build;
import android.os.LocaleList;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.self.SelfPermissions;

public final class AppLocaleManagerCompat {
    private AppLocaleManagerCompat() {
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean canReadApplicationLocales() {
        return isSupported() && (canSetApplicationLocales()
                || SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.READ_APP_SPECIFIC_LOCALES));
    }

    public static boolean canSetApplicationLocales() {
        return isSupported()
                && SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.CHANGE_CONFIGURATION);
    }

    @Nullable
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static String getApplicationLocaleTags(@NonNull String packageName, @UserIdInt int userId)
            throws RemoteException {
        LocaleList locales = getLocaleManager().getApplicationLocales(packageName, userId);
        return locales != null ? locales.toLanguageTags() : null;
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static void setApplicationLocaleTags(@NonNull String packageName,
                                                @UserIdInt int userId,
                                                @Nullable String languageTags) throws RemoteException {
        LocaleList locales = languageTags == null || languageTags.trim().isEmpty()
                ? LocaleList.getEmptyLocaleList()
                : LocaleList.forLanguageTags(languageTags);
        getLocaleManager().setApplicationLocales(packageName, userId, locales, true);
    }

    @NonNull
    private static ILocaleManager getLocaleManager() {
        return ILocaleManager.Stub.asInterface(ProxyBinder.getService(Context.LOCALE_SERVICE));
    }
}
