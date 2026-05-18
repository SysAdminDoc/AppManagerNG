// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialSharedAxis;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.auth.AuthManagerActivity;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.session.SessionMonitoringService;
import io.github.muntashirakon.AppManager.snapshot.SnapshotBundle;
import io.github.muntashirakon.AppManager.snapshot.SnapshotImportException;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class PrivacyPreferences extends PreferenceFragment {
    private static final String MIME_ZIP = "application/zip";

    private final ActivityResultLauncher<String> mExportSnapshot = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(MIME_ZIP),
            uri -> {
                if (uri == null) return; // user cancelled
                Context appContext = requireContext().getApplicationContext();
                Toast.makeText(appContext, R.string.snapshot_export_preparing, Toast.LENGTH_SHORT).show();
                ThreadUtils.postOnBackgroundThread(() -> exportSnapshot(appContext, uri));
            });

    private final ActivityResultLauncher<String[]> mImportSnapshot = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri == null) return;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.snapshot_import_confirm_title)
                        .setMessage(R.string.snapshot_import_confirm_message)
                        .setPositiveButton(R.string.action_continue, (d, w) -> {
                            Context appContext = requireContext().getApplicationContext();
                            Toast.makeText(appContext, R.string.snapshot_import_in_progress, Toast.LENGTH_SHORT).show();
                            ThreadUtils.postOnBackgroundThread(() -> importSnapshot(appContext, uri));
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });

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
        // Operation history
        requirePreference("op_history").setOnPreferenceClickListener(preference -> {
            startActivity(OpHistoryManager.getHistoryActivityIntent(requireContext()));
            return true;
        });
        // Toggle Internet
        SwitchPreferenceCompat toggleInternet = requirePreference("toggle_internet");
        boolean optionalNetworkFeaturesAvailable = FeatureController.areOptionalNetworkFeaturesAvailable();
        toggleInternet.setEnabled(optionalNetworkFeaturesAvailable
                && SelfPermissions.checkSelfPermission(Manifest.permission.INTERNET));
        toggleInternet.setChecked(FeatureController.isInternetEnabled());
        toggleInternet.setSummary(optionalNetworkFeaturesAvailable
                ? getString(R.string.pref_toggle_internet_msg)
                : getString(R.string.pref_toggle_internet_msg_floss_disabled));
        SwitchPreferenceCompat autoUpdateDebloatDefinitions = requirePreference("debloat_definitions_auto_update");
        autoUpdateDebloatDefinitions.setChecked(optionalNetworkFeaturesAvailable
                && Prefs.Privacy.autoUpdateDebloatDefinitions());
        updateDebloatDefinitionsPreference(autoUpdateDebloatDefinitions, FeatureController.isInternetEnabled());
        toggleInternet.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean isEnabled = (boolean) newValue;
            FeatureController.getInstance().modifyState(FeatureController.FEAT_INTERNET, isEnabled);
            updateDebloatDefinitionsPreference(autoUpdateDebloatDefinitions, FeatureController.isInternetEnabled());
            return true;
        });
        // Authorization Management
        requirePreference("auth_manager").setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(requireContext(), AuthManagerActivity.class));
            return true;
        });
        // Permission change monitor (T9). Toggling ON primes the snapshot store so
        // the very next package update has a known-good baseline to diff against.
        SwitchPreferenceCompat permissionMonitor = requirePreference("permission_change_monitor");
        permissionMonitor.setChecked(Prefs.Privacy.isPermissionChangeMonitorEnabled());
        permissionMonitor.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (boolean) newValue;
            Prefs.Privacy.setPermissionChangeMonitorEnabled(enabled);
            if (enabled) {
                Context appContext = requireContext().getApplicationContext();
                ThreadUtils.postOnBackgroundThread(() ->
                        io.github.muntashirakon.AppManager.permission.monitor.PermissionChangeMonitor
                                .primeSnapshotsForAllPackages(appContext));
            }
            return true;
        });
        // Signing-cert change monitor (T9 sibling). Same toggle-then-prime pattern.
        SwitchPreferenceCompat signingCertMonitor = requirePreference("signing_cert_change_monitor");
        signingCertMonitor.setChecked(Prefs.Privacy.isSigningCertChangeMonitorEnabled());
        signingCertMonitor.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (boolean) newValue;
            Prefs.Privacy.setSigningCertChangeMonitorEnabled(enabled);
            if (enabled) {
                Context appContext = requireContext().getApplicationContext();
                ThreadUtils.postOnBackgroundThread(() ->
                        io.github.muntashirakon.AppManager.permission.monitor.SigningCertChangeMonitor
                                .primeSnapshotsForAllPackages(appContext));
            }
            return true;
        });
        // Snapshot Bundle (export / import)
        requirePreference("snapshot_export").setOnPreferenceClickListener(preference -> {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            mExportSnapshot.launch(getString(R.string.snapshot_default_filename, stamp));
            return true;
        });
        requirePreference("snapshot_import").setOnPreferenceClickListener(preference -> {
            mImportSnapshot.launch(new String[]{MIME_ZIP, "application/octet-stream", "*/*"});
            return true;
        });
    }

    private void exportSnapshot(@NonNull Context appContext, @NonNull Uri target) {
        SnapshotBundle.ExportResult result = null;
        Throwable failure = null;
        try (OutputStream out = appContext.getContentResolver().openOutputStream(target)) {
            if (out == null) {
                failure = new IOException("Cannot open output stream for " + target);
            } else {
                result = SnapshotBundle.writeTo(appContext, out);
            }
        } catch (Throwable t) {
            failure = t;
        }
        final SnapshotBundle.ExportResult finalResult = result;
        final Throwable finalFailure = failure;
        ThreadUtils.postOnMainThread(() -> {
            if (!isAdded()) return;
            if (finalFailure != null || finalResult == null) {
                Toast.makeText(requireContext(), R.string.snapshot_export_failed, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(requireContext(),
                    getString(R.string.snapshot_export_done,
                            finalResult.prefsCount,
                            finalResult.profilesCount,
                            finalResult.opHistoryCount),
                    Toast.LENGTH_LONG).show();
        });
    }

    private void importSnapshot(@NonNull Context appContext, @NonNull Uri source) {
        SnapshotBundle.ImportResult result = null;
        String failureMessage = null;
        try (InputStream in = appContext.getContentResolver().openInputStream(source)) {
            if (in == null) {
                failureMessage = "Cannot open input stream";
            } else {
                result = SnapshotBundle.readFrom(appContext, in, new SnapshotBundle.ImportOptions());
            }
        } catch (SnapshotImportException e) {
            failureMessage = e.getMessage();
        } catch (Throwable t) {
            failureMessage = t.getClass().getSimpleName()
                    + (t.getMessage() != null ? ": " + t.getMessage() : "");
        }
        final SnapshotBundle.ImportResult finalResult = result;
        final String finalFailure = failureMessage;
        ThreadUtils.postOnMainThread(() -> {
            if (!isAdded()) return;
            if (finalResult == null) {
                Toast.makeText(requireContext(),
                        getString(R.string.snapshot_import_failed,
                                finalFailure != null ? finalFailure : ""),
                        Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(requireContext(),
                    getString(R.string.snapshot_import_done,
                            finalResult.prefsRestored,
                            finalResult.profilesRestored,
                            finalResult.opHistoryRestored),
                    Toast.LENGTH_LONG).show();
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

    private void updateDebloatDefinitionsPreference(@NonNull SwitchPreferenceCompat preference, boolean internetEnabled) {
        if (!FeatureController.areOptionalNetworkFeaturesAvailable()) {
            preference.setEnabled(false);
            preference.setSummary(R.string.pref_auto_update_debloat_definitions_msg_floss_disabled);
            return;
        }
        preference.setEnabled(internetEnabled);
        if (!internetEnabled) {
            preference.setSummary(R.string.pref_auto_update_debloat_definitions_msg_no_internet);
            return;
        }
        String version = Prefs.Privacy.getDebloatDefinitionsVersion();
        if (TextUtils.isEmpty(version)) {
            preference.setSummary(R.string.pref_auto_update_debloat_definitions_msg);
        } else {
            preference.setSummary(getString(R.string.pref_auto_update_debloat_definitions_msg_current, version));
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
