// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static io.github.muntashirakon.AppManager.backup.BackupManager.KEYSTORE_PLACEHOLDER;
import static io.github.muntashirakon.AppManager.backup.BackupManager.MASTER_KEY;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES;

import android.app.AppOpsManager;
import android.app.INotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.InstallerOptions;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.BackupCompat;
import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.crypto.AESCrypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.magisk.MagiskDenyList;
import io.github.muntashirakon.AppManager.magisk.MagiskHide;
import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.permission.Permission;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.rules.PseudoRules;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.RulesImporter;
import io.github.muntashirakon.AppManager.rules.internet.InternetAccess;
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.AppManager.rules.struct.FreezeRule;
import io.github.muntashirakon.AppManager.rules.struct.MagiskDenyListRule;
import io.github.muntashirakon.AppManager.rules.struct.MagiskHideRule;
import io.github.muntashirakon.AppManager.rules.struct.NetPolicyRule;
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.rules.struct.SsaidRule;
import io.github.muntashirakon.AppManager.rules.struct.UriGrantRule;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.safety.AppOpsUidGuard;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ParcelFileDescriptorUtil;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.UidGidPair;

@WorkerThread
class RestoreOp implements Closeable {
    static final String TAG = RestoreOp.class.getSimpleName();
    private static final Object sLock = new Object();

    /** Stands in for the chown and restorecon calls on restored directories, which tests can't run. */
    @VisibleForTesting
    interface DirectoryCommandHook {
        boolean run(@NonNull String[] command);
    }

    @Nullable
    private static DirectoryCommandHook sDirectoryCommandHook;
    /** Runs once every file the restore reads has been copied and checked, before anything changes. */
    @Nullable
    private static Runnable sStagedInputsHook;

    @NonNull
    private final String mPackageName;
    @NonNull
    private final BackupFlags mBackupFlags;
    @NonNull
    private final BackupFlags mRequestedFlags;
    @NonNull
    private final BackupMetadataV5.Info mBackupInfo;
    @NonNull
    private final BackupMetadataV5.Metadata mBackupMetadata;
    @NonNull
    private final BackupItems.BackupItem mBackupItem;
    @Nullable
    private PackageInfo mPackageInfo;
    private int mUid;
    @NonNull
    private final BackupItems.Checksum mChecksum;
    private final int mUserId;
    private boolean mIsInstalled;
    private boolean mRequiresRestart;
    @NonNull
    private final ArrayList<DefaultAppRoleBackupHelper.RoleRebindRequest> mPendingDefaultRoleRebindRequests = new ArrayList<>();
    @NonNull
    private final ArrayList<String> mRestoreExtraWarnings = new ArrayList<>();
    @NonNull
    private final ArrayList<String> mDirectoryFailures = new ArrayList<>();
    // Private copies of the backup files, checked and decrypted. Nothing past stageInputs() reads
    // the backup itself, which another app or a document provider may change at any time.
    @Nullable
    private RestoreStaging mStaging;
    @Nullable
    private Path[] mStagedSourceFiles;
    @Nullable
    private Path[][] mStagedDataFiles;
    @Nullable
    private Path[] mStagedKeyStoreFiles;
    @Nullable
    private Path mStagedMiscFile;
    @Nullable
    private Path mStagedRulesFile;

    @VisibleForTesting
    static void setDirectoryCommandHook(@Nullable DirectoryCommandHook hook) {
        sDirectoryCommandHook = hook;
    }

    @VisibleForTesting
    static void setStagedInputsHook(@Nullable Runnable hook) {
        sStagedInputsHook = hook;
    }

