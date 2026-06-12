// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.apk.installer.AppArchiveManager;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

public final class PackageStateVerifier {
    private static final int QUERY_FLAGS = PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES
            | PackageManagerCompat.MATCH_DISABLED_COMPONENTS
            | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;

    private PackageStateVerifier() {
    }

    interface StateReader {
        boolean isInstalled(@NonNull UserPackagePair pair) throws Throwable;

        boolean isFrozen(@NonNull UserPackagePair pair) throws Throwable;

        boolean isArchived(@NonNull UserPackagePair pair) throws Throwable;
    }

    public static boolean shouldVerify(@BatchOpsManager.OpType int op) {
        switch (op) {
            case BatchOpsManager.OP_ADVANCED_FREEZE:
            case BatchOpsManager.OP_ARCHIVE:
            case BatchOpsManager.OP_FREEZE:
            case BatchOpsManager.OP_INSTALL_EXISTING:
            case BatchOpsManager.OP_UNARCHIVE:
            case BatchOpsManager.OP_UNFREEZE:
            case BatchOpsManager.OP_UNINSTALL:
                return true;
            default:
                return false;
        }
    }

    public static boolean matchesExpectedAndroidState(@BatchOpsManager.OpType int op,
                                                      @NonNull UserPackagePair pair) {
        return matchesExpectedState(op, pair, AndroidStateReader.INSTANCE);
    }

    static boolean matchesExpectedState(@BatchOpsManager.OpType int op,
                                        @NonNull UserPackagePair pair,
                                        @NonNull StateReader reader) {
        try {
            switch (op) {
                case BatchOpsManager.OP_ADVANCED_FREEZE:
                case BatchOpsManager.OP_FREEZE:
                    return reader.isInstalled(pair) && reader.isFrozen(pair);
                case BatchOpsManager.OP_UNFREEZE:
                    return reader.isInstalled(pair) && !reader.isFrozen(pair);
                case BatchOpsManager.OP_UNINSTALL:
                    return !reader.isInstalled(pair);
                case BatchOpsManager.OP_INSTALL_EXISTING:
                    return reader.isInstalled(pair);
                case BatchOpsManager.OP_ARCHIVE:
                    return reader.isInstalled(pair) && reader.isArchived(pair);
                case BatchOpsManager.OP_UNARCHIVE:
                    return reader.isInstalled(pair) && !reader.isArchived(pair);
                default:
                    return true;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    @NonNull
    public static String getExpectedStateLabel(@BatchOpsManager.OpType int op) {
        switch (op) {
            case BatchOpsManager.OP_ADVANCED_FREEZE:
            case BatchOpsManager.OP_FREEZE:
                return "installed+frozen";
            case BatchOpsManager.OP_UNFREEZE:
                return "installed+not-frozen";
            case BatchOpsManager.OP_UNINSTALL:
                return "not-installed";
            case BatchOpsManager.OP_INSTALL_EXISTING:
                return "installed";
            case BatchOpsManager.OP_ARCHIVE:
                return "installed+archived";
            case BatchOpsManager.OP_UNARCHIVE:
                return "installed+not-archived";
            default:
                return "unchanged";
        }
    }

    private static final class AndroidStateReader implements StateReader {
        static final AndroidStateReader INSTANCE = new AndroidStateReader();

        private AndroidStateReader() {
        }

        @WorkerThread
        @Override
        public boolean isInstalled(@NonNull UserPackagePair pair) throws RemoteException {
            try {
                ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(
                        pair.getPackageName(), QUERY_FLAGS, pair.getUserId());
                return ApplicationInfoCompat.isInstalled(applicationInfo);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }

        @WorkerThread
        @Override
        public boolean isFrozen(@NonNull UserPackagePair pair) throws RemoteException {
            try {
                ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(
                        pair.getPackageName(), QUERY_FLAGS, pair.getUserId());
                return FreezeUtils.isFrozen(applicationInfo);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }

        @WorkerThread
        @Override
        public boolean isArchived(@NonNull UserPackagePair pair) throws RemoteException {
            try {
                PackageInfo packageInfo = PackageManagerCompat.getPackageInfo(
                        pair.getPackageName(), QUERY_FLAGS, pair.getUserId());
                return AppArchiveManager.isArchived(packageInfo);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
    }
}
