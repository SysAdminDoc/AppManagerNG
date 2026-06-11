// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.fm.ContentType2;
import io.github.muntashirakon.AppManager.scanner.vt.VirusTotal;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReport;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.algo.AhoCorasick;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.fs.DexFileSystem;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

public class ScannerViewModel extends AndroidViewModel implements VirusTotal.FullScanResponseInterface {
    private static final Pattern SIG_TO_IGNORE = Pattern.compile("^(android(|x)|com\\.android|com\\.google\\.android|java(|x)|j\\$\\.(util|time)|\\w\\d?(\\.\\w\\d?)+)\\..*$");

    private File mApkFile;
    private boolean mIsSummaryLoaded = false;
    private Uri mApkUri;
    private int mDexVfsId;
    @Nullable
    private final VirusTotal mVt;
    @Nullable
    private String mPackageName;

    private List<String> mAllClasses;
    private List<String> mTrackerClasses;
    private Collection<String> mNativeLibraries;

    private CountDownLatch mWaitForFile;
    private final FileCache mFileCache = new FileCache();
    private final MultithreadedExecutor mExecutor = MultithreadedExecutor.getNewInstance();
    private final MutableLiveData<Pair<String, String>[]> mApkChecksumsLiveData = new MutableLiveData<>();
    private final MutableLiveData<ApkVerifier.Result> mApkVerifierResultLiveData = new MutableLiveData<>();
    private final MutableLiveData<PackageInfo> mPackageInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<String>> mAllClassesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<SignatureInfo>> mTrackerClassesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<SignatureInfo>> mLibraryClassesLiveData = new MutableLiveData<>();
    private final MutableLiveData<ArrayList<String>> mMissingClassesLiveData = new MutableLiveData<>();
    // Null = Uploading, NonNull = Queued
    private final MutableLiveData<String> mVtFileUploadLiveData = new MutableLiveData<>();
    // Null = Failed, NonNull = Result generated
    private final MutableLiveData<VtFileReport> mVtFileReportLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> mPithusReportLiveData = new MutableLiveData<>();

