// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.app.Application;
import android.os.Build;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.Migrations;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class SecurityAndOpsViewModel extends AndroidViewModel implements Ops.AdbConnectionInterface {
    public static final String TAG = SecurityAndOpsViewModel.class.getSimpleName();
    private static final long STARTUP_INIT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(45);

    private boolean mIsAuthenticating = false;
    private final MutableLiveData<Integer> mAuthenticationStatus = new MutableLiveData<>();
    private final MutableLiveData<StartupInitState> mStartupInitState = new MutableLiveData<>(StartupInitState.idle());
    private final Object mStartupInitLock = new Object();
    private final MultithreadedExecutor mExecutor = MultithreadedExecutor.getNewInstance();
    @NonNull
    private StartupInitState mStartupInitSnapshot = StartupInitState.idle();
    private long mStartupInitAttemptId;

    public SecurityAndOpsViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdown();
        super.onCleared();
    }

    public boolean isAuthenticating() {
        return mIsAuthenticating;
    }

    public void setAuthenticating(boolean authenticating) {
        mIsAuthenticating = authenticating;
    }

    public LiveData<Integer> authenticationStatus() {
        return mAuthenticationStatus;
    }

    public LiveData<StartupInitState> startupInitState() {
        return mStartupInitState;
    }

    @AnyThread
    public void setModeOfOps() {
        long attemptId = beginStartupInitAttempt();
        mExecutor.submit(() -> {
            // Migration
            long thisVersion = BuildConfig.VERSION_CODE;
            long lastVersion = AppPref.getLong(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG);
            if (lastVersion == 0) {
                // First version: set this as the last version
                AppPref.set(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG, (long) BuildConfig.VERSION_CODE);
                AppPref.set(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG, (long) BuildConfig.VERSION_CODE);
            }
            if (lastVersion < thisVersion) {
                Log.d(TAG, "Start migration");
                // App is updated
                AppPref.set(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_BOOL, true);
                Migrations.startMigration(lastVersion);
                // Migration is done: set this as the last version
                AppPref.set(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG, (long) BuildConfig.VERSION_CODE);
                Log.d(TAG, "End migration");
            }
            // Ops
            postStartupInitStage(attemptId, StartupInitState.Stage.OPS_INIT, null);
            Log.d(TAG, "Before Ops::init");
            int status = Ops.init(getApplication(), false);
            Log.d(TAG, "After Ops::init");
            postStartupInitStatus(attemptId, status, null);
        });
    }

    @AnyThread
    @RequiresApi(Build.VERSION_CODES.R)
    public void autoConnectWirelessDebugging() {
        long attemptId = getOrBeginStartupInitAttempt();
        mExecutor.submit(() -> {
            postStartupInitStage(attemptId, StartupInitState.Stage.WIRELESS_ADB_PORT_DISCOVERY, null);
            Log.d(TAG, "Before Ops::autoConnectWirelessDebugging");
            int status = Ops.autoConnectWirelessDebugging(getApplication());
            Log.d(TAG, "After Ops::autoConnectWirelessDebugging");
            postStartupInitStatus(attemptId, status, null);
        });
    }

    @Override
    @AnyThread
    public void connectAdb(int port) {
        long attemptId = getOrBeginStartupInitAttempt();
        mExecutor.submit(() -> {
            postStartupInitStage(attemptId, StartupInitState.Stage.ADB_SERVER_RESTART, null);
            Log.d(TAG, "Before Ops::connectAdb");
            int status = Ops.connectAdb(getApplication(), port, Ops.STATUS_FAILURE);
            Log.d(TAG, "After Ops::connectAdb");
            postStartupInitStatus(attemptId, status, null);
        });
    }

    @Override
    @AnyThread
    @RequiresApi(Build.VERSION_CODES.R)
    public void pairAdb() {
        long attemptId = getOrBeginStartupInitAttempt();
        mExecutor.submit(() -> {
            postStartupInitStage(attemptId, StartupInitState.Stage.ADB_PAIRING_WAIT, null);
            Log.d(TAG, "Before Ops::pairAdb");
            int status = Ops.pairAdb(getApplication());
            Log.d(TAG, "After Ops::pairAdb");
            postStartupInitStatus(attemptId, status, null);
        });
    }

    @Override
    public void onStatusReceived(int status) {
        postStartupInitStatus(getOrBeginStartupInitAttempt(), status, null);
    }

    long beginStartupInitAttempt() {
        long now = System.currentTimeMillis();
        return beginStartupInitAttempt(Ops.getMode(), now, now + STARTUP_INIT_TIMEOUT_MILLIS);
    }

    long beginStartupInitAttempt(@NonNull @Ops.Mode String configuredMode, long startedAtMillis,
                                 long deadlineAtMillis) {
        synchronized (mStartupInitLock) {
            long attemptId = ++mStartupInitAttemptId;
            publishStartupInitStateLocked(StartupInitState.startAttempt(attemptId, configuredMode,
                    startedAtMillis, deadlineAtMillis));
            return attemptId;
        }
    }

    void postStartupInitStage(long attemptId, @NonNull StartupInitState.Stage stage, @Nullable String detail) {
        synchronized (mStartupInitLock) {
            StartupInitState nextState = mStartupInitSnapshot.stage(attemptId, stage, detail);
            if (nextState != mStartupInitSnapshot) {
                publishStartupInitStateLocked(nextState);
            }
        }
    }

    void postStartupInitStatus(long attemptId, @Ops.Status int status, @Nullable String detail) {
        synchronized (mStartupInitLock) {
            if (!mStartupInitSnapshot.accepts(attemptId)) {
                return;
            }
            publishStartupInitStateLocked(mStartupInitSnapshot.statusReceived(attemptId, status, detail));
        }
        publishLiveData(mAuthenticationStatus, status);
    }

    public void timeoutStartupInitAttempt(long attemptId, @Nullable String detail) {
        timeoutStartupInitAttempt(attemptId, System.currentTimeMillis(), detail);
    }

    void timeoutStartupInitAttempt(long attemptId, long nowMillis, @Nullable String detail) {
        synchronized (mStartupInitLock) {
            StartupInitState nextState = mStartupInitSnapshot.timeout(attemptId, nowMillis, detail);
            if (nextState != mStartupInitSnapshot) {
                publishStartupInitStateLocked(nextState);
            }
        }
    }

    public void cancelStartupInitAttempt(long attemptId, @Nullable String detail) {
        synchronized (mStartupInitLock) {
            StartupInitState nextState = mStartupInitSnapshot.cancel(attemptId, detail);
            if (nextState != mStartupInitSnapshot) {
                publishStartupInitStateLocked(nextState);
            }
        }
    }

    @NonNull
    StartupInitState retryStartupInitAttempt(long newStartedAtMillis, long newDeadlineAtMillis) {
        synchronized (mStartupInitLock) {
            StartupInitState nextState = mStartupInitSnapshot.retry(++mStartupInitAttemptId,
                    newStartedAtMillis, newDeadlineAtMillis);
            publishStartupInitStateLocked(nextState);
            return nextState;
        }
    }

    @NonNull
    StartupInitState getStartupInitStateSnapshot() {
        synchronized (mStartupInitLock) {
            return mStartupInitSnapshot;
        }
    }

    private long getOrBeginStartupInitAttempt() {
        synchronized (mStartupInitLock) {
            if (mStartupInitSnapshot.getStatus() == StartupInitState.Status.RUNNING) {
                return mStartupInitSnapshot.getAttemptId();
            }
        }
        return beginStartupInitAttempt();
    }

    private void publishStartupInitStateLocked(@NonNull StartupInitState state) {
        mStartupInitSnapshot = state;
        publishLiveData(mStartupInitState, state);
    }

    private static <T> void publishLiveData(@NonNull MutableLiveData<T> liveData, @Nullable T value) {
        if (ThreadUtils.isMainThread()) {
            liveData.setValue(value);
        } else {
            liveData.postValue(value);
        }
    }
}
