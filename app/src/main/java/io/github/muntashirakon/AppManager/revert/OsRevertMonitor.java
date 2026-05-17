// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.revert;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;

import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.DeviceIdleManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;

public final class OsRevertMonitor {
    private static final String TAG = OsRevertMonitor.class.getSimpleName();
    private static final long DEFAULT_REPOLL_DELAY_MILLIS = 30_000L;
    private static final long DOZE_REPOLL_DELAY_MILLIS = 60_000L;

    private static final SingleLiveEvent<RevertEvent> sRevertEvents = new SingleLiveEvent<>();

    private OsRevertMonitor() {
    }

    @NonNull
    public static LiveData<RevertEvent> getRevertEvents() {
        return sRevertEvents;
    }

    public static void watchBatteryOptimization(@NonNull Context context,
                                                @NonNull String packageName,
                                                boolean expectedExempt) {
        Context appContext = context.getApplicationContext();
        DozeAllowlistDiagnostics.Snapshot beforeSnapshot =
                DozeAllowlistDiagnostics.snapshot(appContext, packageName);
        schedule(() -> {
            boolean currentExempt = !DeviceIdleManagerCompat.isBatteryOptimizedApp(packageName);
            if (stateMatches(expectedExempt, currentExempt)) {
                return null;
            }
            DozeAllowlistDiagnostics.Snapshot afterSnapshot =
                    DozeAllowlistDiagnostics.snapshot(appContext, packageName);
            return buildEvent(appContext,
                    appContext.getString(R.string.os_revert_operation_doze),
                    packageName,
                    batteryOptimizationLabel(appContext, expectedExempt),
                    batteryOptimizationLabel(appContext, currentExempt),
                    DozeAllowlistDiagnostics.buildHint(appContext, beforeSnapshot, afterSnapshot));
        }, DOZE_REPOLL_DELAY_MILLIS);
    }

    public static void watchFreeze(@NonNull Context context,
                                   @NonNull String packageName,
                                   int userId,
                                   boolean expectedFrozen) {
        Context appContext = context.getApplicationContext();
        schedule(() -> {
            boolean currentFrozen = FreezeUtils.isFrozen(packageName, userId);
            if (stateMatches(expectedFrozen, currentFrozen)) {
                return null;
            }
            return buildEvent(appContext,
                    appContext.getString(R.string.os_revert_operation_freeze),
                    targetWithUser(packageName, userId),
                    freezeLabel(appContext, expectedFrozen),
                    freezeLabel(appContext, currentFrozen),
                    appContext.getString(R.string.os_revert_freeze_detail));
        });
    }

    public static void watchComponent(@NonNull Context context,
                                      @NonNull ComponentName componentName,
                                      int userId,
                                      @PackageManagerCompat.EnabledState int expectedState) {
        Context appContext = context.getApplicationContext();
        schedule(() -> {
            int currentState = PackageManagerCompat.getComponentEnabledSetting(componentName, userId);
            if (componentStateMatches(expectedState, currentState)) {
                return null;
            }
            return buildEvent(appContext,
                    appContext.getString(R.string.os_revert_operation_component),
                    targetWithUser(componentName.flattenToShortString(), userId),
                    componentStateLabel(appContext, expectedState),
                    componentStateLabel(appContext, currentState),
                    appContext.getString(R.string.os_revert_component_detail));
        });
    }

    public static void watchAppOp(@NonNull Context context,
                                  @NonNull String packageName,
                                  int uid,
                                  int op,
                                  @AppOpsManagerCompat.Mode int expectedMode) {
        Context appContext = context.getApplicationContext();
        schedule(() -> {
            int currentMode = getConfiguredAppOpMode(packageName, uid, op);
            if (appOpModeMatches(expectedMode, currentMode)) {
                return null;
            }
            String opName = AppOpsManagerCompat.opToName(op);
            return buildEvent(appContext,
                    appContext.getString(R.string.os_revert_operation_appop, opName),
                    packageName,
                    appOpModeLabel(expectedMode),
                    appOpModeLabel(currentMode),
                    appContext.getString(R.string.os_revert_appop_detail));
        });
    }

