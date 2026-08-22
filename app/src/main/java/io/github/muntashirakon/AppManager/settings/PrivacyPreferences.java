// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.AppUpdateChangeReport;
import io.github.muntashirakon.AppManager.permission.monitor.AppChangeFeedEntry;
import io.github.muntashirakon.AppManager.permission.monitor.AppChangeFeedStore;
import io.github.muntashirakon.AppManager.permission.monitor.AppChangeFeedTransfer;
import io.github.muntashirakon.AppManager.permission.monitor.AppUpdateChangeReportFormatter;
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
    private static final String MIME_JSON = "application/json";

    @NonNull
    private List<AppChangeFeedEntry> mPendingAppChangeFeedExport = Collections.emptyList();

    private final ActivityResultLauncher<String> mExportSnapshot = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(MIME_ZIP),
            uri -> {
                if (uri == null) return; // user cancelled
                showExportPassphraseDialog(uri);
            });

    private final ActivityResultLauncher<String[]> mImportSnapshot = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri == null) return;
                beginImportPreview(uri, null);
            });

    private final ActivityResultLauncher<String> mExportAppChangeFeed = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(MIME_JSON),
            uri -> {
                if (uri == null) return;
                beginExportAppChangeFeed(uri, mPendingAppChangeFeedExport);
            });

    private final ActivityResultLauncher<String[]> mImportAppChangeFeed = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri == null) return;
                beginImportAppChangeFeed(uri);
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
        SwitchPreferenceCompat appUpdateReport = requirePreference("enable_app_update_change_report");
        appUpdateReport.setChecked(Prefs.Privacy.isAppUpdateChangeReportEnabled());
        appUpdateReport.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (boolean) newValue;
            Prefs.Privacy.setAppUpdateChangeReportEnabled(enabled);
            if (enabled) {
                Context appContext = requireContext().getApplicationContext();
                ThreadUtils.postOnBackgroundThread(() -> {
                    io.github.muntashirakon.AppManager.permission.monitor.PermissionChangeMonitor
                            .primeSnapshotsForAllPackages(appContext);
                    io.github.muntashirakon.AppManager.permission.monitor.ComponentChangeMonitor
                            .primeSnapshotsForAllPackages(appContext);
                });
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
            List<AppChangeFeedEntry> entries = new ArrayList<>(
                    new AppChangeFeedStore(appContext).readAll());
            List<AppUpdateChangeReport> reports;
            try {
                reports = AppsDb.getInstance().appUpdateChangeReportDao().getRecent(200);
            } catch (Exception e) {
                reports = java.util.Collections.emptyList();
                io.github.muntashirakon.AppManager.logs.Log.w(
                        "PrivacyPreferences", "Could not read app update reports", e);
            }
            entries = mergeAppChangeFeed(appContext, entries, reports);
            List<AppChangeFeedEntry> finalEntries = Collections.unmodifiableList(entries);
            ThreadUtils.postOnMainThread(() -> {
                if (!isAdded()) return;
                showAppChangeFeedDialog(appContext, finalEntries);
            });
        });
    }

    @NonNull
    private List<AppChangeFeedEntry> mergeAppChangeFeed(@NonNull Context context,
                                                         @NonNull List<AppChangeFeedEntry> entries,
                                                         @NonNull List<AppUpdateChangeReport> reports) {
        for (AppUpdateChangeReport report : reports) {
            if (TextUtils.isEmpty(report.packageName)) continue;
            entries.add(new AppChangeFeedEntry(
                    "update",
                    report.packageName,
                    report.timestampMillis,
                    context.getString(R.string.app_update_change_report_title, report.packageName),
                    AppUpdateChangeReportFormatter.formatBody(context, report),
                    report.beforeVersionCode,
                    report.afterVersionCode));
        }
        entries.sort(Comparator.comparingLong((AppChangeFeedEntry entry) -> entry.timestampMillis)
                .reversed());
        if (entries.size() > AppChangeFeedTransfer.MAX_ENTRIES) {
            return new ArrayList<>(entries.subList(0, AppChangeFeedTransfer.MAX_ENTRIES));
        }
        return entries;
    }

    private void showAppChangeFeedDialog(@NonNull Context context,
                                         @NonNull List<AppChangeFeedEntry> entries) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 20);
        root.setPadding(padding, dp(context, 4), padding, 0);

        TextInputLayout packageLayout = buildAppChangeFeedInput(context,
                R.string.app_change_feed_filter_package);
        TextInputLayout kindLayout = buildAppChangeFeedInput(context,
                R.string.app_change_feed_filter_kind);
        TextInputEditText packageEdit = (TextInputEditText) packageLayout.getEditText();
        TextInputEditText kindEdit = (TextInputEditText) kindLayout.getEditText();

        LinearLayout dateRow = new LinearLayout(context);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        TextInputLayout fromLayout = buildAppChangeFeedInput(context,
                R.string.app_change_feed_filter_from);
        TextInputLayout untilLayout = buildAppChangeFeedInput(context,
                R.string.app_change_feed_filter_until);
        TextInputEditText fromEdit = (TextInputEditText) fromLayout.getEditText();
        TextInputEditText untilEdit = (TextInputEditText) untilLayout.getEditText();
        fromEdit.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        untilEdit.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dateParams.setMarginEnd(dp(context, 6));
        dateRow.addView(fromLayout, dateParams);
        LinearLayout.LayoutParams untilParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dateRow.addView(untilLayout, untilParams);

        TextView countView = new TextView(context);
        countView.setTextSize(12);
        countView.setPadding(0, dp(context, 4), 0, dp(context, 4));

        TextView resultView = new TextView(context);
        resultView.setTextIsSelectable(true);
        resultView.setTextSize(14);
        resultView.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        resultView.setPadding(0, dp(context, 4), 0, dp(context, 12));
        ScrollView resultScroll = new ScrollView(context);
        resultScroll.setFillViewport(true);
        resultScroll.setMinimumHeight(dp(context, 220));
        resultScroll.addView(resultView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton export = new MaterialButton(context);
        export.setText(R.string.app_change_feed_export);
        MaterialButton importButton = new MaterialButton(context);
        importButton.setText(R.string.app_change_feed_import);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        actionParams.setMarginEnd(dp(context, 6));
        actions.addView(export, actionParams);
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        actions.addView(importButton, importParams);

        root.addView(packageLayout);
        root.addView(kindLayout);
        root.addView(dateRow);
        root.addView(countView);
        root.addView(resultScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(actions);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshAppChangeFeed(context, entries, packageEdit, kindEdit, fromEdit, untilEdit,
                        fromLayout, untilLayout, countView, resultView);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        packageEdit.addTextChangedListener(watcher);
        kindEdit.addTextChangedListener(watcher);
        fromEdit.addTextChangedListener(watcher);
        untilEdit.addTextChangedListener(watcher);
        export.setOnClickListener(v -> {
            mPendingAppChangeFeedExport = new ArrayList<>(entries);
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            mExportAppChangeFeed.launch(getString(R.string.app_change_feed_default_filename, stamp));
        });
        importButton.setOnClickListener(v ->
                mImportAppChangeFeed.launch(new String[]{MIME_JSON, "text/json", "*/*"}));

        refreshAppChangeFeed(context, entries, packageEdit, kindEdit, fromEdit, untilEdit,
                fromLayout, untilLayout, countView, resultView);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.app_change_feed_title)
                .setView(root)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void refreshAppChangeFeed(@NonNull Context context,
                                      @NonNull List<AppChangeFeedEntry> entries,
                                      @NonNull TextInputEditText packageEdit,
                                      @NonNull TextInputEditText kindEdit,
                                      @NonNull TextInputEditText fromEdit,
                                      @NonNull TextInputEditText untilEdit,
                                      @NonNull TextInputLayout fromLayout,
                                      @NonNull TextInputLayout untilLayout,
                                      @NonNull TextView countView,
                                      @NonNull TextView resultView) {
        fromLayout.setError(null);
        untilLayout.setError(null);
        long fromMillis = Long.MIN_VALUE;
        long untilMillis = Long.MAX_VALUE;
        try {
            String value = fromEdit.getText() != null ? fromEdit.getText().toString().trim() : "";
            if (!value.isEmpty()) fromMillis = parseFeedDate(value, false);
        } catch (ParseException e) {
            fromLayout.setError(context.getString(R.string.app_change_feed_invalid_date));
        }
        try {
            String value = untilEdit.getText() != null ? untilEdit.getText().toString().trim() : "";
            if (!value.isEmpty()) untilMillis = parseFeedDate(value, true);
        } catch (ParseException e) {
            untilLayout.setError(context.getString(R.string.app_change_feed_invalid_date));
        }
        if (fromLayout.getError() != null || untilLayout.getError() != null) {
            countView.setText(R.string.app_change_feed_no_matches);
            resultView.setText(R.string.app_change_feed_no_matches);
            return;
        }
        List<AppChangeFeedEntry> filtered = AppChangeFeedTransfer.filter(entries,
                packageEdit.getText() != null ? packageEdit.getText().toString() : null,
                kindEdit.getText() != null ? kindEdit.getText().toString() : null,
                fromMillis, untilMillis);
        countView.setText(context.getResources().getQuantityString(
                R.plurals.app_change_feed_count, filtered.size(), filtered.size()));
        resultView.setText(filtered.isEmpty()
                ? context.getString(R.string.app_change_feed_no_matches)
                : formatAppChangeFeed(context, filtered));
    }

    @NonNull
    private String formatAppChangeFeed(@NonNull Context context,
                                       @NonNull List<AppChangeFeedEntry> entries) {
        if (entries.isEmpty()) return context.getString(R.string.app_change_feed_empty);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder builder = new StringBuilder();
        for (AppChangeFeedEntry entry : entries) {
            if (builder.length() > 0) builder.append("\n\n");
            builder.append(entry.title).append("\n")
                    .append(context.getString(R.string.app_change_feed_entry_meta,
                            entry.packageName, entry.kind, format.format(new Date(entry.timestampMillis))));
            if (entry.hasVersionContext()) {
                builder.append("\n").append(context.getString(R.string.app_change_feed_entry_version,
                        entry.beforeVersionCode, entry.afterVersionCode));
            }
            if (!entry.body.isEmpty()) builder.append("\n").append(entry.body);
        }
        return builder.toString();
    }

    @NonNull
    private TextInputLayout buildAppChangeFeedInput(@NonNull Context context, int hintRes) {
        TextInputLayout layout = new TextInputLayout(context);
        layout.setHint(hintRes);
        TextInputEditText edit = new TextInputEditText(layout.getContext());
        edit.setSingleLine(true);
        layout.addView(edit);
        return layout;
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static long parseFeedDate(@NonNull String value, boolean endOfDay)
            throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        Date date = format.parse(value);
        if (date == null || !value.equals(format.format(date))) throw new ParseException(value, 0);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        if (endOfDay) {
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MILLISECOND, 999);
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
        }
        return calendar.getTimeInMillis();
    }

    private void beginExportAppChangeFeed(@NonNull Uri target,
                                           @NonNull List<AppChangeFeedEntry> entries) {
        Context appContext = requireContext().getApplicationContext();
        List<AppChangeFeedEntry> copy = new ArrayList<>(entries);
        ThreadUtils.postOnBackgroundThread(() -> {
            Throwable failure = null;
            try (OutputStream out = appContext.getContentResolver().openOutputStream(target)) {
                if (out == null) throw new IOException("Cannot open output stream");
                out.write(AppChangeFeedTransfer.serialize(copy).getBytes(StandardCharsets.UTF_8));
            } catch (Throwable t) {
                failure = t;
            }
            final boolean success = failure == null;
            ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(success
                    ? R.string.app_change_feed_export_done
                    : R.string.app_change_feed_export_failed));
        });
    }

    private void beginImportAppChangeFeed(@NonNull Uri source) {
        Context appContext = requireContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            try (InputStream in = appContext.getContentResolver().openInputStream(source)) {
                if (in == null) throw new IOException("Cannot open input stream");
                AppChangeFeedTransfer.ParseResult result = AppChangeFeedTransfer.parse(
                        readBoundedUtf8(in, AppChangeFeedTransfer.MAX_IMPORT_BYTES));
                if (!result.isValid()) {
                    String error = result.error != null ? result.error : "Invalid file";
                    ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(
                            R.string.app_change_feed_import_failed, error));
                    return;
                }
                int imported = new AppChangeFeedStore(appContext).importEntries(result.entries);
                ThreadUtils.postOnMainThread(() -> {
                    if (imported == 0) {
                        UIUtils.displayLongToast(R.string.app_change_feed_import_none);
                    } else {
                        UIUtils.displayLongToastPl(R.plurals.app_change_feed_import_done,
                                imported, imported);
                    }
                });
            } catch (Exception e) {
                String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(
                        R.string.app_change_feed_import_failed, error));
            }
        });
    }

    @NonNull
    private static String readBoundedUtf8(@NonNull InputStream input, long maxBytes)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IOException("The file is too large");
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private void exportSnapshot(@NonNull Context appContext, @NonNull Uri target,
                                @Nullable char[] passphrase) {
        SnapshotBundle.ExportResult result = null;
        Throwable failure = null;
        try (OutputStream out = appContext.getContentResolver().openOutputStream(target)) {
            if (out == null) {
                failure = new IOException("Cannot open output stream for " + target);
            } else if (passphrase != null && passphrase.length > 0) {
                result = SnapshotBundle.writeEncryptedTo(appContext, out, passphrase);
            } else {
                result = SnapshotBundle.writeTo(appContext, out);
            }
        } catch (Exception t) {
            failure = t;
        } finally {
            if (passphrase != null) java.util.Arrays.fill(passphrase, '\0');
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
                    finalResult.opHistoryCount,
                    finalResult.logFiltersCount,
                    finalResult.fmFavoritesCount,
                    finalResult.freezeTypesCount);
        });
    }

    private void showImportPreview(@NonNull SnapshotBundle.ManifestSummary manifest,
                                   @NonNull Uri source, @Nullable char[] passphrase) {
        Context context = requireContext();
        String[] sectionLabels = {
                getString(R.string.snapshot_section_prefs, manifest.prefsCount),
                getString(R.string.snapshot_section_profiles, manifest.profilesCount),
                getString(R.string.snapshot_section_rules, manifest.rulesCount),
                getString(R.string.snapshot_section_tags),
                getString(R.string.snapshot_section_op_history, manifest.opHistoryCount),
                getString(R.string.snapshot_section_log_filters, manifest.logFiltersCount),
                getString(R.string.snapshot_section_fm_favorites, manifest.fmFavoritesCount),
                getString(R.string.snapshot_section_freeze_types, manifest.freezeTypesCount)
        };
        boolean[] available = {
                manifest.hasPrefs(), manifest.hasProfiles(), manifest.hasRules(),
                manifest.hasTags(), manifest.hasOpHistory(),
                manifest.hasLogFilters(), manifest.hasFmFavorites(), manifest.hasFreezeTypes()
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
                    options.restoreLogFilters = checked[5];
                    options.restoreFmFavorites = checked[6];
                    options.restoreFreezeTypes = checked[7];
                    Context appContext = context.getApplicationContext();
                    Toast.makeText(appContext, R.string.snapshot_import_in_progress,
                            Toast.LENGTH_SHORT).show();
                    ThreadUtils.postOnBackgroundThread(
                            () -> importSnapshot(appContext, source, options, passphrase));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void importSnapshot(@NonNull Context appContext, @NonNull Uri source,
                                @NonNull SnapshotBundle.ImportOptions options,
                                @Nullable char[] passphrase) {
        SnapshotBundle.ImportResult result = null;
        String failureMessage = null;
        try (InputStream in = appContext.getContentResolver().openInputStream(source)) {
            if (in == null) {
                failureMessage = "Cannot open input stream";
            } else {
                result = SnapshotBundle.readFrom(appContext, in, options, passphrase);
            }
        } catch (SnapshotImportException e) {
            failureMessage = e.getMessage();
        } catch (Exception t) {
            failureMessage = t.getClass().getSimpleName()
                    + (t.getMessage() != null ? ": " + t.getMessage() : "");
        } finally {
            if (passphrase != null) java.util.Arrays.fill(passphrase, '\0');
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
                    finalResult.opHistoryRestored,
                    finalResult.logFiltersRestored,
                    finalResult.fmFavoritesRestored,
                    finalResult.freezeTypesRestored);
        });
    }

    // Offer optional passphrase encryption before writing the snapshot to the chosen file.
    private void showExportPassphraseDialog(@NonNull Uri target) {
        Context context = requireContext();
        TextInputLayout input = buildPassphraseInput(context);
        TextInputEditText edit = (TextInputEditText) input.getEditText();
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.snapshot_encrypt_title)
                .setMessage(R.string.snapshot_encrypt_message)
                .setView(input)
                .setPositiveButton(R.string.snapshot_encrypt_action, (d, w) ->
                        dispatchExport(context, target, extractPassphrase(edit)))
                .setNeutralButton(R.string.snapshot_export_plaintext, (d, w) ->
                        dispatchExport(context, target, null))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void dispatchExport(@NonNull Context context, @NonNull Uri target,
                                @Nullable char[] passphrase) {
        Context appContext = context.getApplicationContext();
        Toast.makeText(appContext, R.string.snapshot_export_preparing, Toast.LENGTH_SHORT).show();
        ThreadUtils.postOnBackgroundThread(() -> exportSnapshot(appContext, target, passphrase));
    }

    // Read the manifest (decrypting first if the bundle is encrypted) and show the preview.
    private void beginImportPreview(@NonNull Uri uri, @Nullable char[] passphrase) {
        Context appContext = requireContext().getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            try (InputStream in = appContext.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    ThreadUtils.postOnMainThread(() ->
                            UIUtils.displayLongToast(R.string.snapshot_import_failed, "Cannot open file"));
                    return;
                }
                SnapshotBundle.ManifestSummary manifest = SnapshotBundle.readManifestOnly(in, passphrase);
                ThreadUtils.postOnMainThread(() -> showImportPreview(manifest, uri, passphrase));
            } catch (SnapshotBundle.PassphraseRequiredException e) {
                ThreadUtils.postOnMainThread(() -> showDecryptPassphraseDialog(uri));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                ThreadUtils.postOnMainThread(() ->
                        UIUtils.displayLongToast(R.string.snapshot_import_failed, msg));
            }
        });
    }

    private void showDecryptPassphraseDialog(@NonNull Uri uri) {
        Context context = requireContext();
        TextInputLayout input = buildPassphraseInput(context);
        TextInputEditText edit = (TextInputEditText) input.getEditText();
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.snapshot_decrypt_title)
                .setMessage(R.string.snapshot_decrypt_message)
                .setView(input)
                .setPositiveButton(R.string.ok, (d, w) -> beginImportPreview(uri, extractPassphrase(edit)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @NonNull
    private TextInputLayout buildPassphraseInput(@NonNull Context context) {
        TextInputLayout layout = new TextInputLayout(context);
        layout.setHint(getString(R.string.snapshot_passphrase_hint));
        TextInputEditText edit = new TextInputEditText(layout.getContext());
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(edit);
        layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        int pad = Math.round(24 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);
        return layout;
    }

    @NonNull
    private static char[] extractPassphrase(@Nullable TextInputEditText edit) {
        Editable text = edit != null ? edit.getText() : null;
        if (text == null) return new char[0];
        char[] out = new char[text.length()];
        text.getChars(0, text.length(), out, 0);
        return out;
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
