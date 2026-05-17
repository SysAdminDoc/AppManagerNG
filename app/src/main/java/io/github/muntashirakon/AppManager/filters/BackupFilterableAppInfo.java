// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import static io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_APK_FILES;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.db.entity.Backup;

public class BackupFilterableAppInfo extends FilterableAppInfo {
    private static final int SYNTHETIC_APP_ID = 10_000;

    @NonNull
    private final Backup mBackup;
    @NonNull
    private final String mAppLabel;

    public BackupFilterableAppInfo(@NonNull Backup backup) {
        super(createPackageInfo(backup), null);
        mBackup = backup;
        mAppLabel = getBackupLabel(backup);
    }

    @NonNull
    private static PackageInfo createPackageInfo(@NonNull Backup backup) {
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
    public boolean isInstalled() {
        return false;
    }

    @Override
    public boolean isFrozen() {
        return false;
    }

    @Override
    public int getFreezeFlags() {
        return 0;
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
        return false;
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
    @NonNull
    public String getSsaid() {
        return "";
    }

    @Override
    @Nullable
    public SignerInfo fetchSignerInfo() {
        return null;
    }

    @Override
    @NonNull
    public String[] getSignatureSubjectLines() {
        return EmptyArray.STRING;
    }

    @Override
    @NonNull
    public String[] getSignatureSha256Checksums() {
        return EmptyArray.STRING;
    }
}
