// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import static io.github.muntashirakon.AppManager.backup.BackupManager.CERT_PREFIX;
import static io.github.muntashirakon.AppManager.backup.BackupManager.getExt;
import static io.github.muntashirakon.AppManager.utils.TarUtils.DEFAULT_SPLIT_SIZE;
import static io.github.muntashirakon.AppManager.utils.TarUtils.TAR_GZIP;
import static io.github.muntashirakon.AppManager.utils.TarUtils.TAR_ZSTD;

import android.annotation.UserIdInt;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.tar.TarArchiveEntry;
import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import io.github.muntashirakon.AppManager.thirdparty.apache.commons.compress.archivers.tar.TarConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.muntashirakon.AppManager.backup.BackupException;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.CryptoUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV2;
import io.github.muntashirakon.AppManager.backup.struct.BackupMetadataV5;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ArchiveExtractionGuard;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.TarUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.SplitOutputStream;

/**
 * A documentation about OAndBackup is located at
 * <a href=https://github.com/MuntashirAkon/AppManager/issues/371#issuecomment-818429082>GH#371</a>.
 */
public class OABConverter extends Converter {
    public static final String TAG = OABConverter.class.getSimpleName();

    public static final String PATH_SUFFIX = "oandbackups";

    private static final List<String> SPECIAL_BACKUPS = new ArrayList<String>() {
        {
            add("accounts");
            add("appwidgets");
            add("bluetooth");
            add("data.usage.policy");
            add("wallpaper");
            add("wifi.access.points");
        }
    };

    private static final int MODE_UNSET = 0;
    private static final int MODE_APK = 1;
    private static final int MODE_DATA = 2;
    private static final int MODE_BOTH = 3;

    private static final String EXTERNAL_FILES = "external_files";
    private static final String NEO_BACKUP_PROPERTIES = "backup.properties";
    private static final String NEO_DATA = "data";
    private static final String NEO_DEVICE_PROTECTED_FILES = "device_protected_files";
    private static final String NEO_EXTERNAL_FILES = "external_files";
    private static final String NEO_OBB_FILES = "obb_files";
    private static final String NEO_MEDIA_FILES = "media_files";
    private static final String NEO_INSTANCE_REGEX =
            "\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}(?:-\\d{3})?-user_\\d+";
    private static final Pattern NEO_INSTANCE_PATTERN = Pattern.compile(NEO_INSTANCE_REGEX);
    private static final Pattern NEO_FLAT_INSTANCE_PATTERN = Pattern.compile("(.+)@(" + NEO_INSTANCE_REGEX + ")");

    private final Path mBackupLocation;
    private final String mPackageName;
    @UserIdInt
    private final int mUserId;

    private BackupItems.Checksum mChecksum;
    private BackupMetadataV2 mSourceMetadata;
    private String mSourceCryptoMode;
    private Crypto mSourceCrypto;
    private BackupMetadataV5 mDestMetadata;
    private BackupItems.BackupItem mBackupItem;
    private ArchiveExtractionGuard mExtractionGuard;
    private SourceLayout mSourceLayout;
    private Path mSourceLocation;
    private final List<Path> mNeoApkFiles = new ArrayList<>();
    private final List<NeoDataArchive> mNeoDataArchives = new ArrayList<>();

    @VisibleForTesting
    enum Layout {
        LEGACY,
        NEO
    }

    @VisibleForTesting
    static final class SourceLayout {
        @NonNull
        final Layout layout;
        @NonNull
        final Path location;
        @NonNull
        final String packageName;
        @Nullable
        final Path propertiesFile;

        SourceLayout(@NonNull Layout layout, @NonNull Path location, @NonNull String packageName,
                     @Nullable Path propertiesFile) {
            this.layout = layout;
            this.location = location;
            this.packageName = packageName;
            this.propertiesFile = propertiesFile;
        }
    }

    private static final class NeoDataArchive {
        @NonNull
        final Path path;
        @Nullable
        final String compressionType;

        NeoDataArchive(@NonNull Path path, @Nullable String compressionType) {
            this.path = path;
            this.compressionType = compressionType;
        }
    }

    /**
     * @param backupLocation E.g. {@code /sdcard/oandbackups/package.name}
     */
    public OABConverter(@NonNull Path backupLocation) {
        mBackupLocation = backupLocation;
        mPackageName = inferPackageName(backupLocation);
        mUserId = UserHandleHidden.myUserId();
    }