    private static void schedule(@NonNull Probe probe) {
        schedule(probe, DEFAULT_REPOLL_DELAY_MILLIS);
    }

    private static void schedule(@NonNull Probe probe, long delayMillis) {
        ThreadUtils.postOnMainThreadDelayed(() -> ThreadUtils.postOnBackgroundThread(() -> {
            try {
                RevertEvent event = probe.run();
                if (event != null) {
                    Log.w(TAG, "Detected OS-reverted state: " + event.getDetailMessage());
                    ThreadUtils.postOnMainThread(() -> sRevertEvents.setValue(event));
                }
            } catch (Throwable th) {
                Log.w(TAG, "Could not verify post-operation state.", th);
            }
        }), delayMillis);
    }

    @NonNull
    private static RevertEvent buildEvent(@NonNull Context context,
                                          @NonNull String operationLabel,
                                          @NonNull String target,
                                          @NonNull String expectedState,
                                          @NonNull String currentState,
                                          @NonNull String hint) {
        return new RevertEvent(
                context.getString(R.string.os_revert_details_title),
                context.getString(R.string.os_revert_snackbar_message),
                context.getString(R.string.os_revert_details_message,
                        target, operationLabel, expectedState, currentState, hint));
    }

    @NonNull
    private static String targetWithUser(@NonNull String target, int userId) {
        return target + " (u" + userId + ")";
    }

    @NonNull
    private static String batteryOptimizationLabel(@NonNull Context context, boolean exempt) {
        return context.getString(exempt
                ? R.string.os_revert_state_doze_exempt
                : R.string.os_revert_state_doze_optimized);
    }

    @NonNull
    private static String freezeLabel(@NonNull Context context, boolean frozen) {
        return context.getString(frozen
                ? R.string.os_revert_state_frozen
                : R.string.os_revert_state_unfrozen);
    }

    @NonNull
    @VisibleForTesting
    static String componentStateLabel(@NonNull Context context, int state) {
        switch (state) {
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                return context.getString(R.string.os_revert_state_component_default);
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return context.getString(R.string.os_revert_state_component_enabled);
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                return context.getString(R.string.os_revert_state_component_disabled);
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                return context.getString(R.string.os_revert_state_component_disabled_user);
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                return context.getString(R.string.os_revert_state_component_disabled_until_used);
            default:
                return context.getString(R.string.os_revert_state_unknown, state);
        }
    }

    @NonNull
    @VisibleForTesting
    static String appOpModeLabel(int mode) {
        return AppOpsManagerCompat.modeToName(mode);
    }

    @VisibleForTesting
    static boolean stateMatches(boolean expected, boolean current) {
        return expected == current;
    }

    @VisibleForTesting
    static boolean componentStateMatches(int expectedState, int currentState) {
        return expectedState == currentState;
    }

    @VisibleForTesting
    static boolean appOpModeMatches(int expectedMode, int currentMode) {
        return expectedMode == currentMode;
    }

    @AppOpsManagerCompat.Mode
    private static int getConfiguredAppOpMode(@NonNull String packageName, int uid, int op) throws RemoteException {
        AppOpsManagerCompat appOpsManager = new AppOpsManagerCompat();
        List<AppOpsManagerCompat.OpEntry> entries =
                AppOpsManagerCompat.getConfiguredOpsForPackage(appOpsManager, packageName, uid);
        return AppOpsManagerCompat.getModeFromOpEntriesOrDefault(op, entries);
    }

    private interface Probe {
        @Nullable
        RevertEvent run() throws Exception;
    }

    public static final class RevertEvent {
        @NonNull
        private final String mTitle;
        @NonNull
        private final String mBannerMessage;
        @NonNull
        private final String mDetailMessage;

        private RevertEvent(@NonNull String title,
                            @NonNull String bannerMessage,
                            @NonNull String detailMessage) {
            mTitle = title;
            mBannerMessage = bannerMessage;
            mDetailMessage = detailMessage;
        }

        @NonNull
        public String getTitle() {
            return mTitle;
        }

        @NonNull
        public String getBannerMessage() {
            return mBannerMessage;
        }

        @NonNull
        public String getDetailMessage() {
            return mDetailMessage;
        }
    }
}
