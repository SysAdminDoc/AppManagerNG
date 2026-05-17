// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.PackageUsageInfo;
import io.github.muntashirakon.AppManager.usage.TimeInterval;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public final class FilteringUtils {
    @NonNull
    @WorkerThread
    public static List<FilterableAppInfo> loadFilterableAppInfo(@NonNull int[] userIds) {
        return loadFilterableAppInfo(userIds, false);
    }

    @NonNull
    @WorkerThread
    public static List<FilterableAppInfo> loadFilterableAppInfo(@NonNull int[] userIds, boolean includeBackupOnlyApps) {
        List<FilterableAppInfo> filterableAppInfoList = new ArrayList<>();
        boolean hasUsageAccess = FeatureController.isUsageAccessEnabled() && SelfPermissions.checkUsageStatsPermission();
        for (int userId : userIds) {
            if (ThreadUtils.isInterrupted()) return Collections.emptyList();

            if (!SelfPermissions.checkCrossUserPermission(userId, false)) {
                // No support for cross user
                continue;
            }

            // List packages
            List<PackageInfo> packageInfoList = PackageManagerCompat.getInstalledPackages(
                    PackageManager.GET_META_DATA | GET_SIGNING_CERTIFICATES
                            | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                            | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES
                            | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_PERMISSIONS
                            | PackageManager.GET_URI_PERMISSION_PATTERNS
                            | MATCH_DISABLED_COMPONENTS | MATCH_UNINSTALLED_PACKAGES
                            | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
            // List usages
            Map<String, PackageUsageInfo> packageUsageInfoList = new HashMap<>();
            if (hasUsageAccess) {
                TimeInterval interval = UsageUtils.getLastWeek();
                List<PackageUsageInfo> usageInfoList = ExUtils.exceptionAsNull(() ->
                        AppUsageStatsManager.getInstance().getUsageStats(interval, userId));
                if (usageInfoList != null) {
                    for (PackageUsageInfo info : usageInfoList) {
                        if (ThreadUtils.isInterrupted()) return Collections.emptyList();
                        packageUsageInfoList.put(info.packageName, info);
                    }
                }
            }
            for (PackageInfo packageInfo : packageInfoList) {
                // Interrupt thread on request
                if (ThreadUtils.isInterrupted()) return Collections.emptyList();
                filterableAppInfoList.add(new FilterableAppInfo(packageInfo, packageUsageInfoList.get(packageInfo.packageName)));
            }
        }
        if (includeBackupOnlyApps) {
            addBackupOnlyFilterableAppInfo(filterableAppInfoList, userIds);
        }
        return filterableAppInfoList;
    }

    @WorkerThread
    private static void addBackupOnlyFilterableAppInfo(@NonNull List<FilterableAppInfo> filterableAppInfoList,
                                                       @NonNull int[] userIds) {
        if (ThreadUtils.isInterrupted()) return;
        Set<String> existingPackageUsers = new HashSet<>(filterableAppInfoList.size());
        for (FilterableAppInfo info : filterableAppInfoList) {
            existingPackageUsers.add(getPackageUserKey(info.getPackageName(), info.getUserId()));
        }
        int[] permittedUserIds = getPermittedUserIds(userIds);
        if (permittedUserIds.length == 0) return;
        List<Backup> backupOnlyApps = selectLatestBackupOnlyEntries(new AppDb().getAllBackups(),
                existingPackageUsers, permittedUserIds, FilteringUtils::backupExists);
        for (Backup backup : backupOnlyApps) {
            if (ThreadUtils.isInterrupted()) return;
            filterableAppInfoList.add(new BackupFilterableAppInfo(backup));
        }
    }

    @NonNull
    private static int[] getPermittedUserIds(@NonNull int[] userIds) {
        int[] permittedUserIds = new int[userIds.length];
        int count = 0;
        for (int userId : userIds) {
            if (SelfPermissions.checkCrossUserPermission(userId, false)) {
                permittedUserIds[count++] = userId;
            }
        }
        return count == userIds.length ? userIds : Arrays.copyOf(permittedUserIds, count);
    }

    @NonNull
    @VisibleForTesting
    static List<Backup> selectLatestBackupOnlyEntries(@NonNull List<Backup> backups,
                                                      @NonNull Set<String> existingPackageUsers,
                                                      @NonNull int[] userIds,
                                                      @NonNull BackupExistenceChecker existenceChecker) {
        Map<String, Backup> latestBackups = new LinkedHashMap<>();
        for (Backup backup : backups) {
            if (!ArrayUtils.contains(userIds, backup.userId)) {
                continue;
            }
            String packageUserKey = getPackageUserKey(backup.packageName, backup.userId);
            if (existingPackageUsers.contains(packageUserKey) || !existenceChecker.exists(backup)) {
                continue;
            }
            Backup latestBackup = latestBackups.get(packageUserKey);
            if (latestBackup == null || backup.backupTime > latestBackup.backupTime) {
                latestBackups.put(packageUserKey, backup);
            }
        }
        return new ArrayList<>(latestBackups.values());
    }

    @NonNull
    @VisibleForTesting
    static String getPackageUserKey(@NonNull String packageName, int userId) {
        return packageName + ':' + userId;
    }

    private static boolean backupExists(@NonNull Backup backup) {
        try {
            return backup.getItem().exists();
        } catch (IOException ignore) {
            return false;
        }
    }

    @VisibleForTesting
    interface BackupExistenceChecker {
        boolean exists(@NonNull Backup backup);
    }
}
