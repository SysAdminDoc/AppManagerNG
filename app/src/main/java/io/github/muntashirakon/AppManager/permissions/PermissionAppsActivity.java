// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.widget.RecyclerView;

/**
 * Drilldown screen: every installed app that requests any permission in the
 * selected group, with per-app toggles and guarded bulk actions. Opened with
 * {@link #EXTRA_SPECIAL_ID} instead, it lists the apps a special app access
 * entry applies to, with a switch where App Manager can change it and a
 * Settings shortcut where it can't.
 */
public class PermissionAppsActivity extends BaseActivity {
    public static final String EXTRA_GROUP_ID = "group_id";
    public static final String EXTRA_SPECIAL_ID = "special_id";

    private PermissionAppsViewModel mViewModel;
    private PermissionAppsAdapter mAdapter;
    private PermissionGroupCatalog.Group mGroup;
    @Nullable
    private SpecialAccessCatalog.Entry mSpecial;
    private LinearProgressIndicator mProgress;
    private MaterialTextView mPermissionGroupSummary;
    private MaterialTextView mPermissionAppsSummary;
    private View mEmptyState;
    private MaterialTextView mEmptyTitle;
    private MaterialTextView mEmptySummary;
    private MaterialButton mEmptyAction;
    private MenuItem mRevokeAllMenu;
    private MenuItem mGrantAllMenu;
    private List<PermissionAppsViewModel.AppRow> mRows;
    private boolean mLoading;
    /** Set when a Settings page was opened, so the list reloads with what the user changed there. */
    private boolean mReloadOnResume;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        String groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        mGroup = groupId != null
                ? PermissionGroupCatalog.requireById(groupId)
                : null;
        mSpecial = mGroup == null
                ? SpecialAccessCatalog.findById(getIntent().getStringExtra(EXTRA_SPECIAL_ID))
                : null;
        if (mGroup == null && mSpecial == null) {
            finish();
            return;
        }
        int labelRes = mGroup != null ? mGroup.labelRes : mSpecial.labelRes;
        int summaryRes = mGroup != null ? mGroup.summaryRes : mSpecial.summaryRes;

