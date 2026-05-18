// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.Manifest;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbUtils;
import io.github.muntashirakon.AppManager.dhizuku.DhizukuBridge;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler.NotificationInfo;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import io.github.muntashirakon.AppManager.utils.MiuiUtils;

/**
 * Installer-only privilege selector. It tries the least intrusive elevated session that is already
 * available for the device, without permanently rewriting the user's configured operating mode.
 */
public final class InstallerPrivilegeCascade {
    private static final String TAG = InstallerPrivilegeCascade.class.getSimpleName();

    @VisibleForTesting
    static final String ROUTE_CURRENT = "current";
    @VisibleForTesting
    static final String ROUTE_ADB = "adb";
    @VisibleForTesting
    static final String ROUTE_SHIZUKU = "shizuku";
    @VisibleForTesting
    static final String ROUTE_ROOT = "root";
    @VisibleForTesting
    static final String ROUTE_SYSTEM_CONFIRMATION = "system_confirmation";
    @VisibleForTesting
    static final String ROUTE_DHIZUKU_INFO = "dhizuku_info";
    @VisibleForTesting
    static final String ROUTE_MIUI_NUDGE = "miui_nudge";

    private InstallerPrivilegeCascade() {
    }

    @NonNull
    public static Plan getPreviewPlan(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        DhizukuBridge.Result dhizuku = DhizukuBridge.probe(appContext);
        return buildPlan(
                SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES),
                SelfPermissions.checkSelfPermission(Manifest.permission.INTERNET) && AdbUtils.isAdbdRunning(),
                ShizukuBridge.isUsable(),
                Ops.hasRoot(),
                dhizuku.isOfficialOwner() && dhizuku.providerVisible && dhizuku.apiPermissionGranted,
                MiuiUtils.isMiui() && !MiuiUtils.isMiuiOptimizationDisabled());
    }

    @NonNull
    public static Activation activateBestAvailable(@NonNull Context context,
                                                   @Nullable ProgressHandler progressHandler) {
        Context appContext = context.getApplicationContext();
        Plan plan = getPreviewPlan(appContext);
        if (plan.selectedMode == null || ROUTE_CURRENT.equals(plan.selectedRoute)) {
            return new Activation(appContext, Ops.getMode(), null, plan);
        }
        String originalMode = Ops.getMode();
        for (Step step : plan.steps) {
            if (step.mode == null) {
                continue;
            }
            postProgress(appContext, progressHandler, appContext.getString(
                    R.string.installer_privilege_cascade_trying, appContext.getString(step.labelRes)));
            try {
                Ops.setMode(step.mode);
                int status = Ops.init(appContext, true);
                if (status == Ops.STATUS_SUCCESS
                        && SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
                    postProgress(appContext, progressHandler, appContext.getString(
                            R.string.installer_privilege_cascade_using, appContext.getString(step.labelRes)));
                    return new Activation(appContext, originalMode, step.mode, plan);
                }
                Log.w(TAG, "Installer privilege cascade route %s failed with status %d", step.route, status);
            } catch (Throwable e) {
                Log.e(TAG, "Installer privilege cascade route " + step.route + " failed.", e);
            }
        }
        restoreMode(appContext, originalMode);
        postProgress(appContext, progressHandler,
                appContext.getString(R.string.installer_privilege_cascade_system_confirmation_progress));
        return new Activation(appContext, originalMode, null, plan);
    }

    @VisibleForTesting
    @NonNull
    static Plan buildPlan(boolean currentPrivileged,
                          boolean adbAvailable,
                          boolean shizukuAvailable,
                          boolean rootAvailable,
                          boolean dhizukuReady,
                          boolean miuiNudge) {
        List<Step> steps = new ArrayList<>();
        String selectedRoute = null;
        String selectedMode = null;
        if (currentPrivileged) {
            selectedRoute = ROUTE_CURRENT;
            steps.add(Step.selected(ROUTE_CURRENT, R.string.installer_privilege_cascade_chip_current, null));
        } else {
            if (adbAvailable) {
                selectedRoute = ROUTE_ADB;
                selectedMode = Ops.MODE_ADB_OVER_TCP;
                steps.add(Step.selected(ROUTE_ADB, R.string.installer_privilege_cascade_chip_adb,
                        Ops.MODE_ADB_OVER_TCP));
            }
            if (shizukuAvailable) {
                Step step = Step.fallback(ROUTE_SHIZUKU, R.string.installer_privilege_cascade_chip_shizuku,
                        Ops.MODE_SHIZUKU);
                if (selectedRoute == null) {
                    selectedRoute = ROUTE_SHIZUKU;
                    selectedMode = Ops.MODE_SHIZUKU;
                    step = step.asSelected();
                }
                steps.add(step);
            }
            if (rootAvailable) {
                Step step = Step.fallback(ROUTE_ROOT, R.string.installer_privilege_cascade_chip_root,
                        Ops.MODE_ROOT);
                if (selectedRoute == null) {
                    selectedRoute = ROUTE_ROOT;
                    selectedMode = Ops.MODE_ROOT;
                    step = step.asSelected();
                }
                steps.add(step);
            }
            if (selectedRoute == null) {
                selectedRoute = ROUTE_SYSTEM_CONFIRMATION;
                steps.add(Step.info(ROUTE_SYSTEM_CONFIRMATION,
                        R.string.installer_privilege_cascade_chip_system_confirmation));
            }
        }
        if (dhizukuReady) {
            steps.add(Step.info(ROUTE_DHIZUKU_INFO, R.string.installer_privilege_cascade_chip_dhizuku));
        }
        if (miuiNudge) {
            steps.add(Step.info(ROUTE_MIUI_NUDGE, R.string.installer_privilege_cascade_chip_miui));
        }
        return new Plan(selectedRoute, selectedMode, steps);
    }

    @VisibleForTesting
    static void restoreMode(@NonNull Context context, @NonNull String originalMode) {
        try {
            Ops.setMode(originalMode);
            Ops.init(context, true);
        } catch (Throwable e) {
            Log.e(TAG, "Could not restore installer privilege cascade mode " + originalMode, e);
        }
    }

    private static void postProgress(@NonNull Context context,
                                     @Nullable ProgressHandler progressHandler,
                                     @NonNull CharSequence body) {
        if (progressHandler == null) {
            return;
        }
        Object lastMessage = progressHandler.getLastMessage();
        if (!(lastMessage instanceof NotificationInfo)) {
            return;
        }
        NotificationInfo notificationInfo = (NotificationInfo) lastMessage;
        notificationInfo.setOperationName(context.getText(R.string.package_installer));
        notificationInfo.setBody(body);
        progressHandler.postUpdate(-1, 0, notificationInfo);
    }

    public static final class Activation implements AutoCloseable {
        @NonNull
        private final Context mContext;
        @NonNull
        private final String mOriginalMode;
        @Nullable
        private final String mActivatedMode;
        @NonNull
        public final Plan plan;
        private boolean mClosed;

        private Activation(@NonNull Context context,
                           @NonNull String originalMode,
                           @Nullable String activatedMode,
                           @NonNull Plan plan) {
            mContext = context;
            mOriginalMode = originalMode;
            mActivatedMode = activatedMode;
            this.plan = plan;
        }

        @Override
        public void close() {
            if (mClosed) {
                return;
            }
            mClosed = true;
            if (mActivatedMode != null && !mOriginalMode.equals(mActivatedMode)) {
                restoreMode(mContext, mOriginalMode);
            }
        }
    }

    public static final class Plan {
        @NonNull
        public final String selectedRoute;
        @Nullable
        public final String selectedMode;
        @NonNull
        public final List<Step> steps;

        private Plan(@NonNull String selectedRoute,
                     @Nullable String selectedMode,
                     @NonNull List<Step> steps) {
            this.selectedRoute = selectedRoute;
            this.selectedMode = selectedMode;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }

        @NonNull
        public List<CharSequence> getChipLabels(@NonNull Context context) {
            List<CharSequence> labels = new ArrayList<>(steps.size());
            for (Step step : steps) {
                labels.add(context.getString(step.selected
                                ? R.string.installer_privilege_cascade_chip_try
                                : step.mode != null
                                ? R.string.installer_privilege_cascade_chip_fallback
                                : R.string.installer_privilege_cascade_chip_info,
                        context.getString(step.labelRes)));
            }
            return labels;
        }

        @StringRes
        public int getSummaryRes() {
            if (ROUTE_CURRENT.equals(selectedRoute)) {
                return R.string.installer_privilege_cascade_summary_current;
            }
            if (ROUTE_SYSTEM_CONFIRMATION.equals(selectedRoute)) {
                return R.string.installer_privilege_cascade_summary_system_confirmation;
            }
            return R.string.installer_privilege_cascade_summary_elevated;
        }
    }

    public static final class Step {
        @NonNull
        public final String route;
        @StringRes
        public final int labelRes;
        @Nullable
        public final String mode;
        public final boolean selected;

        private Step(@NonNull String route, @StringRes int labelRes, @Nullable String mode,
                     boolean selected) {
            this.route = route;
            this.labelRes = labelRes;
            this.mode = mode;
            this.selected = selected;
        }

        @NonNull
        private static Step selected(@NonNull String route, @StringRes int labelRes, @Nullable String mode) {
            return new Step(route, labelRes, mode, true);
        }

        @NonNull
        private static Step fallback(@NonNull String route, @StringRes int labelRes, @Nullable String mode) {
            return new Step(route, labelRes, mode, false);
        }

        @NonNull
        private static Step info(@NonNull String route, @StringRes int labelRes) {
            return new Step(route, labelRes, null, false);
        }

        @NonNull
        private Step asSelected() {
            return new Step(route, labelRes, mode, true);
        }
    }
}
