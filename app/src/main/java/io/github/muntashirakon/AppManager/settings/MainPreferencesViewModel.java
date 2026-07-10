// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.app.Application;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.collection.ArrayMap;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DocumentFileUtils;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.signing.Signer;
import io.github.muntashirakon.AppManager.changelog.Changelog;
import io.github.muntashirakon.AppManager.changelog.ChangelogParser;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.history.ops.OperationJournalMetadata;
import io.github.muntashirakon.AppManager.history.ops.SingleAppActionHistoryItem;
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.misc.DeviceInfo2;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentRuleResetPlan;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentRuleResetResult;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.StorageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;

public class MainPreferencesViewModel extends AndroidViewModel implements Ops.AdbConnectionInterface {
    public static final String TAG = MainPreferencesViewModel.class.getSimpleName();
    private final Object mRulesLock = new Object();
    private final MutableLiveData<List<UserInfo>> mSelectUsers = new SingleLiveEvent<>();
    private final MutableLiveData<Changelog> mChangeLog = new SingleLiveEvent<>();
    private final MutableLiveData<DeviceInfo2> mDeviceInfo = new SingleLiveEvent<>();
    private final MutableLiveData<String> mCustomCommand0 = new SingleLiveEvent<>();
    private final MutableLiveData<String> mCustomCommand1 = new SingleLiveEvent<>();
    private final MutableLiveData<Integer> mModeOfOpsStatus = new SingleLiveEvent<>();
    private final MutableLiveData<ComponentRuleResetState> mComponentRuleResetState = new MutableLiveData<>();
    private final MutableLiveData<ArrayMap<String, Uri>> mStorageVolumesLiveData = new SingleLiveEvent<>();
    private final MutableLiveData<String> mSigningKeySha256HashLiveData = new SingleLiveEvent<>();
    private final MutableLiveData<List<Pair<String, CharSequence>>> mPackageNameLabelPairLiveData = new SingleLiveEvent<>();
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(1);
    private final AtomicBoolean mRuleResetCancelled = new AtomicBoolean();
    private final Object mRuleResetLock = new Object();
    private Future<?> mRuleResetFuture;
    @NonNull
    private volatile List<ComponentRuleResetPlan> mRuleResetRetryPlans = Collections.emptyList();

    public MainPreferencesViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<UserInfo>> selectUsers() {
        return mSelectUsers;
    }

    public void loadAllUsers() {
        ThreadUtils.postOnBackgroundThread(() -> mSelectUsers.postValue(Users.getAllUsers()));
    }

    public LiveData<Changelog> getChangeLog() {
        return mChangeLog;
    }