        setContentView(R.layout.activity_permission_apps);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(labelRes);
        }

        mProgress = findViewById(R.id.progress_linear);
        mProgress.setVisibilityAfterHide(View.GONE);
        mPermissionGroupSummary = findViewById(R.id.permission_group_summary);
        mPermissionAppsSummary = findViewById(R.id.permission_apps_summary);
        mPermissionGroupSummary.setText(summaryRes);

        mEmptyState = findViewById(android.R.id.empty);
        mEmptyTitle = mEmptyState.findViewById(R.id.empty_state_title);
        mEmptySummary = mEmptyState.findViewById(R.id.empty_state_summary);
        mEmptyAction = mEmptyState.findViewById(R.id.empty_state_action);
        mEmptyTitle.setText(R.string.permission_inspector_empty_title);
        mEmptySummary.setText(getString(mGroup != null ? R.string.permission_inspector_no_apps
                : R.string.special_access_no_apps, getString(labelRes)));
        mEmptyAction.setText(R.string.refresh);
        mEmptyAction.setIconResource(R.drawable.ic_refresh);
        mEmptyAction.setVisibility(View.VISIBLE);
        mEmptyAction.setOnClickListener(v -> {
            if (mViewModel != null) mViewModel.load();
        });

        RecyclerView recycler = findViewById(R.id.recycler_view);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new PermissionAppsAdapter(row -> mViewModel.togglePermission(row),
                row -> mViewModel.referenceAction(row), this::openSettings);
        if (mSpecial != null) {
            mAdapter.setStatusLabels(mSpecial.grantedRes, mSpecial.deniedRes);
            mAdapter.setReferencesEnabled(false);
        }
        recycler.setAdapter(mAdapter);

        mViewModel = new ViewModelProvider(this).get(PermissionAppsViewModel.class);
        if (mGroup != null) mViewModel.setGroup(mGroup);
        else mViewModel.setSpecial(mSpecial);
        bindFilterChips();
        mViewModel.getRows().observe(this, rows -> {
            mRows = rows;
            mAdapter.submit(rows);
            mEmptyState.setVisibility(rows == null || rows.isEmpty() ? View.VISIBLE : View.GONE);
            updateSummary(rows);
            invalidateOptionsMenu();
        });
        mViewModel.getLoading().observe(this, loading -> {
            mLoading = Boolean.TRUE.equals(loading);
            mAdapter.setInteractionsEnabled(!mLoading);
            if (mLoading) mProgress.show();
            else mProgress.hide();
            invalidateOptionsMenu();
        });
        mViewModel.getToast().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
        mViewModel.getLastSkippedCount().observe(this, count -> {
            if (count != null && count > 0) {
                showInfoDialog();
            }
        });
        mViewModel.load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mReloadOnResume && mViewModel != null) {
            mReloadOnResume = false;
            mViewModel.load();
        }
    }

    private void openSettings(@NonNull PermissionAppsViewModel.AppRow row) {
        List<Intent> candidates = row.settingsIntents != null ? row.settingsIntents
                : PermissionSettingsLauncher.candidates(row.packageName, row.permissionNames);
        if (PermissionSettingsLauncher.launch(this, candidates)) {
            mReloadOnResume = true;
        } else {
            Toast.makeText(this, R.string.permission_settings_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void showInfoDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.perm_inspector_info_title)
                .setMessage(R.string.perm_inspector_info_body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_permission_apps_actions, menu);
        mRevokeAllMenu = menu.findItem(R.id.action_revoke_all);
        mGrantAllMenu = menu.findItem(R.id.action_grant_all);
        // Bulk changes stay with runtime permissions, special access is one app at a time
        mRevokeAllMenu.setVisible(mGroup != null);
        mGrantAllMenu.setVisible(mGroup != null);
        MenuItem info = menu.findItem(R.id.action_perm_inspector_info);
        if (info != null) info.setVisible(mGroup != null);
        updateMenuState();
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateMenuState();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_revoke_all) {
            showBulkActionConfirmation(false);
            return true;
        } else if (id == R.id.action_grant_all) {
            showBulkActionConfirmation(true);
            return true;
        } else if (id == R.id.action_perm_inspector_info) {
            showInfoDialog();
            return true;
        } else if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showBulkActionConfirmation(boolean grant) {
        if (mViewModel == null || mGroup == null || mLoading) return;
        int title = grant
                ? R.string.perm_apps_grant_all_confirm_title
                : R.string.perm_apps_revoke_all_confirm_title;
        int body = grant
                ? R.string.perm_apps_grant_all_confirm_body
                : R.string.perm_apps_revoke_all_confirm_body;
        int action = grant ? R.string.master_grant_all : R.string.master_revoke_all;
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(getString(body, getString(mGroup.labelRes)))
                .setPositiveButton(action, (dialog, which) -> {
                    if (grant) mViewModel.grantForAll();
                    else mViewModel.revokeForAll();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateSummary(@Nullable List<PermissionAppsViewModel.AppRow> rows) {
        if (mPermissionAppsSummary == null || rows == null) return;
        int granted = 0;
        int editable = 0;
        boolean known = true;
        for (PermissionAppsViewModel.AppRow row : rows) {
            if (row.anyGranted) granted++;
            if (row.anyModifiable) editable++;
            if (!row.stateKnown) known = false;
        }
        if (!known) {
            // A granted count would only cover the apps Android told us about
            mPermissionAppsSummary.setText(getResources().getQuantityString(
                    R.plurals.perm_inspector_count_unknown, rows.size(), rows.size()));
            return;
        }
        mPermissionAppsSummary.setText(getString(R.string.perm_apps_summary_stats,
                rows.size(), granted, editable));
    }

    private void updateMenuState() {
        if (mRevokeAllMenu != null) {
            mRevokeAllMenu.setEnabled(!mLoading && hasRevokableRows());
        }
        if (mGrantAllMenu != null) {
            mGrantAllMenu.setEnabled(!mLoading && hasGrantableRows());
        }
    }

    private boolean hasRevokableRows() {
        if (mRows == null) return false;
        for (PermissionAppsViewModel.AppRow row : mRows) {
            if (row.anyGranted && row.anyModifiable) return true;
        }
        return false;
    }

    private boolean hasGrantableRows() {
        if (mRows == null) return false;
        for (PermissionAppsViewModel.AppRow row : mRows) {
            if (!row.anyGranted && row.anyModifiable) return true;
        }
        return false;
    }

    /**
     * EI-04 — wire the three filter chips. The ChipGroup is single-selection
     * with selection-required so exactly one chip is always active.
     */
    private void bindFilterChips() {
        com.google.android.material.chip.ChipGroup group = findViewById(R.id.perm_apps_filter_chips);
        if (group == null || mViewModel == null) return;
        group.setOnCheckedStateChangeListener((chipGroup, checkedIds) -> {
            int id = checkedIds.isEmpty() ? R.id.chip_perm_filter_all : checkedIds.get(0);
            PermissionAppsViewModel.Filter filter;
            if (id == R.id.chip_perm_filter_user) {
                filter = PermissionAppsViewModel.Filter.USER_APPS;
            } else if (id == R.id.chip_perm_filter_granted) {
                filter = PermissionAppsViewModel.Filter.GRANTED;
            } else {
                filter = PermissionAppsViewModel.Filter.ALL;
            }
            mViewModel.setFilter(filter);
        });
    }
}
