// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.google.android.material.transition.MaterialSharedAxis;

import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.runner.RootCapabilityDiagnostics;
import io.github.muntashirakon.AppManager.runner.RootManagerInfo;
import io.github.muntashirakon.AppManager.self.SelfBatteryOptimization;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class PrivilegeHealthPreferences extends PreferenceFragment {
    private static final List<String> MODE_NAMES = Arrays.asList(
            Ops.MODE_AUTO,
            Ops.MODE_ROOT,
            Ops.MODE_SHIZUKU,
            Ops.MODE_ADB_OVER_TCP,
            Ops.MODE_ADB_WIFI,
            Ops.MODE_NO_ROOT);

    private Preference mModePref;
    private Preference mSelfTestPref;
    private Preference mRootManagerPref;
    private Preference mCapabilityDroppingPref;
    private Preference mShizukuPref;
    private Preference mAdbPref;
    private Preference mRemoteServicesPref;
    private Preference mBatteryOptimizationPref;
    private Preference mModeDoctorPref;
    private Preference mBootstrapSmokeTestPref;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_privilege_health, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        mModePref = requirePreference("privilege_health_mode");
        mSelfTestPref = requirePreference("privilege_health_self_test");
        mRootManagerPref = requirePreference("privilege_health_root_manager");
        mCapabilityDroppingPref = requirePreference("privilege_health_capability_dropping");
        mShizukuPref = requirePreference("privilege_health_shizuku");
        mAdbPref = requirePreference("privilege_health_adb");
        mRemoteServicesPref = requirePreference("privilege_health_remote_services");
        mBatteryOptimizationPref = requirePreference("privilege_health_battery_optimization");
        mModeDoctorPref = requirePreference("privilege_health_mode_doctor");
        mBootstrapSmokeTestPref = requirePreference("privilege_health_bootstrap_smoke_test");
        mCapabilityDroppingPref.setOnPreferenceClickListener(preference -> {
            bindCapabilityDroppingAsync();
            return true;
        });
        mBootstrapSmokeTestPref.setOnPreferenceClickListener(preference -> {
            runBootstrapSmokeTest();
            return true;
        });
        mBatteryOptimizationPref.setOnPreferenceClickListener(preference -> {
            handleBatteryOptimization();
            return true;
        });
        mModeDoctorPref.setOnPreferenceClickListener(preference -> {
            runModeDoctor();
            return true;
        });
        requirePreference("privilege_health_refresh").setOnPreferenceClickListener(preference -> {
            refreshHealth();
            return true;
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshHealth();
    }

    @Override
    public int getTitle() {
        return R.string.privilege_health_title;
    }

    private void refreshHealth() {
        Context context = getContext();
        if (context == null) return;
        bindMode(context);
        bindShizuku(context);
        bindAdb(context);
        bindRemoteServices(context);
        bindBatteryOptimization(context);
        bindRootManagerAsync(context.getApplicationContext());
        bindCapabilityDroppingAsync();
    }

    private void bindMode(@NonNull Context context) {
        String mode = Ops.getMode();
        int uid = Users.getSelfOrRemoteUid();
        mModePref.setSummary(getString(R.string.privilege_health_mode_summary,
                getModeLabel(mode), Ops.getInferredMode(context), uid));
        if (isModeHealthy(mode, uid)) {
            mSelfTestPref.setSummary(R.string.privilege_health_self_test_pass);
        } else {
            mSelfTestPref.setSummary(getString(R.string.privilege_health_self_test_fail,
                    getModeLabel(mode), uid));
        }
    }

    private void bindRootManagerAsync(@NonNull Context appContext) {
        mRootManagerPref.setSummary(R.string.loading);
        ThreadUtils.postOnBackgroundThread(() -> {
            RootManagerInfo info = RootManagerInfo.detect(appContext);
            ThreadUtils.postOnMainThread(() -> bindRootManager(info));
        });
    }

    private void bindRootManager(@NonNull RootManagerInfo info) {
        if (!isAdded()) return;
        String name = info.displayName();
        if (name == null) {
            mRootManagerPref.setSummary(R.string.privilege_health_root_manager_missing);
            return;
        }
        StringBuilder extras = new StringBuilder();
        if (info.zygiskNextPresent) {
            extras.append(getString(R.string.privilege_health_root_extra_zygisk_next));
        }
        if (info.suiPresent) {
            extras.append(getString(R.string.privilege_health_root_extra_sui));
        }
        mRootManagerPref.setSummary(getString(R.string.privilege_health_root_manager_summary,
                name, getRootManagerSourceLabel(info.source), extras));
    }

    private void bindCapabilityDroppingAsync() {
        mCapabilityDroppingPref.setSummary(R.string.loading);
        ThreadUtils.postOnBackgroundThread(() -> {
            RootCapabilityDiagnostics.Result result = RootCapabilityDiagnostics.probe();
            ThreadUtils.postOnMainThread(() -> bindCapabilityDropping(result));
        });
    }

    private void bindCapabilityDropping(@NonNull RootCapabilityDiagnostics.Result result) {
        if (!isAdded()) return;
        switch (result.state) {
            case UNAVAILABLE:
                mCapabilityDroppingPref.setSummary(R.string.privilege_health_capability_dropping_unavailable);
                break;
            case ROOT:
                mCapabilityDroppingPref.setSummary(getString(
                        R.string.privilege_health_capability_dropping_root,
                        result.capEff != null ? result.capEff : getString(R.string.state_unknown)));
                break;
            case DROPPED:
                mCapabilityDroppingPref.setSummary(getString(
                        R.string.privilege_health_capability_dropping_dropped,
                        result.uid, result.capEff));
                break;
            case PRESENT:
                mCapabilityDroppingPref.setSummary(getString(
                        R.string.privilege_health_capability_dropping_present,
                        result.uid, result.capEff));
                break;
            case UNKNOWN:
            default:
                mCapabilityDroppingPref.setSummary(getString(
                        R.string.privilege_health_capability_dropping_unknown,
                        result.error != null ? result.error : getString(R.string.state_unknown)));
                break;
        }
    }

    private void bindShizuku(@NonNull Context context) {
        String versionName = ShizukuBridge.getInstalledVersionName(context);
        boolean binderAlive = ShizukuBridge.isBinderAlive();
        boolean supportsUserService = ShizukuBridge.supportsUserService();
        boolean hasPermission = ShizukuBridge.hasPermission();
        int statusRes;
        if (!binderAlive) {
            statusRes = R.string.privilege_health_shizuku_not_running;
        } else if (!supportsUserService) {
            statusRes = R.string.privilege_health_shizuku_user_service_unavailable;
        } else if (hasPermission) {
            statusRes = R.string.privilege_health_shizuku_authorized;
        } else {
            statusRes = R.string.privilege_health_shizuku_permission_required;
        }
        String version = versionName != null ? versionName : getString(R.string.state_unknown);
        String warning = "";
        ShizukuBridge.OemCompatibilityWarning oemWarning = ShizukuBridge.getOemCompatibilityWarning(context);
        if (oemWarning != null) {
            warning = "\n" + getString(oemWarning.summaryTextRes, oemWarning.fallbackVersion);
        } else if (ShizukuBridge.hasAndroid17CompatibilityRisk(context)) {
            warning = "\n" + getString(R.string.privilege_health_shizuku_android17_warning);
        } else if (versionName != null && !ShizukuBridge.isRecommendedManagerVersion(context)) {
            warning = "\n" + getString(R.string.privilege_health_shizuku_update_recommended,
                    ShizukuBridge.MIN_RECOMMENDED_MANAGER_VERSION);
        }
        mShizukuPref.setSummary(getString(R.string.privilege_health_shizuku_summary,
                getString(statusRes), version, ShizukuBridge.getVersionOrZero(),
                ShizukuBridge.MIN_USER_SERVICE_VERSION, ShizukuBridge.getUidOrSelf()) + warning);
    }

    private void bindAdb(@NonNull Context context) {
        boolean usbDebugging = isGlobalSettingEnabled(context, "adb_enabled");
        boolean wirelessDebugging = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && isGlobalSettingEnabled(context, "adb_wifi_enabled");
        boolean paired = ServerConfig.hasPairedAdbDevice();
        int wirelessStatus = wirelessDebugging
                ? R.string.mode_of_op_capability_status_active
                : paired
                ? R.string.mode_of_op_capability_status_paired
                : R.string.mode_of_op_capability_status_inactive;
        mAdbPref.setSummary(getString(R.string.privilege_health_adb_summary,
                getString(usbDebugging
                        ? R.string.mode_of_op_capability_status_enabled
                        : R.string.mode_of_op_capability_status_disabled),
                getString(wirelessStatus),
                paired ? String.valueOf(ServerConfig.getLastAdbPairingPort()) : getString(R.string.state_unknown)));
    }

    private void bindRemoteServices(@NonNull Context context) {
        boolean serverAlive = LocalServer.alive(context);
        boolean servicesAlive = LocalServices.alive();
        mRemoteServicesPref.setSummary(getString(R.string.privilege_health_remote_services_summary,
                getString(serverAlive ? R.string.status_remote_server_active : R.string.status_remote_server_inactive),
                getString(servicesAlive ? R.string.status_remote_services_active : R.string.status_remote_services_inactive),
                Users.getSelfOrRemoteUid(),
                Process.myUid()));
    }

    private void bindBatteryOptimization(@NonNull Context context) {
        if (!SelfBatteryOptimization.isSupported()) {
            mBatteryOptimizationPref.setEnabled(false);
            mBatteryOptimizationPref.setSummary(R.string.pref_battery_optimization_unsupported);
            return;
        }
        mBatteryOptimizationPref.setEnabled(true);
        boolean exempt = SelfBatteryOptimization.isExempt(context);
        if (exempt) {
            mBatteryOptimizationPref.setSummary(R.string.pref_battery_optimization_state_exempt);
        } else if (SelfBatteryOptimization.canAutoFix()) {
            mBatteryOptimizationPref.setSummary(R.string.privilege_health_battery_can_auto_fix);
        } else {
            mBatteryOptimizationPref.setSummary(R.string.pref_battery_optimization_state_optimized);
        }
    }

    private void runBootstrapSmokeTest() {
        Context context = getContext();
        if (context == null) return;
        mBootstrapSmokeTestPref.setEnabled(false);
        mBootstrapSmokeTestPref.setSummary(R.string.privilege_health_bootstrap_smoke_test_running);
        ThreadUtils.postOnBackgroundThread(() -> {
            long started = SystemClock.elapsedRealtime();
            String signature;
            try {
                LocalServer server = LocalServer.getInstance();
                Shell.Result result = server.runCommand("id -u");
                signature = LocalServer.buildBootstrapSignature("succeeded", null,
                        SystemClock.elapsedRealtime() - started, result);
            } catch (Throwable th) {
                signature = LocalServer.buildBootstrapSignature("failed", th,
                        SystemClock.elapsedRealtime() - started, null);
            }
            ThreadUtils.postOnMainThread(() -> showBootstrapSmokeTestResult(signature));
        });
    }

    private void runModeDoctor() {
        Context context = getContext();
        if (context == null) return;
        mModeDoctorPref.setEnabled(false);
        mModeDoctorPref.setSummary(R.string.privilege_health_mode_doctor_running);
        ThreadUtils.postOnBackgroundThread(() -> {
            String report = PrivilegeModeDoctor.run(context);
            ThreadUtils.postOnMainThread(() -> showModeDoctorResult(report));
        });
    }

    private void showModeDoctorResult(@NonNull String report) {
        if (!isAdded()) return;
        mModeDoctorPref.setEnabled(true);
        mModeDoctorPref.setSummary(R.string.privilege_health_mode_doctor_summary);
        Context context = getContext();
        if (context != null) {
            UIUtils.displayCopyableErrorDialog(context,
                    getString(R.string.privilege_health_mode_doctor_title), report);
        }
    }

    private void showBootstrapSmokeTestResult(@NonNull String signature) {
        if (!isAdded()) return;
        LocalServer.rememberBootstrapSignature(signature);
        mBootstrapSmokeTestPref.setEnabled(true);
        mBootstrapSmokeTestPref.setSummary(signature);
        Context context = getContext();
        if (context != null) {
            UIUtils.displayCopyableErrorDialog(context,
                    getString(R.string.privilege_health_bootstrap_smoke_test_title), signature);
        }
    }

    private void handleBatteryOptimization() {
        Context context = getContext();
        if (context == null || !SelfBatteryOptimization.isSupported()) return;
        boolean exempt = SelfBatteryOptimization.isExempt(context);
        if (!exempt && SelfBatteryOptimization.canAutoFix()) {
            Context appContext = context.getApplicationContext();
            ThreadUtils.postOnBackgroundThread(() -> {
                @SelfBatteryOptimization.AutoFixResult int result = SelfBatteryOptimization.autoFixIfPossible(appContext);
                ThreadUtils.postOnMainThread(() -> {
                    Context currentContext = getContext();
                    if (currentContext == null) return;
                    if (result == SelfBatteryOptimization.RESULT_FIXED
                            || result == SelfBatteryOptimization.RESULT_ALREADY_EXEMPT) {
                        Toast.makeText(currentContext, R.string.pref_battery_optimization_state_exempt,
                                Toast.LENGTH_SHORT).show();
                        bindBatteryOptimization(currentContext);
                    } else {
                        launchBatteryOptimizationSystemFlow(currentContext, false);
                    }
                });
            });
            return;
        }
        launchBatteryOptimizationSystemFlow(context, exempt);
    }

    private void launchBatteryOptimizationSystemFlow(@NonNull Context context, boolean exempt) {
        Intent intent;
        if (exempt) {
            intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        } else {
            intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(context, R.string.pref_battery_optimization_unsupported,
                    Toast.LENGTH_LONG).show();
        }
    }

    @NonNull
    private String getModeLabel(@NonNull String mode) {
        String[] modes = getResources().getStringArray(R.array.modes);
        int index = MODE_NAMES.indexOf(mode);
        return index >= 0 ? modes[index] : mode;
    }

    @NonNull
    private String getRootManagerSourceLabel(@NonNull RootManagerInfo.Source source) {
        switch (source) {
            case MARKER:
                return getString(R.string.privilege_health_root_source_marker);
            case PACKAGE:
                return getString(R.string.privilege_health_root_source_package);
            case NONE:
            default:
                return getString(R.string.privilege_health_root_source_none);
        }
    }

    private static boolean isGlobalSettingEnabled(@NonNull Context context, @NonNull String key) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), key, 0) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isModeHealthy(@NonNull String mode, int uid) {
        switch (mode) {
            case Ops.MODE_ROOT:
                return uid == Ops.ROOT_UID;
            case Ops.MODE_SHIZUKU:
                return uid == Ops.ROOT_UID || uid == Ops.SYSTEM_UID || uid == Ops.SHELL_UID;
            case Ops.MODE_ADB_OVER_TCP:
            case Ops.MODE_ADB_WIFI:
                return uid <= Ops.SHELL_UID;
            case Ops.MODE_AUTO:
            case Ops.MODE_NO_ROOT:
            default:
                return true;
        }
    }
}
