// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

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
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.PopupMenu;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

/** Every watch rule, with a way to change its state or stop watching it. */
public class WatchedPermissionsActivity extends BaseActivity {
    private WatchedPermissionsViewModel mViewModel;
    private LinearProgressIndicator mProgressIndicator;
    private TextView mBanner;
    private RuleAdapter mAdapter;
    @NonNull
    private List<WatchRule> mRules = Collections.emptyList();

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_watched_permissions);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(WatchedPermissionsViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.show();
        mBanner = findViewById(R.id.banner);

        RecyclerView listView = findViewById(android.R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        View emptyView = findViewById(android.R.id.empty);
        ((ImageView) emptyView.findViewById(R.id.empty_state_icon)).setImageResource(R.drawable.ic_security);
        ((TextView) emptyView.findViewById(R.id.empty_state_title)).setText(R.string.permission_watcher_empty_title);
        ((TextView) emptyView.findViewById(R.id.empty_state_summary)).setText(R.string.permission_watcher_empty_summary);
        listView.setEmptyView(emptyView);
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        mAdapter = new RuleAdapter(new RuleAdapter.Listener() {
            @Override
            public void onRemove(@NonNull WatchRule rule) {
                mViewModel.remove(rule);
            }

            @Override
            public void onChangeState(@NonNull View anchor, @NonNull WatchRule rule) {
                showStateMenu(anchor, rule);
            }
        });
        listView.setAdapter(mAdapter);

        mViewModel.getState().observe(this, this::showState);
        mViewModel.getRemoved().observe(this, this::showRemoved);
        mViewModel.getCheckResult().observe(this, this::showCheckResult);
        mViewModel.load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The enforce setting or the mode of operation may have changed elsewhere.
        if (mViewModel != null) {
            mViewModel.load();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_watched_permissions_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_check_now) {
            mProgressIndicator.show();
            mViewModel.checkNow();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    private void showState(@NonNull WatchedPermissionsViewModel.State state) {
        mProgressIndicator.hide();
        mRules = state.rules;
        mAdapter.setRules(state.rules, state.appInfos, state.labels);
        @StringRes int banner = bannerFor(Prefs.Privacy.isPermissionWatcherEnforceEnabled(), state.canEnforce,
                !state.rules.isEmpty());
        if (banner == 0) {
            mBanner.setVisibility(View.GONE);
        } else {
            mBanner.setText(banner);
            mBanner.setVisibility(View.VISIBLE);
        }
    }

    /** Says why changes would only be reported. Nothing to say with no rules or when they can be put back. */
    @VisibleForTesting
    @StringRes
    static int bannerFor(boolean enforceEnabled, boolean canEnforce, boolean hasRules) {
        if (!hasRules) {
            return 0;
        }
        if (!enforceEnabled) {
            return R.string.permission_watcher_enforce_off_banner;
        }
        if (!canEnforce) {
            return R.string.permission_watcher_needs_privilege;
        }
        return 0;
    }

    private void showRemoved(@Nullable WatchedPermissionsViewModel.Removed removed) {
        if (removed == null) {
            return;
        }
        mViewModel.consumeRemoved();
        WatchRule rule = removed.rule;
        Snackbar.make(findViewById(android.R.id.list), getString(R.string.permission_watcher_rule_removed,
                        rule.shortName()), Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> mViewModel.undoRemove(rule))
                .show();
    }

    private void showCheckResult(@Nullable WatchedPermissionsViewModel.CheckResult result) {
        if (result == null) {
            return;
        }
        mViewModel.consumeCheckResult();
        mProgressIndicator.hide();
        PermissionWatcher.Report report = result.report;
        if (report == null) {
            UIUtils.displayShortToast(R.string.permission_watcher_check_failed);
        } else if (report.drifts.isEmpty()) {
            UIUtils.displayShortToast(R.string.permission_watcher_check_all_match);
        } else {
            int count = report.drifts.size();
            UIUtils.displayLongToast(getResources().getQuantityString(R.plurals.permission_watcher_check_drift,
                    count, count));
        }
    }

    private void showStateMenu(@NonNull View anchor, @NonNull WatchRule rule) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        addStateItem(menu, rule, WatchRule.STATE_ALLOW, R.string.permission_watcher_always_allow);
        addStateItem(menu, rule, WatchRule.STATE_DENY, R.string.permission_watcher_always_deny);
        addStateItem(menu, rule, WatchRule.STATE_IGNORE, R.string.permission_watcher_ignore);
        menu.setGroupCheckable(0, true, true);
        popup.setOnMenuItemClickListener(item -> {
            int state = item.getItemId();
            if (state != rule.state) {
                mViewModel.setState(rule, state);
            }
            return true;
        });
        popup.show();
    }

    private static void addStateItem(@NonNull Menu menu, @NonNull WatchRule rule, @WatchRule.State int state,
                                     @StringRes int title) {
        menu.add(0, state, Menu.NONE, title).setChecked(rule.state == state);
    }

    @VisibleForTesting
    @NonNull
    List<WatchRule> getRules() {
        return mRules;
    }

    @StringRes
    static int stateLabel(@WatchRule.State int state) {
        switch (state) {
            case WatchRule.STATE_ALLOW:
                return R.string.permission_watcher_state_allow;
            case WatchRule.STATE_DENY:
                return R.string.permission_watcher_state_deny;
            case WatchRule.STATE_IGNORE:
            default:
                return R.string.permission_watcher_state_ignore;
        }
    }

    @NonNull
    static String ruleTitle(@NonNull Context context, @NonNull WatchRule rule) {
        return rule.type == WatchRule.TYPE_APP_OP
                ? context.getString(R.string.permission_watcher_app_op, rule.name)
                : rule.shortName();
    }

    private static final class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.ViewHolder> {
        interface Listener {
            void onRemove(@NonNull WatchRule rule);

            void onChangeState(@NonNull View anchor, @NonNull WatchRule rule);
        }

        private final List<WatchRule> mRules = new ArrayList<>();
        @NonNull
        private final Listener mListener;
        @NonNull
        private Map<String, ApplicationInfo> mAppInfos = Collections.emptyMap();
        @NonNull
        private Map<String, String> mLabels = Collections.emptyMap();

        RuleAdapter(@NonNull Listener listener) {
            mListener = listener;
        }

        void setRules(@NonNull List<WatchRule> rules, @NonNull Map<String, ApplicationInfo> appInfos,
                      @NonNull Map<String, String> labels) {
            mAppInfos = appInfos;
            mLabels = labels;
            AdapterUtils.notifyDataSetChanged(this, mRules, rules);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_watched_permission, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WatchRule rule = mRules.get(position);
            Context context = holder.itemView.getContext();
            holder.name.setText(ruleTitle(context, rule));
            String label = mLabels.get(rule.packageName);
            holder.label.setText(label != null ? label : rule.packageName);
            holder.packageName.setText(rule.packageName);
            holder.state.setText(stateLabel(rule.state));
            holder.icon.setTag(rule.packageName);
            ImageLoader.getInstance().displayImage(rule.packageName, mAppInfos.get(rule.packageName), holder.icon);
            holder.remove.setOnClickListener(v -> mListener.onRemove(rule));
            holder.itemView.setOnClickListener(v -> mListener.onChangeState(holder.state, rule));
        }

        @Override
        public int getItemCount() {
            return mRules.size();
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final TextView label;
            final TextView packageName;
            final TextView state;
            final View remove;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.icon);
                name = itemView.findViewById(R.id.name);
                label = itemView.findViewById(R.id.label);
                packageName = itemView.findViewById(R.id.package_name);
                state = itemView.findViewById(R.id.state);
                remove = itemView.findViewById(R.id.remove);
            }
        }
    }
}
