// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_APK_FILES;
import static io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_CONTENT_FLAGS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.filters.options.FreezeOption;
import io.github.muntashirakon.AppManager.utils.ExUtils;

public class BackupFilterableAppInfo extends FilterableAppInfo {
    private static final int SYNTHETIC_APP_ID = 10_000;
    private static final int PACKAGE_INFO_FLAGS = PackageManager.GET_META_DATA | GET_SIGNING_CERTIFICATES
            | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
            | PackageManager.GET_SERVICES | PackageManager.GET_PERMISSIONS | MATCH_DISABLED_COMPONENTS
            | MATCH_UNINSTALLED_PACKAGES | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;

    @NonNull
    private final Backup mBackup;
    @NonNull
    private final String mAppLabel;
    private final boolean mHasCurrentPackageInfo;
    @Nullable
    private Boolean mBackupFrozen;

    public BackupFilterableAppInfo(@NonNull Backup backup) {
        this(backup, findCurrentPackageInfo(backup));
    }

    private BackupFilterableAppInfo(@NonNull Backup backup, @Nullable PackageInfo currentPackageInfo) {
        super(currentPackageInfo != null ? currentPackageInfo : createSyntheticPackageInfo(backup), null);
        mBackup = backup;
        mAppLabel = getBackupLabel(backup);
        mHasCurrentPackageInfo = currentPackageInfo != null;
    }

    @NonNull
    private static PackageInfo createSyntheticPackageInfo(@NonNull Backup backup) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = backup.packageName;
        packageInfo.versionName = backup.versionName;
        packageInfo.versionCode = clampVersionCode(backup.versionCode);
        packageInfo.firstInstallTime = backup.backupTime;
        packageInfo.lastUpdateTime = backup.backupTime;

        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = backup.packageName;
        applicationInfo.uid = UserHandleHidden.getUid(backup.userId, SYNTHETIC_APP_ID);
        applicationInfo.flags = backup.isSystem ? ApplicationInfo.FLAG_SYSTEM : 0;
        if ((backup.flags & BACKUP_APK_FILES) != 0) {
            applicationInfo.flags |= ApplicationInfo.FLAG_HAS_CODE;
        }
        applicationInfo.enabled = true;
        applicationInfo.nonLocalizedLabel = getBackupLabel(backup);
        packageInfo.applicationInfo = applicationInfo;
        return packageInfo;
    }

    @Nullable
    private static PackageInfo findCurrentPackageInfo(@NonNull Backup backup) {
        return ExUtils.exceptionAsNull(() -> PackageManagerCompat.getPackageInfo(
                backup.packageName, PACKAGE_INFO_FLAGS, backup.userId));
    }

    private static int clampVersionCode(long versionCode) {
        if (versionCode <= 0) {
            return 0;
        }
        return (int) Math.min(versionCode, Integer.MAX_VALUE);
    }

    @NonNull
    private static String getBackupLabel(@NonNull Backup backup) {
        return TextUtils.isEmpty(backup.label) ? backup.packageName : backup.label;
    }

    @Override
    @NonNull
    public String getAppLabel() {
        return mAppLabel;
    }

    @Override
    public long getVersionCode() {
        return mBackup.versionCode;
    }

    @Override
    @Nullable
    public String getVersionName() {
        return mBackup.versionName;
    }

    @Override
    public boolean isFrozen() {
        return getFreezeFlags() != 0;
    }

    @Override
    public int getFreezeFlags() {
        int freezeFlags = super.getFreezeFlags();
        if (!mHasCurrentPackageInfo && freezeFlags == 0 && isBackupFrozen()) {
            freezeFlags |= FreezeOption.FREEZE_TYPE_DISABLED;
        }
        return freezeFlags;
    }

    @Override
    public boolean isSystemApp() {
        return mBackup.isSystem;
    }

    @Override
    public boolean hasCode() {
        return (mBackup.flags & BACKUP_APK_FILES) != 0;
    }

    @Override
    public boolean backupAllowed() {
        if (mHasCurrentPackageInfo) {
            return super.backupAllowed();
        }
        // A backup with payload flags records that this package was eligible for
        // the requested historical backup operation, even when its package record
        // is no longer available to expose the current manifest flag.
        return (mBackup.flags & BACKUP_CONTENT_FLAGS) != 0;
    }

    @Override
    public boolean hasKeyStoreItems() {
        return mBackup.hasKeyStore;
    }

    @Override
    public int getRuleCount() {
        return mBackup.hasRules ? 1 : 0;
    }

    @Override
    @Nullable
    public SignerInfo fetchSignerInfo() {
        return mHasCurrentPackageInfo ? super.fetchSignerInfo() : null;
    }

    @Override
    @NonNull
    public String getSsaid() {
        return "";
    }

    private boolean isBackupFrozen() {
        if (mBackupFrozen == null) {
            Boolean frozen = ExUtils.exceptionAsNull(() -> {
                try (BackupItems.BackupItem backupItem = mBackup.getItem()) {
                    return backupItem.isFrozen();
                }
            });
            mBackupFrozen = frozen != null && frozen;
        }
        return mBackupFrozen;
    }
}
