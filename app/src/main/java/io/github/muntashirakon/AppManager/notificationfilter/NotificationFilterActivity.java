// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.adapters.AnyFilterArrayAdapter;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.MaterialAutoCompleteTextView;

/** Notification access, quiet mode, the rule list and the history settings, on one screen. */
public class NotificationFilterActivity extends BaseActivity {
    private static final String TAG = "NotificationFilterActivity";
    private static final NotificationFilterRule.Action[] QUIET_ACTIONS = {
            NotificationFilterRule.Action.SILENT, NotificationFilterRule.Action.VIBRATE};

    private NotificationFilterSettings mSettings;
    private TextView mAccessStatus;
    private MaterialButton mAccessGrant;
    private MaterialButton mAccessOpen;
    private TextView mPostStatus;
    private MaterialButton mPostAllow;
    private MaterialSwitch mQuietSwitch;
    private TextView mQuietStatus;
    private MaterialAutoCompleteTextView mQuietAction;
    private MaterialSwitch mScheduleSwitch;
    private View mScheduleTimes;
    private MaterialButton mScheduleStart;
    private MaterialButton mScheduleEnd;
    private ViewGroup mRulesList;
    private View mRulesEmpty;
    private MaterialSwitch mRecordAll;
    private MaterialAutoCompleteTextView mRetention;
    private boolean mGranting;
    private final ActivityResultLauncher<String> mNotificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> refreshAccess());

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_notification_filter);
        setSupportActionBar(findViewById(R.id.toolbar));
        mSettings = NotificationFilterSettings.getInstance(this);
        UiUtils.applyWindowInsetsAsPaddingNoTop(findViewById(R.id.scroll_view));

        mAccessStatus = findViewById(R.id.access_status);
        mAccessGrant = findViewById(R.id.access_grant);
        mAccessOpen = findViewById(R.id.access_open_settings);
        mPostStatus = findViewById(R.id.post_permission_status);
        mPostAllow = findViewById(R.id.post_permission_allow);
        mAccessOpen.setOnClickListener(v -> openAccessSettings());
        mAccessGrant.setOnClickListener(v -> grantAccess());
        mPostAllow.setOnClickListener(v -> askToPost());

        mQuietSwitch = findViewById(R.id.quiet_switch);
        mQuietStatus = findViewById(R.id.quiet_status);
        mQuietAction = findViewById(R.id.quiet_action);
        mScheduleSwitch = findViewById(R.id.schedule_switch);
        mScheduleTimes = findViewById(R.id.schedule_times);
        mScheduleStart = findViewById(R.id.schedule_start);
        mScheduleEnd = findViewById(R.id.schedule_end);
        mScheduleStart.setOnClickListener(v -> pickTime(true));
        mScheduleEnd.setOnClickListener(v -> pickTime(false));
        setUpQuietAction();

        mRulesList = findViewById(R.id.rules_list);
        mRulesEmpty = findViewById(R.id.rules_empty);
        findViewById(R.id.rule_add).setOnClickListener(v -> startActivity(NotificationRuleEditorActivity.newIntent(this, null)));

        mRecordAll = findViewById(R.id.record_all_switch);
        mRetention = findViewById(R.id.retention);
        setUpRetention();
        findViewById(R.id.history_open).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationHistoryActivity.class)));
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from Settings or the rule editor is what changes things here.
        if (mSettings != null) {
            refreshAll();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshAll() {
        refreshAccess();
        refreshQuietMode();
        refreshRules();
        refreshHistorySettings();
    }

    // Access

    private void refreshAccess() {
        boolean granted = NotificationListenerAccess.isGranted(this);
        mAccessStatus.setText(granted ? R.string.notification_filter_access_on : R.string.notification_filter_access_off);
        mAccessOpen.setText(granted ? R.string.notification_filter_access_manage
                : R.string.notification_filter_access_open_settings);
        mAccessGrant.setVisibility(!granted && NotificationListenerAccess.canGrantPrivileged() ? View.VISIBLE : View.GONE);
        mAccessGrant.setEnabled(!mGranting);
        // Silent and vibrate copies are our own notifications.
        boolean canPost = NotificationReposter.canPost(this, NotificationFilterRule.Action.SILENT);
        mPostStatus.setVisibility(canPost ? View.GONE : View.VISIBLE);
        mPostAllow.setVisibility(canPost ? View.GONE : View.VISIBLE);
    }

    private void openAccessSettings() {
        try {
            NotificationListenerAccess.openSettings(this);
        } catch (RuntimeException e) {
            Log.w(TAG, "No settings page for notification access.", e);
            UIUtils.displayLongToast(R.string.notification_filter_access_no_settings);
        }
    }

    private void grantAccess() {
        mGranting = true;
        mAccessGrant.setEnabled(false);
        ThreadUtils.postOnBackgroundThread(() -> {
            boolean granted = NotificationListenerAccess.grantPrivileged(getApplicationContext());
            ThreadUtils.postOnMainThread(() -> {
                mGranting = false;
                if (isDestroyed()) {
                    return;
                }
                UIUtils.displayShortToast(granted ? R.string.notification_filter_access_granted
                        : R.string.notification_filter_access_grant_failed);
                refreshAccess();
            });
        });
    }

    private void askToPost() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            mNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        // Turned off in the system settings, or one of our channels is.
        Intent settings;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            settings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", getPackageName(), null));
        }
        try {
            startActivity(settings);
        } catch (RuntimeException e) {
            Log.w(TAG, "No notification settings page.", e);
            UIUtils.displayLongToast(R.string.notification_filter_post_needed);
        }
    }

    // Quiet mode

    private void refreshQuietMode() {
        long now = System.currentTimeMillis();
        TimeZone zone = TimeZone.getDefault();
        QuietMode quiet = mSettings.getQuietMode();
        boolean active = quiet.isActive(now, zone);
        mQuietSwitch.setOnCheckedChangeListener(null);
        mQuietSwitch.setChecked(active);
        mQuietSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> onQuietToggled());
        mQuietStatus.setText(quietStatus(quiet, now, zone));
        mScheduleSwitch.setOnCheckedChangeListener(null);
        mScheduleSwitch.setChecked(quiet.scheduled);
        mScheduleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            QuietMode current = mSettings.getQuietMode();
            mSettings.setQuietMode(current.withSchedule(isChecked, current.startMinute, current.endMinute));
            quietModeChanged();
        });
        mScheduleTimes.setVisibility(quiet.scheduled ? View.VISIBLE : View.GONE);
        mScheduleStart.setText(getString(R.string.notification_filter_schedule_from, formatMinute(quiet.startMinute)));
        mScheduleEnd.setText(getString(R.string.notification_filter_schedule_until, formatMinute(quiet.endMinute)));
        mQuietAction.setText(getString(FilterLabels.of(mSettings.getQuietAction())), false);
    }

    @NonNull
    private String quietStatus(@NonNull QuietMode quiet, long now, @NonNull TimeZone zone) {
        if (quiet.manual) {
            return getString(R.string.notification_filter_quiet_on_manual);
        }
        if (quiet.isScheduleRunning(now, zone)) {
            return getString(R.string.notification_filter_quiet_on_until, formatMinute(quiet.endMinute));
        }
        if (quiet.scheduled) {
            return getString(R.string.notification_filter_quiet_off_next, formatMinute(quiet.startMinute));
        }
        return getString(R.string.off);
    }

    private void onQuietToggled() {
        mSettings.toggleQuietMode(System.currentTimeMillis());
        quietModeChanged();
    }

    private void quietModeChanged() {
        refreshQuietMode();
    }

    private void setUpQuietAction() {
        List<String> labels = new ArrayList<>(QUIET_ACTIONS.length);
        for (NotificationFilterRule.Action action : QUIET_ACTIONS) {
            labels.add(getString(FilterLabels.of(action)));
        }
        mQuietAction.setAdapter(new AnyFilterArrayAdapter<>(this,
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, labels));
        mQuietAction.setOnItemClickListener((parent, view, position, id) -> {
            String label = (String) parent.getItemAtPosition(position);
            mSettings.setQuietAction(QUIET_ACTIONS[labels.indexOf(label)]);
        });
    }

    private void pickTime(boolean start) {
        QuietMode quiet = mSettings.getQuietMode();
        int minute = start ? quiet.startMinute : quiet.endMinute;
        new TimePickerDialog(this, (view, hourOfDay, pickedMinute) -> {
            QuietMode current = mSettings.getQuietMode();
            int picked = hourOfDay * 60 + pickedMinute;
            mSettings.setQuietMode(current.withSchedule(current.scheduled, start ? picked : current.startMinute,
                    start ? current.endMinute : picked));
            quietModeChanged();
        }, minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(this)).show();
    }

    @NonNull
    private String formatMinute(int minuteOfDay) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        calendar.set(Calendar.MINUTE, minuteOfDay % 60);
        return android.text.format.DateFormat.getTimeFormat(this).format(calendar.getTime());
    }

    // Rules

    private void refreshRules() {
        List<NotificationFilterRule> rules = mSettings.getRules();
        mRulesList.removeAllViews();
        mRulesEmpty.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < rules.size(); ++i) {
            NotificationFilterRule rule = rules.get(i);
            View row = inflater.inflate(R.layout.item_notification_rule, mRulesList, false);
            ((TextView) row.findViewById(R.id.rule_name)).setText(rule.name);
            ((TextView) row.findViewById(R.id.rule_summary)).setText(describe(rule));
            row.findViewById(R.id.rule_body).setOnClickListener(v ->
                    startActivity(NotificationRuleEditorActivity.newIntent(this, rule.id)));
            View up = row.findViewById(R.id.rule_up);
            View down = row.findViewById(R.id.rule_down);
            int position = i;
            up.setEnabled(i > 0);
            down.setEnabled(i < rules.size() - 1);
            up.setOnClickListener(v -> moveRule(position, position - 1));
            down.setOnClickListener(v -> moveRule(position, position + 1));
            row.findViewById(R.id.rule_delete).setOnClickListener(v -> deleteRule(position));
            mRulesList.addView(row);
        }
    }

    @NonNull
    private String describe(@NonNull NotificationFilterRule rule) {
        String app = rule.packageName == null ? getString(R.string.notification_filter_rule_any_app)
                : PackageUtils.getPackageLabel(getPackageManager(), rule.packageName);
        String field = getString(FilterLabels.of(rule.field));
        String condition;
        if (rule.pattern.isEmpty()) {
            condition = getString(R.string.notification_filter_rule_condition_all);
        } else if (rule.regex) {
            condition = getString(R.string.notification_filter_rule_condition_regex, field, rule.pattern);
        } else {
            condition = getString(R.string.notification_filter_rule_condition_contains, field, rule.pattern);
        }
        String summary = getString(R.string.notification_filter_rule_summary, app, condition,
                getString(FilterLabels.of(rule.action)));
        if (!rule.isValid()) {
            summary = summary + "\n" + getString(R.string.notification_filter_rule_invalid);
        }
        return summary;
    }

    private void moveRule(int from, int to) {
        List<NotificationFilterRule> rules = new ArrayList<>(mSettings.getRules());
        if (from < 0 || from >= rules.size() || to < 0 || to >= rules.size()) {
            return;
        }
        rules.add(to, rules.remove(from));
        mSettings.setRules(rules);
        refreshRules();
    }

    private void deleteRule(int position) {
        List<NotificationFilterRule> before = mSettings.getRules();
        if (position < 0 || position >= before.size()) {
            return;
        }
        List<NotificationFilterRule> after = new ArrayList<>(before);
        NotificationFilterRule removed = after.remove(position);
        mSettings.setRules(after);
        refreshRules();
        Snackbar.make(mRulesList, getString(R.string.notification_filter_rule_deleted, removed.name),
                        Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> {
                    List<NotificationFilterRule> now = new ArrayList<>(mSettings.getRules());
                    now.add(Math.min(position, now.size()), removed);
                    mSettings.setRules(now);
                    refreshRules();
                })
                .show();
    }

    // History

    private void refreshHistorySettings() {
        mRecordAll.setOnCheckedChangeListener(null);
        mRecordAll.setChecked(mSettings.isRecordAll());
        mRecordAll.setOnCheckedChangeListener((buttonView, isChecked) -> mSettings.setRecordAll(isChecked));
        mRetention.setText(retentionLabel(mSettings.getRetentionDays()), false);
    }

    private void setUpRetention() {
        int[] choices = NotificationFilterSettings.RETENTION_CHOICES;
        List<String> labels = new ArrayList<>(choices.length);
        for (int days : choices) {
            labels.add(retentionLabel(days));
        }
        mRetention.setAdapter(new AnyFilterArrayAdapter<>(this,
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, labels));
        mRetention.setOnItemClickListener((parent, view, position, id) -> {
            String label = (String) parent.getItemAtPosition(position);
            mSettings.setRetentionDays(choices[labels.indexOf(label)]);
            // A shorter time drops the older entries now, not at the next notification.
            NotificationHistory.getInstance(this).prune();
        });
    }

    @NonNull
    private String retentionLabel(int days) {
        return getResources().getQuantityString(R.plurals.notification_filter_retention_days, days, days);
    }

    @VisibleForTesting
    @NonNull
    ViewGroup getRulesList() {
        return mRulesList;
    }
}