    @Override
    public void convert() throws BackupException {
        if (SPECIAL_BACKUPS.contains(mPackageName)) {
            throw new BackupException("Cannot convert special backup " + mPackageName);
        }
        mSourceLayout = detectSourceLayout(mBackupLocation);
        mSourceLocation = mSourceLayout.location;
        // Source metadata
        mSourceMetadata = mSourceLayout.layout == Layout.LEGACY ? readLogFile() : readNeoProperties();
        List<Path> guardedSources = new ArrayList<>();
        guardedSources.addAll(Arrays.asList(mSourceLocation.listFiles()));
        if (mSourceLayout.layout == Layout.LEGACY) {
            try {
                guardedSources.addAll(Arrays.asList(mSourceLocation.findFile(EXTERNAL_FILES).listFiles()));
            } catch (FileNotFoundException ignore) {
            }
        }
        mExtractionGuard = createExtractionGuard(guardedSources);
        // Simulate a backup creation
        try {
            mBackupItem = BackupItems.createBackupItemGracefully(mUserId, "OAndBackup", mPackageName);
        } catch (IOException e) {
            throw new BackupException("Could not get backup files.", e);
        }
        boolean backupSuccess = false;
        try {
            try {
                // Destination metadata
                mDestMetadata = ConvertUtils.getV5Metadata(mSourceMetadata, mBackupItem);
            } catch (CryptoException e) {
                throw new BackupException("Failed to get crypto " + mDestMetadata.info.crypto, e);
            }
            try {
                mChecksum = mBackupItem.getChecksum();
            } catch (IOException e) {
                throw new BackupException("Failed to create checksum file.", e);
            }
            if (mDestMetadata.info.flags.backupApkFiles()) {
                if (mSourceLayout.layout == Layout.NEO) {
                    backupNeoApkFiles();
                } else {
                    backupApkFile();
                }
            }
            if (mDestMetadata.info.flags.backupData()) {
                if (mSourceLayout.layout == Layout.NEO) {
                    backupNeoData();
                } else {
                    backupData();
                }
            }
            // Write modified metadata
            try {
                Map<String, String> filenameChecksumMap = MetadataManager.writeMetadata(mDestMetadata, mBackupItem);
                for (Map.Entry<String, String> filenameChecksumPair : filenameChecksumMap.entrySet()) {
                    mChecksum.add(filenameChecksumPair.getKey(), filenameChecksumPair.getValue());
                }
            } catch (IOException e) {
                throw new BackupException("Failed to write metadata.", e);
            }
            // Store checksum for metadata
            mChecksum.close();
            // Encrypt checksum
            try {
                mBackupItem.encrypt(new Path[]{mChecksum.getFile()});
            } catch (IOException e) {
                throw new BackupException("Failed to encrypt checksums.txt", e);
            }
            // Replace current backup
            try {
                mBackupItem.commit();
            } catch (IOException e) {
                throw new BackupException("Could not finalise backup.", e);
            }
            backupSuccess = true;
        } catch (BackupException e) {
            throw e;
        } catch (Exception th) {
            throw new BackupException("Unknown error occurred.", th);
        } finally {
            // Always release the source crypto here: cleanup() (which used to close it) is only
            // invoked when the imported directory is being removed, so on the common "keep imported
            // files" path the decrypted AES key / OpenPGP binding would otherwise leak.
            if (mSourceCrypto != null) {
                mSourceCrypto.close();
            }
            mBackupItem.cleanup();
            if (backupSuccess) {
                BackupUtils.putBackupToDbAndBroadcast(ContextUtils.getContext(), mDestMetadata);
            }
        }
    }

    @Override
    public void cleanup() {
        if (mSourceLayout != null && mSourceLayout.layout == Layout.NEO) {
            if (mSourceLayout.propertiesFile != null) {
                mSourceLayout.propertiesFile.delete();
            }
            mSourceLayout.location.delete();
        } else {
            mBackupLocation.delete();
        }
    }

    @Override
    public String getPackageName() {
        return mPackageName;
    }

    @NonNull
    private static String inferPackageName(@NonNull Path backupLocation) {
        Matcher flatMatcher = NEO_FLAT_INSTANCE_PATTERN.matcher(backupLocation.getName());
        if (flatMatcher.matches()) {
            return flatMatcher.group(1);
        }
        if (NEO_INSTANCE_PATTERN.matcher(backupLocation.getName()).matches()) {
            Path parent = backupLocation.getParent();
            if (parent != null) {
                return parent.getName();
            }
        }
        return backupLocation.getName();
    }

    @NonNull
    static Path[] getRelevantImportLocations(@NonNull Path baseLocation) {
        List<Path> importLocations = new ArrayList<>();
        for (Path packageDirectory : baseLocation.listFiles(Path::isDirectory)) {
            if (NEO_FLAT_INSTANCE_PATTERN.matcher(packageDirectory.getName()).matches()
                    && findNeoPropertiesFile(packageDirectory) != null) {
                importLocations.add(packageDirectory);
                continue;
            }
            Path[] neoInstances = getNeoBackupInstances(packageDirectory);
            if (neoInstances.length > 0) {
                // A Neo Backup package can retain its legacy log after migration. Prefer its
                // revision directories so deleting one successful import cannot remove siblings
                // that are still being converted concurrently.
                importLocations.addAll(Arrays.asList(neoInstances));
            } else {
                // This is either a legacy package or an unrecognized directory. Keep both so the
                // converter can report the expected layouts instead of silently skipping errors.
                importLocations.add(packageDirectory);
            }
        }
        return importLocations.toArray(new Path[0]);
    }

