// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;

public class BroadcastUtils {
    public static void sendPackageAdded(@NonNull Context context, @NonNull String[] packageNames) {
        sendPackageSignal(context, PackageChangeReceiver.ACTION_PACKAGE_ADDED, packageNames);
    }

    public static void sendPackageAltered(@NonNull Context context, @NonNull String[] packageNames) {
        sendPackageSignal(context, PackageChangeReceiver.ACTION_PACKAGE_ALTERED, packageNames);
    }

    public static void sendPackageRemoved(@NonNull Context context, @NonNull String[] packageNames) {
        sendPackageSignal(context, PackageChangeReceiver.ACTION_PACKAGE_REMOVED, packageNames);
    }

    public static void sendDbPackageAdded(@NonNull Context context, @NonNull String[] packageNames) {
        sendPackageSignal(context, PackageChangeReceiver.ACTION_DB_PACKAGE_ADDED, packageNames);
    }

    public static void sendDbPackageAltered(@NonNull Context context, @NonNull String[] packageNames) {
        sendPackageSignal(context, PackageChangeReceiver.ACTION_DB_PACKAGE_ALTERED, packageNames);
    }

    public static void sendDbPackageRemoved(@NonNull Context context, @NonNull String[] packageNames) {
        sendPackageSignal(context, PackageChangeReceiver.ACTION_DB_PACKAGE_REMOVED, packageNames);
    }

    private static void sendPackageSignal(@NonNull Context context, @NonNull String action,
                                          @NonNull String[] packageNames) {
        Intent intent = new Intent(action);
        intent.setPackage(context.getPackageName());
        intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, packageNames);
        context.sendBroadcast(intent);
    }
}