    public ScannerViewModel(@NonNull Application application) {
        super(application);
        mVt = VirusTotal.getInstance();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mExecutor.shutdownNow();
        IoUtils.closeQuietly(mFileCache);
        try {
            VirtualFileSystem.unmount(mDexVfsId);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @AnyThread
    public void loadSummary() {
        if (mIsSummaryLoaded) return;
        mIsSummaryLoaded = true;
        mWaitForFile = new CountDownLatch(1);
        // Cache files
        mExecutor.submit(() -> {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            try {
                cacheFileIfRequired();
            } finally {
                mWaitForFile.countDown();
            }
        });
        // Generate APK checksums
        mExecutor.submit(this::generateApkChecksumsAndFetchScanReports);
        // Verify APK
        mExecutor.submit(this::loadApkVerifierResult);
        // Load package info
        mExecutor.submit(this::loadPackageInfo);
        // Load all classes
        mExecutor.submit(() -> {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            loadAllClasses();
        });
    }

    @NonNull
    public LiveData<Pair<String, String>[]> apkChecksumsLiveData() {
        return mApkChecksumsLiveData;
    }

    @NonNull
    public LiveData<ApkVerifier.Result> apkVerifierResultLiveData() {
        return mApkVerifierResultLiveData;
    }

    @NonNull
    public LiveData<PackageInfo> packageInfoLiveData() {
        return mPackageInfoLiveData;
    }

    @NonNull
    public LiveData<List<String>> allClassesLiveData() {
        return mAllClassesLiveData;
    }

    public LiveData<List<SignatureInfo>> trackerClassesLiveData() {
        return mTrackerClassesLiveData;
    }

    public LiveData<List<SignatureInfo>> libraryClassesLiveData() {
        return mLibraryClassesLiveData;
    }

    public LiveData<ArrayList<String>> missingClassesLiveData() {
        return mMissingClassesLiveData;
    }

    public LiveData<VtFileReport> vtFileReportLiveData() {
        return mVtFileReportLiveData;
    }

    public LiveData<String> vtFileUploadLiveData() {
        return mVtFileUploadLiveData;
    }

    public LiveData<String> getPithusReportLiveData() {
        return mPithusReportLiveData;
    }

    public List<String> getTrackerClasses() {
        return mTrackerClasses;
    }

    public void setTrackerClasses(List<String> trackerClasses) {
        mTrackerClasses = trackerClasses;
    }

    @Nullable
    public File getApkFile() {
        return mApkFile;
    }

    @Nullable
    public String getPackageName() {
        return mPackageName;
    }

    public void setApkFile(@Nullable File apkFile) {
        mApkFile = apkFile;
    }

    public Uri getApkUri() {
        return mApkUri;
    }

    public void setApkUri(@NonNull Uri apkUri) {
        mApkUri = apkUri;
    }

    public List<String> getAllClasses() {
        return mAllClasses;
    }

    public Collection<String> getNativeLibraries() {
        return mNativeLibraries;
    }

    @NonNull
    public String getDefaultReportFileName() {
        String source = mPackageName;
        if (source == null && mApkFile != null) {
            source = mApkFile.getName();
        }
        if (source == null && mApkUri != null) {
            source = mApkUri.getLastPathSegment();
        }
        String safeSource = sanitizeReportFilePart(source);
        if (safeSource.isEmpty()) {
            safeSource = "apk";
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return getApplication().getString(R.string.scanner_export_filename, safeSource + "-" + timestamp);
    }

    @NonNull
    public String buildScanReportJson() throws JSONException {
        long now = System.currentTimeMillis();
        JSONObject report = new JSONObject();
        report.put("schema_version", 1);
        report.put("generated_at", now);
        report.put("generated_at_label", DateUtils.formatLongDateTime(getApplication(), now));
        report.put("app_manager_version_name", BuildConfig.VERSION_NAME);
        report.put("app_manager_version_code", BuildConfig.VERSION_CODE);
        report.put("device", buildDeviceJson());
        report.put("apk", buildApkJson());
        report.put("checksums", checksumsToJson(mApkChecksumsLiveData.getValue()));
        report.put("tracker_matches", signatureMatchesToJson(mTrackerClassesLiveData.getValue()));
        report.put("tracker_classes", stringCollectionToJson(mTrackerClasses));
        report.put("library_matches", signatureMatchesToJson(mLibraryClassesLiveData.getValue()));
        report.put("native_libraries", stringCollectionToJson(mNativeLibraries));
        report.put("missing_signatures", stringCollectionToJson(mMissingClassesLiveData.getValue()));
        report.put("virus_total", buildVirusTotalJson());
        report.put("pithus", buildPithusJson());
        return report.toString(2);
    }

    @VisibleForTesting
    @NonNull
    static String sanitizeReportFilePart(@Nullable String value) {
        if (value == null) return "";
        return value.trim().replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("_+", "_");
    }

    @VisibleForTesting
    @NonNull
    static JSONArray signatureMatchesToJson(@Nullable List<SignatureInfo> signatures) throws JSONException {
        JSONArray items = new JSONArray();
        if (signatures == null) {
            return items;
        }
        for (SignatureInfo signature : signatures) {
            JSONObject item = new JSONObject();
            item.put("label", signature.label);
            item.put("signature", signature.signature);
            item.put("type", signature.type);
            item.put("match_count", signature.getCount());
            item.put("classes", stringCollectionToJson(signature.classes));
            items.put(item);
        }
        return items;
    }

    @NonNull
    private JSONObject buildDeviceJson() throws JSONException {
        JSONObject device = new JSONObject();
        device.put("manufacturer", Build.MANUFACTURER);
        device.put("model", Build.MODEL);
        device.put("sdk_int", Build.VERSION.SDK_INT);
        device.put("release", Build.VERSION.RELEASE);
        return device;
    }

    @NonNull
    private JSONObject buildApkJson() throws JSONException {
        JSONObject apk = new JSONObject();
        putNullable(apk, "package_name", mPackageName);
        putNullable(apk, "uri", mApkUri != null ? mApkUri.toString() : null);
        putNullable(apk, "path", mApkFile != null ? mApkFile.getAbsolutePath() : null);
        apk.put("all_class_count", mAllClasses != null ? mAllClasses.size() : JSONObject.NULL);
        apk.put("tracker_class_count", mTrackerClasses != null ? mTrackerClasses.size() : JSONObject.NULL);
        apk.put("native_library_count", mNativeLibraries != null ? mNativeLibraries.size() : JSONObject.NULL);
        return apk;
    }

    @NonNull
    private JSONObject buildVirusTotalJson() throws JSONException {
        JSONObject vt = new JSONObject();
        VtFileReport report = mVtFileReportLiveData.getValue();
        vt.put("enabled", mVt != null);
        vt.put("available", report != null);
        if (report != null) {
            vt.put("permalink", report.permalink);
            vt.put("scan_date", report.scanDate);
            vt.put("positives", report.getPositives());
            vt.put("total", report.getTotal());
        }
        String uploadPermalink = mVtFileUploadLiveData.getValue();
        putNullable(vt, "queued_upload_permalink", uploadPermalink);
        return vt;
    }

    @NonNull
    private JSONObject buildPithusJson() throws JSONException {
        JSONObject pithus = new JSONObject();
        putNullable(pithus, "report_url", mPithusReportLiveData.getValue());
        pithus.put("available", mPithusReportLiveData.getValue() != null);
        return pithus;
    }

    @NonNull
    private static JSONArray checksumsToJson(@Nullable Pair<String, String>[] checksums) throws JSONException {
        JSONArray items = new JSONArray();
        if (checksums == null) {
            return items;
        }
        for (Pair<String, String> checksum : checksums) {
            JSONObject item = new JSONObject();
            item.put("algorithm", checksum.first);
            item.put("value", checksum.second);
            items.put(item);
        }
        return items;
    }

    @NonNull
    private static JSONArray stringCollectionToJson(@Nullable Collection<String> values) {
        JSONArray items = new JSONArray();
        if (values == null) {
            return items;
        }
        for (String value : values) {
            items.put(value);
        }
        return items;
    }

    private static void putNullable(@NonNull JSONObject object, @NonNull String key,
                                    @Nullable String value) throws JSONException {
        object.put(key, value != null ? value : JSONObject.NULL);
    }

    public Uri getUriFromClassName(String className) throws FileNotFoundException {
        Path fsRoot = VirtualFileSystem.getFsRoot(mDexVfsId);
        if (fsRoot == null) {
            throw new FileNotFoundException("FS Root not found.");
        }
        return fsRoot.findFile(className.replace('.', '/') + ".smali").getUri();
    }

    @WorkerThread
    private void cacheFileIfRequired() {
        // Test if this path is readable
        if (mApkFile == null || !FileUtils.canReadUnprivileged(mApkFile)) {
            // Not readable, cache the file
            try {
                mApkFile = mFileCache.getCachedFile(Paths.get(mApkUri));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @WorkerThread
    private void generateApkChecksumsAndFetchScanReports() {
        waitForFile();
        Path file = Paths.getUnprivileged(mApkFile);
        String pithusReportUrl = null;
        Pair<String, String>[] digests = ExUtils.exceptionAsNull(() -> DigestUtils.getDigests(file));
        mApkChecksumsLiveData.postValue(digests);
        if (digests != null && FeatureController.isInternetEnabled()) {
            String sha256 = digests[2].second;
            pithusReportUrl = ExUtils.exceptionAsNull(() -> Pithus.resolveReport(sha256));
        }
        mPithusReportLiveData.postValue(pithusReportUrl);
        if (mVt != null && digests != null && FeatureController.isVirusTotalEnabled()) {
            String md5 = digests[0].second;
            try {
                mVt.fetchFileReportOrScan(file, md5, this);
            } catch (IOException e) {
                e.printStackTrace();
                mVtFileReportLiveData.postValue(null);
            }
        } else mVtFileReportLiveData.postValue(null);
    }

    private void loadApkVerifierResult() {
        waitForFile();
        try {
            ApkVerifier.Builder builder = new ApkVerifier.Builder(mApkFile)
                    .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT);
            ApkVerifier apkVerifier = builder.build();
            ApkVerifier.Result apkVerifierResult = apkVerifier.verify();
            mApkVerifierResultLiveData.postValue(apkVerifierResult);
        } catch (IOException | ApkFormatException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @WorkerThread
    private void loadPackageInfo() {
        waitForFile();
        PackageManager pm = getApplication().getPackageManager();
        PackageInfo packageInfo = pm.getPackageArchiveInfo(mApkFile.getAbsolutePath(), 0);
        if (packageInfo != null) {
            mPackageName = packageInfo.packageName;
        }
        mPackageInfoLiveData.postValue(packageInfo);
    }

    @WorkerThread
    private void loadAllClasses() {
        waitForFile();
        try {
            NativeLibraries nativeLibraries = new NativeLibraries(mApkFile);
            mNativeLibraries = nativeLibraries.getUniqueLibs();
        } catch (Throwable e) {
            mNativeLibraries = Collections.emptyList();
        }
        try {
            VirtualFileSystem.MountOptions options = new VirtualFileSystem.MountOptions.Builder()
                    .setDexApiLevel(Build.VERSION.SDK_INT)
                    .build();
            mDexVfsId = VirtualFileSystem.mount(Uri.fromFile(mApkFile), Paths.getUnprivileged(mApkFile),
                    ContentType2.DEX.getMimeType(), options);
            DexFileSystem dfs = (DexFileSystem) Objects.requireNonNull(VirtualFileSystem.getFileSystem(mDexVfsId));
            mAllClasses = dfs.getDexClasses().getBaseClassNames();
            Collections.sort(mAllClasses);
        } catch (Throwable e) {
            e.printStackTrace();
            mAllClasses = Collections.emptyList();
        }
        mAllClassesLiveData.postValue(mAllClasses);
        // Load tracker and library info
        loadTrackers();
        loadLibraries();
    }

    @WorkerThread
    private void loadTrackers() {
        if (mAllClasses == null) return;
        List<SignatureInfo> trackerInfoList = new ArrayList<>();
        String[] trackerNames = StaticDataset.getTrackerNames();
        String[] trackerSignatures = StaticDataset.getTrackerCodeSignatures();
        AtomicIntegerArray signatureCount = new AtomicIntegerArray(trackerSignatures.length);
        mTrackerClasses = new ArrayList<>();
        AhoCorasick aho = StaticDataset.getSearchableTrackerSignatures();
        {
            // Iterate over all classes
            ConcurrentLinkedQueue<String> matchedClasses = new ConcurrentLinkedQueue<>();
            mAllClasses.parallelStream()
                    .filter(className -> className.length() > 8 && className.contains("."))
                    .forEach(className -> {
                        int[] matches = aho.search(className);
                        if (matches.length > 0) {
                            matchedClasses.add(className);
                            for (int idx : matches) {
                                signatureCount.incrementAndGet(idx);
                            }
                        }
                    });
            mTrackerClasses.addAll(matchedClasses);
        }
        // Iterate over signatures again but this time list only the found ones.
        for (int i = 0; i < trackerSignatures.length; i++) {
            if (signatureCount.get(i) == 0) continue;
            SignatureInfo signatureInfo = new SignatureInfo(trackerSignatures[i], trackerNames[i]);
            signatureInfo.setCount(signatureCount.get(i));
            trackerInfoList.add(signatureInfo);
        }
        mTrackerClassesLiveData.postValue(trackerInfoList);
    }

    public void loadLibraries() {
        if (mAllClasses == null) return;
        List<SignatureInfo> libraryInfoList = new ArrayList<>();
        ArrayList<String> missingLibs = new ArrayList<>();
        String[] libNames = getApplication().getResources().getStringArray(R.array.lib_names);
        String[] libSignatures = getApplication().getResources().getStringArray(R.array.lib_signatures);
        String[] libTypes = getApplication().getResources().getStringArray(R.array.lib_types);
        // The following array is directly mapped to the arrays above
        AtomicIntegerArray signatureCount = new AtomicIntegerArray(libSignatures.length);
        try (AhoCorasick aho = new AhoCorasick(libSignatures)) {
            // Iterate over all classes
            ConcurrentLinkedQueue<String> missingClasses = new ConcurrentLinkedQueue<>();
            mAllClasses.parallelStream()
                    .filter(className -> className.length() > 8 && className.contains("."))
                    .forEach(className -> {
                        int[] matches = aho.search(className);
                        if (matches.length > 0) {
                            for (int idx : matches) {
                                signatureCount.incrementAndGet(idx);
                            }
                        } else if ((mPackageName != null
                                && !className.startsWith(mPackageName))
                                && !SIG_TO_IGNORE.matcher(className).matches()) {
                            missingClasses.add(className);
                        }
                    });
            missingLibs.addAll(missingClasses);
        }
        // Iterate over signatures again but this time list only the found ones.
        for (int i = 0; i < libSignatures.length; i++) {
            if (signatureCount.get(i) == 0) continue;
            SignatureInfo signatureInfo = new SignatureInfo(libSignatures[i], libNames[i], libTypes[i]);
            signatureInfo.setCount(signatureCount.get(i));
            libraryInfoList.add(signatureInfo);
        }
        mLibraryClassesLiveData.postValue(libraryInfoList);

        if (BuildConfig.DEBUG) {
            mMissingClassesLiveData.postValue(missingLibs);
        }
    }

    @WorkerThread
    private void waitForFile() {
        try {
            mWaitForFile.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean mUploadingEnabled;
    private CountDownLatch mUploadingEnabledWatcher;

    public void enableUploading() {
        mUploadingEnabled = true;
        if (mUploadingEnabledWatcher != null) {
            mUploadingEnabledWatcher.countDown();
        }
    }

    public void disableUploading() {
        mUploadingEnabled = false;
        if (mUploadingEnabledWatcher != null) {
            mUploadingEnabledWatcher.countDown();
        }
    }

    @Override
    public boolean uploadFile() {
        mUploadingEnabled = false;
        mUploadingEnabledWatcher = new CountDownLatch(1);
        mVtFileUploadLiveData.postValue(null);
        try {
            mUploadingEnabledWatcher.await(2, TimeUnit.MINUTES);
        } catch (InterruptedException ignore) {
        }
        return mUploadingEnabled;
    }

    @Override
    public void onUploadInitiated() {
    }

    @Override
    public void onUploadCompleted(@NonNull String permalink) {
        mVtFileUploadLiveData.postValue(permalink);
    }

    @Override
    public void onReportReceived(@NonNull VtFileReport report) {
        mVtFileReportLiveData.postValue(report);
    }
}
