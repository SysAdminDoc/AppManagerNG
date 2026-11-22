// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;

import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.internal.zip.CentralDirectoryRecord;
import com.android.apksig.internal.zip.LocalFileRecord;
import com.android.apksig.internal.zip.ZipUtils;
import com.android.apksig.util.DataSource;
import com.android.apksig.util.DataSources;
import com.android.apksig.zip.ZipFormatException;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.chunk.xml.ResXmlElement;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.apk.installer.FileNameChecksum;
import io.github.muntashirakon.AppManager.apk.installer.InstallDependencyChecker;
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlDecoder;
import io.github.muntashirakon.AppManager.apk.splitapk.SplitApkExporter;
import io.github.muntashirakon.AppManager.backup.BackupItems;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ExportFilenameUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public final class ApkUtils {
    public static final String TAG = ApkUtils.class.getSimpleName();

    public static final String EXT_APK = ".apk";
    public static final String EXT_APKS = ".apks";

    private static final Object sLock = new Object();
    private static final String MANIFEST_FILE = "AndroidManifest.xml";
    @VisibleForTesting
    static final int MAX_APK_STREAM_ENTRIES = 50_000;
    @VisibleForTesting
    static final int MAX_MANIFEST_SIZE_BYTES = 16 * 1024 * 1024;

    /**
     * The native code ABIs of the saved APKs, or "universal" (#44).
     */
    public static final String TOKEN_ABI = "%abi%";
    /**
     * The first 8 hex characters of the SHA-256 of the saved file (#44).
     */
    public static final String TOKEN_SHA256_SHORT = "%sha256_short%";

    // An APK with code for all four runs on any device, as one with none does
    private static final List<String> COMMON_ABIS = Arrays.asList("arm64-v8a", "armeabi-v7a", "x86", "x86_64");
    // The order ABIs appear in a name: 64-bit before 32-bit, ARM before x86
    private static final List<String> ABI_ORDER = Arrays.asList("arm64-v8a", "armeabi-v7a", "armeabi", "x86_64",
            "x86");

    @WorkerThread
    @NonNull
    public static Path getSharableApkFile(@NonNull Context ctx, @NonNull PackageInfo packageInfo) throws IOException {
        synchronized (sLock) {
            ApplicationInfo info = packageInfo.applicationInfo;
            boolean bundle = isSplitApk(info) || hasObbFiles(info.packageName, UserHandleHidden.getUserId(info.uid));
            Path cacheDir = Paths.get(FileUtils.getBestExternalPathWithRoot(
                    ContextUtils.getContext().getExternalCacheDirs()));
            return saveApk(ctx, packageInfo, cacheDir, bundle);
        }
    }

    /**
     * Backup the given apk (both root and no-root). This is similar to apk sharing feature except
     * that these are saved at /sdcard/AppManager/apks
     */
    @WorkerThread
    public static void backupApk(@NonNull Context ctx, @NonNull String packageName, @UserIdInt int userId)
            throws IOException, PackageManager.NameNotFoundException, RemoteException {
        Path backupPath = BackupItems.getApkBackupDirectory();
        // Fetch package info
        PackageInfo packageInfo = PackageManagerCompat.getPackageInfo(packageName,
                MATCH_UNINSTALLED_PACKAGES | PackageManager.GET_SHARED_LIBRARY_FILES
                        | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
        boolean bundle = isSplitApk(packageInfo.applicationInfo) || hasObbFiles(packageName, userId);
        saveApk(ctx, packageInfo, backupPath, bundle);
    }

    /**
     * Saves the app's APK, or an .apks bundle of its split APKs and OBB files, in {@code dir} under
     * the name format from the settings.
     */
    @WorkerThread
    @NonNull
    private static Path saveApk(@NonNull Context context, @NonNull PackageInfo packageInfo, @NonNull Path dir,
                                boolean bundle) throws IOException {
        ApplicationInfo info = packageInfo.applicationInfo;
        String format = AppPref.getString(AppPref.PrefKey.PREF_SAVED_APK_FORMAT_STR);
        Integer minSdk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? info.minSdkVersion : null;
        String label = info.loadLabel(context.getPackageManager()).toString();
        String datetime = DateUtils.formatDateTime(context, System.currentTimeMillis());
        // Reading every APK's entries costs time, so only when the name needs it
        String abi = format.contains(TOKEN_ABI) ? abiTokenOf(info) : null;
        ApkNamer namer = sha256Short -> ExportFilenameUtils.sanitizeBaseName(formatApkFilename(format, label,
                        packageInfo.packageName, packageInfo.versionName, PackageInfoCompat.getLongVersionCode(packageInfo),
                        info.targetSdkVersion, minSdk, datetime, abi, sha256Short),
                EXT_APKS, packageInfo.packageName);
        return saveNamed(dir, format, namer, bundle ? EXT_APKS : EXT_APK, out -> {
            if (bundle) {
                SplitApkExporter.saveApks(packageInfo, out);
            } else {
                IoUtils.copy(Paths.get(info.publicSourceDir), out);
            }
        });
    }

    @WorkerThread
    @NonNull
    private static String abiTokenOf(@NonNull ApplicationInfo info) {
        List<File> apks = new ArrayList<>();
        apks.add(new File(info.sourceDir));
        if (info.splitSourceDirs != null) {
            for (String split : info.splitSourceDirs) {
                apks.add(new File(split));
            }
        }
        try {
            return abiToken(nativeAbisOf(apks));
        } catch (IOException e) {
            Log.w(TAG, "Could not read the native code of %s", e, info.packageName);
            return "unknown";
        }
    }

    interface ApkNamer {
        /**
         * @param sha256Short the checksum for {@link #TOKEN_SHA256_SHORT}, {@code null} when the
         *                    format doesn't use it
         * @return the file name without its extension
         */
        @NonNull
        String nameFor(@Nullable String sha256Short);
    }

    interface ApkWriter {
        void writeTo(@NonNull Path out) throws IOException;
    }

    /**
     * Writes a file in {@code dir} and names it. With {@link #TOKEN_SHA256_SHORT} in the format,
     * the file goes under a temporary name first, since its checksum is known only once it's
     * written, and is then hashed as stored and renamed. A file already under the final name is
     * replaced, as one under a plain name always was.
     */
    @WorkerThread
    @NonNull
    @VisibleForTesting
    static Path saveNamed(@NonNull Path dir, @NonNull String format, @NonNull ApkNamer namer, @NonNull String ext,
                          @NonNull ApkWriter writer) throws IOException {
        if (!format.contains(TOKEN_SHA256_SHORT)) {
            Path out = dir.createNewFile(namer.nameFor(null) + ext, null);
            writer.writeTo(out);
            return out;
        }
        Path pending = dir.createNewFile(".pending-" + System.nanoTime() + ext, null);
        boolean renamed = false;
        try {
            writer.writeTo(pending);
            String sha256;
            try (InputStream in = pending.openInputStream()) {
                sha256 = FileNameChecksum.sha256Hex(in);
            }
            String name = namer.nameFor(sha256.substring(0, 8)) + ext;
            Path existing = dir.findFileOrNull(name);
            if (existing != null && !existing.delete()) {
                throw new IOException("Could not replace " + name);
            }
            if (pending.renameTo(name)) {
                renamed = true;
                return dir.findFile(name);
            }
            // Some document providers can't rename
            Path out = dir.createNewFile(name, null);
            boolean copied = false;
            try {
                IoUtils.copy(pending, out);
                copied = true;
            } finally {
                if (!copied) {
                    out.delete();
                }
            }
            return out;
        } finally {
            if (!renamed) {
                pending.delete();
            }
        }
    }

    /**
     * The ABIs joined with "+" in a fixed order, or "universal" for no native code or code for every
     * common ABI.
     */
    @NonNull
    static String abiToken(@NonNull Collection<String> abis) {
        if (abis.isEmpty() || abis.containsAll(COMMON_ABIS)) {
            return "universal";
        }
        List<String> sorted = new ArrayList<>(new TreeSet<>(abis));
        Collections.sort(sorted, (a, b) -> {
            int ia = ABI_ORDER.indexOf(a);
            int ib = ABI_ORDER.indexOf(b);
            if (ia >= 0 && ib >= 0) {
                return Integer.compare(ia, ib);
            }
            if (ia >= 0 || ib >= 0) {
                // Known ABIs first
                return ia >= 0 ? -1 : 1;
            }
            return a.compareTo(b);
        });
        return TextUtils.join("+", sorted);
    }

    /**
     * The ABIs with native code in any of the APKs, base and splits alike.
     */
    @WorkerThread
    @NonNull
    static Set<String> nativeAbisOf(@NonNull List<File> apks) throws IOException {
        Set<String> abis = new TreeSet<>();
        for (File apk : apks) {
            List<String> names = new ArrayList<>();
            try (ZipFile zip = new ZipFile(apk)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    names.add(entries.nextElement().getName());
                }
            }
            abis.addAll(InstallDependencyChecker.nativeAbisOf(names));
        }
        return abis;
    }

    @NonNull
    @VisibleForTesting
    static String formatApkFilename(@NonNull String format,
                                    @NonNull String label,
                                    @NonNull String packageName,
                                    @Nullable String version,
                                    long versionCode,
                                    int targetSdk,
                                    @Nullable Integer minSdk,
                                    @NonNull String datetime,
                                    @Nullable String abi,
                                    @Nullable String sha256Short) {
        String apkName = formatApkFilename(format, label, packageName, version, versionCode, targetSdk, minSdk,
                datetime);
        if (abi != null) {
            apkName = apkName.replace(TOKEN_ABI, abi);
        }
        if (sha256Short != null) {
            apkName = apkName.replace(TOKEN_SHA256_SHORT, sha256Short);
        }
        return apkName;
    }

    @NonNull
    @VisibleForTesting
    static String formatApkFilename(@NonNull String format,
                                    @NonNull String label,
                                    @NonNull String packageName,
                                    @Nullable String version,
                                    long versionCode,
                                    int targetSdk,
                                    @Nullable Integer minSdk,
                                    @NonNull String datetime) {
        String apkName = format
                .replace("%label%", label)
                .replace("%package_name%", packageName)
                .replace("%version%", version != null ? version : "")
                .replace("%version_code%", String.valueOf(versionCode))
                .replace("%target_sdk%", String.valueOf(targetSdk))
                .replace("%datetime%", datetime);
        if (minSdk != null) {
            return apkName.replace("%min_sdk%", String.valueOf(minSdk));
        }
        return apkName;
    }

    public static boolean isSplitApk(@NonNull ApplicationInfo info) {
        return info.splitPublicSourceDirs != null && info.splitPublicSourceDirs.length > 0;
    }

    @NonNull
    public static ByteBuffer getManifestFromApk(File apkFile) throws ApkFile.ApkFileException {
        try (RandomAccessFile in = new RandomAccessFile(apkFile, "r")) {
            DataSource apk = DataSources.asDataSource(in);
            com.android.apksig.apk.ApkUtils.ZipSections apkSections;
            try {
                apkSections = com.android.apksig.apk.ApkUtils.findZipSections(apk);
            } catch (ZipFormatException e) {
                throw new ApkFile.ApkFileException("Malformed APK: not a ZIP archive", e);
            }
            List<CentralDirectoryRecord> cdRecords;
            try {
                cdRecords = ZipUtils.parseZipCentralDirectory(apk, apkSections);
            } catch (ApkFormatException e) {
                throw new ApkFile.ApkFileException(e.getMessage(), e);
            }
            try {
                return getAndroidManifestFromApk(
                        cdRecords,
                        apk.slice(0, apkSections.getZipCentralDirectoryOffset()));
            } catch (ApkFormatException e) {
                throw new ApkFile.ApkFileException(e.getMessage(), e);
            } catch (ZipFormatException e) {
                throw new ApkFile.ApkFileException("Failed to read " + MANIFEST_FILE, e);
            }
        } catch (IOException e) {
            throw new ApkFile.ApkFileException(e.getMessage(), e);
        }
    }

    @NonNull
    public static ByteBuffer getManifestFromApk(InputStream apkInputStream) throws ApkFile.ApkFileException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(apkInputStream))) {
            ZipEntry zipEntry;
            int entryCount = 0;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                boolean shouldCloseEntry = true;
                try {
                    ++entryCount;
                    if (entryCount > MAX_APK_STREAM_ENTRIES) {
                        shouldCloseEntry = false;
                        throw new IOException("APK stream has too many entries.");
                    }
                    if (zipEntry.isDirectory() || !MANIFEST_FILE.equals(zipEntry.getName())) {
                        continue;
                    }
                    shouldCloseEntry = false;
                    return readManifestEntry(zipInputStream, zipEntry);
                } finally {
                    if (shouldCloseEntry) {
                        zipInputStream.closeEntry();
                    }
                }
            }
        } catch (IOException e) {
            throw new ApkFile.ApkFileException("Could not fetch AndroidManifest.xml from APK stream", e);
        }
        throw new ApkFile.ApkFileException("No AndroidManifest.xml found in APK stream.");
    }

    @NonNull
    private static ByteBuffer readManifestEntry(@NonNull ZipInputStream zipInputStream,
                                                @NonNull ZipEntry zipEntry) throws IOException {
        long declaredSize = zipEntry.getSize();
        if (declaredSize > MAX_MANIFEST_SIZE_BYTES) {
            throw new IOException(MANIFEST_FILE + " is too large.");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] buf = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
        long totalBytes = 0;
        int n;
        while (-1 != (n = zipInputStream.read(buf))) {
            totalBytes += n;
            if (totalBytes > MAX_MANIFEST_SIZE_BYTES) {
                throw new IOException(MANIFEST_FILE + " is too large.");
            }
            buffer.write(buf, 0, n);
        }
        return ByteBuffer.wrap(buffer.toByteArray());
    }

    @NonNull
    public static HashMap<String, String> getManifestAttributes(@NonNull ByteBuffer manifestBytes)
            throws ApkFile.ApkFileException {
        try {
            HashMap<String, String> manifestAttrs = new HashMap<>();
            ResXmlDocument xmlBlock;
            try {
                // Bounds-checked first, so a hostile manifest can't exhaust memory or the stack
                xmlBlock = AndroidBinXmlDecoder.readDocument(AndroidBinXmlDecoder.toByteArray(manifestBytes));
            } catch (IOException e) {
                throw new ApkFile.ApkFileException(e);
            }
            xmlBlock.setPackageBlock(AndroidBinXmlDecoder.getFrameworkPackageBlock());
            ResXmlElement resManifestElement = xmlBlock.getDocumentElement();
            // manifest — getDocumentElement() can return null for a manifest with no root element;
            // guard it instead of relying on the broad RuntimeException catch to mask the NPE.
            if (resManifestElement == null || !"manifest".equals(resManifestElement.getName())) {
                throw new ApkFile.ApkFileException("No manifest found.");
            }
            Iterator<ResXmlAttribute> attrIt = resManifestElement.getAttributes();
            ResXmlAttribute attr;
            String attrName;
            while (attrIt.hasNext()) {
                attr = attrIt.next();
                attrName = attr.getName();
                if (TextUtils.isEmpty(attrName)) {
                    continue;
                }
                String attrValue = attr.getValueAsString();
                if (attrValue == null && ("versionCode".equals(attrName)
                        || "versionCodeMajor".equals(attrName)
                        || "android:versionCode".equals(attrName)
                        || "android:versionCodeMajor".equals(attrName))) {
                    // ARSC integer values do not have a string-pool representation. Preserve the
                    // unsigned manifest value so ApkFile can compare split version identities.
                    attrValue = String.valueOf(((long) attr.getData()) & 0xffffffffL);
                }
                manifestAttrs.put(attrName, attrValue);
            }
            // application
            ResXmlElement resApplicationElement = null;
            Iterator<ResXmlElement> resXmlElementIt = resManifestElement.getElements("application");
            if (resXmlElementIt.hasNext()) {
                resApplicationElement = resXmlElementIt.next();
            }
            if (resXmlElementIt.hasNext()) {
                throw new ApkFile.ApkFileException("\"manifest\" has duplicate \"application\" tags.");
            }
            if (resApplicationElement == null) {
                Log.w(TAG, "No application tag found while parsing APK.");
                return manifestAttrs;
            }
            attrIt = resApplicationElement.getAttributes();
            while (attrIt.hasNext()) {
                attr = attrIt.next();
                attrName = attr.getName();
                if (TextUtils.isEmpty(attrName)) {
                    continue;
                }
                if (manifestAttrs.containsKey(attrName)) {
                    Log.w(TAG, "Ignoring invalid attribute in the application tag: " + attrName);
                    continue;
                }
                manifestAttrs.put(attrName, attr.getValueAsString());
            }
            return manifestAttrs;
        } catch (ApkFile.ApkFileException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ApkFile.ApkFileException("Malformed AndroidManifest.xml.", e);
        }
    }

    public static boolean hasObbFiles(@NonNull String packageName, @UserIdInt int userId) {
        try {
            return getObbDir(packageName, userId).listFiles().length > 0;
        } catch (FileNotFoundException e) {
            Log.w(TAG, e);
            return false;
        }
    }

    @NonNull
    public static Path getObbDir(@NonNull String packageName, @UserIdInt int userId) throws FileNotFoundException {
        // Get writable OBB directory
        Path obbDir = getWritableExternalDirectory(userId)
                .findFile("Android")
                .findFile("obb")
                .findFile(packageName);
        return Paths.get(obbDir.getUri());
    }

    @NonNull
    public static Path getOrCreateObbDir(@NonNull String packageName, @UserIdInt int userId) throws IOException {
        // Get writable OBB directory
        Path obbDir = getWritableExternalDirectory(userId)
                .findOrCreateDirectory("Android")
                .findOrCreateDirectory("obb")
                .findOrCreateDirectory(packageName);
        return Paths.get(obbDir.getUri());
    }

    @NonNull
    public static Path getWritableExternalDirectory(@UserIdInt int userId) throws FileNotFoundException {
        // Get the first writable external storage directory
        OsEnvironment.UserEnvironment userEnvironment = OsEnvironment.getUserEnvironment(userId);
        Path[] extDirs = userEnvironment.getExternalDirs();
        Path writableExtDir = null;
        for (Path extDir : extDirs) {
            if (extDir.canWrite() || Objects.requireNonNull(extDir.getFilePath()).startsWith("/storage/emulated")) {
                writableExtDir = extDir;
                break;
            }
        }
        if (writableExtDir == null) {
            throw new FileNotFoundException("Couldn't find any writable Obb dir");
        }
        return writableExtDir;
    }

    public static int getDensityFromName(@Nullable String densityName) {
        Integer density = StaticDataset.DENSITY_NAME_TO_DENSITY.get(densityName);
        if (density == null) {
            throw new IllegalArgumentException("Unknown density " + densityName);
        }
        return density;
    }

    @NonNull
    private static ByteBuffer getAndroidManifestFromApk(
            @NonNull List<CentralDirectoryRecord> cdRecords, @NonNull DataSource lhfSection)
            throws IOException, ApkFormatException, ZipFormatException {
        CentralDirectoryRecord androidManifestCdRecord = findCdRecord(cdRecords, MANIFEST_FILE);
        if (androidManifestCdRecord == null) {
            throw new ApkFormatException("Missing " + MANIFEST_FILE);
        }
        return ByteBuffer.wrap(LocalFileRecord.getUncompressedData(
                lhfSection, androidManifestCdRecord, lhfSection.size()));
    }

    @Nullable
    private static CentralDirectoryRecord findCdRecord(
            @NonNull List<CentralDirectoryRecord> cdRecords, @NonNull String name) {
        for (CentralDirectoryRecord cdRecord : cdRecords) {
            if (name.equals(cdRecord.getName())) {
                return cdRecord;
            }
        }
        return null;
    }
}
