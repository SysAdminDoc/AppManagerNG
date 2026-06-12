// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.Manifest;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.verify.domain.DomainVerificationUserState;
import android.os.Build;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.apk.installer.AppArchiveManager;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerService;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.AppLocaleManagerCompat;
import io.github.muntashirakon.AppManager.compat.DeveloperVerificationCompat;
import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;
import io.github.muntashirakon.AppManager.compat.DomainVerificationManagerCompat;
import io.github.muntashirakon.AppManager.compat.InstallSourceInfoCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageInfoCompat2;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.SensorServiceCompat;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.details.AppDetailsViewModel;
import io.github.muntashirakon.AppManager.magisk.MagiskDenyList;
import io.github.muntashirakon.AppManager.magisk.MagiskHide;
import io.github.muntashirakon.AppManager.magisk.MagiskProcess;
import io.github.muntashirakon.AppManager.magisk.MagiskUtils;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.misc.XposedModuleInfo;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.ssaid.SsaidSettings;
import io.github.muntashirakon.AppManager.types.PackageSizeInfo;
import io.github.muntashirakon.AppManager.uri.UriManager;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.TimeInterval;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class AppInfoViewModel extends AndroidViewModel {
    private final MutableLiveData<CharSequence> mAppLabel = new MutableLiveData<>();
    private final MutableLiveData<TagCloud> mTagCloud = new MutableLiveData<>();
    private final MutableLiveData<AppInfo> mAppInfo = new MutableLiveData<>();
    private final MutableLiveData<Pair<Integer, CharSequence>> mInstallExistingResult = new MutableLiveData<>();
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(4);
    private Future<?> mTagCloudFuture;
    private Future<?> mAppInfoFuture;
    @Nullable
    private AppDetailsViewModel mMainModel;

    public AppInfoViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        if (mTagCloudFuture != null) {
            mTagCloudFuture.cancel(true);
        }
        if (mAppInfoFuture != null) {
            mAppInfoFuture.cancel(true);
        }
        mExecutor.shutdownNow();
        super.onCleared();
    }

    public void setMainModel(@NonNull AppDetailsViewModel mainModel) {
        mMainModel = mainModel;
    }

    public LiveData<CharSequence> getAppLabel() {
        return mAppLabel;
    }

    public LiveData<TagCloud> getTagCloud() {
        return mTagCloud;
    }

    public LiveData<AppInfo> getAppInfo() {
        return mAppInfo;
    }

    public LiveData<Pair<Integer, CharSequence>> getInstallExistingResult() {
        return mInstallExistingResult;
    }

    @AnyThread
    public void loadAppLabel(@NonNull ApplicationInfo applicationInfo) {
        ThreadUtils.postOnBackgroundThread(() -> {
            CharSequence appLabel = applicationInfo.loadLabel(getApplication().getPackageManager());
            mAppLabel.postValue(appLabel);
        });
    }

    @AnyThread
    public void loadTagCloud(@NonNull PackageInfo packageInfo, boolean isExternalApk) {
        if (mTagCloudFuture != null) {
            mTagCloudFuture.cancel(true);
        }
        mTagCloudFuture = ThreadUtils.postOnBackgroundThread(() -> loadTagCloudInternal(packageInfo, isExternalApk));
    }

    /**
     * Compute the colon-separated upper-case hex SHA-256 of the package's
     * current X.509 signing certificate plus its Subject and Issuer DNs,
     * and write the trio into {@code tagCloud}. Suitable for one-line tag
     * display and cross-verification with AppVerifier or
     * {@code apksigner verify --print-certs}. All three fields stay
     * {@code null} when the package has no signers, more than one current
     * signer, or the digest can't be computed.
     */
    @WorkerThread
    private static void populateSigningCertInfo(@NonNull TagCloud tagCloud,
                                                @NonNull PackageInfo packageInfo,
                                                boolean isExternalApk) {
        SigningCertInfo certInfo = loadSigningCertInfo(packageInfo, isExternalApk);
        if (certInfo == null) return;
        tagCloud.signingCertSha256 = certInfo.sha256;
        tagCloud.signingCertSubject = certInfo.subject;
        tagCloud.signingCertIssuer = certInfo.issuer;
    }

    @WorkerThread
    private static void populateSigningCertInfo(@NonNull AppInfo appInfo,
                                                @NonNull PackageInfo packageInfo,
                                                boolean isExternalApk) {
        SigningCertInfo certInfo = loadSigningCertInfo(packageInfo, isExternalApk);
        if (certInfo == null) return;
        appInfo.signingCertSha256 = certInfo.sha256;
        appInfo.signingCertSubject = certInfo.subject;
        appInfo.signingCertIssuer = certInfo.issuer;
    }

    @WorkerThread
    @Nullable
    private static SigningCertInfo loadSigningCertInfo(@NonNull PackageInfo packageInfo, boolean isExternalApk) {
        try {
            SignerInfo signerInfo = PackageUtils.getSignerInfo(packageInfo, isExternalApk);
            if (signerInfo == null) return null;
            X509Certificate[] certs = signerInfo.getCurrentSignerCerts();
            // Multi-signer APKs are rare; surfacing only the single-signer case
            // keeps the tag chip unambiguous. Multi-signer details are reachable
            // through the icon-tap verify flow.
            if (certs == null || certs.length != 1) return null;
            X509Certificate cert = certs[0];
            String hex = DigestUtils.getHexDigest(DigestUtils.SHA_256, cert.getEncoded());
            String sha256 = colonifyHex(hex);
            if (sha256 == null) return null;
            return new SigningCertInfo(sha256,
                    cert.getSubjectX500Principal().getName(),
                    cert.getIssuerX500Principal().getName());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Format a flat hex digest as "AB:CD:..." in upper case, matching the
     * canonical fingerprint shape that AppVerifier and {@code apksigner}
     * print. Returns {@code null} on a malformed input.
     */
    @Nullable
    static String colonifyHex(@Nullable String hex) {
        if (hex == null || hex.isEmpty() || (hex.length() & 1) != 0) return null;
        StringBuilder sb = new StringBuilder(hex.length() + hex.length() / 2);
        for (int i = 0; i < hex.length(); i += 2) {
            if (i > 0) sb.append(':');
            sb.append(Character.toUpperCase(hex.charAt(i)));
            sb.append(Character.toUpperCase(hex.charAt(i + 1)));
        }
        return sb.toString();
    }

    @WorkerThread
    private void loadTagCloudInternal(@NonNull PackageInfo packageInfo, boolean isExternalApk) {
        if (mMainModel == null) return;
        String packageName = packageInfo.packageName;
        int userId = mMainModel.getUserId();
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        TagCloud tagCloud = new TagCloud();
        try {
            Map<String, RuleType> trackerComponents = ComponentUtils.getTrackerComponentsForPackage(packageInfo);
            tagCloud.trackerComponents = new ArrayList<>(trackerComponents.size());
            for (String component : trackerComponents.keySet()) {
                ComponentRule componentRule = mMainModel.getComponentRule(component);
                if (componentRule == null) {
                    componentRule = new ComponentRule(packageName, component, trackerComponents.get(component),
                            Prefs.Blocking.getDefaultBlockingMethod());
                }
                tagCloud.trackerComponents.add(componentRule);
                tagCloud.areAllTrackersBlocked &= componentRule.isBlocked();
            }
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            tagCloud.isSystemApp = ApplicationInfoCompat.isSystemApp(applicationInfo);
            tagCloud.isUpdatedSystemApp = (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            tagCloud.isArchived = AppArchiveManager.isArchived(packageInfo);
            String codePath = PackageUtils.getHiddenCodePathOrDefault(packageName, applicationInfo.publicSourceDir);
            tagCloud.isSystemlessPath = !isExternalApk && MagiskUtils.isSystemlessPath(codePath);
            if (!isExternalApk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                DomainVerificationUserState userState = DomainVerificationManagerCompat
                        .getDomainVerificationUserState(packageName, userId);
                if (userState != null) {
                    tagCloud.canOpenLinks = userState.isLinkHandlingAllowed();
                    if (!userState.getHostToStateMap().isEmpty()) {
                        tagCloud.hostsToOpen = userState.getHostToStateMap();
                        tagCloud.domainLinkConflicts = loadDomainLinkConflicts(packageName, userId,
                                userState.getHostToStateMap());
                    }
                }
            }
            // Dangerous-permission overview: walk requestedPermissions, classify each
            // as PROTECTION_DANGEROUS via PermissionInfoCompat, and tally how many of
            // those the user has granted (REQUESTED_PERMISSION_GRANTED bit on the
            // parallel requestedPermissionsFlags array). Lookup failures are skipped
            // so a single broken permission resolution doesn't drop the whole count.
            if (packageInfo.requestedPermissions != null) {
                String[] perms = packageInfo.requestedPermissions;
                int[] permFlags = packageInfo.requestedPermissionsFlags;
                int total = 0;
                int granted = 0;
                android.content.pm.PackageManager pm = getApplication().getPackageManager();
                for (int i = 0; i < perms.length; i++) {
                    if (ThreadUtils.isInterrupted()) return;
                    try {
                        android.content.pm.PermissionInfo info =
                                pm.getPermissionInfo(perms[i], 0);
                        int prot = androidx.core.content.pm.PermissionInfoCompat.getProtection(info);
                        if (prot == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS) {
                            total++;
                            boolean isGranted = permFlags != null && i < permFlags.length
                                    && (permFlags[i] & android.content.pm.PackageInfo
                                            .REQUESTED_PERMISSION_GRANTED) != 0;
                            if (isGranted) granted++;
                        }
                    } catch (Throwable ignore) {
                        // unknown / removed permission; skip
                    }
                }
                tagCloud.dangerousPermissionTotal = total;
                tagCloud.dangerousPermissionGranted = granted;
            }
            tagCloud.splitCount = mMainModel.getSplitCount();
            tagCloud.isDebuggable = (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            tagCloud.isTestOnly = ApplicationInfoCompat.isTestOnly(applicationInfo);
            tagCloud.hasCode = (applicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) != 0;
            tagCloud.isOverlay = PackageInfoCompat2.getOverlayTarget(packageInfo) != null;
            tagCloud.hasRequestedLargeHeap = (applicationInfo.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0;
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            tagCloud.isRunning = false;
            for (ActivityManager.RunningAppProcessInfo info : ActivityManagerCompat.getRunningAppProcesses()) {
                if (ArrayUtils.contains(info.pkgList, packageName)) {
                    tagCloud.isRunning = true;
                    break;
                }
            }
            tagCloud.runningServices = ActivityManagerCompat.getRunningServices(packageName, userId);
            tagCloud.isForceStopped = ApplicationInfoCompat.isStopped(applicationInfo);
            tagCloud.isAppEnabled = applicationInfo.enabled;
            tagCloud.isAppSuspended = ApplicationInfoCompat.isSuspended(applicationInfo);
            tagCloud.isAppHidden = ApplicationInfoCompat.isHidden(applicationInfo);
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            tagCloud.magiskHiddenProcesses = MagiskHide.getProcesses(packageInfo);
            boolean magiskHideEnabled = false;
            for (MagiskProcess magiskProcess : tagCloud.magiskHiddenProcesses) {
                magiskHideEnabled |= magiskProcess.isEnabled();
                for (ActivityManager.RunningServiceInfo info : tagCloud.runningServices) {
                    if (info.process.startsWith(magiskProcess.name)) {
                        magiskProcess.setRunning(true);
                    }
                }
            }
            tagCloud.isMagiskHideEnabled = !isExternalApk && magiskHideEnabled;
            tagCloud.magiskDeniedProcesses = MagiskDenyList.getProcesses(packageInfo);
            boolean magiskDenyListEnabled = false;
            for (MagiskProcess magiskProcess : tagCloud.magiskDeniedProcesses) {
                magiskDenyListEnabled |= magiskProcess.isEnabled();
                for (ActivityManager.RunningServiceInfo info : tagCloud.runningServices) {
                    if (info.process.startsWith(magiskProcess.name)) {
                        magiskProcess.setRunning(true);
                    }
                }
            }
            tagCloud.isMagiskDenyListEnabled = !isExternalApk && magiskDenyListEnabled;
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            List<DebloatObject> debloatObjects = StaticDataset.getDebloatObjects();
            for (DebloatObject debloatObject : debloatObjects) {
                if (packageName.equals(debloatObject.packageName)) {
                    tagCloud.bloatwareRemovalType = debloatObject.getRemoval();
                    break;
                }
            }
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            if (!isExternalApk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_SENSORS)) {
                tagCloud.sensorsEnabled = SensorServiceCompat.isSensorEnabled(packageName, userId);
            } else tagCloud.sensorsEnabled = true;
            if (!TextUtils.isEmpty(applicationInfo.publicSourceDir)) {
                try (ZipFile zipFile = new ZipFile(applicationInfo.publicSourceDir)) {
                    Boolean isXposedModule = XposedModuleInfo.isXposedModule(applicationInfo, zipFile);
                    if (!Boolean.FALSE.equals(isXposedModule)) {
                        tagCloud.xposedModuleInfo = new XposedModuleInfo(applicationInfo, isXposedModule == null ? null : zipFile);
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
            tagCloud.canWriteAndExecute = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && applicationInfo.targetSdkVersion < Build.VERSION_CODES.Q;
            tagCloud.memoryTaggingInfo = MemoryTaggingInfo.from(applicationInfo);
            tagCloud.sdkSandboxInfo = SdkSandboxInfo.from(applicationInfo);
            tagCloud.healthConnectInfo = HealthConnectInfo.from(packageInfo);
            tagCloud.credentialProviderManifestInfo = CredentialProviderManifestInfo.from(applicationInfo,
                    packageInfo.services);
            tagCloud.manifestMetadataInfo = ManifestMetadataInfo.from(applicationInfo);
            tagCloud.warnsCleartextDeprecation = shouldWarnCleartextDeprecation(
                    (applicationInfo.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0,
                    ApplicationInfoCompat.getNetworkSecurityConfigRes(applicationInfo));
            tagCloud.hasKeyStoreItems = KeyStoreUtils.hasKeyStore(applicationInfo.uid);
            tagCloud.hasMasterKeyInKeyStore = KeyStoreUtils.hasMasterKey(applicationInfo.uid);
            tagCloud.usesPlayAppSigning = PackageUtils.usesPlayAppSigning(applicationInfo);
            tagCloud.developerVerificationStatus = DeveloperVerificationCompat
                    .getVerificationStatus(getApplication(), packageName);
            tagCloud.packageVisibility = PackageVisibilityInfo.from(packageInfo);
            populateSigningCertInfo(tagCloud, packageInfo, isExternalApk);
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            tagCloud.backups = BackupUtils.getBackupMetadataFromDbNoLockValidate(packageName);
            if (!isExternalApk) {
                tagCloud.isBatteryOptimized = DeviceIdleManagerCompat.isBatteryOptimizedApp(packageName);
            } else {
                tagCloud.isBatteryOptimized = true;
            }
            if (!isExternalApk && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY)) {
                tagCloud.netPolicies = NetworkPolicyManagerCompat.getUidPolicy(applicationInfo.uid);
            } else {
                tagCloud.netPolicies = 0;
            }
            if (!isExternalApk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    tagCloud.ssaid = new SsaidSettings(userId)
                            .getSsaid(packageName, applicationInfo.uid);
                    if (TextUtils.isEmpty(tagCloud.ssaid)) tagCloud.ssaid = null;
                } catch (IOException ignore) {
                }
            }
            if (!isExternalApk) {
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                List<UriManager.UriGrant> uriGrants = new UriManager().getGrantedUris(packageName);
                if (uriGrants != null) {
                    Iterator<UriManager.UriGrant> uriGrantIterator = uriGrants.listIterator();
                    UriManager.UriGrant uriGrant;
                    while (uriGrantIterator.hasNext()) {
                        uriGrant = uriGrantIterator.next();
                        if (uriGrant.targetUserId != userId) {
                            uriGrantIterator.remove();
                        }
                    }
                    tagCloud.uriGrants = uriGrants;
                }
            }
            if (ApplicationInfoCompat.isStaticSharedLibrary(applicationInfo)) {
                if (ThreadUtils.isInterrupted()) {
                    return;
                }
                List<String> staticSharedLibraryNames = new ArrayList<>();
                // Check for packages by the same packagename
                List<ApplicationInfo> appList;
                try {
                    appList = PackageManagerCompat.getInstalledApplications(PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
                    for (ApplicationInfo info : appList) {
                        if (info.packageName.equals(packageName)) {
                            staticSharedLibraryNames.add(info.processName);
                        }
                    }
                } catch (Throwable ignore) {
                    staticSharedLibraryNames.add(applicationInfo.processName);
                }
                tagCloud.staticSharedLibraryNames = staticSharedLibraryNames.toArray(new String[0]);
            }
            if (ThreadUtils.isInterrupted()) {
                return;
            }
            mTagCloud.postValue(tagCloud);
        } catch (Throwable th) {
            if (isInterruption(th)) {
                // A newer load cancelled this one (Future.cancel(true)); the result is
                // already obsolete, so drop it instead of crashing the app.
                return;
            }
            // Unknown behaviour
            ThreadUtils.postOnMainThread(() -> {
                // Throw Runtime exception in main thread to crash the app
                throw new RuntimeException(th);
            });
        }
    }

    private static boolean isInterruption(@Nullable Throwable th) {
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        for (Throwable t = th; t != null; t = t.getCause()) {
            if (t instanceof InterruptedException || t instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }

    @VisibleForTesting
    static boolean shouldWarnCleartextDeprecation(boolean usesCleartextTraffic, int networkSecurityConfigRes) {
        return usesCleartextTraffic && networkSecurityConfigRes == 0;
    }

    @NonNull
    @WorkerThread
    private Map<String, List<DomainLinkConflictDetector.Conflict>> loadDomainLinkConflicts(
            @NonNull String packageName, int userId, @NonNull Map<String, Integer> hostStates) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hostStates.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DomainLinkConflictDetector.AppDomainClaims> claims = new ArrayList<>();
        CharSequence appLabel = mAppLabel.getValue();
        claims.add(DomainLinkConflictDetector.claim(packageName,
                appLabel != null ? appLabel.toString() : packageName, userId, hostStates));
        List<PackageInfo> packageInfoList = PackageManagerCompat.getInstalledPackages(PackageManager.GET_META_DATA, userId);
        PackageManager packageManager = getApplication().getPackageManager();
        for (PackageInfo packageInfo : packageInfoList) {
            if (ThreadUtils.isInterrupted()) {
                return Collections.emptyMap();
            }
            if (packageInfo.packageName.equals(packageName)) {
                continue;
            }
            DomainVerificationUserState userState = DomainVerificationManagerCompat
                    .getDomainVerificationUserState(packageInfo.packageName, userId);
            if (userState == null || userState.getHostToStateMap().isEmpty()) {
                continue;
            }
            String label = packageInfo.applicationInfo != null
                    ? packageInfo.applicationInfo.loadLabel(packageManager).toString()
                    : packageInfo.packageName;
            claims.add(DomainLinkConflictDetector.claim(packageInfo.packageName, label, userId,
                    userState.getHostToStateMap()));
        }
        Map<String, Map<String, List<DomainLinkConflictDetector.Conflict>>> conflicts =
                DomainLinkConflictDetector.findConflictsByPackageUser(claims);
        Map<String, List<DomainLinkConflictDetector.Conflict>> packageConflicts = conflicts.get(
                DomainLinkConflictDetector.packageUserKey(packageName, userId));
        return packageConflicts != null ? packageConflicts : Collections.emptyMap();
    }

    @AnyThread
    public void loadAppInfo(@NonNull PackageInfo packageInfo, boolean isExternalApk) {
        if (mAppInfoFuture != null) {
            mAppInfoFuture.cancel(true);
        }
        mAppInfoFuture = ThreadUtils.postOnBackgroundThread(() -> loadAppInfoInternal(packageInfo, isExternalApk));
    }

    @WorkerThread
    private void loadAppInfoInternal(@NonNull PackageInfo packageInfo, boolean isExternalApk) {
        String packageName = packageInfo.packageName;
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        int userId = UserHandleHidden.getUserId(applicationInfo.uid);
        PackageManager pm = getApplication().getPackageManager();
        AppInfo appInfo = new AppInfo();
        try {
            if (!isExternalApk) {
                boolean hasSourcePath = !TextUtils.isEmpty(applicationInfo.publicSourceDir);
                // Set source dir
                if (hasSourcePath) {
                    appInfo.sourceDir = new File(applicationInfo.publicSourceDir).getParent();
                }
                // Set data dirs
                appInfo.dataDir = applicationInfo.dataDir;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo.dataDeDir = applicationInfo.deviceProtectedDataDir;
                }
                // Set directories
                appInfo.extDataDirs = new ArrayList<>();
                OsEnvironment.UserEnvironment ue = OsEnvironment.getUserEnvironment(userId);
                Path[] externalDataDirs = ue.buildExternalStorageAppDataDirs(packageName);
                for (Path externalDataDir : externalDataDirs) {
                    Path accessiblePath = Paths.getAccessiblePath(externalDataDir);
                    if (accessiblePath.exists()) {
                        appInfo.extDataDirs.add(Objects.requireNonNull(accessiblePath.getFilePath()));
                    }
                }
                // Set JNI dir
                if (Paths.exists(applicationInfo.nativeLibraryDir)) {
                    appInfo.jniDir = applicationInfo.nativeLibraryDir;
                }
                boolean hasUsageAccess = FeatureController.isUsageAccessEnabled() && SelfPermissions.checkUsageStatsPermission();
                if (hasUsageAccess) {
                    // Net statistics
                    AppUsageStatsManager.DataUsage dataUsage;
                    TimeInterval interval = UsageUtils.getLastWeek();
                    dataUsage = AppUsageStatsManager.getDataUsageForPackage(applicationInfo.uid, interval);
                    if (dataUsage.getTotal() == 0 && !ArrayUtils.contains(
                            packageInfo.requestedPermissions, Manifest.permission.INTERNET)) {
                        appInfo.dataUsage = null;
                    } else appInfo.dataUsage = dataUsage;
                    // Set sizes
                    appInfo.sizeInfo = PackageUtils.getPackageSizeInfo(getApplication(), packageName, userId,
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? applicationInfo.storageUuid : null);
                }
                // Set installer app
                InstallSourceInfoCompat installSourceInfo = ExUtils.exceptionAsNull(() ->
                        PackageManagerCompat.getInstallSourceInfo(packageName, userId));
                if (installSourceInfo != null) {
                    if (installSourceInfo.getInstallingPackageName() != null) {
                        CharSequence label = PackageUtils.getPackageLabel(pm,
                                installSourceInfo.getInstallingPackageName(), userId);
                        appInfo.installerApp = label;
                        installSourceInfo.setInstallingPackageLabel(label);
                    }
                    if (installSourceInfo.getInitiatingPackageName() != null) {
                        CharSequence label = PackageUtils.getPackageLabel(pm,
                                installSourceInfo.getInitiatingPackageName(), userId);
                        if (appInfo.installerApp == null) {
                            appInfo.installerApp = label;
                        }
                        installSourceInfo.setInitiatingPackageLabel(label);
                    }
                    if (installSourceInfo.getOriginatingPackageName() != null) {
                        installSourceInfo.setOriginatingPackageLabel(PackageUtils.getPackageLabel(pm,
                                installSourceInfo.getOriginatingPackageName(), userId));
                    }
                    appInfo.installSource = installSourceInfo;
                }
                // Set main activity
                appInfo.mainActivity = PackageManagerCompat.getLaunchIntentForPackage(packageName, userId);
                // SELinux
                appInfo.seInfo = ApplicationInfoCompat.getSeInfo(applicationInfo);
                appInfo.dataDirSelinuxContext = AppSelinuxContexts.readFileContext(applicationInfo.dataDir);
                if (hasSourcePath) {
                    appInfo.sourceFileSelinuxContext = AppSelinuxContexts.readFileContext(applicationInfo.publicSourceDir);
                }
                appInfo.processSelinuxContexts = AppSelinuxContexts.collectProcessContexts(packageName,
                        ActivityManagerCompat.getRunningAppProcesses(), AppSelinuxContexts::readProcAttrCurrent);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && AppLocaleManagerCompat.canReadApplicationLocales()) {
                    appInfo.applicationLocaleTags = ExUtils.exceptionAsNull(() ->
                            AppLocaleManagerCompat.getApplicationLocaleTags(packageName, userId));
                }
                // Primary ABI
                appInfo.primaryCpuAbi = ApplicationInfoCompat.getPrimaryCpuAbi(applicationInfo);
                // zygotePreloadName
                appInfo.zygotePreloadName = ApplicationInfoCompat.getZygotePreloadName(applicationInfo);
                // hiddenApiEnforcementPolicy
                appInfo.hiddenApiEnforcementPolicy = ApplicationInfoCompat.getHiddenApiEnforcementPolicy(applicationInfo);
            }
            appInfo.sdkSandboxInfo = SdkSandboxInfo.from(applicationInfo);
            populateSigningCertInfo(appInfo, packageInfo, isExternalApk);
            mAppInfo.postValue(appInfo);
        } catch (Throwable th) {
            if (isInterruption(th)) {
                // A newer load cancelled this one; drop the obsolete result silently.
                return;
            }
            // Unknown behaviour
            ThreadUtils.postOnMainThread(() -> {
                // Throw Runtime exception in main thread to crash the app
                throw new RuntimeException(th);
            });
        }
    }

    public void installExisting(@NonNull String packageName, @UserIdInt int userId) {
        mExecutor.submit(() -> {
            PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
            installer.setOnInstallListener(new PackageInstallerCompat.OnInstallListener() {
                @Override
                public void onStartInstall(int sessionId, String packageName) {
                }

                @Override
                public void onFinishedInstall(int sessionId, String packageName, int result,
                                              @Nullable String blockingPackage, @Nullable String statusMessage) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(PackageInstallerService.getStringFromStatus(getApplication(), result,
                            getAppLabel().getValue(), blockingPackage));
                    if (statusMessage != null) {
                        sb.append("\n\n").append(statusMessage);
                    }
                    mInstallExistingResult.postValue(new Pair<>(result, sb));
                }
            });
            installer.installExisting(packageName, userId);
        });
    }

    private static final class SigningCertInfo {
        @NonNull
        final String sha256;
        @NonNull
        final String subject;
        @NonNull
        final String issuer;

        private SigningCertInfo(@NonNull String sha256, @NonNull String subject, @NonNull String issuer) {
            this.sha256 = sha256;
            this.subject = subject;
            this.issuer = issuer;
        }
    }

    public static class TagCloud {
        public List<ComponentRule> trackerComponents;
        public boolean areAllTrackersBlocked = true;
        /** Total number of {@code PROTECTION_DANGEROUS} permissions the app declares. */
        public int dangerousPermissionTotal;
        /** Subset of {@link #dangerousPermissionTotal} that are currently granted. */
        public int dangerousPermissionGranted;
        public boolean isSystemApp;
        public boolean isSystemlessPath;
        public boolean isUpdatedSystemApp;
        public boolean isArchived;
        public boolean canOpenLinks;
        /**
         * Hosts that can be opened by the app (Android 12+). State is one of {@link DomainVerificationUserState#DOMAIN_STATE_NONE},
         * {@link DomainVerificationUserState#DOMAIN_STATE_SELECTED}, {@link DomainVerificationUserState#DOMAIN_STATE_VERIFIED}.
         */
        public Map<String, Integer> hostsToOpen;
        @NonNull
        public Map<String, List<DomainLinkConflictDetector.Conflict>> domainLinkConflicts = Collections.emptyMap();
        public int splitCount;
        public boolean isDebuggable;
        public boolean isTestOnly;
        public boolean hasCode;
        public boolean isOverlay;
        public boolean hasRequestedLargeHeap;
        public boolean isRunning;
        public List<ActivityManager.RunningServiceInfo> runningServices;
        public List<MagiskProcess> magiskHiddenProcesses;
        public List<MagiskProcess> magiskDeniedProcesses;
        public boolean isForceStopped;
        public boolean isAppEnabled;
        public boolean isAppHidden;
        public boolean isAppSuspended;
        public boolean isMagiskHideEnabled;
        public boolean isMagiskDenyListEnabled;
        @DebloatObject.Removal
        public int bloatwareRemovalType;
        public boolean sensorsEnabled;
        @Nullable
        public XposedModuleInfo xposedModuleInfo;
        public boolean canWriteAndExecute;
        @NonNull
        public MemoryTaggingInfo memoryTaggingInfo = MemoryTaggingInfo.unsupported(Build.VERSION.SDK_INT);
        @NonNull
        public SdkSandboxInfo sdkSandboxInfo = SdkSandboxInfo.unsupported(Build.VERSION.SDK_INT);
        @NonNull
        public HealthConnectInfo healthConnectInfo = HealthConnectInfo.unsupported(Build.VERSION.SDK_INT);
        @NonNull
        public CredentialProviderManifestInfo credentialProviderManifestInfo =
                CredentialProviderManifestInfo.unsupported(Build.VERSION.SDK_INT);
        @NonNull
        public ManifestMetadataInfo manifestMetadataInfo = ManifestMetadataInfo.empty();
        public boolean warnsCleartextDeprecation;
        public boolean hasKeyStoreItems;
        public boolean hasMasterKeyInKeyStore;
        public boolean usesPlayAppSigning;
        @DeveloperVerificationCompat.VerificationStatus
        public int developerVerificationStatus = DeveloperVerificationCompat.STATUS_UNAVAILABLE;
        /** NF-11 — package-visibility signal: declares QUERY_ALL_PACKAGES and/or non-empty <queries>. */
        @Nullable
        public PackageVisibilityInfo packageVisibility;
        public List<Backup> backups;
        public boolean isBatteryOptimized;
        public int netPolicies;
        @Nullable
        public String ssaid;
        @Nullable
        public List<UriManager.UriGrant> uriGrants;
        @Nullable
        public String[] staticSharedLibraryNames;
        /**
         * Colon-separated, upper-case hex SHA-256 of the current signing
         * certificate (X.509 DER). {@code null} when the package isn't signed,
         * has multiple current signers, or signer info couldn't be parsed.
         * Used by the App Info "Sign · SHA-256" tag for one-tap clipboard
         * copy and cross-verification with AppVerifier / apksigner output.
         */
        @Nullable
        public String signingCertSha256;
        /**
         * X.509 Subject DN of the current signing certificate (RFC 2253 form).
         * {@code null} when the cert wasn't computable. Surfaced alongside the
         * fingerprint in the cert dialog so users vetting an APK can see who
         * the certificate claims to be issued <em>to</em>, not just the digest.
         */
        @Nullable
        public String signingCertSubject;
        /**
         * X.509 Issuer DN of the current signing certificate (RFC 2253 form).
         * Differs from {@link #signingCertSubject} only for CA-issued certs;
         * self-signed APKs (the Android norm) have Subject == Issuer.
         */
        @Nullable
        public String signingCertIssuer;
    }

    public static class AppInfo {
        // Paths & dirs
        @Nullable
        public String sourceDir;
        @Nullable
        public String dataDir;
        @Nullable
        public String dataDeDir;
        public List<String> extDataDirs = Collections.emptyList();
        @Nullable
        public String jniDir;
        // Data usage
        @Nullable
        public AppUsageStatsManager.DataUsage dataUsage;
        @Nullable
        public PackageSizeInfo sizeInfo;
        // More info
        @Nullable
        public InstallSourceInfoCompat installSource;
        @Nullable
        public CharSequence installerApp;
        @Nullable
        public Intent mainActivity;
        @Nullable
        public String seInfo;
        @Nullable
        public String dataDirSelinuxContext;
        @Nullable
        public String sourceFileSelinuxContext;
        @NonNull
        public List<AppSelinuxContexts.ProcessContext> processSelinuxContexts = Collections.emptyList();
        @Nullable
        public String applicationLocaleTags;
        @Nullable
        public String primaryCpuAbi;
        @Nullable
        public String zygotePreloadName;
        public int hiddenApiEnforcementPolicy;
        @NonNull
        public SdkSandboxInfo sdkSandboxInfo = SdkSandboxInfo.unsupported(Build.VERSION.SDK_INT);
        @Nullable
        public String signingCertSha256;
        @Nullable
        public String signingCertSubject;
        @Nullable
        public String signingCertIssuer;
    }
}