    @NonNull
    private static Path[] getNeoBackupInstances(@NonNull Path packageDirectory) {
        Path[] instances = packageDirectory.listFiles(path -> path.isDirectory()
                && NEO_INSTANCE_PATTERN.matcher(path.getName()).matches()
                && findNeoPropertiesFile(path) != null);
        Arrays.sort(instances, Comparator.comparing(Path::getName));
        return instances;
    }

    @Nullable
    private static Path findNeoPropertiesFile(@NonNull Path instanceDirectory) {
        try {
            if (instanceDirectory.hasFile(NEO_BACKUP_PROPERTIES)) {
                return instanceDirectory.findFile(NEO_BACKUP_PROPERTIES);
            }
            Path parent = instanceDirectory.getParent();
            String siblingName = instanceDirectory.getName() + ".properties";
            if (parent != null && parent.hasFile(siblingName)) {
                return parent.findFile(siblingName);
            }
        } catch (IOException ignore) {
        }
        return null;
    }

    @VisibleForTesting
    @NonNull
    static SourceLayout detectSourceLayout(@NonNull Path importLocation) throws BackupException {
        Path propertiesFile = findNeoPropertiesFile(importLocation);
        if (NEO_INSTANCE_PATTERN.matcher(importLocation.getName()).matches() && propertiesFile != null) {
            Path parent = importLocation.getParent();
            if (parent == null) {
                throw unrecognizedLayout(importLocation);
            }
            return new SourceLayout(Layout.NEO, importLocation, parent.getName(), propertiesFile);
        }
        Matcher flatMatcher = NEO_FLAT_INSTANCE_PATTERN.matcher(importLocation.getName());
        if (flatMatcher.matches() && propertiesFile != null) {
            return new SourceLayout(Layout.NEO, importLocation, flatMatcher.group(1), propertiesFile);
        }
        String packageName = importLocation.getName();
        if (importLocation.hasFile(packageName + ".log")) {
            return new SourceLayout(Layout.LEGACY, importLocation, packageName, null);
        }
        Path[] neoInstances = getNeoBackupInstances(importLocation);
        if (neoInstances.length > 0) {
            Path latestInstance = neoInstances[neoInstances.length - 1];
            return new SourceLayout(Layout.NEO, latestInstance, packageName,
                    findNeoPropertiesFile(latestInstance));
        }
        throw unrecognizedLayout(importLocation);
    }

    @NonNull
    private static BackupException unrecognizedLayout(@NonNull Path importLocation) {
        return new BackupException("Unrecognized OAndBackup/Neo Backup layout at " + importLocation
                + ". Expected <packageName>.log, <revision>.properties beside a Neo Backup "
                + "revision directory, or backup.properties inside YYYY-MM-DD-HH-MM-SS[-mmm]-user_N.");
    }