    RestoreOp(@NonNull String packageName, @NonNull BackupFlags requestedFlags,
              @NonNull BackupItems.BackupItem backupItem, int userId) throws BackupException {
        mPackageName = packageName;
        mRequestedFlags = requestedFlags;
        mBackupItem = backupItem;
        mUserId = userId;
        try {
            mBackupInfo = mBackupItem.getInfo();
            mBackupFlags = mBackupInfo.flags;
        } catch (IOException e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not read backup info. Possibly due to a malformed json file.", e);
        }
        try {
            // The info file alone names the mode, and so which checksums count, but it isn't
            // encrypted. An encrypted backup swapped for plain files stops here.
            BackupCryptoRecord.requireRecordedMode(mBackupItem.getRelativeDir(), mBackupInfo.crypto);
            BackupChecksumPolicy.requireCryptographicChecksum(mBackupInfo);
        } catch (BackupException e) {
            mBackupItem.cleanup();
            throw e;
        }
        // Setup crypto
        if (!CryptoUtils.isAvailable(mBackupInfo.crypto)) {
            mBackupItem.cleanup();
            throw new BackupException("Mode " + mBackupInfo.crypto + " is currently unavailable.");
        }
        try {
            mBackupItem.setCrypto(mBackupInfo.getCrypto());
        } catch (CryptoException e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not get crypto " + mBackupInfo.crypto, e);
        }
        try {
            mBackupMetadata = mBackupItem.getMetadata(mBackupInfo).metadata;
        } catch (IOException e) {
            mBackupItem.cleanup();
            throw new BackupException("Could not read backup metadata. Possibly due to a malformed json file.", e);
        }
        // Get checksums
        try {
            mChecksum = mBackupItem.getChecksum();
        } catch (Exception e) {
            mBackupItem.cleanup();
            throw new BackupException("Failed to get checksums.", e);
        }
        // Verify metadata
        if (!requestedFlags.skipSignatureCheck()) {
            try {
                verifyMetadata();
            } catch (BackupException e) {
                mBackupItem.cleanup();
                throw e;
            }
        }
        // Check user handle
        if (mBackupInfo.userId != userId) {
            Log.w(TAG, "Using different user handle.");
        }
        // Get package info
        mPackageInfo = null;
        try {
            mPackageInfo = PackageManagerCompat.getPackageInfo(packageName, GET_SIGNING_CERTIFICATES
                    | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
            mUid = Objects.requireNonNull(mPackageInfo.applicationInfo).uid;
        } catch (Exception e) {
            Log.w(TAG, "Could not query installed package %s; treating the restore as not installed.",
                    e, packageName);
        }
        mIsInstalled = mPackageInfo != null;
        try {
            BackupArchiveStateGuard.requireRestoreAllowed(mPackageName, mRequestedFlags, mPackageInfo);
        } catch (BackupException e) {
            mChecksum.close();
            mBackupItem.cleanup();
            throw e;
        }
        checkApiLevelCompatibility();
        checkEncryptionIntegrityCompatibility();
    }

    private void checkApiLevelCompatibility() {
        int sourceApi = mBackupMetadata.sourceApiLevel;
        if (sourceApi == 0) return;
        int deviceApi = Build.VERSION.SDK_INT;
        int delta = Math.abs(deviceApi - sourceApi);
        if (delta < 2) return;
        Context context = ContextUtils.getContext();
        StringBuilder warning = new StringBuilder();
        if (deviceApi > sourceApi) {
            warning.append(context.getString(R.string.restore_api_warning_newer_device, sourceApi, deviceApi));
        } else {
            warning.append(context.getString(R.string.restore_api_warning_older_device, sourceApi, deviceApi));
        }
        if (sourceApi <= 29 && deviceApi >= 30) {
            warning.append("\n").append(context.getString(R.string.restore_api_risk_scoped_storage));
        }
        if (sourceApi <= 22 && deviceApi >= 23) {
            warning.append("\n").append(context.getString(R.string.restore_api_risk_runtime_permissions));
        }
        if (sourceApi <= 29 && deviceApi >= 30) {
            warning.append("\n").append(context.getString(R.string.restore_api_risk_package_visibility));
        }
        addRestoreExtraWarning(warning.toString());
    }

    private void checkEncryptionIntegrityCompatibility() {
        String warning = getLegacyGcmIntegrityWarning(mBackupInfo);
        if (warning != null) {
            addRestoreExtraWarning(warning);
        }
    }

    @Nullable
    static String getLegacyGcmIntegrityWarning(@NonNull BackupMetadataV5.Info backupInfo) {
        if (!AESCrypto.metadataUsesLegacyAuthTag(backupInfo.version)) {
            return null;
        }
        switch (backupInfo.crypto) {
            case CryptoUtils.MODE_AES:
            case CryptoUtils.MODE_RSA:
            case CryptoUtils.MODE_ECC:
                return ContextUtils.getContext().getString(R.string.restore_legacy_gcm_integrity_warning);
            default:
                return null;
        }
    }

    @Override
    public void close() {
        Log.d(TAG, "Close called");
        // The staged copies go even if closing the checksum file throws
        try {
            mChecksum.close();
        } finally {
            if (mStaging != null) {
                mStaging.close();
            }
            mBackupItem.cleanup();
        }
    }

    void runRestore(@Nullable ProgressHandler progressHandler) throws BackupException {
        try {
            if (mRequestedFlags.backupData() && mBackupMetadata.keyStore && !mRequestedFlags.skipSignatureCheck()) {
                // Check checksum of master key first
                checkMasterKey();
            }
            // Every check and decryption happens here, before the app or its data is touched
            stageInputs();
            incrementProgress(progressHandler);
            if (mRequestedFlags.backupApkFiles()) {
                restoreApkFiles();
                incrementProgress(progressHandler);
            }
            if (mRequestedFlags.backupData()) {
                restoreData();
                if (mBackupMetadata.keyStore) {
                    restoreKeyStore();
                }
                incrementProgress(progressHandler);
            }
            if (mRequestedFlags.backupExtras()) {
                restoreExtras();
                incrementProgress(progressHandler);
            }
            if (mRequestedFlags.backupRules()) {
                restoreRules();
                incrementProgress(progressHandler);
            }
            restoreDefaultAppRoles();
            if (!mDirectoryFailures.isEmpty()) {
                // Reported last, so one directory the app can't use yet doesn't stop the rest of the restore
                throw new BackupException(TextUtils.join("\n", mDirectoryFailures));
            }
        } catch (BackupException e) {
            throw e;
        } catch (Exception th) {
            throw new BackupException("Unknown error occurred", th);
        }
    }

    private static void incrementProgress(@Nullable ProgressHandler progressHandler) {
        if (progressHandler == null) {
            return;
        }
        float current = progressHandler.getLastProgress() + 1;
        progressHandler.postUpdate(current);
    }

    public boolean requiresRestart() {
        return mRequiresRestart;
    }

    @NonNull
    public List<DefaultAppRoleBackupHelper.RoleRebindRequest> getPendingDefaultRoleRebindRequests() {
        return new ArrayList<>(mPendingDefaultRoleRebindRequests);
    }

    @NonNull
    public List<String> getRestoreExtraWarnings() {
        return new ArrayList<>(mRestoreExtraWarnings);
    }

    private void restoreDefaultAppRoles() {
        mPendingDefaultRoleRebindRequests.clear();
        if (!mIsInstalled) {
            return;
        }
        mPendingDefaultRoleRebindRequests.addAll(DefaultAppRoleBackupHelper.restoreHeldDefaultRoles(
                mPackageName, mUserId, mBackupMetadata.defaultRoles));
    }

    private void verifyMetadata() throws BackupException {
        boolean isV5AndUp = mBackupItem.isV5AndUp();
        if (isV5AndUp) {
            Path infoFile;
            try {
                infoFile = mBackupItem.getInfoFile();
            } catch (IOException e) {
                throw new BackupException("Could not get metadata file.", e);
            }
            String checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, infoFile);
            if (!checksum.equals(mChecksum.get(infoFile.getName()))) {
                throw new BackupException("Couldn't verify metadata file." +
                        "\nFile: " + infoFile +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum.get(infoFile.getName()));
            }
        }
        Path metadataFile;
        try {
            metadataFile = isV5AndUp ? mBackupItem.getMetadataV5File(false) : mBackupItem.getMetadataV2File();
        } catch (IOException e) {
            throw new BackupException("Could not get metadata file.", e);
        }
        String checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, metadataFile);
        if (!checksum.equals(mChecksum.get(metadataFile.getName()))) {
            throw new BackupException("Couldn't verify metadata file." +
                    "\nFile: " + metadataFile +
                    "\nFound: " + checksum +
                    "\nRequired: " + mChecksum.get(metadataFile.getName()));
        }
    }

    private void checkMasterKey() throws BackupException {
        String oldChecksum = mChecksum.get(MASTER_KEY);
        Path masterKey;
        try {
            masterKey = KeyStoreUtils.getMasterKey(mUserId);
        } catch (FileNotFoundException e) {
            if (oldChecksum == null) return;
            else
                throw new BackupException("Master key existed when the checksum was made but now it doesn't.");
        }
        if (oldChecksum == null) {
            throw new BackupException("Master key exists but it didn't exist when the backup was made.");
        }
        String newChecksum;
        try {
            newChecksum = MasterKeyChecksum.calculate(mBackupInfo.checksumAlgo,
                    mBackupInfo.version, masterKey);
        } catch (IOException e) {
            throw new BackupException("Could not read master key for checksum verification.", e);
        }
        if (!newChecksum.equals(oldChecksum)) {
            throw new BackupException("Checksums for master key did not match.");
        }
    }

    /**
     * Reads every backup file the requested parts of the restore use once, into private storage,
     * and checks and decrypts those copies. A file that fails stops the restore here, before the
     * app is reinstalled or its data cleared, and the steps after this read only the copies.
     */
    private void stageInputs() throws BackupException {
        boolean skipChecks = mRequestedFlags.skipSignatureCheck();
        mStaging = new RestoreStaging(mBackupItem, skipChecks ? null : mChecksum, mBackupInfo.checksumAlgo);
        if (mRequestedFlags.backupApkFiles()) {
            if (!mBackupFlags.backupApkFiles()) {
                throw new BackupException("APK restore is requested but backup doesn't contain any source files.");
            }
            Path[] sourceFiles = mBackupItem.getSourceFiles();
            if (sourceFiles.length == 0) {
                // No source backup found
                throw new BackupException("Source restore is requested but there are no source files.");
            }
            mStagedSourceFiles = mStaging.stage(sourceFiles, "Source file verification failed.");
        }
        if (mRequestedFlags.backupData()) {
            stageDataFiles(mStaging, skipChecks);
            if (mBackupMetadata.keyStore && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Path[] keyStoreFiles = mBackupItem.getKeyStoreFiles();
                if (keyStoreFiles.length == 0) {
                    throw new BackupException("KeyStore files should've existed but they didn't");
                }
                mStagedKeyStoreFiles = mStaging.stage(keyStoreFiles, "KeyStore file verification failed.");
            }
        }
        if (mRequestedFlags.backupExtras()) {
            Path miscFile = findMiscFile();
            if (miscFile != null) {
                mStagedMiscFile = mStaging.stage(new Path[]{miscFile}, "Couldn't verify misc file.")[0];
            }
        }
        if (mRequestedFlags.backupRules()) {
            Path rulesFile;
            try {
                rulesFile = mBackupItem.getRulesFile();
            } catch (IOException e) {
                if (mBackupMetadata.hasRules) {
                    throw new BackupException("Rules file is missing.", e);
                }
                // There are no rules, just skip
                rulesFile = null;
            }
            if (rulesFile != null) {
                mStagedRulesFile = mStaging.stage(new Path[]{rulesFile}, "Couldn't verify permission file.")[0];
            }
        }
        Runnable hook = sStagedInputsHook;
        if (hook != null) {
            hook.run();
        }
    }

    /**
     * Stages the archives of every data directory this restore writes back. The rest are still
     * checked where they are, as before, though nothing reads them again.
     */
    private void stageDataFiles(@NonNull RestoreStaging staging, boolean skipChecks) throws BackupException {
        String[] dataDirs = mBackupMetadata.dataDirs;
        mStagedDataFiles = new Path[dataDirs.length][];
        for (int i = 0; i < dataDirs.length; ++i) {
            String dataDir = dataDirs[i];
            boolean isAdb = dataDir.equals(BackupManager.DATA_BACKUP_SPECIAL_ADB);
            boolean isSystem = !isAdb && SystemDataBackup.isSystemDataToken(dataDir);
            boolean isRestored = isAdb || (isSystem ? mRequestedFlags.backupSystemData()
                    : isRequestedDataDirectory(dataDir));
            Path[] dataFiles = mBackupItem.getDataFiles(i);
            if (dataFiles.length == 0) {
                if (!skipChecks || (!isAdb && !isSystem)) {
                    throw new BackupException("Data restore is requested but there are no data files for index " + i + ".");
                }
                if (isAdb) {
                    throw new BackupException("ADB restore is requested but there are no .ab files.");
                }
                if (isRestored) {
                    throw new BackupException("System data restore is requested but there are no data files for index " + i + ".");
                }
                continue;
            }
            if (isAdb && dataFiles.length != 1) {
                throw new BackupException("ADB restore is requested but there are no .ab files.");
            }
            String failurePrefix = "Data file verification failed for index " + i + ".";
            if (isRestored) {
                mStagedDataFiles[i] = staging.stage(dataFiles, failurePrefix);
            } else if (!skipChecks) {
                verifyUnusedFiles(dataFiles, failurePrefix);
            }
        }
    }

    private void verifyUnusedFiles(@NonNull Path[] files, @NonNull String failurePrefix) throws BackupException {
        for (Path file : files) {
            String checksum = DigestUtils.getHexDigest(mBackupInfo.checksumAlgo, file);
            if (!checksum.equals(mChecksum.get(file.getName()))) {
                throw new BackupException(failurePrefix +
                        "\nFile: " + file +
                        "\nFound: " + checksum +
                        "\nRequired: " + mChecksum.get(file.getName()));
            }
        }
    }

    @NonNull
    private Path[] requireStagedDataFiles(int index) throws BackupException {
        Path[] dataFiles = mStagedDataFiles != null ? mStagedDataFiles[index] : null;
        if (dataFiles == null) {
            throw new BackupException("Data files for index " + index + " weren't checked before the restore.");
        }
        return dataFiles;
    }

    @Nullable
    private Path findMiscFile() {
        try {
            return mBackupItem.getMiscFile();
        } catch (IOException e) {
            // There are no permissions, just skip
            return null;
        }
    }

    private void restoreApkFiles() throws BackupException {
        Path[] backupSourceFiles = mStagedSourceFiles;
        if (backupSourceFiles == null || mStaging == null) {
            throw new BackupException("Source files weren't checked before the restore.");
        }
        boolean isVerified = true;
        if (mPackageInfo != null) {
            // Check signature of the installed app
            List<String> certChecksumList = Arrays.asList(PackageUtils.getSigningCertChecksums(mBackupInfo.checksumAlgo, mPackageInfo, false));
            String[] certChecksums = BackupItems.Checksum.getCertChecksums(mChecksum);
            for (String checksum : certChecksums) {
                if (certChecksumList.contains(checksum)) continue;
                isVerified = false;
                if (!mRequestedFlags.skipSignatureCheck()) {
                    throw new BackupException("Signing info verification failed." +
                            "\nInstalled: " + certChecksumList +
                            "\nBackup: " + Arrays.toString(certChecksums));
                }
            }
        }
        // Setup package staging directory
        Path packageStagingDirectory = Paths.get(PackageUtils.PACKAGE_STAGING_DIRECTORY);
        try {
            synchronized (sLock) {
                PackageUtils.ensurePackageStagingDirectoryPrivileged();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not prepare the privileged package staging directory; using private restore staging.", e);
        }
        if (!packageStagingDirectory.canWrite()) {
            // Not the backup folder, where another app could swap the packages before the install
            packageStagingDirectory = mStaging.newDirectory("apk-");
        }
        synchronized (sLock) {
            // Setup apk files, including split apk
            final int splitCount = mBackupMetadata.splitConfigs.length;
            String[] allApkNames = new String[splitCount + 1];
            Path[] allApks = new Path[splitCount + 1];
            try {
                Path baseApk = packageStagingDirectory.createNewFile(mBackupMetadata.apkName, null);
                allApks[0] = baseApk;
                allApkNames[0] = mBackupMetadata.apkName;
                for (int i = 1; i < allApkNames.length; ++i) {
                    allApkNames[i] = mBackupMetadata.splitConfigs[i - 1];
                    allApks[i] = packageStagingDirectory.createNewFile(allApkNames[i], null);
                }
            } catch (IOException e) {
                throw new BackupException("Could not create staging files", e);
            }
            // Extract apk files to the package staging directory
            try {
                TarUtils.extract(mBackupInfo.tarType, backupSourceFiles, packageStagingDirectory,
                        getLiteralApkExtractionFilters(allApkNames), null, null);
            } catch (Exception th) {
                throw new BackupException("Failed to extract the apk file(s).", th);
            }
            // A normal update will do it now
            InstallerOptions options = InstallerOptions.getDefault();
            options.setInstallerName(mBackupMetadata.installer);
            options.setUserId(mUserId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                options.setInstallScenario(PackageManager.INSTALL_SCENARIO_BULK);
            }
            AtomicReference<String> status = new AtomicReference<>();
            PackageInstallerCompat packageInstaller = PackageInstallerCompat.getNewInstance();
            packageInstaller.setOnInstallListener(new PackageInstallerCompat.OnInstallListener() {
                @Override
                public void onStartInstall(int sessionId, String packageName) {
                }

                @Override
                public void onFinishedInstall(int sessionId, String packageName, int result, @Nullable String blockingPackage, @Nullable String statusMessage) {
                    status.set(statusMessage);
                }
            });
            try {
                if (!isVerified) {
                    // Signature verification failed but still here because signature check is disabled.
                    // The only way to restore is to reinstall the app. The signer pins are checked on
                    // the extracted APKs first: the install would refuse them after the app and its
                    // data were already gone.
                    String refusal = PackageInstallerCompat.signerPinRefusal(ContextUtils.getContext(), allApks,
                            mPackageName);
                    if (refusal != null) {
                        throw new BackupException("Couldn't perform a re-installation: " + refusal);
                    }
                    PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                    // uninstall() returns true on success; the guard was inverted, so it
                    // aborted the restore exactly when the required uninstall SUCCEEDED and
                    // silently continued (re-installing over a still-present, signature-
                    // mismatched package) when it FAILED. Throw only on actual failure.
                    if (!installer.uninstall(mPackageName, mUserId, false)) {
                        throw new BackupException("An uninstallation was necessary but couldn't perform it.");
                    }
                }
                if (!packageInstaller.install(allApks, mPackageName, options)) {
                    String statusMessage;
                    if (!isVerified) {
                        // Previously installed app was uninstalled.
                        statusMessage = "Couldn't perform a re-installation";
                    } else {
                        statusMessage = "Couldn't perform an installation";
                    }
                    if (status.get() != null) {
                        statusMessage += ": " + status.get();
                    } else statusMessage += ".";
                    throw new BackupException(statusMessage);
                }
            } finally {
                deleteFiles(allApks);  // Clean up apk files
            }
            // Get package info, again
            try {
                mPackageInfo = PackageManagerCompat.getPackageInfo(mPackageName, GET_SIGNING_CERTIFICATES
                        | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, mUserId);
                mUid = Objects.requireNonNull(mPackageInfo.applicationInfo).uid;
                mIsInstalled = true;
            } catch (Exception e) {
                throw new BackupException("Apparently the install wasn't complete in the previous section.", e);
            }
        }
    }

    @NonNull
    static String[] getLiteralApkExtractionFilters(@NonNull String[] apkNames) {
        String[] filters = new String[apkNames.length];
        for (int i = 0; i < apkNames.length; ++i) {
            filters[i] = Pattern.quote(apkNames[i]);
        }
        return filters;
    }

    private void restoreKeyStore() throws BackupException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // keystore v2 is not supported.
            Log.w(TAG, "Ignoring KeyStore backups for %s", mPackageName);
            return;
        }
        if (mPackageInfo == null) {
            throw new BackupException("KeyStore restore is requested but the app isn't installed.");
        }
        Path[] keyStoreFiles = mStagedKeyStoreFiles;
        if (keyStoreFiles == null) {
            throw new BackupException("KeyStore files weren't checked before the restore.");
        }
        // Restore KeyStore files to the /data/misc/keystore folder
        Path keyStorePath = KeyStoreUtils.getKeyStorePath(mUserId);
        // Note down UID/GID
        UidGidPair uidGidPair;
        int mode;
        try {
            uidGidPair = Objects.requireNonNull(keyStorePath.getFile()).getUidGid();
            mode = keyStorePath.getFile().getMode();
        } catch (ErrnoException e) {
            throw new BackupException("Failed to access properties of the KeyStore folder.", e);
        }
        try {
            TarUtils.extract(mBackupInfo.tarType, keyStoreFiles, keyStorePath, null, null, null);
            // Restore folder permission
            Paths.chown(keyStorePath, uidGidPair.uid, uidGidPair.gid);
            //noinspection OctalInteger
            Paths.chmod(keyStorePath, mode & 0777);
        } catch (Exception th) {
            throw new BackupException("Failed to restore the KeyStore files.", th);
        }
        // Rename files
        List<String> keyStoreFileNames = KeyStoreUtils.getKeyStoreFiles(KEYSTORE_PLACEHOLDER, mUserId);
        for (String keyStoreFileName : keyStoreFileNames) {
            try {
                String newFilename = Utils.replaceOnce(keyStoreFileName, String.valueOf(KEYSTORE_PLACEHOLDER), String.valueOf(mUid));
                keyStorePath.findFile(keyStoreFileName).renameTo(newFilename);
                Path targetFile = keyStorePath.findFile(newFilename);
                // Restore file permission
                Paths.chown(targetFile, uidGidPair.uid, uidGidPair.gid);
                //noinspection OctalInteger
                Paths.chmod(targetFile, 0600);
            } catch (IOException | ErrnoException e) {
                throw new BackupException("Failed to rename KeyStore files", e);
            }
        }
        Runner.runCommand(new String[]{"restorecon", "-R", keyStorePath.getFilePath()});
    }

    private void restoreData() throws BackupException {
        // Data restore is requested: Data restore is only possible if the app is actually
        // installed. So, check if it's installed first.
        if (mPackageInfo == null) {
            throw new BackupException("Data restore is requested but the app isn't installed.");
        }
        boolean hasSystemData = SystemDataBackup.hasSystemDataToken(mBackupMetadata.dataDirs);
        // The archives were checked when stageInputs() copied them
        StagedDataDirectoryRestore[] stagedDataRestores = null;
        if (!hasSystemData) {
            stagedDataRestores = stageRegularDataRestores();
            // Force-stop and clear app data only after regular tar-backed
            // restore payloads have been extracted into sibling staging dirs.
            // Global system-data restores must never clear the framework package.
            if (!PackageManagerCompat.clearApplicationUserData(mPackageName, mUserId)) {
                throw new BackupException("Could not clear app data before committing staged restore for "
                        + mPackageName + ".");
            }
        }
        try {
            // Restore backups
            for (int i = 0; i < mBackupMetadata.dataDirs.length; ++i) {
                String backupDataDir = mBackupMetadata.dataDirs[i];
                if (backupDataDir.equals(BackupManager.DATA_BACKUP_SPECIAL_ADB)) {
                    // Adb backup restore
                    restoreAdb(i);
                } else if (SystemDataBackup.isSystemDataToken(backupDataDir)) {
                    restoreSystemData(backupDataDir, i);
                } else if (stagedDataRestores != null) {
                    StagedDataDirectoryRestore stagedDataRestore = stagedDataRestores[i];
                    if (stagedDataRestore != null) {
                        stagedDataRestore.commit();
                    }
                } else {
                    // Regular directory restore
                    restoreDirectory(mBackupMetadata.dataDirs[i], i);
                }
            }
        } finally {
            cleanupStagedDataRestores(stagedDataRestores);
        }
    }

    @NonNull
    private StagedDataDirectoryRestore[] stageRegularDataRestores() throws BackupException {
        StagedDataDirectoryRestore[] stagedDataRestores = new StagedDataDirectoryRestore[mBackupMetadata.dataDirs.length];
        try {
            for (int i = 0; i < mBackupMetadata.dataDirs.length; ++i) {
                String backupDataDir = mBackupMetadata.dataDirs[i];
                if (backupDataDir.equals(BackupManager.DATA_BACKUP_SPECIAL_ADB)
                        || SystemDataBackup.isSystemDataToken(backupDataDir)) {
                    continue;
                }
                DataDirectoryRestoreTarget restoreTarget = prepareDirectoryRestore(backupDataDir, i);
                if (restoreTarget == null) {
                    continue;
                }
                StagedDataDirectoryRestore stagedDataRestore = new StagedDataDirectoryRestore(restoreTarget,
                        createRestoreStagingDirectory(restoreTarget.dataSourceFile, i));
                stagedDataRestore.extract();
                stagedDataRestores[i] = stagedDataRestore;
            }
            return stagedDataRestores;
        } catch (BackupException e) {
            cleanupStagedDataRestores(stagedDataRestores);
            throw e;
        }
    }

    private void cleanupStagedDataRestores(@Nullable StagedDataDirectoryRestore[] stagedDataRestores) {
        if (stagedDataRestores == null) {
            return;
        }
        for (StagedDataDirectoryRestore stagedDataRestore : stagedDataRestores) {
            if (stagedDataRestore != null) {
                stagedDataRestore.cleanup();
            }
        }
    }

    private void restoreSystemData(@NonNull String token, int index) throws BackupException {
        if (!mRequestedFlags.backupSystemData()) {
            return;
        }
        Path[] dataFiles = requireStagedDataFiles(index);
        if (dataFiles.length == 0) {
            throw new BackupException("System data restore is requested but there are no data files for index " + index + ".");
        }
        SystemDataBackup.Source source = SystemDataBackup.getSource(token, mUserId);
        try {
            TarUtils.extract(mBackupInfo.tarType, dataFiles, source.source, source.filters, source.exclusions, null);
        } catch (Exception th) {
            throw new BackupException("Failed to restore system data for index " + index + ".", th);
        }
        if (!Utils.isRoboUnitTest()) {
            Runner.runCommand(new String[]{"restorecon", "-R", source.source.getFilePath()});
        }
    }

    private void restoreDirectory(@NonNull String dir, int index) throws BackupException {
        DataDirectoryRestoreTarget restoreTarget = prepareDirectoryRestore(dir, index);
        if (restoreTarget == null) {
            return;
        }
        restoreDirectoryToTarget(restoreTarget, restoreTarget.dataSourceFile);
        finishRestoredDirectory(restoreTarget, restoreTarget.dataSourceFile);
    }

    @Nullable
    private DataDirectoryRestoreTarget prepareDirectoryRestore(@NonNull String dir, int index) throws BackupException {
        String dataSource = BackupUtils.getWritableDataDirectory(dir, mBackupInfo.userId, mUserId);
        BackupDataDirectoryInfo dataDirectoryInfo = BackupDataDirectoryInfo.getInfo(dataSource, mUserId);
        Path dataSourceFile = dataDirectoryInfo.getDirectory();
        if (!isRequestedDataDirectory(dataDirectoryInfo)) {
            return null;
        }
        Path[] dataFiles = requireStagedDataFiles(index);
        UidGidPair uidGidPair = dataSourceFile.getUidGid();
        if (uidGidPair == null) {
            // Fallback to app UID
            uidGidPair = new UidGidPair(mUid, mUid);
        }
        if (!dataSourceFile.exists() && dataDirectoryInfo.isExternal()
                && !dataDirectoryInfo.isMounted && !Utils.isRoboUnitTest()) {
            throw new BackupException("External directory containing " + dataSource + " is not mounted.");
        }
        return new DataDirectoryRestoreTarget(index, dataDirectoryInfo, dataSourceFile, dataFiles, uidGidPair);
    }

    private boolean isRequestedDataDirectory(@NonNull String dataDir) {
        String dataSource = BackupUtils.getWritableDataDirectory(dataDir, mBackupInfo.userId, mUserId);
        return isRequestedDataDirectory(BackupDataDirectoryInfo.getInfo(dataSource, mUserId));
    }

    private boolean isRequestedDataDirectory(@NonNull BackupDataDirectoryInfo dataDirectoryInfo) {
        if (!dataDirectoryInfo.isExternal()) {
            // Skip if internal data restore is not requested.
            return mRequestedFlags.backupInternalData();
        }
        // Skip if external data restore is not requested
        switch (dataDirectoryInfo.subtype) {
            case BackupDataDirectoryInfo.TYPE_ANDROID_DATA:
                // Skip restoring Android/data directory if not requested
                return mRequestedFlags.backupExternalData();
            case BackupDataDirectoryInfo.TYPE_ANDROID_OBB:
            case BackupDataDirectoryInfo.TYPE_ANDROID_MEDIA:
                // Skip restoring Android/data or Android/media if media/obb restore not requested
                return mRequestedFlags.backupMediaObb();
            case BackupDataDirectoryInfo.TYPE_CREDENTIAL_PROTECTED:
            case BackupDataDirectoryInfo.TYPE_CUSTOM:
            case BackupDataDirectoryInfo.TYPE_DEVICE_PROTECTED:
            default:
                return true;
        }
    }

    private void restoreDirectoryToTarget(@NonNull DataDirectoryRestoreTarget restoreTarget,
                                          @NonNull Path destination) throws BackupException {
        // Create data folder if not exists
        if (!destination.exists()) {
            if (!destination.mkdirs()) {
                throw new BackupException("Could not create directory " + destination);
            }
            if (!restoreTarget.dataDirectoryInfo.isExternal()) {
                // Restore UID, GID
                destination.setUidGid(restoreTarget.uidGidPair);
            }
        }
        // Already checked and decrypted
        Path[] dataFiles = restoreTarget.dataFiles;
        // Extract data to the data directory
        try {
            String publicSourceDir = new File(Objects.requireNonNull(mPackageInfo.applicationInfo).publicSourceDir).getParent();
            TarUtils.extract(mBackupInfo.tarType, dataFiles, destination, null, BackupUtils
                    .getExcludeDirs(!mRequestedFlags.backupCache(), null), publicSourceDir);
        } catch (Exception th) {
            throw new BackupException("Failed to restore data files for index " + restoreTarget.index + ".", th);
        }
    }

    /**
     * Hands a restored directory back to the app. A failure is recorded rather than thrown: the
     * app's data is already cleared by now, so stopping here would throw away the directories still
     * staged. runRestore fails once everything else is restored.
     */
    private void finishRestoredDirectory(@NonNull DataDirectoryRestoreTarget restoreTarget,
                                         @NonNull Path restoredDirectory) {
        // Restore UID and GID
        if (!runDirectoryCommand(new String[]{"chown", "-R",
                restoreTarget.uidGidPair.uid + ":" + restoreTarget.uidGidPair.gid,
                restoredDirectory.getFilePath()})) {
            addDirectoryFailure("Failed to restore ownership info for index " + restoreTarget.index + ".");
        }
        // Restore context. Without -D, restorecon leaves everything below /data/user/N/<pkg>
        // alone, so files extracted in a staging dir would keep system_data_file and the app
        // could not read its own data.
        if (!restoreTarget.dataDirectoryInfo.isExternal() && !runDirectoryCommand(new String[]{
                "restorecon", "-R", "-D", restoredDirectory.getFilePath()})) {
            addDirectoryFailure("Failed to restore SELinux context for index " + restoreTarget.index + ".");
        }
    }

    private static boolean runDirectoryCommand(@NonNull String[] command) {
        DirectoryCommandHook hook = sDirectoryCommandHook;
        if (hook != null) {
            return hook.run(command);
        }
        return Utils.isRoboUnitTest() || Runner.runCommand(command).isSuccessful();
    }

    private void addDirectoryFailure(@NonNull String message) {
        Log.e(TAG, "%s", message);
        mDirectoryFailures.add(message);
    }

    @NonNull
    private Path createRestoreStagingDirectory(@NonNull Path dataSourceFile, int index) throws BackupException {
        Path parent = dataSourceFile.requireParent();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new BackupException("Could not create parent directory " + parent);
        }
        String baseName = dataSourceFile.getName() + ".restore-" + index + "-" + Long.toHexString(System.nanoTime());
        for (int attempt = 0; attempt < 8; ++attempt) {
            String displayName = attempt == 0 ? baseName : baseName + "-" + attempt;
            try {
                return parent.createNewDirectory(displayName);
            } catch (IOException e) {
                if (attempt == 7) {
                    throw new BackupException("Could not create restore staging directory in " + parent, e);
                }
            }
        }
        throw new BackupException("Could not create restore staging directory in " + parent);
    }

    private void restoreAdb(int index) throws BackupException {
        Path[] dataFiles = requireStagedDataFiles(index);
        if (dataFiles.length != 1) {
            throw new BackupException("ADB restore is requested but there are no .ab files.");
        }
        // Restore data
        try (InputStream is = dataFiles[0].openInputStream()) {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            ParcelFileDescriptor readSide = pipe[0];
            ParcelFileDescriptor writeSide = pipe[1];
            // The transfer thread feeds the .ab stream into writeSide; adbRestore reads readSide.
            // Close our copy of readSide after adbRestore returns and join the transfer thread so
            // the read descriptor is not leaked on every ADB restore.
            ParcelFileDescriptorUtil.TransferThread t = ParcelFileDescriptorUtil.pipeFrom(is, writeSide);
            try {
                BackupCompat.adbRestore(mUserId, readSide);
            } finally {
                readSide.close();
            }
            t.join();
        } catch (Exception th) {
            throw new BackupException("Failed to restore ADB data", th);
        }
    }

    private synchronized void restoreExtras() throws BackupException {
        if (!mIsInstalled) {
            throw new BackupException("Misc restore is requested but the app isn't installed.");
        }
        PseudoRules rules = new PseudoRules(mPackageName, mUserId);
        // Backward compatibility for restoring permissions
        loadMiscRules(rules);
        // Apply rules
        List<RuleEntry> entries = rules.getAll();
        AppOpsManagerCompat appOpsManager = new AppOpsManagerCompat();
        INotificationManager notificationManager = INotificationManager.Stub.asInterface(ProxyBinder.getService(Context.NOTIFICATION_SERVICE));
        boolean magiskHideAvailable = MagiskHide.available();
        boolean magiskDenyListAvailable = MagiskDenyList.available();
        BackupExtrasCoverage.RestoreCapabilities baseCapabilities = BackupExtrasCoverage.fromCurrentMode(false);
        BackupExtrasCoverage.RestoreCapabilities capabilities = new BackupExtrasCoverage.RestoreCapabilities(
                baseCapabilities.canRestorePermissions,
                baseCapabilities.canRestoreAppOps,
                baseCapabilities.canRestoreNetworkPolicy,
                baseCapabilities.canRestoreBatteryOptimization,
                baseCapabilities.canRestoreNotificationListeners,
                baseCapabilities.canRestoreUriGrants,
                baseCapabilities.canRestoreSsaid,
                magiskHideAvailable || magiskDenyListAvailable);
        int appliedRules = 0;
        int failedRules = 0;
        for (RuleEntry entry : entries) {
            boolean applied = false;
            try {
                switch (entry.type) {
                    case APP_OP:
                        if (capabilities.canRestoreAppOps) {
                            appOpsManager.setMode(Integer.parseInt(entry.name), mUid, mPackageName,
                                    ((AppOpRule) entry).getMode(),
                                    AppOpsUidGuard.MutationSource.RESTORE, null);
                            applied = true;
                        } else {
                            recordExtraSkip(entry, capabilities);
                        }
                        break;
                    case NET_POLICY:
                        if (capabilities.canRestoreNetworkPolicy) {
                            NetworkPolicyManagerCompat.setUidPolicy(mUid,
                                    ((NetPolicyRule) entry).getPolicies());
                            applied = true;
                        } else {
                            recordExtraSkip(entry, capabilities);
                        }
                        break;
                    case PERMISSION: {
                        if (capabilities.canRestorePermissions) {
                            PermissionRule permissionRule = (PermissionRule) entry;
                            Permission permission = permissionRule.getPermission(true);
                            permission.setAppOpAllowed(permission.getAppOp() != AppOpsManagerCompat.OP_NONE && appOpsManager
                                    .checkOperation(permission.getAppOp(), mUid, mPackageName) == AppOpsManager.MODE_ALLOWED);
                            if (permissionRule.isGranted()) {
                                PermUtils.grantPermission(mPackageInfo, permission, appOpsManager, true, true);
                            } else {
                                PermUtils.revokePermission(mPackageInfo, permission, appOpsManager, true);
                            }
                            applied = true;
                        } else {
                            recordExtraSkip(entry, capabilities);
                        }
                        break;
                    }
                    case BATTERY_OPT:
                        if (capabilities.canRestoreBatteryOptimization) {
                            DeviceIdleManagerCompat.disableBatteryOptimization(mPackageName);
                            applied = true;
                        } else {
                            recordExtraSkip(entry, capabilities);
                        }
                        break;
                    case MAGISK_HIDE: {
                        MagiskHideRule magiskHideRule = (MagiskHideRule) entry;
                        if (magiskHideAvailable) {
                            if (!MagiskHide.apply(magiskHideRule.getMagiskProcess(), false)) {
                                throw new IOException("MagiskHide command failed");
                            }
                            applied = true;
                        } else if (magiskDenyListAvailable) {
                            // Fall-back to Magisk DenyList
                            if (!MagiskDenyList.apply(magiskHideRule.getMagiskProcess(), false)) {
                                throw new IOException("Magisk DenyList command failed");
                            }
                            applied = true;
                        } else {
                            recordExtraSkip(entry, capabilities);
                        }
                        break;
                    }
                    case MAGISK_DENY_LIST: {
                        if (magiskDenyListAvailable) {
                            if (!MagiskDenyList.apply(((MagiskDenyListRule) entry).getMagiskProcess(), false)) {
                                throw new IOException("Magisk DenyList command failed");
                            }
                            applied = true;
                        } else {
                            recordExtraSkip(entry, capabilities);
                        }
                        break;
                    }
                    case NOTIFICATION:
                        if (capabilities.canRestoreNotificationListeners
                                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            notificationManager.setNotificationListenerAccessGrantedForUser(
                                    new ComponentName(mPackageName, entry.name), mUserId, true);
                            applied = true;
                        } else {
                            recordExtraSkip(entry, capabilities);
                        }
                        break;
                    case URI_GRANT:
                        if (capabilities.canRestoreUriGrants) {
                            UriManager.UriGrant uriGrant = ((UriGrantRule) entry).getUriGrant();
                            UriManager.UriGrant newUriGrant = new UriManager.UriGrant(
                                    uriGrant.sourceUserId, mUserId, uriGrant.userHandle,
                                    uriGrant.sourcePkg, uriGrant.targetPkg, uriGrant.uri,
                                    uriGrant.prefix, uriGrant.modeFlags, uriGrant.createdTime);
                            UriManager uriManager = new UriManager();
                            uriManager.grantUri(newUriGrant);
                            uriManager.writeGrantedUriPermissions();
                            mRequiresRestart = true;
                            applied = true;
                        } else {
                            recordExtraSkip(entry, capabilities);
                        }
                        break;
                    case SSAID:
                        if (capabilities.canRestoreSsaid && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            new SsaidSettings(mUserId).setSsaid(mPackageName, mUid,
                                    ((SsaidRule) entry).getSsaid());
                            mRequiresRestart = true;
                            applied = true;
                        } else {
                            recordExtraSkip(entry, capabilities);
                        }
                        break;
                    case FREEZE:
                        int freezeType = ((FreezeRule) entry).getFreezeType();
                        FreezeUtils.storeFreezeMethod(mPackageName, freezeType);
                        applied = true;
                        break;
                }
            } catch (Exception e) {
                // There are several reason restoring these things go wrong, especially when
                // downgrading from an Android to another. It's better to simply suppress these
                // exceptions instead of causing a failure or worse, a crash
                recordExtraFailure(entry, e);
                Log.e(TAG, e);
                ++failedRules;
            }
            if (applied) {
                ++appliedRules;
            }
        }
        requireAcceptableExtraRestoreResult(appliedRules, failedRules);
    }

    static void requireAcceptableExtraRestoreResult(int appliedRules, int failedRules)
            throws BackupException {
        if (failedRules > appliedRules) {
            throw new BackupException("Extras restore failed for " + failedRules + " of "
                    + (appliedRules + failedRules) + " attempted rules; applied " + appliedRules
                    + ". The restore may be partial.");
        }
    }

    private void recordExtraSkip(@NonNull RuleEntry entry,
                                 @NonNull BackupExtrasCoverage.RestoreCapabilities capabilities) {
        addRestoreExtraWarning(BackupExtrasCoverage.formatAuditWarning(entry, capabilities));
    }

    private void recordExtraFailure(@NonNull RuleEntry entry, @NonNull Throwable error) {
        addRestoreExtraWarning(BackupExtrasCoverage.formatAuditWarning(entry, error));
    }

    private void addRestoreExtraWarning(@NonNull String warning) {
        if (mRestoreExtraWarnings.size() < BackupExtrasCoverage.MAX_AUDIT_WARNINGS) {
            mRestoreExtraWarnings.add(warning);
        } else if (mRestoreExtraWarnings.size() == BackupExtrasCoverage.MAX_AUDIT_WARNINGS) {
            mRestoreExtraWarnings.add("Additional Extras restore warnings omitted.");
        }
    }

    private void loadMiscRules(final PseudoRules rules) throws BackupException {
        // Checked and decrypted by stageInputs(), or there are no permissions
        Path miscFile = mStagedMiscFile;
        if (miscFile == null) {
            return;
        }
        try {
            rules.loadExternalEntries(miscFile);
        } catch (Exception e) {
            throw new BackupException("Failed to load rules from misc.", e);
        }
    }

    private void restoreRules() throws BackupException {
        // Apply rules
        if (!mIsInstalled) {
            throw new BackupException("Rules restore is requested but the app isn't installed.");
        }
        // Checked and decrypted by stageInputs(), which also failed the restore if rules were due
        Path rulesFile = mStagedRulesFile;
        if (rulesFile == null) {
            // There are no rules, just skip
            return;
        }
        try (RulesImporter importer = new RulesImporter(Arrays.asList(RuleType.values()), new int[]{mUserId})) {
            importer.addRulesFromPath(rulesFile);
            importer.setPackagesToImport(Collections.singletonList(mPackageName));
            importer.applyRules(true);
            Context context = ContextUtils.getContext();
            for (InternetAccess.Status status : importer.getInternetRulesNotInForce()) {
                // The wish is saved, but the firewall doesn't match it until it's in force
                String detail = status.describe(context);
                if (detail != null) {
                    addRestoreExtraWarning(context.getString(status.blocked ? R.string.restore_internet_rule_warning
                            : R.string.restore_internet_allow_warning, detail));
                }
            }
        } catch (IOException e) {
            throw new BackupException("Failed to restore rules file.", e);
        }
    }

    private static final class DataDirectoryRestoreTarget {
        private final int index;
        @NonNull
        private final BackupDataDirectoryInfo dataDirectoryInfo;
        @NonNull
        private final Path dataSourceFile;
        @NonNull
        private final Path[] dataFiles;
        @NonNull
        private final UidGidPair uidGidPair;

        private DataDirectoryRestoreTarget(int index, @NonNull BackupDataDirectoryInfo dataDirectoryInfo,
                                           @NonNull Path dataSourceFile, @NonNull Path[] dataFiles,
                                           @NonNull UidGidPair uidGidPair) {
            this.index = index;
            this.dataDirectoryInfo = dataDirectoryInfo;
            this.dataSourceFile = dataSourceFile;
            this.dataFiles = dataFiles;
            this.uidGidPair = uidGidPair;
        }
    }

    private final class StagedDataDirectoryRestore {
        @NonNull
        private final DataDirectoryRestoreTarget mRestoreTarget;
        @NonNull
        private final Path mStagingDirectory;
        private boolean mCommitted;
        private boolean mPreserveStaging;

        private StagedDataDirectoryRestore(@NonNull DataDirectoryRestoreTarget restoreTarget,
                                           @NonNull Path stagingDirectory) {
            mRestoreTarget = restoreTarget;
            mStagingDirectory = stagingDirectory;
        }

        private void extract() throws BackupException {
            restoreDirectoryToTarget(mRestoreTarget, mStagingDirectory);
        }

        private void commit() throws BackupException {
            mPreserveStaging = true;
            Path targetDirectory = mRestoreTarget.dataSourceFile;
            Path targetParent = targetDirectory.requireParent();
            if (!targetParent.exists() && !targetParent.mkdirs()) {
                throw new BackupException("Could not create parent directory " + targetParent);
            }
            if (targetDirectory.exists() && !targetDirectory.delete()) {
                throw new BackupException("Could not clear restore target directory " + targetDirectory);
            }
            if (!mStagingDirectory.moveTo(targetDirectory, false)) {
                copyStagingDirectoryToTarget(targetDirectory);
            }
            mCommitted = true;
            mPreserveStaging = false;
            finishRestoredDirectory(mRestoreTarget, targetDirectory);
        }

        private void copyStagingDirectoryToTarget(@NonNull Path targetDirectory) throws BackupException {
            if (targetDirectory.exists() && !targetDirectory.delete()) {
                throw new BackupException("Could not clear partial restore target directory " + targetDirectory
                        + "; staged restore data is preserved at " + mStagingDirectory + ".");
            }
            if (!targetDirectory.mkdirs()) {
                throw new BackupException("Could not create restore target directory " + targetDirectory
                        + "; staged restore data is preserved at " + mStagingDirectory + ".");
            }
            for (Path child : mStagingDirectory.listFiles()) {
                if (child.copyTo(targetDirectory, true) == null) {
                    throw new BackupException("Could not copy staged restore item " + child + " to "
                            + targetDirectory + "; staged restore data is preserved at " + mStagingDirectory + ".");
                }
            }
            if (!mStagingDirectory.delete()) {
                Log.w(TAG, "Could not delete restore staging directory %s after fallback copy.", mStagingDirectory);
            }
        }

        private void cleanup() {
            if (!mCommitted && !mPreserveStaging) {
                mStagingDirectory.delete();
            }
        }
    }

    private void deleteFiles(@NonNull Path[] files) {
        for (Path file : files) {
            file.delete();
        }
    }
}
