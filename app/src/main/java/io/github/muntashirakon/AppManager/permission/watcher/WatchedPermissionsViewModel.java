// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;

public class WatchedPermissionsViewModel extends AndroidViewModel {
    private static final String TAG = "WatchedPermissionsVM";

    public static final class State {
        @NonNull
        public final List<WatchRule> rules;
        /** For icons. An app that's gone has no entry and gets the default icon. */
        @NonNull
        public final Map<String, ApplicationInfo> appInfos;
        @NonNull
        public final Map<String, String> labels;
        /** Whether root or ADB mode is up, so a mismatch can be put back and not only reported. */
        public final boolean canEnforce;

        State(@NonNull List<WatchRule> rules, @NonNull Map<String, ApplicationInfo> appInfos,
              @NonNull Map<String, String> labels, boolean canEnforce) {
            this.rules = rules;
            this.appInfos = appInfos;
            this.labels = labels;
            this.canEnforce = canEnforce;
        }
    }

    /** A one-time event: what was just removed, so it can be put back. */
    public static final class Removed {
        @NonNull
        public final WatchRule rule;

        Removed(@NonNull WatchRule rule) {
            this.rule = rule;
        }
    }

    /** A one-time event: how a Check now went. A {@code null} report means the check itself failed. */
    public static final class CheckResult {
        @Nullable
        public final PermissionWatcher.Report report;

        CheckResult(@Nullable PermissionWatcher.Report report) {
            this.report = report;
        }
    }

    private final MutableLiveData<State> mState = new MutableLiveData<>();
    private final MutableLiveData<Removed> mRemoved = new MutableLiveData<>();
    private final MutableLiveData<CheckResult> mCheckResult = new MutableLiveData<>();
    // One thread, so a write always lands before the read that follows it.
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public WatchedPermissionsViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdown();
        super.onCleared();
    }

    @NonNull
    public LiveData<State> getState() {
        return mState;
    }

    @NonNull
    public LiveData<Removed> getRemoved() {
        return mRemoved;
    }

    public void consumeRemoved() {
        mRemoved.setValue(null);
    }

    @NonNull
    public LiveData<CheckResult> getCheckResult() {
        return mCheckResult;
    }

    public void consumeCheckResult() {
        mCheckResult.setValue(null);
    }

    public void load() {
        mExecutor.execute(this::loadNow);
    }

    public void remove(@NonNull WatchRule rule) {
        mExecutor.execute(() -> {
            dao().delete(rule);
            mRemoved.postValue(new Removed(rule));
            loadNow();
        });
    }

    public void undoRemove(@NonNull WatchRule rule) {
        mExecutor.execute(() -> {
            dao().upsert(rule);
            loadNow();
        });
    }

    /** Saves the new state and checks that app right away, so the rule takes effect without waiting. */
    public void setState(@NonNull WatchRule rule, @WatchRule.State int state) {
        mExecutor.execute(() -> {
            dao().upsert(rule.withState(state));
            loadNow();
            PermissionWatchScheduler.checkSoon(getApplication(), rule.packageName, rule.userId);
        });
    }

    public void checkNow() {
        mExecutor.execute(() -> {
            try {
                PermissionWatcher.Report report = PermissionWatchWorker.runCheck(dao(),
                        new SystemWatchBackend(getApplication()), Prefs.Privacy.isPermissionWatcherEnforceEnabled(),
                        null, 0);
                PermissionWatchNotifier.notify(getApplication(), report);
                mCheckResult.postValue(new CheckResult(report));
            } catch (RuntimeException e) {
                Log.e(TAG, "Check failed.", e);
                mCheckResult.postValue(new CheckResult(null));
            }
            // An enforced rule changes nothing on this screen, but the privilege banner may be stale.
            loadNow();
        });
    }

    @NonNull
    private WatchRuleDao dao() {
        return PermissionWatchDb.getInstance(getApplication()).ruleDao();
    }

    @WorkerThread
    private void loadNow() {
        List<WatchRule> rules = dao().getAll();
        Map<String, ApplicationInfo> appInfos = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        PackageManager pm = getApplication().getPackageManager();
        for (WatchRule rule : rules) {
            if (labels.containsKey(rule.packageName)) {
                continue;
            }
            ApplicationInfo info = getApplicationInfo(pm, rule.packageName);
            String label = rule.packageName;
            if (info != null) {
                appInfos.put(rule.packageName, info);
                CharSequence loaded = info.loadLabel(pm);
                if (loaded.length() > 0) {
                    label = loaded.toString();
                }
            }
            labels.put(rule.packageName, label);
        }
        mState.postValue(new State(rules, appInfos, labels, canEnforce()));
    }

    private static boolean canEnforce() {
        try {
            return SelfPermissions.canModifyPermissions() || SelfPermissions.canModifyAppOpMode();
        } catch (RuntimeException e) {
            Log.w(TAG, "Couldn't tell whether a privileged mode is running.", e);
            return false;
        }
    }

    @SuppressLint("WrongConstant")
    @Nullable
    private static ApplicationInfo getApplicationInfo(@NonNull PackageManager pm, @NonNull String packageName) {
        try {
            return pm.getApplicationInfo(packageName, MATCH_UNINSTALLED_PACKAGES);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