    private BackupMetadataV2 readLogFile() throws BackupException {
        try {
            BackupMetadataV2 metadataV2 = new BackupMetadataV2();
            Path logFile = mBackupLocation.findFile(mPackageName + ".log");
            String jsonString = logFile.getContentAsString();
            if (TextUtils.isEmpty(jsonString)) throw new JSONException("Empty JSON string.");
            JSONObject jsonObject = new JSONObject(jsonString);
            metadataV2.label = jsonObject.getString("label");
            metadataV2.packageName = jsonObject.getString("packageName");
            metadataV2.versionName = jsonObject.getString("versionName");
            metadataV2.versionCode = jsonObject.getInt("versionCode");
            metadataV2.isSystem = jsonObject.optBoolean("isSystem");
            metadataV2.isSplitApk = false;
            metadataV2.splitConfigs = ArrayUtils.emptyArray(String.class);
            metadataV2.hasRules = false;
            metadataV2.backupTime = jsonObject.getLong("lastBackupMillis");
            metadataV2.crypto = jsonObject.optBoolean("isEncrypted") ? CryptoUtils.MODE_OPEN_PGP : CryptoUtils.MODE_NO_ENCRYPTION;
            mSourceCryptoMode = metadataV2.crypto;
            mSourceCrypto = CryptoUtils.setupCrypto(metadataV2);
            metadataV2.apkName = new File(jsonObject.getString("sourceDir")).getName();
            // Flags
            metadataV2.flags = new BackupFlags(BackupFlags.BACKUP_MULTIPLE);
            int backupMode = jsonObject.optInt("backupMode", MODE_UNSET);
            if (backupMode == MODE_UNSET) {
                throw new BackupException("Destination doesn't contain any backup.");
            }
            if (backupMode == MODE_APK || backupMode == MODE_BOTH) {
                if (mBackupLocation.hasFile(CryptoUtils.getAppropriateFilename(metadataV2.apkName,
                        mSourceCryptoMode))) {
                    metadataV2.flags.addFlag(BackupFlags.BACKUP_APK_FILES);
                } else {
                    throw new BackupException("Destination doesn't contain any APK files.");
                }
            }
            if (backupMode == MODE_DATA || backupMode == MODE_BOTH) {
                boolean hasBackup = false;
                if (mBackupLocation.hasFile(CryptoUtils.getAppropriateFilename(mPackageName + ".zip",
                        mSourceCryptoMode))) {
                    metadataV2.flags.addFlag(BackupFlags.BACKUP_INT_DATA);
                    hasBackup = true;
                }
                if (mBackupLocation.hasFile(EXTERNAL_FILES) && mBackupLocation.findFile(EXTERNAL_FILES).hasFile(
                        CryptoUtils.getAppropriateFilename(mPackageName + ".zip", mSourceCryptoMode))) {
                    metadataV2.flags.addFlag(BackupFlags.BACKUP_EXT_DATA);
                    hasBackup = true;
                }
                if (!hasBackup) {
                    throw new BackupException("Destination doesn't contain any data files.");
                }
                metadataV2.flags.addFlag(BackupFlags.BACKUP_CACHE);
            }
            metadataV2.userId = UserHandleHidden.myUserId();
            metadataV2.dataDirs = ConvertUtils.getDataDirs(mPackageName, mUserId, metadataV2.flags
                    .backupInternalData(), metadataV2.flags.backupExternalData(), false);
            metadataV2.tarType = Prefs.BackupRestore.getCompressionMethod();
            metadataV2.keyStore = false;
            metadataV2.installer = Prefs.Installer.getInstallerPackageName();
            metadataV2.version = 2;  // Old version is used so that we know that it needs permission fixes
            return metadataV2;
        } catch (JSONException | IOException | CryptoException e) {
            return ExUtils.rethrowAsBackupException("Could not parse JSON file.", e);
        }
    }

