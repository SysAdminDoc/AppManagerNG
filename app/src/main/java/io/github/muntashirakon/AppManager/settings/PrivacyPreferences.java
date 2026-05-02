// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialSharedAxis;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.auth.AuthManagerActivity;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.session.SessionMonitoringService;

public class PrivacyPreferences extends PreferenceFragment {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_privacy, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        boolean isScreenLockEnabled = Prefs.Privacy.isScreenLockEnabled();
        boolean isPersistentSessionEnabled = Prefs.Privacy.isPersistentSessionAllowed();
        // Auto lock
        SwitchPreferenceCompat autoLock = requirePreference("enable_auto_lock");
        autoLock.setVisible(isScreenLockEnabled && isPersistentSessionEnabled);
        autoLock.setChecked(Prefs.Privacy.isAutoLockEnabled());
        autoLock.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (boolean) newValue;
            restartServiceIfNeeded(null, enabled, null);
            return true;
        });
        // Screen lock
        SwitchPreferenceCompat screenLock = requirePreference("enable_screen_lock");
        screenLock.setChecked(isScreenLockEnabled);
        screenLock.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (boolean) newValue;
            // Auto lock pref has to be updated depending on this
            if (enabled) {
                autoLock.setVisible(Prefs.Privacy.isPersistentSessionAllowed());
            } else autoLock.setVisible(false);
            restartServiceIfNeeded(enabled, null, null);
            return true;
        });
        // Persistent session
        SwitchPreferenceCompat persistentSession = requirePreference("enable_persistent_session");
        persistentSession.setChecked(isPersistentSessionEnabled);
        persistentSession.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (boolean) newValue;
            // Auto lock pref has to be updated depending on this
            if (enabled) {
                autoLock.setVisible(Prefs.Privacy.isScreenLockEnabled());
            } else autoLock.setVisible(false);
            restartServiceIfNeeded(null, null, enabled);
            return true;
        });
        // Operation history retention
        Preference opHistoryRetention = requirePreference("op_history_retention_days");
        updateOpHistoryRetentionSummary(opHistoryRetention);
        opHistoryRetention.setOnPreferenceClickListener(preference -> {
            int[] values = {0, 7, 30, 90, 180, 365};
            CharSequence[] labels = {
                    getString(R.string.op_history_retention_never),
                    getString(R.string.op_history_retention_7d),
                    getString(R.string.op_history_retention_30d),
                    getString(R.string.op_history_retention_90d),
                    getString(R.string.op_history_retention_180d),
                    getString(R.string.op_history_retention_365d)
            };
            int currentValue = Prefs.Privacy.getOpHistoryRetentionDays();
            int checkedItem = 0;
            for (int i = 0; i < values.length; ++i) {
                if (values[i] == currentValue) {
                    checkedItem = i;
                    break;
                }
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.op_history_retention_title)
                    .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> {
                        Prefs.Privacy.setOpHistoryRetentionDays(values[which]);
                        updateOpHistoryRetentionSummary(opHistoryRetention);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Toggle Internet
        SwitchPreferenceCompat toggleInternet = requirePreference("toggle_internet");
        toggleInternet.setEnabled(SelfPermissions.checkSelfPermission(Manifest.permission.INTERNET));
        toggleInternet.setChecked(FeatureController.isInternetEnabled());
        toggleInternet.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean isEnabled = (boolean) newValue;
            FeatureController.getInstance().modifyState(FeatureController.FEAT_INTERNET, isEnabled);
            return true;
        });
        // Authorization Management
        requirePreference("auth_manager").setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(requireContext(), AuthManagerActivity.class));
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
    public int getTitle() {
        return R.string.pref_privacy;
    }

    private void updateOpHistoryRetentionSummary(@NonNull Preference preference) {
        int retentionDays = Prefs.Privacy.getOpHistoryRetentionDays();
        CharSequence summary = retentionDays > 0
                ? getString(R.string.op_history_retention_summary, getRetentionLabel(retentionDays))
                : getString(R.string.op_history_retention_never);
        preference.setSummary(summary);
    }

    @NonNull
    private String getRetentionLabel(int retentionDays) {
        switch (retentionDays) {
            case 7:
                return getString(R.string.op_history_retention_7d);
            case 30:
                return getString(R.string.op_history_retention_30d);
            case 90:
                return getString(R.string.op_history_retention_90d);
            case 180:
                return getString(R.string.op_history_retention_180d);
            case 365:
                return getString(R.string.op_history_retention_365d);
            default:
                return getString(R.string.op_history_retention_never);
        }
    }

    public void restartServiceIfNeeded(@Nullable Boolean screenLockEnabled, @Nullable Boolean autoLockEnabled,
                                       @Nullable Boolean persistentSessionEnabled) {
        if (screenLockEnabled == null && autoLockEnabled == null && persistentSessionEnabled == null) {
            // Nothing is set
            return;
        }
        Intent service = new Intent(requireContext(), SessionMonitoringService.class);
        if (Boolean.FALSE.equals(persistentSessionEnabled)) {
            // Stop background session
            requireContext().stopService(service);
            return;
        }
        if (Boolean.TRUE.equals(persistentSessionEnabled)) {
            // Start background session
            ContextCompat.startForegroundService(requireContext(), service);
            return;
        }
        persistentSessionEnabled = Prefs.Privacy.isPersistentSessionAllowed();
        if (!persistentSessionEnabled) {
            // Session not enabled and not running
            return;
        }
        // Session enabled
        if (autoLockEnabled != null || screenLockEnabled != null) {
            // Auto lock preference has changed, restart service
            requireContext().stopService(service);
            ContextCompat.startForegroundService(requireContext(), service);
        }
    }

}
