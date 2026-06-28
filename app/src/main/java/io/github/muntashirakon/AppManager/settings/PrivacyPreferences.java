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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.permission.monitor.AppChangeFeedEntry;
import io.github.muntashirakon.AppManager.permission.monitor.AppChangeFeedStore;
import io.github.muntashirakon.AppManager.utils.MotionUtils;
import io.github.muntashirakon.AppManager.crypto.auth.ActionAuthGate;
import io.github.muntashirakon.AppManager.crypto.auth.AuthManagerActivity;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryPruneScheduler;
import io.github.muntashirakon.AppManager.scanner.TrackerDatabaseFreshnessChecker;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.session.SessionMonitoringService;
import io.github.muntashirakon.AppManager.snapshot.SnapshotBundle;
import io.github.muntashirakon.AppManager.snapshot.SnapshotImportException;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

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
                Context appContext = requireContext().getApplicationContext();
                ThreadUtils.postOnBackgroundThread(() -> {
                    try (InputStream in = appContext.getContentResolver().openInputStream(uri)) {
                        if (in == null) {
                            ThreadUtils.postOnMainThread(() ->
                                    UIUtils.displayLongToast(R.string.snapshot_import_failed, "Cannot open file"));
                            return;
                        }
                        SnapshotBundle.ManifestSummary manifest = SnapshotBundle.readManifestOnly(in);
                        ThreadUtils.postOnMainThread(() -> showImportPreview(manifest, uri));
                    } catch (Exception e) {
                        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        ThreadUtils.postOnMainThread(() ->
                                UIUtils.displayLongToast(R.string.snapshot_import_failed, msg));
                    }
                });
            });

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_privacy, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        bindTrackerBlockingIntensity();
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
        // Per-action authentication gate
        SwitchPreferenceCompat actionAuthGate = requirePreference("enable_action_auth_gate");
        actionAuthGate.setChecked(Prefs.Privacy.isActionAuthGateEnabled());
        actionAuthGate.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (boolean) newValue;
            if (enabled && !ActionAuthGate.canAuthenticate(requireContext())) {
                Toast.makeText(requireContext(), R.string.screen_lock_not_enabled, Toast.LENGTH_LONG).show();
                return false;
            }
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
                        OpHistoryPruneScheduler.scheduleOrCancel(requireContext(), values[which]);
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
        SwitchPreferenceCompat trackerDatabaseFreshness = requirePreference("tracker_database_freshness_check");
        trackerDatabaseFreshness.setChecked(optionalNetworkFeaturesAvailable
                && Prefs.Privacy.checkTrackerDatabaseFreshness());
        updateTrackerDatabaseFreshnessPreference(trackerDatabaseFreshness, FeatureController.isInternetEnabled());
        toggleInternet.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean isEnabled = (boolean) newValue;
            FeatureController.getInstance().modifyState(FeatureController.FEAT_INTERNET, isEnabled);
            updateDebloatDefinitionsPreference(autoUpdateDebloatDefinitions, FeatureController.isInternetEnabled());
            updateTrackerDatabaseFreshnessPreference(trackerDatabaseFreshness, FeatureController.isInternetEnabled());
            return true;
        });
        trackerDatabaseFreshness.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (boolean) newValue;
            Prefs.Privacy.setCheckTrackerDatabaseFreshness(enabled);
            if (enabled) {
                TrackerDatabaseFreshnessChecker.scheduleCheckIfAllowed(requireContext());
            }
            updateTrackerDatabaseFreshnessPreference(trackerDatabaseFreshness, FeatureController.isInternetEnabled());
            return true;
        });
        // Network transparency ledger
        Preference networkLedger = requirePreference("network_transparency_ledger");
        networkLedger.setVisible(optionalNetworkFeaturesAvailable);
        networkLedger.setOnPreferenceClickListener(preference -> {
            List<NetworkTransparencyLedger.Entry> entries = NetworkTransparencyLedger.buildEntries();
            String text = NetworkTransparencyLedger.formatForDisplay(requireContext(), entries);
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.pref_network_transparency)
                    .setMessage(text)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return true;
        });
        // Authorization Management
        requirePreference("auth_manager").setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(requireContext(), AuthManagerActivity.class));
            return true;
        });
        // Permission change monitor (T9). Toggling ON primes the snapshot store so
        // the very next package update has a known-good baseline to diff against.
        SwitchPreferenceCompat permissionMonitor = requirePreference("enable_permission_change_monitor");
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
        SwitchPreferenceCompat signingCertMonitor = requirePreference("enable_signing_cert_change_monitor");
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
        // App Change Auditor: component + tracker deltas, sharing the same feed
        // as permission and signing-cert monitors.
        requirePreference("app_change_feed").setOnPreferenceClickListener(preference -> {
            showAppChangeFeed();
            return true;
        });
        SwitchPreferenceCompat appChangeAuditor = requirePreference("enable_app_change_auditor");
        appChangeAuditor.setChecked(Prefs.Privacy.isAppChangeAuditorEnabled());
        appChangeAuditor.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (boolean) newValue;
            Prefs.Privacy.setAppChangeAuditorEnabled(enabled);
            if (enabled) {
                Context appContext = requireContext().getApplicationContext();
                ThreadUtils.postOnBackgroundThread(() ->
                        io.github.muntashirakon.AppManager.permission.monitor.ComponentChangeMonitor
                                .primeSnapshotsForAllPackages(appContext));
            }
            return true;
        });
        // Local crash sink
        SwitchPreferenceCompat localCrashSink = requirePreference("local_crash_sink_enabled");
        localCrashSink.setChecked(Prefs.Privacy.isLocalCrashSinkEnabled());
        localCrashSink.setOnPreferenceChangeListener((preference, newValue) -> {
            Prefs.Privacy.setLocalCrashSinkEnabled((boolean) newValue);
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

    private void showAppChangeFeed() {
        Context appContext = requireContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            List<AppChangeFeedEntry> entries = new AppChangeFeedStore(appContext).readAll();
            String message = formatAppChangeFeed(entries);
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded()) return;
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.app_change_feed_title)
                        .setMessage(message)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            });
        });
    }

    @NonNull
    private String formatAppChangeFeed(@NonNull List<AppChangeFeedEntry> entries) {
        if (entries.isEmpty()) {
            return getString(R.string.app_change_feed_empty);
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder builder = new StringBuilder();
        int count = Math.min(entries.size(), 20);
        for (int i = 0; i < count; ++i) {
            AppChangeFeedEntry entry = entries.get(i);
            if (builder.length() > 0) builder.append("\n\n");
            builder.append(entry.title)
                    .append("\n")
                    .append(entry.packageName)
                    .append(" - ")
                    .append(format.format(new Date(entry.timestampMillis)))
                    .append("\n")
                    .append(entry.body);
        }
        int hidden = entries.size() - count;
        if (hidden > 0) {
            builder.append("\n\n").append(getResources().getQuantityString(
                    R.plurals.app_change_feed_more, hidden, hidden));
        }
        return builder.toString();
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
        } catch (Exception t) {
            failure = t;
        }
        final SnapshotBundle.ExportResult finalResult = result;
        final Throwable finalFailure = failure;
        // Use the app-context toast helpers (not requireContext) and don't gate on isAdded():
        // the result arrives after a SAF round-trip, so the user may have navigated away — the
        // success/failure feedback (especially failure) must not be silently dropped.
        ThreadUtils.postOnMainThread(() -> {
            if (finalFailure != null || finalResult == null) {
                UIUtils.displayLongToast(R.string.snapshot_export_failed);
                return;
            }
            UIUtils.displayLongToast(R.string.snapshot_export_done,
                    finalResult.prefsCount,
                    finalResult.profilesCount,
                    finalResult.rulesCount,
                    finalResult.opHistoryCount);
        });
    }

    private void showImportPreview(@NonNull SnapshotBundle.ManifestSummary manifest,
                                   @NonNull Uri source) {
        Context context = requireContext();
        String[] sectionLabels = {
                getString(R.string.snapshot_section_prefs, manifest.prefsCount),
                getString(R.string.snapshot_section_profiles, manifest.profilesCount),
                getString(R.string.snapshot_section_rules, manifest.rulesCount),
                getString(R.string.snapshot_section_tags),
                getString(R.string.snapshot_section_op_history, manifest.opHistoryCount)
        };
        boolean[] available = {
                manifest.hasPrefs(), manifest.hasProfiles(), manifest.hasRules(),
                manifest.hasTags(), manifest.hasOpHistory()
        };
        boolean[] checked = available.clone();

        StringBuilder summary = new StringBuilder();
        if (manifest.sourceVersionName != null) {
            summary.append(getString(R.string.snapshot_preview_source,
                    manifest.sourceVersionName, manifest.sourceVersionCode));
        }
        if (manifest.generatedAt > 0) {
            if (summary.length() > 0) summary.append('\n');
            summary.append(getString(R.string.snapshot_preview_date,
                    new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .format(new Date(manifest.generatedAt))));
        }
        summary.append('\n');
        summary.append(getString(R.string.snapshot_preview_schema, manifest.schemaVersion));

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.snapshot_import_preview_title)
                .setMessage(summary)
                .setMultiChoiceItems(sectionLabels, checked, (dialog, which, isChecked) -> {
                    if (!available[which]) {
                        checked[which] = false;
                        ((android.app.AlertDialog) dialog).getListView()
                                .setItemChecked(which, false);
                    } else {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton(R.string.action_import, (d, w) -> {
                    SnapshotBundle.ImportOptions options = new SnapshotBundle.ImportOptions();
                    options.restorePrefs = checked[0];
                    options.restoreProfiles = checked[1];
                    options.restoreRules = checked[2];
                    options.restoreTags = checked[3];
                    options.restoreOpHistory = checked[4];
                    Context appContext = context.getApplicationContext();
                    Toast.makeText(appContext, R.string.snapshot_import_in_progress,
                            Toast.LENGTH_SHORT).show();
                    ThreadUtils.postOnBackgroundThread(
                            () -> importSnapshot(appContext, source, options));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void importSnapshot(@NonNull Context appContext, @NonNull Uri source,
                                @NonNull SnapshotBundle.ImportOptions options) {
        SnapshotBundle.ImportResult result = null;
        String failureMessage = null;
        try (InputStream in = appContext.getContentResolver().openInputStream(source)) {
            if (in == null) {
                failureMessage = "Cannot open input stream";
            } else {
                result = SnapshotBundle.readFrom(appContext, in, options);
            }
        } catch (SnapshotImportException e) {
            failureMessage = e.getMessage();
        } catch (Exception t) {
            failureMessage = t.getClass().getSimpleName()
                    + (t.getMessage() != null ? ": " + t.getMessage() : "");
        }
        final SnapshotBundle.ImportResult finalResult = result;
        final String finalFailure = failureMessage;
        ThreadUtils.postOnMainThread(() -> {
            if (finalResult == null) {
                UIUtils.displayLongToast(R.string.snapshot_import_failed,
                        finalFailure != null ? finalFailure : "");
                return;
            }
            UIUtils.displayLongToast(R.string.snapshot_import_done,
                    finalResult.prefsRestored,
                    finalResult.profilesRestored,
                    finalResult.rulesRestored,
                    finalResult.opHistoryRestored);
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MotionUtils.applySharedAxisZTransition(this);
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

    private void updateTrackerDatabaseFreshnessPreference(@NonNull SwitchPreferenceCompat preference,
                                                          boolean internetEnabled) {
        if (!FeatureController.areOptionalNetworkFeaturesAvailable()) {
            preference.setEnabled(false);
            preference.setSummary(R.string.pref_check_tracker_database_msg_floss_disabled);
            return;
        }
        preference.setEnabled(internetEnabled);
        if (!internetEnabled) {
            preference.setSummary(R.string.pref_check_tracker_database_msg_no_internet);
            return;
        }
        String latestVersion = Prefs.Privacy.getLatestTrackerDatabaseVersion();
        if (TextUtils.isEmpty(latestVersion)) {
            preference.setSummary(R.string.pref_check_tracker_database_msg);
        } else {
            preference.setSummary(getString(R.string.pref_check_tracker_database_msg_current,
                    StaticDataset.getTrackerDatabaseVersion(), latestVersion));
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

    /**
     * NF-07 — wire the tracker-blocking intensity picker. The Preference itself
     * is a plain entry (no per-row checkbox); tapping it opens a single-choice
     * dialog with one row per {@link io.github.muntashirakon.AppManager.rules.compontents.TrackerBlockingIntensity}.
     * The current choice is also shown as the row's live summary so users can
     * see what is active without opening the dialog.
     */
    private void bindTrackerBlockingIntensity() {
        androidx.preference.Preference pref = findPreference("tracker_blocking_intensity");
        if (pref == null) return;
        applyTrackerBlockingIntensitySummary(pref);
        pref.setOnPreferenceClickListener(p -> {
            io.github.muntashirakon.AppManager.rules.compontents.TrackerBlockingIntensity[] options =
                    io.github.muntashirakon.AppManager.rules.compontents.TrackerBlockingIntensity.values();
            CharSequence[] labels = new CharSequence[options.length];
            for (int i = 0; i < options.length; ++i) {
                labels[i] = getString(options[i].getLabelRes());
            }
            int current = Prefs.Privacy.getTrackerBlockingIntensity().ordinal();
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.tracker_blocking_intensity_title)
                    .setSingleChoiceItems(labels, current, (dialog, which) -> {
                        Prefs.Privacy.setTrackerBlockingIntensity(options[which]);
                        applyTrackerBlockingIntensitySummary(pref);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
    }

    private void applyTrackerBlockingIntensitySummary(@NonNull androidx.preference.Preference pref) {
        io.github.muntashirakon.AppManager.rules.compontents.TrackerBlockingIntensity intensity =
                Prefs.Privacy.getTrackerBlockingIntensity();
        pref.setSummary(getString(R.string.tracker_blocking_intensity_summary)
                + "\n" + getString(intensity.getLabelRes())
                + " — " + getString(intensity.getSummaryRes()));
    }

}