    private BackupMetadataV2 readNeoProperties() throws BackupException {
        try {
            mNeoApkFiles.clear();
            mNeoDataArchives.clear();
            Path propertiesFile = mSourceLayout.propertiesFile;
            if (propertiesFile == null) {
                throw new BackupException("Neo Backup properties file is missing.");
            }
            String jsonString = propertiesFile.getContentAsString();
            if (TextUtils.isEmpty(jsonString)) {
                throw new JSONException("Empty JSON string.");
            }
            JSONObject jsonObject = new JSONObject(jsonString);
            String packageName = jsonObject.getString("packageName");
            if (!mPackageName.equals(packageName)) {
                throw new BackupException("Neo Backup package mismatch: directory is " + mPackageName
                        + " but the properties file describes " + packageName + ".");
            }

            String cipherType = getNullableString(jsonObject, "cipherType");
            if (!TextUtils.isEmpty(cipherType)) {
                throw new BackupException("Encrypted Neo Backup archives are not supported.");
            }
            String declaredCompression = getNullableString(jsonObject, "compressionType");
            if (!TextUtils.isEmpty(declaredCompression)
                    && !"no".equals(declaredCompression)
                    && !"gz".equals(declaredCompression)
                    && !"zst".equals(declaredCompression)) {
                throw new BackupException("Unsupported Neo Backup compression type: " + declaredCompression);
            }

            BackupMetadataV2 metadataV2 = new BackupMetadataV2();
            metadataV2.label = jsonObject.getString("packageLabel");
            metadataV2.packageName = packageName;
            String versionName = getNullableString(jsonObject, "versionName");
            metadataV2.versionName = versionName != null ? versionName : "-";
            metadataV2.versionCode = jsonObject.optLong("versionCode", 0);
            metadataV2.isSystem = jsonObject.optBoolean("isSystem");
            String sourceDir = getNullableString(jsonObject, "sourceDir");
            metadataV2.apkName = TextUtils.isEmpty(sourceDir) ? "base.apk" : new File(sourceDir).getName();
            metadataV2.splitConfigs = getNeoSplitApkNames(jsonObject.optJSONArray("splitSourceDirs"));
            metadataV2.isSplitApk = metadataV2.splitConfigs.length > 0;
            metadataV2.hasRules = false;
            metadataV2.backupTime = LocalDateTime.parse(jsonObject.getString("backupDate"))
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            metadataV2.crypto = CryptoUtils.MODE_NO_ENCRYPTION;
            mSourceCryptoMode = metadataV2.crypto;

            boolean hasApk = jsonObject.optBoolean("hasApk");
            boolean hasAppData = jsonObject.optBoolean("hasAppData");
            boolean hasDeviceProtectedData = jsonObject.optBoolean("hasDevicesProtectedData");
            boolean hasExternalData = jsonObject.optBoolean("hasExternalData");
            boolean hasObbData = jsonObject.optBoolean("hasObbData");
            boolean hasMediaData = jsonObject.optBoolean("hasMediaData");

            metadataV2.flags = new BackupFlags(BackupFlags.BACKUP_MULTIPLE);
            if (hasApk) {
                metadataV2.flags.addFlag(BackupFlags.BACKUP_APK_FILES);
                addNeoApkFile(metadataV2.apkName);
                for (String splitConfig : metadataV2.splitConfigs) {
                    addNeoApkFile(splitConfig);
                }
            }
            if (hasAppData || hasDeviceProtectedData) {
                metadataV2.flags.addFlag(BackupFlags.BACKUP_INT_DATA);
            }
            if (hasExternalData) {
                metadataV2.flags.addFlag(BackupFlags.BACKUP_EXT_DATA);
            }
            if (hasObbData || hasMediaData) {
                metadataV2.flags.addFlag(BackupFlags.BACKUP_EXT_OBB_MEDIA);
            }
            if (hasAppData || hasDeviceProtectedData || hasExternalData || hasObbData || hasMediaData) {
                metadataV2.flags.addFlag(BackupFlags.BACKUP_CACHE);
            }

            addNeoDataArchive(NEO_DATA, hasAppData, declaredCompression);
            addNeoDataArchive(NEO_DEVICE_PROTECTED_FILES, hasDeviceProtectedData, declaredCompression);
            addNeoDataArchive(NEO_EXTERNAL_FILES, hasExternalData, declaredCompression);
            addNeoDataArchive(NEO_OBB_FILES, hasObbData, declaredCompression);
            addNeoDataArchive(NEO_MEDIA_FILES, hasMediaData, declaredCompression);
            if (!hasApk && mNeoDataArchives.isEmpty()) {
                throw new BackupException("Neo Backup properties do not describe any APK or data files.");
            }

            metadataV2.userId = mUserId;
            metadataV2.dataDirs = ConvertUtils.getNeoDataDirs(mPackageName, mUserId, hasAppData,
                    hasDeviceProtectedData, hasExternalData, hasObbData, hasMediaData);
            metadataV2.tarType = Prefs.BackupRestore.getCompressionMethod();
            metadataV2.keyStore = false;
            metadataV2.installer = Prefs.Installer.getInstallerPackageName();
            // Neo Backup's tar entries preserve permissions, but its representation is not the
            // same as App Manager's v3+ metadata. Keep the established converter repair path.
            metadataV2.version = 2;
            return metadataV2;
        } catch (BackupException e) {
            throw e;
        } catch (JSONException | DateTimeParseException e) {
            return ExUtils.rethrowAsBackupException("Could not parse Neo Backup properties.", e);
        }
    }

    @Nullable
    private static String getNullableString(@NonNull JSONObject jsonObject, @NonNull String key) {
        return !jsonObject.has(key) || jsonObject.isNull(key) ? null : jsonObject.optString(key, null);
    }

    @NonNull
    private static String[] getNeoSplitApkNames(@Nullable JSONArray splitSourceDirs) throws JSONException {
        if (splitSourceDirs == null || splitSourceDirs.length() == 0) {
            return ArrayUtils.emptyArray(String.class);
        }
        Set<String> splitNames = new LinkedHashSet<>();
        for (int i = 0; i < splitSourceDirs.length(); ++i) {
            String splitPath = splitSourceDirs.getString(i);
            if (!TextUtils.isEmpty(splitPath)) {
                splitNames.add(new File(splitPath).getName());
            }
        }
        return splitNames.toArray(new String[0]);
    }

    private void addNeoApkFile(@NonNull String apkName) throws BackupException {
        try {
            Path apkFile = mSourceLocation.findFile(apkName);
            if (!apkFile.isFile()) {
                throw new FileNotFoundException(apkName);
            }
            mNeoApkFiles.add(apkFile);
        } catch (FileNotFoundException e) {
            throw new BackupException("Neo Backup says it contains " + apkName
                    + ", but that APK is missing.", e);
        }
    }

    private void addNeoDataArchive(@NonNull String dataType, boolean present,
                                   @Nullable String declaredCompression) throws BackupException {
        if (present) {
            mNeoDataArchives.add(findNeoDataArchive(dataType, declaredCompression));
        }
    }

