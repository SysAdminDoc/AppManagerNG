// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.ClipboardUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

/**
 * Labs > Settings editor: the System, Secure and Global tables, searchable and editable with root, ADB or Shizuku
 * (read only otherwise), and a Changes tab where every change made here can be put back (#48).
 */
public class SettingsEditorActivity extends BaseActivity implements SearchView.OnQueryTextListener {
    private static final int TAB_CHANGES = 3;

    @Nullable
    private SettingsEditorViewModel mViewModel;
    private final ActivityResultLauncher<String> mExportSnapshot = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null && mViewModel != null) {
                    mViewModel.exportSnapshot(uri);
                }
            });
    private LinearProgressIndicator mProgressIndicator;
    private TextView mModeNote;
    private FloatingActionButton mAddButton;
    private RecyclerView mList;
    private TextView mEmptyTitle;
    private TextView mEmptySummary;
    private EntryAdapter mEntryAdapter;
    private ChangeAdapter mChangeAdapter;
    @Nullable
    private SettingsEditorViewModel.TableState mState;
    @Nullable
    private AlertDialog mDialog;
    private boolean mPaused;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_settings_editor);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(SettingsEditorViewModel.class);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            UIUtils.setupSearchView(actionBar, this);
        }
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mModeNote = findViewById(R.id.mode_note);
        mAddButton = findViewById(R.id.floatingActionButton);
        UiUtils.applyWindowInsetsAsMargin(mAddButton);
        mAddButton.setOnClickListener(v -> showEntryDialog(null));

        mList = findViewById(android.R.id.list);
        mList.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        View emptyView = findViewById(android.R.id.empty);
        ((ImageView) emptyView.findViewById(R.id.empty_state_icon)).setImageResource(R.drawable.ic_settings);
        mEmptyTitle = emptyView.findViewById(R.id.empty_state_title);
        mEmptySummary = emptyView.findViewById(R.id.empty_state_summary);
        emptyView.findViewById(R.id.empty_state_action).setVisibility(View.GONE);
        mList.setEmptyView(emptyView);
        UiUtils.applyWindowInsetsAsPaddingNoTop(mList);
        mEntryAdapter = new EntryAdapter(this, this::showEntryDialog);
        SettingsEditorViewModel viewModel = mViewModel;
        mChangeAdapter = new ChangeAdapter(viewModel::revert);

        TabLayout tabs = findViewById(R.id.tab_layout);
        tabs.addTab(tabs.newTab().setText(R.string.system));
        tabs.addTab(tabs.newTab().setText(R.string.settings_editor_tab_secure));
        tabs.addTab(tabs.newTab().setText(R.string.settings_editor_tab_global));
        tabs.addTab(tabs.newTab().setText(R.string.settings_editor_tab_changes));
        TabLayout.Tab current = tabs.getTabAt(mViewModel.isShowingChanges() ? TAB_CHANGES
                : mViewModel.getTable().ordinal());
        if (current != null) {
            current.select();
        }
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                show(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        mViewModel.getTableState().observe(this, this::showTableState);
        mViewModel.getChanges().observe(this, this::showChanges);
        mViewModel.getOutcome().observe(this, this::showOutcome);
        show(mViewModel.isShowingChanges() ? TAB_CHANGES : mViewModel.getTable().ordinal());
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPaused = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from "Modify system settings", or after changes made elsewhere.
        if (mPaused && mViewModel != null && !mViewModel.isShowingChanges()) {
            mViewModel.showTable(mViewModel.getTable());
        }
        mPaused = false;
    }

    @Override
    protected void onDestroy() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_settings_editor_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_export) {
            try {
                mExportSnapshot.launch(SettingsSnapshot.fileName(System.currentTimeMillis()));
            } catch (ActivityNotFoundException e) {
                UIUtils.displayLongToast(getString(R.string.settings_editor_export_failed, e.getMessage()));
            }
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        String query = newText.toLowerCase(Locale.ROOT);
        mEntryAdapter.setQuery(query);
        mChangeAdapter.setQuery(query);
        return true;
    }

    private void show(int tab) {
        if (mViewModel == null) {
            return;
        }
        mProgressIndicator.show();
        if (tab == TAB_CHANGES) {
            mList.setAdapter(mChangeAdapter);
            mModeNote.setVisibility(View.GONE);
            mAddButton.hide();
            mEmptyTitle.setText(R.string.settings_editor_changes_empty_title);
            mEmptySummary.setText(R.string.settings_editor_changes_empty_summary);
            mViewModel.showChanges();
        } else {
            mList.setAdapter(mEntryAdapter);
            // Nothing can be added until the new table says whether it takes changes.
            mState = null;
            mAddButton.hide();
            mEntryAdapter.setEntries(Collections.emptyList());
            mViewModel.showTable(SettingsTable.values()[tab]);
        }
    }

    private void showTableState(@Nullable SettingsEditorViewModel.TableState state) {
        if (state == null || mViewModel == null || mViewModel.isShowingChanges()
                || state.table != mViewModel.getTable()) {
            // A table loaded for a tab that's no longer showing.
            return;
        }
        mProgressIndicator.hide();
        mState = state;
        mEntryAdapter.setEntries(state.entries);
        mEmptyTitle.setText(R.string.settings_editor_empty_title);
        if (state.error != null) {
            mEmptySummary.setText(getString(R.string.settings_editor_read_failed, state.error));
        } else {
            mEmptySummary.setText(R.string.settings_editor_empty_summary);
        }
        if (state.writable) {
            mAddButton.show();
        } else {
            mAddButton.hide();
        }
        showModeNote(state);
    }

    /** One line on what can be changed without root, ADB or Shizuku. */
    private void showModeNote(@NonNull SettingsEditorViewModel.TableState state) {
        mModeNote.setOnClickListener(null);
        mModeNote.setClickable(false);
        if (state.privileged) {
            mModeNote.setVisibility(View.GONE);
            return;
        }
        mModeNote.setVisibility(View.VISIBLE);
        if (state.writable) {
            mModeNote.setText(R.string.settings_editor_note_limited);
        } else if (state.table == SettingsTable.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mModeNote.setText(R.string.settings_editor_note_read_only_system);
            mModeNote.setOnClickListener(v -> openModifySystemSettings());
        } else {
            mModeNote.setText(R.string.settings_editor_note_read_only);
        }
    }

    private void openModifySystemSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS));
            } catch (ActivityNotFoundException ignore) {
                UIUtils.displayShortToast(R.string.settings_editor_note_read_only_system);
            }
        }
    }

    private void showChanges(@Nullable List<SettingsChange> changes) {
        mChangeAdapter.setChanges(changes != null ? changes : Collections.emptyList());
        if (mViewModel != null && mViewModel.isShowingChanges()) {
            mProgressIndicator.hide();
        }
    }

    private void showOutcome(@Nullable SettingsEditorViewModel.Outcome outcome) {
        if (outcome == null || mViewModel == null) {
            return;
        }
        mViewModel.consumeOutcome();
        switch (outcome.kind) {
            case SAVED:
            case REVERTED: {
                SettingsChange change = outcome.change;
                if (change == null) break;
                @StringRes int text = outcome.kind == SettingsEditorViewModel.Outcome.Kind.REVERTED
                        ? R.string.settings_editor_reverted
                        : change.isDeletion() ? R.string.settings_editor_deleted : R.string.settings_editor_saved;
                Snackbar.make(mList, getString(text, outcome.key), Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, v -> mViewModel.revert(change))
                        .show();
                break;
            }
            case UNCHANGED:
                Snackbar.make(mList, getString(R.string.settings_editor_unchanged, outcome.key),
                        Snackbar.LENGTH_SHORT).show();
                break;
            case FAILED:
                Snackbar.make(mList, getString(R.string.settings_editor_failed, outcome.key, outcome.error),
                        Snackbar.LENGTH_LONG).show();
                break;
            case EXPORTED:
                UIUtils.displayShortToast(R.string.settings_editor_exported);
                break;
            case EXPORT_FAILED:
                UIUtils.displayLongToast(getString(R.string.settings_editor_export_failed, outcome.error));
                break;
        }
    }

    /** Edits a key, or adds one when {@code entry} is {@code null}. Read only when the table can't be changed. */
    private void showEntryDialog(@Nullable SettingEntry entry) {
        if (mViewModel == null || mState == null) {
            return;
        }
        SettingsTable table = mState.table;
        boolean adding = entry == null;
        boolean editable = mState.writable && (adding || SettingsCommands.isValidKey(entry.key));
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings_editor_entry, null, false);
        TextInputLayout keyLayout = view.findViewById(R.id.key_layout);
        TextInputEditText keyField = view.findViewById(R.id.key);
        TextInputEditText valueField = view.findViewById(R.id.value);
        TextView riskNote = view.findViewById(R.id.risk_note);
        if (adding) {
            keyField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    keyLayout.setError(null);
                    showRiskNote(riskNote, s.toString().trim());
                }
            });
        } else {
            keyField.setText(entry.key);
            keyField.setEnabled(false);
            valueField.setText(entry.value);
            showRiskNote(riskNote, entry.key);
        }
        valueField.setEnabled(editable);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(adding ? getString(R.string.add_item) : tableLabel(this, table))
                .setView(view);
        if (editable) {
            builder.setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null);
            if (!adding) {
                // Deleted at once, with Undo on the snackbar.
                builder.setNeutralButton(R.string.delete, (dialog, which) -> mViewModel.set(table, entry.key, null));
            }
        } else {
            String value = entry != null && entry.value != null ? entry.value : "";
            builder.setPositiveButton(R.string.copy, (dialog, which) -> {
                ClipboardUtils.copyToClipboard(this, entry != null ? entry.key : null, value);
                UIUtils.displayShortToast(R.string.copied_to_clipboard);
            }).setNegativeButton(R.string.close, null);
        }
        AlertDialog dialog = builder.create();
        if (editable) {
            dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String key = adding ? String.valueOf(keyField.getText()).trim() : entry.key;
                if (!SettingsCommands.isValidKey(key)) {
                    keyLayout.setError(getString(R.string.settings_editor_invalid_key));
                    return;
                }
                mViewModel.set(table, key, String.valueOf(valueField.getText()));
                dialog.dismiss();
            }));
        }
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            // The value can run long, and the keyboard mustn't cover the buttons.
            //noinspection deprecation
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        mDialog = dialog;
    }

    @VisibleForTesting
    @Nullable
    AlertDialog getOpenDialog() {
        return mDialog;
    }

    private static void showRiskNote(@NonNull TextView riskNote, @NonNull String key) {
        int note = RiskyKeys.getNote(key);
        if (note != 0) {
            riskNote.setText(note);
            riskNote.setVisibility(View.VISIBLE);
        } else {
            riskNote.setVisibility(View.GONE);
        }
    }

    @NonNull
    static String tableLabel(@NonNull Context context, @NonNull SettingsTable table) {
        switch (table) {
            case SYSTEM:
                return context.getString(R.string.system);
            case SECURE:
                return context.getString(R.string.settings_editor_tab_secure);
            case GLOBAL:
            default:
                return context.getString(R.string.settings_editor_tab_global);
        }
    }

    private interface OnEntryClickListener {
        void onEntryClick(@NonNull SettingEntry entry);
    }

    private interface OnRevertListener {
        void onRevert(@NonNull SettingsChange change);
    }

    @VisibleForTesting
    static final class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.ViewHolder> {
        @NonNull
        private final OnEntryClickListener mListener;
        private final int mHighlightColor;
        @NonNull
        private List<SettingEntry> mAll = Collections.emptyList();
        private final List<SettingEntry> mShown = new ArrayList<>();
        @NonNull
        private String mQuery = "";

        EntryAdapter(@NonNull Context context, @NonNull OnEntryClickListener listener) {
            mListener = listener;
            mHighlightColor = ColorCodes.getQueryStringHighlightColor(context);
        }

        void setEntries(@NonNull List<SettingEntry> entries) {
            mAll = entries;
            refilter();
        }

        void setQuery(@NonNull String query) {
            mQuery = query;
            refilter();
        }

        private void refilter() {
            int previousCount = mShown.size();
            mShown.clear();
            for (SettingEntry entry : mAll) {
                if (mQuery.isEmpty() || entry.key.toLowerCase(Locale.ROOT).contains(mQuery)
                        || (entry.value != null && entry.value.toLowerCase(Locale.ROOT).contains(mQuery))) {
                    mShown.add(entry);
                }
            }
            AdapterUtils.notifyDataSetChanged(this, previousCount, mShown.size());
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_settings_editor_entry, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SettingEntry entry = mShown.get(position);
            Context context = holder.itemView.getContext();
            holder.key.setText(mQuery.isEmpty() ? entry.key
                    : UIUtils.getHighlightedText(entry.key, mQuery, mHighlightColor));
            holder.value.setText(entry.value != null ? entry.value
                    : context.getString(R.string.settings_editor_not_set));
            holder.riskyChip.setVisibility(RiskyKeys.isRisky(entry.key) ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> mListener.onEntryClick(entry));
        }

        @Override
        public int getItemCount() {
            return mShown.size();
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {
            final TextView key;
            final TextView value;
            final Chip riskyChip;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                key = itemView.findViewById(R.id.key);
                value = itemView.findViewById(R.id.value);
                riskyChip = itemView.findViewById(R.id.risky_chip);
            }
        }
    }

    @VisibleForTesting
    static final class ChangeAdapter extends RecyclerView.Adapter<ChangeAdapter.ViewHolder> {
        @NonNull
        private final OnRevertListener mListener;
        private final DateFormat mTimeFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
        @NonNull
        private List<SettingsChange> mAll = Collections.emptyList();
        private final List<SettingsChange> mShown = new ArrayList<>();
        @NonNull
        private String mQuery = "";

        ChangeAdapter(@NonNull OnRevertListener listener) {
            mListener = listener;
        }

        void setChanges(@NonNull List<SettingsChange> changes) {
            mAll = changes;
            refilter();
        }

        void setQuery(@NonNull String query) {
            mQuery = query;
            refilter();
        }

        private void refilter() {
            int previousCount = mShown.size();
            mShown.clear();
            for (SettingsChange change : mAll) {
                if (mQuery.isEmpty() || change.key.toLowerCase(Locale.ROOT).contains(mQuery)) {
                    mShown.add(change);
                }
            }
            AdapterUtils.notifyDataSetChanged(this, previousCount, mShown.size());
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_settings_editor_change, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SettingsChange change = mShown.get(position);
            Context context = holder.itemView.getContext();
            holder.title.setText(context.getString(R.string.settings_editor_change_key, change.key,
                    tableLabel(context, change.table)));
            holder.summary.setText(describe(context, change));
            holder.time.setText(mTimeFormat.format(new Date(change.timeMillis)));
            holder.revert.setOnClickListener(v -> mListener.onRevert(change));
        }

        @Override
        public int getItemCount() {
            return mShown.size();
        }

        /** "1 → 0", with "(not set)" or "(deleted)" for a missing value. */
        @NonNull
        static String describe(@NonNull Context context, @NonNull SettingsChange change) {
            String oldValue = change.oldValue != null ? change.oldValue
                    : context.getString(R.string.settings_editor_not_set);
            String newValue = change.newValue != null ? change.newValue
                    : context.getString(R.string.settings_editor_deleted_value);
            return context.getString(R.string.settings_editor_change_values, oldValue, newValue);
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView summary;
            final TextView time;
            final MaterialButton revert;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.title);
                summary = itemView.findViewById(R.id.summary);
                time = itemView.findViewById(R.id.time);
                revert = itemView.findViewById(R.id.revert);
            }
        }
    }
}
