// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.TextQuery;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.LiveDataChanges;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.adapters.AnyFilterArrayAdapter;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.MaterialAutoCompleteTextView;
import io.github.muntashirakon.widget.RecyclerView;

/** What the notification filter did, newest first. Nothing here leaves the device. */
public class NotificationHistoryActivity extends BaseActivity implements AdvancedSearchView.OnQueryTextListener {
    /** The action filter's choices, in the order the dropdown lists them after "All". */
    private static final String[] ACTIONS = {
            NotificationFilterRule.Action.BLOCK.name(),
            NotificationFilterRule.Action.SILENT.name(),
            NotificationFilterRule.Action.VIBRATE.name(),
            NotificationFilterRule.Action.ALLOW.name(),
            NotificationHistoryEntry.ACTION_NONE,
    };

    private NotificationHistoryViewModel mViewModel;
    private LinearProgressIndicator mProgressIndicator;
    private View mFilterGroup;
    private MaterialAutoCompleteTextView mAppFilterView;
    private MaterialAutoCompleteTextView mActionFilterView;
    private TextView mEmptyTitle;
    private TextView mEmptySummary;
    private EntryAdapter mAdapter;
    @NonNull
    private List<NotificationHistoryEntry> mVisible = Collections.emptyList();

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_notification_history);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(NotificationHistoryViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.show();
        mFilterGroup = findViewById(R.id.filter_group);
        mAppFilterView = findViewById(R.id.filter_app);
        mActionFilterView = findViewById(R.id.filter_action);

        RecyclerView listView = findViewById(android.R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        View emptyView = findViewById(android.R.id.empty);
        ((ImageView) emptyView.findViewById(R.id.empty_state_icon)).setImageResource(R.drawable.ic_history);
        mEmptyTitle = emptyView.findViewById(R.id.empty_state_title);
        mEmptySummary = emptyView.findViewById(R.id.empty_state_summary);
        emptyView.findViewById(R.id.empty_state_action).setVisibility(View.GONE);
        listView.setEmptyView(emptyView);
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        mAdapter = new EntryAdapter();
        listView.setAdapter(mAdapter);

        mViewModel.getState().observe(this, this::showState);
        mViewModel.getCleared().observe(this, this::showCleared);
        LiveDataChanges.observe(NotificationHistory.getInstance(this).getChanges(), this, mViewModel::load);
        mViewModel.load();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_notification_history_actions, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        AdvancedSearchView searchView = (AdvancedSearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setEnabledTypes(AdvancedSearchView.SEARCH_TYPE_CONTAINS | AdvancedSearchView.SEARCH_TYPE_REGEX);
            searchView.setQueryHint(getString(R.string.notification_history_search_hint));
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
        } else if (id == R.id.action_clear) {
            mViewModel.clear();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    private void showCleared(@Nullable NotificationHistoryViewModel.Cleared cleared) {
        if (cleared == null) {
            return;
        }
        mViewModel.consumeCleared();
        List<NotificationHistoryEntry> removed = cleared.removed;
        if (removed == null) {
            UIUtils.displayShortToast(R.string.toast_history_clear_failed);
            return;
        }
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.list), R.string.notification_history_cleared,
                Snackbar.LENGTH_LONG);
        if (!removed.isEmpty()) {
            snackbar.setAction(R.string.undo, v -> mViewModel.undoClear(removed));
        }
        snackbar.show();
    }

    private void showState(@NonNull NotificationHistoryViewModel.State state) {
        mProgressIndicator.hide();
        mVisible = state.visible;
        mAdapter.setEntries(state.visible, state.appInfos);
        mFilterGroup.setVisibility(state.total == 0 ? View.GONE : View.VISIBLE);
        showAppFilter(state);
        showActionFilter(state);
        if (state.total == 0) {
            mEmptyTitle.setText(R.string.notification_history_empty_title);
            mEmptySummary.setText(R.string.notification_history_empty);
        } else {
            mEmptyTitle.setText(R.string.toast_history_no_matches_title);
            mEmptySummary.setText(state.query.isValid() ? R.string.notification_history_no_matches
                    : R.string.search_regex_unusable);
        }
    }

    private void showAppFilter(@NonNull NotificationHistoryViewModel.State state) {
        List<String> labels = new ArrayList<>(state.apps.size() + 1);
        List<String> packages = new ArrayList<>(state.apps.size() + 1);
        labels.add(getString(R.string.toast_history_filter_all, state.total));
        packages.add(null);
        String selected = labels.get(0);
        for (NotificationHistoryFilter.AppChoice app : state.apps) {
            String label = getString(R.string.toast_history_filter_item, app.label, app.count);
            labels.add(label);
            packages.add(app.packageName);
            if (app.packageName.equals(state.filterPackage)) {
                selected = label;
            }
        }
        mAppFilterView.setAdapter(new AnyFilterArrayAdapter<>(this,
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, labels));
        mAppFilterView.setText(selected, false);
        mAppFilterView.setOnItemClickListener((parent, view, position, id) ->
                mViewModel.setFilterPackage(packages.get(position)));
    }

    private void showActionFilter(@NonNull NotificationHistoryViewModel.State state) {
        List<String> labels = new ArrayList<>(ACTIONS.length + 1);
        List<String> actions = new ArrayList<>(ACTIONS.length + 1);
        labels.add(getString(R.string.notification_history_filter_action_all));
        actions.add(null);
        String selected = labels.get(0);
        for (String action : ACTIONS) {
            String label = getString(FilterLabels.ofSaved(action));
            labels.add(label);
            actions.add(action);
            if (action.equals(state.filterAction)) {
                selected = label;
            }
        }
        mActionFilterView.setAdapter(new AnyFilterArrayAdapter<>(this,
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item, labels));
        mActionFilterView.setText(selected, false);
        mActionFilterView.setOnItemClickListener((parent, view, position, id) ->
                mViewModel.setFilterAction(actions.get(position)));
    }

    @VisibleForTesting
    @NonNull
    List<NotificationHistoryEntry> getVisibleEntries() {
        return mVisible;
    }

    private static final class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.ViewHolder> {
        private final List<NotificationHistoryEntry> mEntries = new ArrayList<>();
        private final DateFormat mTimeFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
        @NonNull
        private Map<String, ApplicationInfo> mAppInfos = Collections.emptyMap();

        void setEntries(@NonNull List<NotificationHistoryEntry> entries,
                        @NonNull Map<String, ApplicationInfo> appInfos) {
            mAppInfos = appInfos;
            AdapterUtils.notifyDataSetChanged(this, mEntries, entries);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification_history, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            NotificationHistoryEntry entry = mEntries.get(position);
            Context context = holder.itemView.getContext();
            holder.label.setText(entry.appLabel);
            holder.packageName.setText(entry.packageName);
            holder.title.setText(entry.title);
            holder.title.setVisibility(entry.title.isEmpty() ? View.GONE : View.VISIBLE);
            holder.text.setText(entry.text);
            holder.text.setVisibility(entry.text.isEmpty() ? View.GONE : View.VISIBLE);
            String action = context.getString(FilterLabels.ofSaved(entry.action));
            if (entry.ruleName != null) {
                holder.outcome.setText(context.getString(R.string.notification_history_by_rule, action,
                        entry.ruleName));
            } else if (entry.byQuietMode) {
                holder.outcome.setText(context.getString(R.string.notification_history_by_quiet_mode, action));
            } else {
                holder.outcome.setText(action);
            }
            holder.time.setText(mTimeFormat.format(new Date(entry.timestampMillis)));
            holder.icon.setTag(entry.packageName);
            ImageLoader.getInstance().displayImage(entry.packageName, mAppInfos.get(entry.packageName), holder.icon);
        }

        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;
            final TextView packageName;
            final TextView title;
            final TextView text;
            final TextView outcome;
            final TextView time;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.icon);
                label = itemView.findViewById(R.id.label);
                packageName = itemView.findViewById(R.id.package_name);
                title = itemView.findViewById(R.id.title);
                text = itemView.findViewById(R.id.text);
                outcome = itemView.findViewById(R.id.outcome);
                time = itemView.findViewById(R.id.time);
            }
        }
    }
}