    @NonNull
    private NeoDataArchive findNeoDataArchive(@NonNull String dataType,
                                              @Nullable String declaredCompression) throws BackupException {
        String expectedSuffix;
        if ("gz".equals(declaredCompression)) {
            expectedSuffix = ".tar.gz";
        } else if ("zst".equals(declaredCompression)) {
            expectedSuffix = ".tar.zst";
        } else {
            expectedSuffix = ".tar";
        }
        List<String> candidateNames = new ArrayList<>(Arrays.asList(
                dataType + expectedSuffix,
                dataType + ".tar",
                dataType + ".tar.gz",
                dataType + ".tar.zst"));
        Set<String> uniqueNames = new LinkedHashSet<>(candidateNames);
        for (String candidateName : uniqueNames) {
            if (mSourceLocation.hasFile(candidateName)) {
                try {
                    String compressionType = candidateName.endsWith(".tar.gz") ? TAR_GZIP
                            : candidateName.endsWith(".tar.zst") ? TAR_ZSTD : null;
                    return new NeoDataArchive(mSourceLocation.findFile(candidateName), compressionType);
                } catch (FileNotFoundException ignore) {
                }
            }
        }
        for (String candidateName : uniqueNames) {
            if (mSourceLocation.hasFile(candidateName + ".enc")) {
                throw new BackupException("Encrypted Neo Backup archives are not supported.");
            }
        }
        throw new BackupException("Neo Backup says it contains " + dataType
                + " data, but no " + dataType + ".tar[.gz|.zst] archive exists.");
    }

