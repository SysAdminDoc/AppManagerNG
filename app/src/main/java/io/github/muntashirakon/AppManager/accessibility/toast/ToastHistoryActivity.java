// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.BaseAccessibilityService;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.TextQuery;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.LiveDataChanges;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.adapters.AnyFilterArrayAdapter;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.MaterialAutoCompleteTextView;
import io.github.muntashirakon.widget.RecyclerView;

public class ToastHistoryActivity extends BaseActivity implements AdvancedSearchView.OnQueryTextListener {
    private ToastHistoryViewModel mViewModel;
    private LinearProgressIndicator mProgressIndicator;
    private MaterialSwitch mRecordSwitch;
    private View mServiceOffGroup;
    private TextInputLayout mFilterLayout;
    private MaterialAutoCompleteTextView mFilterView;
    private View mEmptyView;
    private TextView mEmptyTitle;
    private TextView mEmptySummary;
    private MaterialButton mEmptyAction;
    private ToastAdapter mAdapter;
    /** What the screen shows, for the copy action. */
    @NonNull
    private List<ToastEntry> mVisible = Collections.emptyList();
    @NonNull
    private TextQuery mShownQuery = TextQuery.empty();
    private int mTotal;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_toast_history);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(ToastHistoryViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.show();

        mRecordSwitch = findViewById(R.id.record_switch);
        mServiceOffGroup = findViewById(R.id.service_off_group);
        MaterialButton openSettings = findViewById(R.id.open_accessibility_settings);
        openSettings.setOnClickListener(v -> openAccessibilitySettings());
        mFilterLayout = findViewById(R.id.filter_layout);
        mFilterView = findViewById(R.id.filter_app);

        RecyclerView listView = findViewById(android.R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        mEmptyView = findViewById(android.R.id.empty);
        ((ImageView) mEmptyView.findViewById(R.id.empty_state_icon)).setImageResource(R.drawable.ic_history);
        mEmptyTitle = mEmptyView.findViewById(R.id.empty_state_title);
        mEmptySummary = mEmptyView.findViewById(R.id.empty_state_summary);
        mEmptyAction = mEmptyView.findViewById(R.id.empty_state_action);
        listView.setEmptyView(mEmptyView);
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        mAdapter = new ToastAdapter(this::copyRow);
        listView.setAdapter(mAdapter);

        mViewModel.getState().observe(this, this::showState);
        mViewModel.getCleared().observe(this, this::showCleared);
        // New toasts show up while the screen is open.
        LiveDataChanges.observe(ToastHistory.getInstance(this).getChanges(), this, mViewModel::load);
        mViewModel.load();
        refreshRecording();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from the system screen is what turns the service on.
        if (mRecordSwitch != null) {
            refreshRecording();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_toast_history_actions, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        AdvancedSearchView searchView = (AdvancedSearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setEnabledTypes(AdvancedSearchView.SEARCH_TYPE_CONTAINS | AdvancedSearchView.SEARCH_TYPE_REGEX);
            searchView.setQueryHint(getString(R.string.toast_history_search_hint));
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(@NonNull MenuItem item) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(@NonNull MenuItem item) {
                    mViewModel.setQuery(null, false);
                    return true;
                }
            });
            TextQuery query = mViewModel.getQuery();
            if (!query.isEmpty()) {
                // After a rotation: show the search that's still narrowing the list.
                searchItem.expandActionView();
                searchView.setQuery(query.getQuery(), false);
            }
            searchView.setOnQueryTextListener(this);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onQueryTextChange(@NonNull String newText, @AdvancedSearchView.SearchType int type) {
        mViewModel.setQuery(newText, type == AdvancedSearchView.SEARCH_TYPE_REGEX);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(@NonNull String query, @AdvancedSearchView.SearchType int type) {
        return onQueryTextChange(query, type);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_copy_all) {
            mViewModel.copy(mVisible);
        } else if (id == R.id.action_clear) {
            mViewModel.clear();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    private void refreshRecording() {
        boolean recording = Prefs.Privacy.isToastHistoryEnabled();
        // Detached first, or setting the state to match would count as the user turning it on.
        mRecordSwitch.setOnCheckedChangeListener(null);
        mRecordSwitch.setChecked(recording);
        mRecordSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> onRecordingToggled(isChecked));
        updateGuidance();
    }

    /**
     * The empty screen walks the user through the two steps, the setting and then the service, with a button for
     * whichever is missing. Once there are rows, a missing service gets a banner above them instead.
     */
    private void updateGuidance() {
        boolean recording = Prefs.Privacy.isToastHistoryEnabled();
        boolean serviceOn = BaseAccessibilityService.isAccessibilityEnabled(this);
        boolean empty = mVisible.isEmpty();
        mServiceOffGroup.setVisibility(recording && !serviceOn && !empty ? View.VISIBLE : View.GONE);
        if (mTotal > 0 && (!mShownQuery.isValid() || !mShownQuery.isEmpty())) {
            // The history has rows, the search hides them all.
            mEmptyTitle.setText(R.string.toast_history_no_matches_title);
            mEmptySummary.setText(mShownQuery.isValid() ? R.string.toast_history_no_matches
                    : R.string.search_regex_unusable);
            mEmptyAction.setOnClickListener(null);
            mEmptyAction.setVisibility(View.GONE);
            return;
        }
        mEmptyTitle.setText(R.string.toast_history_empty_title);
        if (!recording) {
            mEmptySummary.setText(R.string.toast_history_empty_off);
            mEmptyAction.setText(R.string.toast_history_turn_on);
            mEmptyAction.setOnClickListener(v -> mRecordSwitch.setChecked(true));
            mEmptyAction.setVisibility(View.VISIBLE);
        } else if (!serviceOn) {
            mEmptySummary.setText(R.string.toast_history_service_off);
            mEmptyAction.setText(R.string.toast_history_open_accessibility);
            mEmptyAction.setOnClickListener(v -> openAccessibilitySettings());
            mEmptyAction.setVisibility(View.VISIBLE);
        } else {
            mEmptySummary.setText(R.string.toast_history_empty_on);
            mEmptyAction.setOnClickListener(null);
            mEmptyAction.setVisibility(View.GONE);
        }
    }

    private void onRecordingToggled(boolean recording) {
        Prefs.Privacy.setToastHistoryEnabled(recording);
        refreshRecording();
        if (recording && !BaseAccessibilityService.isAccessibilityEnabled(this)) {
            openAccessibilitySettings();
        }
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            UIUtils.displayLongToast(R.string.toast_history_needs_accessibility);
        } catch (ActivityNotFoundException e) {
            UIUtils.displayLongToast(R.string.toast_history_open_settings_failed);
        }
    }

    private void showCleared(@Nullable ToastHistoryViewModel.Cleared cleared) {
        if (cleared == null) {
            return;
        }
        mViewModel.consumeCleared();
        List<ToastEntry> removed = cleared.removed;
        if (removed == null) {
            UIUtils.displayShortToast(R.string.toast_history_clear_failed);
            return;
        }
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.list), R.string.toast_history_cleared,
                Snackbar.LENGTH_LONG);
        if (!removed.isEmpty()) {
            snackbar.setAction(R.string.undo, v -> mViewModel.undoClear(removed));
        }
        snackbar.show();
    }

    private void copyRow(@NonNull ToastEntry entry) {
        mViewModel.copy(Collections.singletonList(entry));
    }

    private void showState(@NonNull ToastHistoryViewModel.State state) {
        mProgressIndicator.hide();
        mVisible = state.visible;
        mShownQuery = state.query;
        mTotal = state.total;
        mAdapter.setEntries(state.visible, state.appInfos);
        showFilter(state);
        updateGuidance();
    }

    private void showFilter(@NonNull ToastHistoryViewModel.State state) {
        mFilterLayout.setVisibility(state.total == 0 ? View.GONE : View.VISIBLE);
        List<String> labels = new ArrayList<>(state.apps.size() + 1);
        List<String> packages = new ArrayList<>(state.apps.size() + 1);
        labels.add(getString(R.string.toast_history_filter_all, state.total));
        packages.add(null);
        String selected = labels.get(0);
        for (ToastHistoryFilter.AppChoice app : state.apps) {
            String label = getString(R.string.toast_history_filter_item, app.label, app.count);
            labels.add(label);
            packages.add(app.packageName);
            if (app.packageName.equals(state.filterPackage)) {
                selected = label;
            }
        }
        mFilterView.setAdapter(new AnyFilterArrayAdapter<>(this,
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, labels));
        mFilterView.setText(selected, false);
        mFilterView.setOnItemClickListener((parent, view, position, id) ->
                mViewModel.setFilterPackage(packages.get(position)));
    }

    @VisibleForTesting
    @NonNull
    List<ToastEntry> getVisibleEntries() {
        return mVisible;
    }

    private interface OnEntryClickListener {
        void onEntryClick(@NonNull ToastEntry entry);
    }

    private static final class ToastAdapter extends RecyclerView.Adapter<ToastAdapter.ViewHolder> {
        private final List<ToastEntry> mEntries = new ArrayList<>();
        private final DateFormat mTimeFormat = ToastEntry.newTimeFormat();
        @NonNull
        private final OnEntryClickListener mListener;
        @NonNull
        private Map<String, ApplicationInfo> mAppInfos = Collections.emptyMap();

        ToastAdapter(@NonNull OnEntryClickListener listener) {
            mListener = listener;
        }

        void setEntries(@NonNull List<ToastEntry> entries, @NonNull Map<String, ApplicationInfo> appInfos) {
            mAppInfos = appInfos;
            AdapterUtils.notifyDataSetChanged(this, mEntries, entries);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_toast_history, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ToastEntry entry = mEntries.get(position);
            holder.time.setText(mTimeFormat.format(new Date(entry.timestampMillis)));
            holder.label.setText(entry.appLabel);
            holder.packageName.setText(entry.packageName);
            if (entry.text.isEmpty()) {
                holder.text.setText(R.string.toast_history_no_text);
            } else {
                holder.text.setText(entry.text);
            }
            holder.icon.setTag(entry.packageName);
            ImageLoader.getInstance().displayImage(entry.packageName, mAppInfos.get(entry.packageName), holder.icon);
            holder.itemView.setOnClickListener(v -> mListener.onEntryClick(entry));
            ViewCompat.replaceAccessibilityAction(holder.itemView,
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                    holder.itemView.getContext().getString(R.string.copy), null);
        }

        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView time;
            final TextView label;
            final TextView packageName;
            final TextView text;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.icon);
                time = itemView.findViewById(R.id.time);
                label = itemView.findViewById(R.id.label);
                packageName = itemView.findViewById(R.id.package_name);
                text = itemView.findViewById(R.id.text);
            }
        }
    }
}