    public void loadChangeLog() {
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                Changelog changelog = new ChangelogParser(getApplication(), R.raw.changelog).parse();
                mChangeLog.postValue(changelog);
            } catch (IOException | XmlPullParserException e) {
                Log.e(TAG, "Could not parse changelog", e);
                // Post null so the observer can surface an error instead of silently
                // doing nothing when the user taps "What's new".
                mChangeLog.postValue(null);
            }
        });
    }

    public LiveData<DeviceInfo2> getDeviceInfo() {
        return mDeviceInfo;
    }

    public void loadDeviceInfo(@NonNull DeviceInfo2 di) {
        ThreadUtils.postOnBackgroundThread(() -> {
            di.loadInfo();
            mDeviceInfo.postValue(di);
        });
    }

    public void reloadApps() {
        ThreadUtils.postOnBackgroundThread(() -> {
            PowerManager.WakeLock wakeLock = CpuUtils.getPartialWakeLock("appDbUpdater");
            try {
                wakeLock.acquire();
                AppDb appDb = new AppDb();
                appDb.deleteAllApplications();
                appDb.deleteAllBackups();
                appDb.loadInstalledOrBackedUpApplications(getApplication());
            } finally {
                CpuUtils.releaseWakeLock(wakeLock);
            }
        });
    }

    public MutableLiveData<String> getCustomCommand0() {
        return mCustomCommand0;
    }

    public MutableLiveData<String> getCustomCommand1() {
        return mCustomCommand1;
    }

    public void loadCustomCommands() {
        mExecutor.submit(() -> {
            try {
                ServerConfig.init(getApplication());
                mCustomCommand0.postValue(ServerConfig.getServerRunnerCommand(0));
                mCustomCommand1.postValue(ServerConfig.getServerRunnerCommand(1));
            } catch (Exception e) {
                Log.w(TAG, e);
                mCustomCommand0.postValue(null);
                mCustomCommand1.postValue(null);
            }
        });
    }

    public LiveData<Integer> getModeOfOpsStatus() {
        return mModeOfOpsStatus;
    }

    public void setModeOfOps() {
        mExecutor.submit(() -> {
            int status = Ops.init(getApplication(), true);
            mModeOfOpsStatus.postValue(status);
        });
    }

    public void setModeOfOps(@NonNull @Ops.Mode String mode) {
        mExecutor.submit(() -> {
            int status = Ops.init(getApplication(), true, mode);
            mModeOfOpsStatus.postValue(status);
        });
    }

    public LiveData<ComponentRuleResetState> getComponentRuleResetState() {
        return mComponentRuleResetState;
    }

    public void applyAllRules() {
        ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mRulesLock) {
                // TODO: 13/8/22 Synchronise in ComponentsBlocker instead of here
                ComponentsBlocker.applyAllRules(getApplication(), UserHandleHidden.myUserId());
            }
        });
    }

    public void removeAllRules() {
        startRuleReset(null);
    }

    public void retryFailedRuleResetTargets() {
        startRuleReset(new ArrayList<>(mRuleResetRetryPlans));
    }

    public void cancelRuleReset() {
        mRuleResetCancelled.set(true);
    }

    public void clearComponentRuleResetResult() {
        mComponentRuleResetState.setValue(null);
    }

    private void startRuleReset(@Nullable List<ComponentRuleResetPlan> requestedPlans) {
        synchronized (mRuleResetLock) {
            if (mRuleResetFuture != null && !mRuleResetFuture.isDone()) {
                return;
            }
            mRuleResetCancelled.set(false);
            mComponentRuleResetState.postValue(ComponentRuleResetState.preparing());
            mRuleResetFuture = mExecutor.submit(() -> {
                List<ComponentRuleResetPlan> plans = requestedPlans != null
                        ? requestedPlans
                        : ComponentUtils.snapshotAllRules(getApplication(), Users.getUsersIds());
                int total = 0;
                for (ComponentRuleResetPlan plan : plans) total += plan.size();
                mComponentRuleResetState.postValue(ComponentRuleResetState.running(0, total,
                        null, 0, null));
                ComponentRuleResetResult result = ComponentUtils.resetRules(plans,
                        mRuleResetCancelled::get,
                        (completed, targetCount, target) -> mComponentRuleResetState.postValue(
                                ComponentRuleResetState.running(completed, targetCount,
                                        target.getPackageName(), target.getUserId(), target.getLabel())));
                mRuleResetRetryPlans = ComponentUtils.getRetryPlans(plans, result);
                recordRuleResetHistory(plans, result);
                mComponentRuleResetState.postValue(ComponentRuleResetState.finished(result));
            });
        }
    }

    private void recordRuleResetHistory(@NonNull List<ComponentRuleResetPlan> plans,
                                        @NonNull ComponentRuleResetResult result) {
        Map<String, List<ComponentRuleResetResult.Outcome>> outcomesByTarget = new LinkedHashMap<>();
        for (ComponentRuleResetResult.Outcome outcome : result.getOutcomes()) {
            ComponentRuleResetPlan.Target target = outcome.getTarget();
            String key = target.getPackageName() + ':' + target.getUserId();
            outcomesByTarget.computeIfAbsent(key, ignored -> new ArrayList<>()).add(outcome);
        }
        for (ComponentRuleResetPlan plan : plans) {
            Map<Integer, List<ComponentRuleResetPlan.Target>> targetsByUser = new LinkedHashMap<>();
            for (ComponentRuleResetPlan.Target target : plan.getTargets()) {
                targetsByUser.computeIfAbsent(target.getUserId(), ignored -> new ArrayList<>()).add(target);
            }
            for (Map.Entry<Integer, List<ComponentRuleResetPlan.Target>> entry : targetsByUser.entrySet()) {
                int userId = entry.getKey();
                List<ComponentRuleResetResult.Outcome> outcomes = outcomesByTarget.getOrDefault(
                        plan.getPackageName() + ':' + userId, Collections.emptyList());
                int succeeded = 0;
                List<String> failures = new ArrayList<>();
                for (ComponentRuleResetResult.Outcome outcome : outcomes) {
                    if (outcome.isSuccess()) {
                        ++succeeded;
                    } else if (failures.size() < 3) {
                        failures.add(outcome.getTarget().getLabel() + ": " + outcome.getError());
                    }
                }
                int total = entry.getValue().size();
                int failed = outcomes.size() - succeeded;
                int pending = total - outcomes.size();
                String detail = "targets=" + total + "; succeeded=" + succeeded + "; failed="
                        + failed + "; pending=" + pending
                        + (failures.isEmpty() ? "" : "; retry=" + TextUtils.join(" | ", failures));
                boolean success = failed == 0 && pending == 0;
                try {
                    SingleAppActionHistoryItem item = new SingleAppActionHistoryItem(
                            SingleAppActionHistoryItem.ACTION_COMPONENT_RULE_RESET,
                            getApplication().getString(R.string.pref_remove_all_rules),
                            plan.getPackageName(), userId,
                            getApplication().getString(R.string.rules), detail);
                    OpHistoryManager.addHistoryItem(OpHistoryManager.HISTORY_TYPE_SINGLE_APP_ACTION,
                            item, success, OperationJournalMetadata.forSingleAppAction(getApplication(),
                                    item, success, OperationJournalMetadata.RISK_HIGH, false, null));
                } catch (Exception e) {
                    Log.e(TAG, "Could not record component-rule reset history.", e);
                }
            }
        }
    }

    public LiveData<ArrayMap<String, Uri>> getStorageVolumesLiveData() {
        return mStorageVolumesLiveData;
    }

    public void loadStorageVolumes() {
        ThreadUtils.postOnBackgroundThread(() -> {
            ArrayMap<String, Uri> locations = StorageUtils.getAllStorageLocations(getApplication());
            ArrayMap<String, Uri> newLocations = new ArrayMap<>(locations.size());
            PackageManager pm = getApplication().getPackageManager();
            for (int i = 0; i < locations.size(); ++i) {
                Uri uri = locations.valueAt(i);
                String authority = uri.getAuthority();
                if (authority != null) {
                    ResolveInfo resolveInfo = DocumentFileUtils.getUriSource(getApplication(), uri);
                    String readableName = resolveInfo != null
                            ? resolveInfo.loadLabel(pm).toString()
                            : locations.keyAt(i);
                    newLocations.put(readableName, locations.valueAt(i));
                } else newLocations.put(locations.keyAt(i), locations.valueAt(i));
            }
            mStorageVolumesLiveData.postValue(newLocations);
        });
    }

    public LiveData<String> getSigningKeySha256HashLiveData() {
        return mSigningKeySha256HashLiveData;
    }

    public void loadSigningKeySha256Hash() {
        mExecutor.submit(() -> {
            String hash = null;
            try {
                KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
                if (keyStoreManager.containsKey(Signer.SIGNING_KEY_ALIAS)) {
                    KeyPair keyPair = keyStoreManager.getKeyPair(Signer.SIGNING_KEY_ALIAS);
                    if (keyPair != null) {
                        Certificate certificate = keyPair.getCertificate();
                        hash = DigestUtils.getHexDigest(DigestUtils.SHA_256, certificate.getEncoded());
                        try {
                            keyPair.destroy();
                        } catch (Exception ignore) {
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, e);
            }
            mSigningKeySha256HashLiveData.postValue(hash);
        });
    }

    public LiveData<List<Pair<String, CharSequence>>> getPackageNameLabelPairLiveData() {
        return mPackageNameLabelPairLiveData;
    }

    public void loadPackageNameLabelPair() {
        mExecutor.submit(() -> {
            List<App> appList = new AppDb().getAllApplications();
            Map<String, CharSequence> packageNameLabelMap = new HashMap<>(appList.size());
            for (App app : appList) {
                packageNameLabelMap.put(app.packageName, app.packageLabel);
            }
            List<Pair<String, CharSequence>> appInfo = new ArrayList<>();
            for (String packageName : packageNameLabelMap.keySet()) {
                appInfo.add(new Pair<>(packageName, packageNameLabelMap.get(packageName)));
            }
            Collections.sort(appInfo, (o1, o2) -> o1.second.toString().compareTo(o2.second.toString()));
            mPackageNameLabelPairLiveData.postValue(appInfo);
        });
    }

    @RequiresApi(Build.VERSION_CODES.R)
    public void autoConnectWirelessDebugging() {
        mExecutor.submit(() -> {
            int status = Ops.autoConnectWirelessDebugging(getApplication());
            mModeOfOpsStatus.postValue(status);
        });
    }

    @Override
    public void connectAdb(int port) {
        mExecutor.submit(() -> {
            int status = Ops.connectAdb(getApplication(), port, Ops.STATUS_FAILURE);
            mModeOfOpsStatus.postValue(status);
        });
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.R)
    public void pairAdb() {
        mExecutor.submit(() -> {
            int status = Ops.pairAdb(getApplication());
            mModeOfOpsStatus.postValue(status);
        });
    }

    @Override
    public void onStatusReceived(int status) {
        mModeOfOpsStatus.postValue(status);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mRuleResetCancelled.set(true);
        synchronized (mRuleResetLock) {
            if (mRuleResetFuture != null) {
                mRuleResetFuture.cancel(true);
            }
        }
        mExecutor.shutdownNow();
    }
}