    private void backupApkFile() throws BackupException {
        Path[] baseApkFiles;
        try {
            baseApkFiles = new Path[]{mBackupLocation.findFile(CryptoUtils.getAppropriateFilename(
                    mSourceMetadata.apkName, mSourceCryptoMode))};
        } catch (FileNotFoundException e) {
            throw new BackupException("Could not get base.apk file.", e);
        }
        // Decrypt APK file if needed
        try {
            baseApkFiles = ConvertUtils.decryptSourceFiles(baseApkFiles, mSourceCrypto, mSourceCryptoMode, mBackupItem);
        } catch (IOException e) {
            throw new BackupException("Failed to decrypt " + Arrays.toString(baseApkFiles), e);
        }
        // baseApkFiles should be a singleton array
        if (baseApkFiles.length != 1) {
            throw new BackupException("Incorrect number of APK files: " + baseApkFiles.length);
        }
        Path baseApkFile = baseApkFiles[0];
        // Get certificate checksums
        try {
            String[] checksums = ConvertUtils.getChecksumsFromApk(baseApkFile, mDestMetadata.info.checksumAlgo);
            for (int i = 0; i < checksums.length; ++i) {
                mChecksum.add(CERT_PREFIX + i, checksums[i]);
            }
        } catch (Exception ignore) {
        }
        // Backup APK file
        String sourceBackupFilePrefix = BackupUtils.getSourceFilePrefix(getExt(mDestMetadata.info.tarType));
        Path[] sourceFiles;
        try {
            sourceFiles = TarUtils.create(mDestMetadata.info.tarType, baseApkFile, mBackupItem.getUnencryptedBackupPath(), sourceBackupFilePrefix,
                            /* language=regexp */ new String[]{".*\\.apk"}, null, null, false)
                    .toArray(new Path[0]);
        } catch (Exception th) {
            throw new BackupException("APK files backup is requested but no APK files have been backed up.", th);
        }
        // Overwrite with the new files
        try {
            sourceFiles = mBackupItem.encrypt(sourceFiles);
        } catch (IOException e) {
            throw new BackupException("Failed to encrypt " + Arrays.toString(sourceFiles), e);
        }
        try {
            for (Path file : sourceFiles) {
                mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.info.checksumAlgo, file));
            }
        } catch (IOException e) {
            throw new BackupException("Failed to write source checksums.", e);
        }
    }

    private void backupNeoApkFiles() throws BackupException {
        if (mNeoApkFiles.isEmpty()) {
            throw new BackupException("Neo Backup APK files are missing.");
        }
        Path baseApkFile = mNeoApkFiles.get(0);
        try {
            String[] checksums = ConvertUtils.getChecksumsFromApk(baseApkFile, mDestMetadata.info.checksumAlgo);
            for (int i = 0; i < checksums.length; ++i) {
                mChecksum.add(CERT_PREFIX + i, checksums[i]);
            }
        } catch (Exception ignore) {
        }

        String[] apkFilters = new String[mNeoApkFiles.size()];
        for (int i = 0; i < mNeoApkFiles.size(); ++i) {
            apkFilters[i] = Pattern.quote(mNeoApkFiles.get(i).getName());
        }
        String sourceBackupFilePrefix = BackupUtils.getSourceFilePrefix(getExt(mDestMetadata.info.tarType));
        Path[] sourceFiles;
        try {
            sourceFiles = TarUtils.create(mDestMetadata.info.tarType, mSourceLocation,
                            mBackupItem.getUnencryptedBackupPath(), sourceBackupFilePrefix, apkFilters,
                            null, null, false)
                    .toArray(new Path[0]);
        } catch (Exception e) {
            throw new BackupException("APK files backup is requested but no Neo Backup APK files were imported.", e);
        }
        try {
            sourceFiles = mBackupItem.encrypt(sourceFiles);
            for (Path file : sourceFiles) {
                mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.info.checksumAlgo, file));
            }
        } catch (IOException e) {
            throw new BackupException("Failed to store imported Neo Backup APK files.", e);
        }
    }

    private void backupNeoData() throws BackupException {
        String tarType = mDestMetadata.info.tarType;
        int dataIndex = 0;
        for (NeoDataArchive dataArchive : mNeoDataArchives) {
            String dataBackupFilePrefix = BackupUtils.getDataFilePrefix(dataIndex++, getExt(tarType));
            List<Path> outputFiles = new ArrayList<>();
            try (InputStream rawInputStream = dataArchive.path.openInputStream();
                 BufferedInputStream bufferedInputStream = new BufferedInputStream(rawInputStream);
                 InputStream archiveInputStream = dataArchive.compressionType != null
                         ? TarUtils.createDecompressedStream(bufferedInputStream, dataArchive.compressionType)
                         : bufferedInputStream;
                 TarArchiveInputStream tis = new TarArchiveInputStream(archiveInputStream);
                 SplitOutputStream sos = new SplitOutputStream(mBackupItem.getUnencryptedBackupPath(),
                         dataBackupFilePrefix, DEFAULT_SPLIT_SIZE);
                 BufferedOutputStream bos = new BufferedOutputStream(sos);
                 OutputStream compressedOutputStream = TarUtils.createCompressedStream(bos, tarType);
                 TarArchiveOutputStream tos = new TarArchiveOutputStream(compressedOutputStream)) {
                tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
                TarArchiveEntry sourceEntry;
                while ((sourceEntry = tis.getNextEntry()) != null) {
                    mExtractionGuard.onNewEntry();
                    mExtractionGuard.assertEntrySize(sourceEntry.getSize());
                    String fileName = getNeoRelativeBackupEntryName(sourceEntry.getName(), mPackageName);
                    if (fileName.isEmpty()) {
                        if (sourceEntry.isFile()) {
                            mExtractionGuard.drain(tis);
                        }
                        continue;
                    }
                    TarArchiveEntry targetEntry = copyNeoTarEntry(sourceEntry, fileName);
                    tos.putArchiveEntry(targetEntry);
                    if (sourceEntry.isFile()) {
                        mExtractionGuard.copy(tis, tos);
                    }
                    tos.closeArchiveEntry();
                }
                tos.finish();
                outputFiles.addAll(sos.getFiles());
            } catch (IOException e) {
                throw new BackupException("Could not import Neo Backup archive " + dataArchive.path + ".", e);
            }
            try {
                Path[] newBackupFiles = mBackupItem.encrypt(outputFiles.toArray(new Path[0]));
                for (Path file : newBackupFiles) {
                    mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.info.checksumAlgo, file));
                }
            } catch (IOException e) {
                throw new BackupException("Failed to store imported Neo Backup data.", e);
            }
        }
    }

    @VisibleForTesting
    @NonNull
    static String getNeoRelativeBackupEntryName(@NonNull String entryName,
                                                 @NonNull String packageName) throws IOException {
        String normalizedName = entryName.replace('\\', '/');
        while (normalizedName.startsWith("./")) {
            normalizedName = normalizedName.substring(2);
        }
        if (normalizedName.equals(".") || normalizedName.equals(packageName)) {
            return "";
        }
        String packagePrefix = packageName + "/";
        String relativeName = normalizedName.startsWith(packagePrefix)
                ? normalizedName.substring(packagePrefix.length()) : normalizedName;
        ConvertUtils.validateRelativeBackupEntryName(relativeName, entryName);
        return relativeName;
    }

    @NonNull
    private static TarArchiveEntry copyNeoTarEntry(@NonNull TarArchiveEntry sourceEntry,
                                                    @NonNull String fileName) throws IOException {
        byte linkFlag;
        if (sourceEntry.isDirectory()) {
            linkFlag = TarConstants.LF_DIR;
            if (!fileName.endsWith("/")) {
                fileName += "/";
            }
        } else if (sourceEntry.isSymbolicLink()) {
            linkFlag = TarConstants.LF_SYMLINK;
        } else if (sourceEntry.isLink()) {
            linkFlag = TarConstants.LF_LINK;
        } else if (sourceEntry.isFIFO()) {
            linkFlag = TarConstants.LF_FIFO;
        } else if (sourceEntry.isFile()) {
            linkFlag = TarConstants.LF_NORMAL;
        } else {
            throw new IOException("Unsupported Neo Backup archive entry type: " + sourceEntry.getName());
        }
        TarArchiveEntry targetEntry = new TarArchiveEntry(fileName, linkFlag);
        targetEntry.setMode(sourceEntry.getMode());
        targetEntry.setUserId(sourceEntry.getUserId());
        targetEntry.setGroupId(sourceEntry.getGroupId());
        targetEntry.setUserName(sourceEntry.getUserName());
        targetEntry.setGroupName(sourceEntry.getGroupName());
        targetEntry.setModTime(sourceEntry.getModTime());
        targetEntry.setSize(sourceEntry.isFile() ? sourceEntry.getSize() : 0);
        if (sourceEntry.isSymbolicLink() || sourceEntry.isLink()) {
            targetEntry.setLinkName(sourceEntry.getLinkName());
        }
        return targetEntry;
    }

    private void backupData() throws BackupException {
        List<Path> dataFiles = new ArrayList<>(2);
        if (mDestMetadata.info.flags.backupInternalData()) {
            try {
                dataFiles.add(mBackupLocation.findFile(CryptoUtils.getAppropriateFilename(mPackageName + ".zip",
                        mSourceCryptoMode)));
            } catch (FileNotFoundException e) {
                throw new BackupException("Could not get internal data backup.", e);
            }
        }
        if (mDestMetadata.info.flags.backupExternalData()) {
            try {
                dataFiles.add(mBackupLocation.findFile(EXTERNAL_FILES).findFile(CryptoUtils.getAppropriateFilename(
                        mPackageName + ".zip", mSourceCryptoMode)));
            } catch (FileNotFoundException e) {
                throw new BackupException("Could not get external data backup.", e);
            }
        }
        String tarType = mDestMetadata.info.tarType;
        int i = 0;
        Path[] files;
        for (Path dataFile : dataFiles) {
            files = new Path[]{dataFile};
            // Decrypt APK file if needed
            try {
                files = ConvertUtils.decryptSourceFiles(files, mSourceCrypto, mSourceCryptoMode, mBackupItem);
            } catch (IOException e) {
                throw new BackupException("Failed to decrypt " + Arrays.toString(files), e);
            }
            // baseApkFiles should be a singleton array
            if (files.length != 1) {
                throw new BackupException("Incorrect number of APK files: " + files.length);
            }
            String dataBackupFilePrefix = BackupUtils.getDataFilePrefix(i++, getExt(tarType));
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(files[0].openInputStream()));
                 SplitOutputStream sos = new SplitOutputStream(mBackupItem.getUnencryptedBackupPath(), dataBackupFilePrefix, DEFAULT_SPLIT_SIZE);
                 BufferedOutputStream bos = new BufferedOutputStream(sos);
                 OutputStream os = TarUtils.createCompressedStream(bos, tarType)) {
                try (TarArchiveOutputStream tos = new TarArchiveOutputStream(os)) {
                    tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                    tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
                    ZipEntry zipEntry;
                    while ((zipEntry = zis.getNextEntry()) != null) {
                        mExtractionGuard.onNewEntry();
                        mExtractionGuard.assertEntrySize(zipEntry.getSize());
                        String fileName = ConvertUtils.getRelativeBackupEntryName(zipEntry.getName(), mPackageName + "/");
                        // Check before creating the temp file: a skipped entry must not
                        // leak an orphaned cache file.
                        if (fileName.isEmpty()) {
                            if (!zipEntry.isDirectory()) mExtractionGuard.drain(zis);
                            continue;
                        }
                        File tmpFile = null;
                        try {
                            if (!zipEntry.isDirectory()) {
                                // We need to use a temporary file so tar entry size is known.
                                tmpFile = FileCache.getGlobalFileCache()
                                        .createCachedFile(files[0].getExtension());
                                try (OutputStream fos = new FileOutputStream(tmpFile)) {
                                    mExtractionGuard.copyToTemporary(zis, fos);
                                }
                            }
                            TarArchiveEntry tarArchiveEntry = new TarArchiveEntry(fileName);
                            if (tmpFile != null) {
                                tarArchiveEntry.setSize(tmpFile.length());
                            }
                            tos.putArchiveEntry(tarArchiveEntry);
                            if (tmpFile != null) {
                                try (FileInputStream fis = new FileInputStream(tmpFile)) {
                                    IoUtils.copy(fis, tos);
                                }
                            }
                            tos.closeArchiveEntry();
                        } finally {
                            if (tmpFile != null) {
                                long tempBytes = tmpFile.length();
                                FileCache.getGlobalFileCache().delete(tmpFile);
                                mExtractionGuard.releaseTemporaryBytes(tempBytes);
                            }
                        }
                    }
                    tos.finish();
                }
                // Encrypt backups
                Path[] newBackupFiles = mBackupItem.encrypt(sos.getFiles().toArray(new Path[0]));
                for (Path file : newBackupFiles) {
                    mChecksum.add(file.getName(), DigestUtils.getHexDigest(mDestMetadata.info.checksumAlgo, file));
                }
            } catch (IOException e) {
                throw new BackupException("Backup failed for " + dataFile, e);
            }
        }
    }
}
