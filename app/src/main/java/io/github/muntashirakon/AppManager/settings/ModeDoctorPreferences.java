// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.dhizuku.DhizukuBridge;
import io.github.muntashirakon.AppManager.misc.SupportInfoBundle;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import io.github.muntashirakon.AppManager.utils.ClipboardUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;

public class ModeDoctorPreferences extends PreferenceFragment {
    private static final String[] ROOT_MANAGER_PACKAGES = new String[]{
            "com.topjohnwu.magisk",
            "me.weishu.kernelsu",
            "com.rifsxd.ksunext",
            "me.bmax.apatch",
    };

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        renderRunning();
        runDoctor();
    }

    @Override
    public int getTitle() {
        return R.string.privilege_health_mode_doctor_title;
    }

    private void runDoctor() {
        Context context = getContext();
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        renderRunning();
        ThreadUtils.postOnBackgroundThread(() -> {
            PrivilegeModeDoctor.Report report = PrivilegeModeDoctor.runReport(appContext);
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded()) return;
                renderReport(report);
            });
        });
    }

    private void renderRunning() {
        Context context = getContext();
        if (context == null) return;
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        screen.addPreference(newPreference(context,
                getString(R.string.privilege_health_mode_doctor_title),
                getString(R.string.privilege_health_mode_doctor_running),
                false));
        setPreferenceScreen(screen);
    }

    private void renderReport(@NonNull PrivilegeModeDoctor.Report report) {
        Context context = getContext();
        if (context == null) return;
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        screen.addPreference(newPreference(context,
                getString(R.string.privilege_health_mode_doctor_title),
                getString(R.string.privilege_health_mode_doctor_complete),
                false));

        Preference refresh = newPreference(context, getString(R.string.refresh),
                getString(R.string.privilege_health_refresh_summary), true);
        refresh.setOnPreferenceClickListener(preference -> {
            runDoctor();
            return true;
        });
        screen.addPreference(refresh);

        Preference copy = newPreference(context, getString(R.string.copy_to_clipboard),
                getString(R.string.privilege_health_mode_doctor_copy_summary), true);
        copy.setOnPreferenceClickListener(preference -> {
            ClipboardUtils.copyToClipboard(context, "mode-doctor",
                    PrivilegeModeDoctor.formatReportForClipboard(report.text));
            UIUtils.displayShortToast(R.string.copied_to_clipboard);
            return true;
        });
        screen.addPreference(copy);

        Preference share = newPreference(context, getString(R.string.privilege_health_mode_doctor_share_with_bundle),
                getString(R.string.privilege_health_mode_doctor_share_summary), true);
        share.setOnPreferenceClickListener(preference -> {
            shareModeDoctorWithSupportBundle(report.text);
            return true;
        });
        screen.addPreference(share);

        PreferenceCategory category = new PreferenceCategory(context);
        category.setTitle(R.string.privilege_health_mode_doctor_results_category);
        category.setIconSpaceReserved(false);
        screen.addPreference(category);

        for (PrivilegeModeDoctor.Probe probe : report.probes) {
            Preference preference = newPreference(context,
                    probe.status + " - " + probe.name,
                    getProbeSummary(probe),
                    probe.fixTarget != null);
            if (probe.fixTarget != null) {
                preference.setOnPreferenceClickListener(pref -> {
                    launchFix(probe.fixTarget, report.text);
                    return true;
                });
            }
            screen.addPreference(preference);
        }
        setPreferenceScreen(screen);
    }

    @NonNull
    private Preference newPreference(@NonNull Context context,
                                     @NonNull String title,
                                     @NonNull String summary,
                                     boolean selectable) {
        Preference preference = new Preference(context);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setSelectable(selectable);
        preference.setIconSpaceReserved(false);
        return preference;
    }

    @NonNull
    private String getProbeSummary(@NonNull PrivilegeModeDoctor.Probe probe) {
        StringBuilder summary = new StringBuilder(probe.details);
        if (probe.fix != null && !probe.fix.isEmpty()) {
            summary.append('\n').append("Fix: ").append(probe.fix);
        }
        if (probe.fixTarget != null) {
            summary.append('\n').append(getString(R.string.privilege_health_mode_doctor_tap_to_open,
                    getFixTargetLabel(probe.fixTarget)));
        }
        return summary.toString();
    }

    @NonNull
    private String getFixTargetLabel(@NonNull PrivilegeModeDoctor.FixTarget target) {
        switch (target) {
            case MODE_SETTINGS:
                return getString(R.string.privilege_health_mode_doctor_action_mode_settings);
            case ROOT_MANAGER:
                return getString(R.string.privilege_health_mode_doctor_action_root_manager);
            case SHIZUKU_SETTINGS:
                return getString(R.string.privilege_health_mode_doctor_action_shizuku_settings);
            case SHIZUKU_ARCHIVE:
                return getString(R.string.privilege_health_mode_doctor_action_shizuku_archive);
            case DHIZUKU_SETTINGS:
                return getString(R.string.privilege_health_mode_doctor_action_dhizuku_settings);
            case DEVELOPER_OPTIONS:
                return getString(R.string.open_developer_options);
            case RESTRICTED_APP_INFO:
                return getString(R.string.privilege_health_restricted_settings_open_app_info);
            case BOOTSTRAP_SMOKE_TEST:
                return getString(R.string.privilege_health_bootstrap_smoke_test_title);
            case SUPPORT_BUNDLE:
            default:
                return getString(R.string.support_info_bundle_share_title);
        }
    }

    private void launchFix(@NonNull PrivilegeModeDoctor.FixTarget target,
                           @NonNull String reportText) {
        Context context = getContext();
        if (context == null) return;
        if (target == PrivilegeModeDoctor.FixTarget.SUPPORT_BUNDLE) {
            shareModeDoctorWithSupportBundle(reportText);
            return;
        }
        Intent intent = buildFixIntent(context, target);
        if (intent == null) {
            Toast.makeText(context, R.string.privilege_health_restricted_settings_settings_unavailable,
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(context, R.string.privilege_health_restricted_settings_settings_unavailable,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Nullable
    private Intent buildFixIntent(@NonNull Context context, @NonNull PrivilegeModeDoctor.FixTarget target) {
        switch (target) {
            case MODE_SETTINGS:
                return SettingsActivity.getSettingsIntent(context, "mode_of_operations");
            case ROOT_MANAGER: {
                Intent rootManagerIntent = getRootManagerIntent(context);
                return rootManagerIntent != null
                        ? rootManagerIntent
                        : SettingsActivity.getSettingsIntent(context, "mode_of_operations");
            }
            case SHIZUKU_SETTINGS:
                return ShizukuBridge.getTrustedWlanAutoStartIntent(context);
            case SHIZUKU_ARCHIVE:
                return ShizukuBridge.getPinnedSafeManagerArchiveIntent();
            case DHIZUKU_SETTINGS:
                return DhizukuBridge.getSettingsIntent(context);
            case DEVELOPER_OPTIONS:
                return new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            case RESTRICTED_APP_INFO:
                return RestrictedSettingsDiagnostics.buildAppInfoIntent(context);
            case BOOTSTRAP_SMOKE_TEST:
                return SettingsActivity.getSettingsIntent(context, "privilege_health",
                        "privilege_health_bootstrap_smoke_test");
            case SUPPORT_BUNDLE:
            default:
                return null;
        }
    }

    @Nullable
    private static Intent getRootManagerIntent(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        for (String packageName : ROOT_MANAGER_PACKAGES) {
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent != null) {
                return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
        }
        return null;
    }

    private void shareModeDoctorWithSupportBundle(@NonNull String report) {
        Context context = getContext();
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        Toast.makeText(context, R.string.privilege_health_mode_doctor_share_preparing,
                Toast.LENGTH_SHORT).show();
        ThreadUtils.postOnBackgroundThread(() -> {
            Path bundle;
            try {
                bundle = SupportInfoBundle.writeTextBundle(appContext,
                        PrivilegeModeDoctor.buildSupportPreamble(report));
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
}
