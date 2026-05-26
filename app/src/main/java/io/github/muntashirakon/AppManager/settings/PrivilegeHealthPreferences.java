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
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.MotionUtils;
import io.github.muntashirakon.AppManager.dhizuku.DhizukuBridge;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.misc.SupportInfoBundle;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.AppManager.runner.KernelSuDiagnostics;
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
import io.github.muntashirakon.AppManager.utils.Utils;

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
    private Preference mKernelSuPref;
    private Preference mShizukuPref;
    private Preference mDhizukuPref;
    private Preference mAdbPref;
    private Preference mRemoteServicesPref;
    private Preference mBatteryOptimizationPref;
    private Preference mRestrictedSettingsPref;
    private Preference mModeDoctorPref;
    private Preference mBootstrapSmokeTestPref;
    @Nullable
    private KernelSuDiagnostics.Result mKernelSuLastResult;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_privilege_health, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        mModePref = requirePreference("privilege_health_mode");
        mSelfTestPref = requirePreference("privilege_health_self_test");
        mRootManagerPref = requirePreference("privilege_health_root_manager");
        mCapabilityDroppingPref = requirePreference("privilege_health_capability_dropping");
        mKernelSuPref = requirePreference("privilege_health_kernelsu");
        mShizukuPref = requirePreference("privilege_health_shizuku");
        mDhizukuPref = requirePreference("privilege_health_dhizuku");
        mAdbPref = requirePreference("privilege_health_adb");
        mRemoteServicesPref = requirePreference("privilege_health_remote_services");
        mBatteryOptimizationPref = requirePreference("privilege_health_battery_optimization");
        mRestrictedSettingsPref = requirePreference("privilege_health_restricted_settings");
        mModeDoctorPref = requirePreference("privilege_health_mode_doctor");
        mBootstrapSmokeTestPref = requirePreference("privilege_health_bootstrap_smoke_test");
        mCapabilityDroppingPref.setOnPreferenceClickListener(preference -> {
            bindCapabilityDroppingAsync();
            return true;
        });
        mKernelSuPref.setOnPreferenceClickListener(preference -> {
            showKernelSuDetails();
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
        mDhizukuPref.setOnPreferenceClickListener(preference -> {
            showDhizukuDetails();
            return true;
        });
        mRestrictedSettingsPref.setOnPreferenceClickListener(preference -> {
            showRestrictedSettingsWalkthrough();
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
        MotionUtils.applySharedAxisZTransition(this);
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
        bindDhizuku(context);
        bindAdb(context);
        bindRemoteServices(context);
        bindBatteryOptimization(context);
        bindRestrictedSettings(context);
        bindRootManagerAsync(context.getApplicationContext());
        bindCapabilityDroppingAsync();
        bindKernelSuAsync(context.getApplicationContext());
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
        String summary;
        switch (result.state) {
            case UNAVAILABLE:
                summary = getString(R.string.privilege_health_capability_dropping_unavailable);
                break;
            case ROOT:
                summary = getString(
                        R.string.privilege_health_capability_dropping_root,
                        result.capEff != null ? result.capEff : getString(R.string.state_unknown));
                break;
            case DROPPED:
                summary = getString(
                        R.string.privilege_health_capability_dropping_dropped,
                        result.uid, result.capEff);
                break;
            case PRESENT:
                summary = getString(
                        R.string.privilege_health_capability_dropping_present,
                        result.uid, result.capEff);
                break;
            case UNKNOWN:
            default:
                summary = getString(
                        R.string.privilege_health_capability_dropping_unknown,
                        result.error != null ? result.error : getString(R.string.state_unknown));
                break;
        }
        mCapabilityDroppingPref.setSummary(appendMagiskCapabilityContext(summary, result));
    }

    private void bindKernelSuAsync(@NonNull Context appContext) {
        mKernelSuPref.setSummary(R.string.loading);
        ThreadUtils.postOnBackgroundThread(() -> {
            KernelSuDiagnostics.Result result = KernelSuDiagnostics.probe(appContext);
            ThreadUtils.postOnMainThread(() -> bindKernelSu(result));
        });
    }

    private void bindKernelSu(@NonNull KernelSuDiagnostics.Result result) {
        if (!isAdded()) return;
        mKernelSuLastResult = result;
        mKernelSuPref.setEnabled(true);
        switch (result.state) {
            case NOT_KERNELSU:
                mKernelSuPref.setEnabled(false);
                mKernelSuPref.setSummary(R.string.privilege_health_kernelsu_not_detected);
                break;
            case UNAVAILABLE:
                mKernelSuPref.setSummary(getString(R.string.privilege_health_kernelsu_unavailable,
                        getRootManagerSourceLabel(result.source)));
                break;
            case ACTIVE:
                mKernelSuPref.setSummary(getString(R.string.privilege_health_kernelsu_summary,
                        KernelSuDiagnostics.formatSeccompMode(result.seccompMode),
                        getKernelSuSulogSummary(result),
                        getKernelSuAppProfileSummary(result)));
                break;
            case UNKNOWN:
            default:
                mKernelSuPref.setSummary(getString(R.string.privilege_health_kernelsu_unknown,
                        result.error != null ? result.error : getString(R.string.state_unknown)));
                break;
        }
    }

    private void bindShizuku(@NonNull Context context) {
        // Prefer the display-friendly version that falls back to the binder-reported
        // API version when "Hide Shizuku" mode hides the manager package from
        // PackageManager queries (binder still works for authorized callers).
        String versionName = ShizukuBridge.getDisplayVersion(context);
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
        StringBuilder warning = new StringBuilder();
        if (ShizukuBridge.isRootBacked()) {
            warning.append('\n').append(getString(R.string.privilege_health_shizuku_root_backed_warning));
        }
        ShizukuBridge.OemCompatibilityWarning oemWarning = ShizukuBridge.getOemCompatibilityWarning(context);
        if (oemWarning != null) {
            warning.append('\n').append(getString(oemWarning.summaryTextRes, oemWarning.fallbackVersion));
        } else if (ShizukuBridge.hasAndroid17CompatibilityRisk(context)) {
            warning.append('\n').append(getString(R.string.privilege_health_shizuku_android17_warning));
        } else if (versionName != null && !ShizukuBridge.isRecommendedManagerVersion(context)) {
            warning.append('\n').append(getString(R.string.privilege_health_shizuku_update_recommended,
                    ShizukuBridge.MIN_RECOMMENDED_MANAGER_VERSION));
        }
        mShizukuPref.setSummary(getString(R.string.privilege_health_shizuku_summary,
                getString(statusRes), version, ShizukuBridge.getVersionOrZero(),
                ShizukuBridge.MIN_USER_SERVICE_VERSION, ShizukuBridge.getUidOrSelf()) + warning.toString());
    }

    private void bindDhizuku(@NonNull Context context) {
        DhizukuBridge.Result result = DhizukuBridge.probe(context);
        if (DhizukuBridge.isBelowMinimumSupportedAndroidVersion(result.sdk)) {
            mDhizukuPref.setSummary(R.string.privilege_health_dhizuku_unsupported);
            return;
        }
        String version = result.installedVersionName != null
                ? result.installedVersionName
                : getString(R.string.state_unknown);
        String compatibilityWarning = DhizukuBridge.isAboveDeclaredSupportedAndroidVersion(result.sdk)
                ? "\n" + getString(R.string.privilege_health_dhizuku_android_newer_warning,
                        DhizukuBridge.MAX_DECLARED_SUPPORTED_SDK)
                : "";
        if (result.isOfficialOwner()) {
            mDhizukuPref.setSummary(getString(R.string.privilege_health_dhizuku_active_summary,
                    version,
                    getString(result.providerVisible
                            ? R.string.mode_of_op_capability_status_active
                            : R.string.mode_of_op_capability_status_inactive),
                    getString(result.apiPermissionGranted
                            ? R.string.mode_of_op_capability_status_authorized
                            : R.string.mode_of_op_capability_status_permission_required),
                    result.ownerLabel()) + compatibilityWarning);
            return;
        }
        if (result.isInstalled()) {
            mDhizukuPref.setSummary(getString(R.string.privilege_health_dhizuku_installed_summary,
                    version, DhizukuBridge.ACTIVATION_COMMAND) + compatibilityWarning);
            return;
        }
        mDhizukuPref.setSummary(getString(R.string.privilege_health_dhizuku_missing_summary,
                DhizukuBridge.ACTIVATION_COMMAND) + compatibilityWarning);
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

    private void bindRestrictedSettings(@NonNull Context context) {
        RestrictedSettingsDiagnostics.Result result = RestrictedSettingsDiagnostics.probe(context);
        String source = getRestrictedSettingsSourceLabel(result);
        switch (result.status) {
            case RestrictedSettingsDiagnostics.STATUS_NOT_APPLICABLE:
                mRestrictedSettingsPref.setEnabled(false);
                mRestrictedSettingsPref.setSummary(R.string.privilege_health_restricted_settings_not_applicable);
                break;
            case RestrictedSettingsDiagnostics.STATUS_TRUSTED_STORE:
                mRestrictedSettingsPref.setEnabled(true);
                mRestrictedSettingsPref.setSummary(getString(
                        R.string.privilege_health_restricted_settings_trusted, source));
                break;
            case RestrictedSettingsDiagnostics.STATUS_LIKELY_RESTRICTED:
                mRestrictedSettingsPref.setEnabled(true);
                mRestrictedSettingsPref.setSummary(getString(
                        R.string.privilege_health_restricted_settings_likely, source));
                break;
            case RestrictedSettingsDiagnostics.STATUS_UNKNOWN_SOURCE:
                mRestrictedSettingsPref.setEnabled(true);
                mRestrictedSettingsPref.setSummary(R.string.privilege_health_restricted_settings_unknown);
                break;
            case RestrictedSettingsDiagnostics.STATUS_REVIEW_RECOMMENDED:
            default:
                mRestrictedSettingsPref.setEnabled(true);
                mRestrictedSettingsPref.setSummary(getString(
                        R.string.privilege_health_restricted_settings_review, source));
                break;
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
            String finalSignature = signature;
            ThreadUtils.postOnMainThread(() -> showBootstrapSmokeTestResult(finalSignature));
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
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.privilege_health_mode_doctor_title)
                .setMessage(report)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.copy, (d, w) -> {
                    io.github.muntashirakon.AppManager.utils.ClipboardUtils
                            .copyToClipboard(context, "mode-doctor", report);
                    UIUtils.displayShortToast(R.string.copied_to_clipboard);
                })
                .setNegativeButton(R.string.privilege_health_mode_doctor_share_with_bundle,
                        (d, w) -> shareModeDoctorWithSupportBundle(report))
                .show();
    }

    /**
     * Build a Support Info Bundle with the Mode Doctor probe report inlined as
     * a preamble, then hand the file off to the system share chooser.
     * EI-08 follow-up: replaces the previously-separate flows where the user
     * had to copy the doctor report and grab the bundle by hand.
     */
    private void shareModeDoctorWithSupportBundle(@NonNull String report) {
        Context context = getContext();
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        Toast.makeText(context, R.string.privilege_health_mode_doctor_share_preparing,
                Toast.LENGTH_SHORT).show();
        ThreadUtils.postOnBackgroundThread(() -> {
            Path bundle;
            try {
                bundle = SupportInfoBundle.writeTextBundle(appContext, buildSupportPreamble(report));
            } catch (Throwable t) {
                ThreadUtils.postOnMainThread(() -> {
                    Context current = getContext();
                    if (current != null) {
                        Toast.makeText(current, R.string.diagnostic_failed, Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }
            Intent intent = SupportInfoBundle.buildShareIntent(appContext, bundle);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ThreadUtils.postOnMainThread(() -> {
                Context current = getContext();
                if (current == null) return;
                try {
                    current.startActivity(Intent.createChooser(intent,
                            current.getString(R.string.support_info_bundle_share_title)));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(current, R.string.no_apps_to_handle, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @NonNull
    private static String buildSupportPreamble(@NonNull String modeDoctorReport) {
        return "Mode Doctor probe\n=================\n" + modeDoctorReport;
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

    private void showRestrictedSettingsWalkthrough() {
        Context context = getContext();
        if (context == null) return;
        RestrictedSettingsDiagnostics.Result result = RestrictedSettingsDiagnostics.probe(context);
        String source = getRestrictedSettingsSourceLabel(result);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.privilege_health_restricted_settings_dialog_title)
                .setMessage(getString(R.string.privilege_health_restricted_settings_dialog_message, source))
                .setPositiveButton(R.string.privilege_health_restricted_settings_open_app_info,
                        (dialog, which) -> launchSettingsIntent(
                                RestrictedSettingsDiagnostics.buildAppInfoIntent(context)))
                .setNeutralButton(R.string.privilege_health_restricted_settings_open_accessibility,
                        (dialog, which) -> launchSettingsIntent(
                                RestrictedSettingsDiagnostics.buildAccessibilitySettingsIntent()))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void showDhizukuDetails() {
        Context context = getContext();
        if (context == null) return;
        DhizukuBridge.Result result = DhizukuBridge.probe(context);
        String version = result.installedVersionName != null
                ? result.installedVersionName
                : getString(R.string.state_unknown);
        String message = getString(R.string.privilege_health_dhizuku_dialog_message,
                version,
                result.ownerLabel(),
                result.providerVisible
                        ? getString(R.string.mode_of_op_capability_status_active)
                        : getString(R.string.mode_of_op_capability_status_inactive),
                result.apiPermissionGranted
                        ? getString(R.string.mode_of_op_capability_status_authorized)
                        : getString(R.string.mode_of_op_capability_status_permission_required),
                DhizukuBridge.ACTIVATION_COMMAND);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.privilege_health_dhizuku_title)
                .setMessage(message)
                .setNeutralButton(R.string.copy, (dialog, which) ->
                        Utils.copyToClipboard(context, "Dhizuku activation", DhizukuBridge.ACTIVATION_COMMAND));
        if (result.isInstalled()) {
            builder.setPositiveButton(R.string.open,
                    (dialog, which) -> launchSettingsIntent(DhizukuBridge.getSettingsIntent(context)))
                    .setNegativeButton(R.string.close, null);
        } else {
            builder.setPositiveButton(R.string.close, null);
        }
        builder.show();
    }

    private void showKernelSuDetails() {
        Context context = getContext();
        if (context == null) return;
        KernelSuDiagnostics.Result result = mKernelSuLastResult;
        if (result == null) {
            bindKernelSuAsync(context.getApplicationContext());
            return;
        }
        String message = buildKernelSuDetails(result);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.privilege_health_kernelsu_title)
                .setMessage(message)
                .setNeutralButton(R.string.copy, (dialog, which) ->
                        Utils.copyToClipboard(context, "KernelSU diagnostics", message));
        Intent managerIntent = getKernelSuManagerIntent(context);
        if (managerIntent != null) {
            builder.setPositiveButton(R.string.open, (dialog, which) -> launchSettingsIntent(managerIntent))
                    .setNegativeButton(R.string.close, null);
        } else {
            builder.setPositiveButton(R.string.close, null);
        }
        builder.show();
    }

    private void launchSettingsIntent(@NonNull Intent intent) {
        Context context = getContext();
        if (context == null) return;
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(context, R.string.privilege_health_restricted_settings_settings_unavailable,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Nullable
    private Intent getKernelSuManagerIntent(@NonNull Context context) {
        Intent launchIntent = context.getPackageManager()
                .getLaunchIntentForPackage(KernelSuDiagnostics.KERNELSU_PACKAGE);
        if (launchIntent == null) {
            launchIntent = context.getPackageManager()
                    .getLaunchIntentForPackage(KernelSuDiagnostics.KERNELSU_NEXT_PACKAGE);
        }
        if (launchIntent != null) {
            return launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return null;
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
    private String getRestrictedSettingsSourceLabel(@NonNull RestrictedSettingsDiagnostics.Result result) {
        if (result.sourcePackageName != null) {
            return result.sourcePackageName;
        }
        if (result.error != null) {
            return getString(R.string.state_unknown) + " (" + result.error + ")";
        }
        return getString(R.string.state_unknown);
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

    @NonNull
    private String getKernelSuSulogSummary(@NonNull KernelSuDiagnostics.Result result) {
        switch (result.sulogState) {
            case READABLE:
                if (result.sulogDenials.isEmpty()) {
                    return getString(R.string.privilege_health_kernelsu_sulog_no_denials);
                }
                return getString(R.string.privilege_health_kernelsu_sulog_denials,
                        result.sulogDenials.size());
            case MISSING:
                return getString(R.string.privilege_health_kernelsu_sulog_missing);
            case UNAVAILABLE:
            default:
                return getString(R.string.privilege_health_kernelsu_sulog_unavailable);
        }
    }

    @NonNull
    private String buildKernelSuDetails(@NonNull KernelSuDiagnostics.Result result) {
        StringBuilder message = new StringBuilder();
        message.append(getString(R.string.privilege_health_kernelsu_details_state,
                result.state.name(), getRootManagerSourceLabel(result.source)));
        message.append('\n').append(getString(R.string.privilege_health_kernelsu_details_seccomp,
                KernelSuDiagnostics.formatSeccompMode(result.seccompMode)));
        message.append('\n').append(getString(R.string.privilege_health_kernelsu_details_sulog,
                getKernelSuSulogSummary(result)));
        message.append('\n').append(getString(R.string.privilege_health_kernelsu_details_app_profile,
                getKernelSuAppProfileSummary(result)));
        appendKernelSuAppProfileDetails(message, result.appProfile);
        if (result.error != null) {
            message.append('\n').append(getString(R.string.privilege_health_kernelsu_details_error,
                    result.error));
        }
        if (!result.sulogDenials.isEmpty()) {
            message.append("\n\n").append(getString(R.string.privilege_health_kernelsu_details_recent_denials));
            for (String line : result.sulogDenials) {
                message.append('\n').append(line);
            }
        }
        return message.toString();
    }

    private void appendKernelSuAppProfileDetails(@NonNull StringBuilder message,
                                                 @NonNull KernelSuDiagnostics.AppProfile profile) {
        if (profile.state == KernelSuDiagnostics.AppProfileState.UNAVAILABLE) {
            return;
        }
        message.append('\n').append(getString(R.string.privilege_health_kernelsu_details_profile_uid_gid,
                getKernelSuIdLabel(profile.uid), getKernelSuIdLabel(profile.gid)));
        message.append('\n').append(getString(R.string.privilege_health_kernelsu_details_profile_groups,
                profile.groups.isEmpty()
                        ? getString(R.string.state_unknown)
                        : TextUtils.join(", ", profile.groups)));
        message.append('\n').append(getString(R.string.privilege_health_kernelsu_details_profile_selinux,
                valueOrUnknown(profile.selinuxContext)));
        message.append('\n').append(getString(R.string.privilege_health_kernelsu_details_profile_cap_eff,
                valueOrUnknown(profile.capEff)));
        if (!profile.missingExpectedCapabilities.isEmpty()) {
            message.append('\n').append(getString(R.string.privilege_health_kernelsu_details_profile_missing_caps,
                    TextUtils.join(", ", profile.missingExpectedCapabilities)));
        }
        if (profile.rawId != null) {
            message.append('\n').append(getString(R.string.privilege_health_kernelsu_details_profile_raw_id,
                    profile.rawId));
        }
    }

    @NonNull
    private String getKernelSuAppProfileSummary(@NonNull KernelSuDiagnostics.Result result) {
        KernelSuDiagnostics.AppProfile profile = result.appProfile;
        switch (profile.state) {
            case DEFAULT_ROOT:
                return getString(R.string.privilege_health_kernelsu_app_profile_default_root);
            case RESTRICTED:
                if (profile.missingExpectedCapabilities.isEmpty()) {
                    return getString(R.string.privilege_health_kernelsu_app_profile_restricted_identity,
                            getKernelSuIdLabel(profile.uid), getKernelSuIdLabel(profile.gid));
                }
                return getString(R.string.privilege_health_kernelsu_app_profile_restricted_caps,
                        getKernelSuIdLabel(profile.uid),
                        getKernelSuIdLabel(profile.gid),
                        TextUtils.join(", ", profile.missingExpectedCapabilities));
            case UNKNOWN:
                return getString(R.string.privilege_health_kernelsu_app_profile_unknown);
            case UNAVAILABLE:
            default:
                return getString(R.string.privilege_health_kernelsu_app_profile_unavailable);
        }
    }

    @NonNull
    private String getKernelSuIdLabel(int id) {
        return id >= 0 ? String.valueOf(id) : getString(R.string.state_unknown);
    }

    @NonNull
    private String valueOrUnknown(@Nullable String value) {
        return value != null ? value : getString(R.string.state_unknown);
    }

    @NonNull
    private String appendMagiskCapabilityContext(@NonNull String summary,
                                                 @NonNull RootCapabilityDiagnostics.Result result) {
        if (result.magiskVersion == null
                && result.magiskVersionCode == null
                && result.magiskPolicyState == RootCapabilityDiagnostics.MagiskPolicyState.NOT_MAGISK) {
            return summary;
        }
        return summary + "\n" + getString(R.string.privilege_health_capability_dropping_magisk_context,
                getMagiskVersionLabel(result),
                getMagiskDropCapLabel(result),
                getMagiskPolicyLabel(result));
    }

    @NonNull
    private String getMagiskVersionLabel(@NonNull RootCapabilityDiagnostics.Result result) {
        if (result.magiskVersion != null && result.magiskVersionCode != null) {
            return getString(R.string.privilege_health_capability_dropping_magisk_version,
                    result.magiskVersion, result.magiskVersionCode);
        }
        if (result.magiskVersion != null) {
            return result.magiskVersion;
        }
        if (result.magiskVersionCode != null) {
            return result.magiskVersionCode;
        }
        return getString(R.string.state_unknown);
    }

    @NonNull
    private String getMagiskDropCapLabel(@NonNull RootCapabilityDiagnostics.Result result) {
        if (result.isMagiskDropCapOptInVersion()) {
            return getString(R.string.privilege_health_capability_dropping_magisk_drop_cap_opt_in);
        }
        if (result.magiskVersionCode != null) {
            return getString(R.string.privilege_health_capability_dropping_magisk_drop_cap_legacy);
        }
        return getString(R.string.privilege_health_capability_dropping_magisk_drop_cap_unknown);
    }

    @NonNull
    private String getMagiskPolicyLabel(@NonNull RootCapabilityDiagnostics.Result result) {
        switch (result.magiskPolicyState) {
            case MATCHED:
                return getString(R.string.privilege_health_capability_dropping_magisk_policy_matched,
                        result.magiskPolicyRules.size());
            case NO_MATCH:
                return getString(R.string.privilege_health_capability_dropping_magisk_policy_no_match);
            case UNAVAILABLE:
                return getString(R.string.privilege_health_capability_dropping_magisk_policy_unavailable);
            case NOT_MAGISK:
            default:
                return getString(R.string.privilege_health_capability_dropping_magisk_policy_not_magisk);
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
