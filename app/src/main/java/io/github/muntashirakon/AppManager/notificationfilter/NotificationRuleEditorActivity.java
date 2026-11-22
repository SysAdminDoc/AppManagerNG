// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.TextQuery;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.adapters.AnyFilterArrayAdapter;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.MaterialAutoCompleteTextView;

/** Adds a rule, or changes one when started with {@link #EXTRA_RULE_ID}. */
public class NotificationRuleEditorActivity extends BaseActivity {
    private static final String TAG = "NotificationRuleEditor";
    public static final String EXTRA_RULE_ID = "rule_id";
    private static final String STATE_PACKAGE = "package";
    private static final String STATE_FIELD = "field";
    private static final String STATE_ACTION = "action";
    private static final String STATE_PRIORITY = "priority";
    private static final String STATE_PIN_FOR = "pin_for";
    private static final NotificationFilterRule.Field[] FIELDS = NotificationFilterRule.Field.values();
    /** From leaving it alone to taking it away. */
    private static final NotificationFilterRule.Action[] ACTIONS = {
            NotificationFilterRule.Action.ALLOW,
            NotificationFilterRule.Action.SILENT,
            NotificationFilterRule.Action.KEEP_ICON,
            NotificationFilterRule.Action.VIBRATE,
            NotificationFilterRule.Action.PIN,
            NotificationFilterRule.Action.BLOCK,
    };
    private static final NotificationFilterRule.Priority[] PRIORITIES = NotificationFilterRule.Priority.values();
    private static final NotificationFilterRule.PinLength[] PIN_LENGTHS = NotificationFilterRule.PinLength.values();

    @NonNull
    public static Intent newIntent(@NonNull Context context, @Nullable String ruleId) {
        Intent intent = new Intent(context, NotificationRuleEditorActivity.class);
        if (ruleId != null) {
            intent.putExtra(EXTRA_RULE_ID, ruleId);
        }
        return intent;
    }

    /** One row of the app list. The text is what the dropdown shows and filters on. */
    @VisibleForTesting
    static final class AppItem {
        @Nullable
        final String packageName;
        @NonNull
        final String label;
        @NonNull
        private final String mText;

        AppItem(@Nullable String packageName, @NonNull String label) {
            this.packageName = packageName;
            this.label = label;
            mText = packageName == null || packageName.equals(label) ? label : label + " (" + packageName + ")";
        }

        @NonNull
        @Override
        public String toString() {
            return mText;
        }
    }

    private NotificationFilterSettings mSettings;
    @Nullable
    private NotificationFilterRule mEditing;
    private TextInputLayout mNameLayout;
    private TextInputEditText mName;
    private TextInputLayout mAppLayout;
    private MaterialAutoCompleteTextView mApp;
    private MaterialAutoCompleteTextView mField;
    private TextInputLayout mPatternLayout;
    private TextInputEditText mPattern;
    private MaterialSwitch mRegex;
    private MaterialSwitch mOngoing;
    private TextView mOngoingNote;
    private MaterialAutoCompleteTextView mAction;
    private TextView mActionSummary;
    private View mPinForLayout;
    private MaterialAutoCompleteTextView mPinFor;
    private View mPriorityLayout;
    private MaterialAutoCompleteTextView mPriority;
    @NonNull
    private AppItem mAnyApp = new AppItem(null, "");
    /** The chosen app, or the "any app" row. */
    @NonNull
    private AppItem mSelectedApp = mAnyApp;
    @NonNull
    private NotificationFilterRule.Field mSelectedField = NotificationFilterRule.Field.ANY;
    @NonNull
    private NotificationFilterRule.Action mSelectedAction = NotificationFilterRule.Action.SILENT;
    @NonNull
    private NotificationFilterRule.Priority mSelectedPriority = NotificationFilterRule.Priority.NORMAL;
    @NonNull
    private NotificationFilterRule.PinLength mSelectedPinFor = NotificationFilterRule.PinLength.UNTIL_UNPINNED;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_notification_rule_editor);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mSettings = NotificationFilterSettings.getInstance(this);
        UiUtils.applyWindowInsetsAsPaddingNoTop(findViewById(R.id.scroll_view));
        mNameLayout = findViewById(R.id.rule_name_layout);
        mName = findViewById(R.id.rule_name);
        mAppLayout = findViewById(R.id.rule_app_layout);
        mApp = findViewById(R.id.rule_app);
        mField = findViewById(R.id.rule_field);
        mPatternLayout = findViewById(R.id.rule_pattern_layout);
        mPattern = findViewById(R.id.rule_pattern);
        mRegex = findViewById(R.id.rule_regex);
        mOngoing = findViewById(R.id.rule_ongoing);
        mOngoingNote = findViewById(R.id.rule_ongoing_note);
        mAction = findViewById(R.id.rule_action);
        mActionSummary = findViewById(R.id.rule_action_summary);
        mPinForLayout = findViewById(R.id.rule_pin_for_layout);
        mPinFor = findViewById(R.id.rule_pin_for);
        mPriorityLayout = findViewById(R.id.rule_priority_layout);
        mPriority = findViewById(R.id.rule_priority);
        mAnyApp = new AppItem(null, getString(R.string.notification_filter_rule_any_app));
        mSelectedApp = mAnyApp;

        String ruleId = getIntent().getStringExtra(EXTRA_RULE_ID);
        mEditing = ruleId != null ? mSettings.findRule(ruleId) : null;
        if (ruleId != null && mEditing == null) {
            // Deleted while the editor was open in another task.
            UIUtils.displayShortToast(R.string.notification_filter_rule_gone);
            finish();
            return;
        }
        String packageName = null;
        if (mEditing != null) {
            setTitle(R.string.notification_filter_rule_edit);
            if (savedInstanceState == null) {
                mName.setText(mEditing.name);
                mPattern.setText(mEditing.pattern);
                mRegex.setChecked(mEditing.regex);
                mOngoing.setChecked(mEditing.ongoing);
            }
            packageName = mEditing.packageName;
            mSelectedField = mEditing.field;
            mSelectedAction = mEditing.action;
            mSelectedPriority = mEditing.priority;
            mSelectedPinFor = mEditing.pinFor;
        }
        if (savedInstanceState != null) {
            packageName = savedInstanceState.getString(STATE_PACKAGE);
            mSelectedField = enumOr(NotificationFilterRule.Field.class, savedInstanceState.getString(STATE_FIELD),
                    mSelectedField);
            mSelectedAction = enumOr(NotificationFilterRule.Action.class, savedInstanceState.getString(STATE_ACTION),
                    mSelectedAction);
            mSelectedPriority = enumOr(NotificationFilterRule.Priority.class,
                    savedInstanceState.getString(STATE_PRIORITY), mSelectedPriority);
            mSelectedPinFor = enumOr(NotificationFilterRule.PinLength.class,
                    savedInstanceState.getString(STATE_PIN_FOR), mSelectedPinFor);
        }
        if (packageName != null) {
            mSelectedApp = new AppItem(packageName, PackageUtils.getPackageLabel(getPackageManager(), packageName));
        }
        setUpApps();
        setUpFields();
        setUpActions();
        setUpPriorities();
        setUpPinLengths();
        mOngoing.setOnCheckedChangeListener((buttonView, isChecked) -> showOngoingNote());
        showOngoingNote();
        mPattern.addTextChangedListener(new SimpleTextWatcher(() -> mPatternLayout.setError(null)));
        mRegex.setOnCheckedChangeListener((buttonView, isChecked) -> mPatternLayout.setError(null));
        findViewById(R.id.rule_save).setOnClickListener(v -> save());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PACKAGE, mSelectedApp.packageName);
        outState.putString(STATE_FIELD, mSelectedField.name());
        outState.putString(STATE_ACTION, mSelectedAction.name());
        outState.putString(STATE_PRIORITY, mSelectedPriority.name());
        outState.putString(STATE_PIN_FOR, mSelectedPinFor.name());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setUpApps() {
        mApp.setText(mSelectedApp.toString(), false);
        mApp.addTextChangedListener(new SimpleTextWatcher(() -> mAppLayout.setError(null)));
        mApp.setOnItemClickListener((parent, view, position, id) -> {
            mSelectedApp = (AppItem) parent.getItemAtPosition(position);
            mAppLayout.setError(null);
        });
        // Every app can post, so the list includes system apps. Reading it can take a moment.
        Context appContext = getApplicationContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            List<AppItem> apps = loadApps(appContext);
            ThreadUtils.postOnMainThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                List<AppItem> items = new ArrayList<>(apps.size() + 1);
                items.add(mAnyApp);
                items.addAll(apps);
                mApp.setAdapter(new AnyFilterArrayAdapter<>(this,
                        io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, items));
            });
        });
    }

    @NonNull
    private static List<AppItem> loadApps(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        List<AppItem> apps = new ArrayList<>();
        try {
            for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                if (info.packageName.equals(context.getPackageName())) {
                    // Its own notifications are never filtered.
                    continue;
                }
                apps.add(new AppItem(info.packageName, info.loadLabel(pm).toString()));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not list the apps.", e);
        }
        Collator collator = Collator.getInstance();
        Collections.sort(apps, (a, b) -> {
            int byLabel = collator.compare(a.label, b.label);
            return byLabel != 0 ? byLabel : String.valueOf(a.packageName).compareTo(String.valueOf(b.packageName));
        });
        return apps;
    }

    private void setUpFields() {
        List<String> labels = new ArrayList<>(FIELDS.length);
        for (NotificationFilterRule.Field field : FIELDS) {
            labels.add(getString(FilterLabels.of(field)));
        }
        mField.setAdapter(new AnyFilterArrayAdapter<>(this,
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, labels));
        mField.setText(getString(FilterLabels.of(mSelectedField)), false);
        mField.setOnItemClickListener((parent, view, position, id) ->
                mSelectedField = FIELDS[labels.indexOf((String) parent.getItemAtPosition(position))]);
    }

    private void setUpActions() {
        List<String> labels = new ArrayList<>(ACTIONS.length);
        for (NotificationFilterRule.Action action : ACTIONS) {
            labels.add(getString(FilterLabels.of(action)));
        }
        mAction.setAdapter(new AnyFilterArrayAdapter<>(this,
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, labels));
        mAction.setText(getString(FilterLabels.of(mSelectedAction)), false);
        showActionOptions();
        mAction.setOnItemClickListener((parent, view, position, id) -> {
            mSelectedAction = ACTIONS[labels.indexOf(String.valueOf(mAction.getAdapter().getItem(position)))];
            showActionOptions();
        });
    }

    /** The priority applies only to what posts a copy, and only a pin has a length. */
    private void showActionOptions() {
        mActionSummary.setText(FilterLabels.summaryOf(mSelectedAction));
        mPriorityLayout.setVisibility(NotificationFilterRule.postsCopy(mSelectedAction) ? View.VISIBLE : View.GONE);
        mPinForLayout.setVisibility(mSelectedAction == NotificationFilterRule.Action.PIN ? View.VISIBLE : View.GONE);
    }

    private void setUpPriorities() {
        List<String> labels = new ArrayList<>(PRIORITIES.length);
        for (NotificationFilterRule.Priority priority : PRIORITIES) {
            labels.add(getString(FilterLabels.of(priority)));
        }
        mPriority.setAdapter(new AnyFilterArrayAdapter<>(this,
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, labels));
        mPriority.setText(getString(FilterLabels.of(mSelectedPriority)), false);
        mPriority.setOnItemClickListener((parent, view, position, id) -> mSelectedPriority =
                PRIORITIES[labels.indexOf(String.valueOf(mPriority.getAdapter().getItem(position)))]);
    }

    private void setUpPinLengths() {
        List<String> labels = new ArrayList<>(PIN_LENGTHS.length);
        for (NotificationFilterRule.PinLength length : PIN_LENGTHS) {
            labels.add(getString(FilterLabels.of(length)));
        }
        mPinFor.setAdapter(new AnyFilterArrayAdapter<>(this,
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, labels));
        mPinFor.setText(getString(FilterLabels.of(mSelectedPinFor)), false);
        mPinFor.setOnItemClickListener((parent, view, position, id) -> mSelectedPinFor =
                PIN_LENGTHS[labels.indexOf(String.valueOf(mPinFor.getAdapter().getItem(position)))]);
    }

    /** Says what happens to an ongoing notification on this phone, since it depends on the version and the mode. */
    private void showOngoingNote() {
        if (mOngoing.isChecked()) {
            mOngoingNote.setText(OngoingPolicy.describe(Build.VERSION.SDK_INT, Ops.isWorkingUidRoot()));
            mOngoingNote.setVisibility(View.VISIBLE);
        } else {
            mOngoingNote.setVisibility(View.GONE);
        }
    }

    private void save() {
        String appText = textOf(mApp).trim();
        if (appText.isEmpty()) {
            mSelectedApp = mAnyApp;
        } else if (!appText.equals(mSelectedApp.toString())) {
            // Typed to search but never picked a row.
            mAppLayout.setError(getString(R.string.notification_filter_rule_pick_app));
            mApp.requestFocus();
            return;
        }
        String pattern = textOf(mPattern);
        boolean regex = mRegex.isChecked();
        if (regex && !TextQuery.isUsableRegex(pattern)) {
            mPatternLayout.setError(getString(R.string.search_regex_unusable));
            mPattern.requestFocus();
            return;
        }
        String name = textOf(mName).trim();
        if (name.isEmpty()) {
            name = defaultName(mSelectedApp, pattern);
        }
        NotificationFilterRule rule;
        if (mEditing != null) {
            rule = new NotificationFilterRule(mEditing.id, name, mSelectedApp.packageName, mSelectedField, pattern,
                    regex, mSelectedAction);
        } else {
            rule = NotificationFilterRule.create(name, mSelectedApp.packageName, mSelectedField, pattern, regex,
                    mSelectedAction);
        }
        mSettings.saveRule(rule.withOptions(mOngoing.isChecked(), mSelectedPriority, mSelectedPinFor));
        UIUtils.displayShortToast(R.string.notification_filter_rule_saved);
        finish();
    }

    @NonNull
    private String defaultName(@NonNull AppItem app, @NonNull String pattern) {
        if (pattern.isEmpty()) {
            return app.label;
        }
        return getString(R.string.notification_filter_rule_default_name, app.label, pattern);
    }

    @NonNull
    private static <E extends Enum<E>> E enumOr(@NonNull Class<E> type, @Nullable String name, @NonNull E fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @NonNull
    private static String textOf(@NonNull TextView view) {
        CharSequence text = view.getText();
        return TextUtils.isEmpty(text) ? "" : text.toString();
    }

    private static final class SimpleTextWatcher implements android.text.TextWatcher {
        @NonNull
        private final Runnable mOnChange;

        SimpleTextWatcher(@NonNull Runnable onChange) {
            mOnChange = onChange;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            mOnChange.run();
        }
    }
}
