// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.widget.RecyclerView;

/**
 * Catalog screen for the Permission Inspector. Shows every curated dangerous
 * permission group with a count of "X of Y apps granted", tap to drill in.
 */
public class PermissionInspectorActivity extends BaseActivity {
    private PermissionInspectorViewModel mViewModel;
    private PermissionInspectorAdapter mAdapter;
    private LinearProgressIndicator mProgress;
    private MaterialTextView mSummary;
    private MenuItem mRestoreCriticalMenu;
    private boolean mLoading;
    private boolean mRestoring;
    private final ExecutorService mRecoveryExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_permission_inspector);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.title_permission_inspector);
        }

        mProgress = findViewById(R.id.progress_linear);
        mProgress.setVisibilityAfterHide(View.GONE);
        mSummary = findViewById(R.id.inspector_summary);

        RecyclerView recycler = findViewById(R.id.recycler_view);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new PermissionInspectorAdapter(group -> {
            Intent i = new Intent(this, PermissionAppsActivity.class);
            i.putExtra(PermissionAppsActivity.EXTRA_GROUP_ID, group.id);
            startActivity(i);
        });
        recycler.setAdapter(mAdapter);

        mViewModel = new ViewModelProvider(this).get(PermissionInspectorViewModel.class);
        mViewModel.getRows().observe(this, rows -> {
            mAdapter.submit(rows);
            updateSummary(rows);
        });
        mViewModel.getLoading().observe(this, loading -> {
            mLoading = Boolean.TRUE.equals(loading);
            if (mLoading) mProgress.show();
            else if (!mRestoring) mProgress.hide();
            invalidateOptionsMenu();
        });
        mViewModel.load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mViewModel != null) mViewModel.load();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_permission_inspector_actions, menu);
        mRestoreCriticalMenu = menu.findItem(R.id.action_restore_critical);
        updateMenuState();
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateMenuState();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@androidx.annotation.NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_restore_critical) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.perm_recovery_confirm_title)
                    .setMessage(R.string.perm_recovery_confirm_body)
                    .setPositiveButton(R.string.perm_recovery_action, (d, w) -> runRecovery())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void runRecovery() {
        mRestoring = true;
        Toast.makeText(this, R.string.perm_recovery_running, Toast.LENGTH_SHORT).show();
        mProgress.show();
        invalidateOptionsMenu();
        mRecoveryExecutor.submit(() -> {
            try {
                PermissionRecovery.Result result =
                        PermissionRecovery.restoreAll(new AppOpsManagerCompat());
                runOnUiThread(() -> {
                    finishRecoveryUi();
                    String msg = getString(R.string.perm_recovery_done_fmt,
                            result.packagesProcessed,
                            result.permissionsRestored,
                            result.rulesCleared);
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    if (mViewModel != null) mViewModel.load();
                });
            } catch (Throwable th) {
                runOnUiThread(() -> {
                    finishRecoveryUi();
                    Toast.makeText(this, R.string.perm_recovery_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateSummary(@Nullable List<PermissionInspectorViewModel.Row> rows) {
        if (mSummary == null || rows == null) return;
        int requested = 0;
        int granted = 0;
        for (PermissionInspectorViewModel.Row row : rows) {
            requested += row.requestedCount;
            granted += row.grantedCount;
        }
        mSummary.setText(getString(R.string.perm_inspector_summary_stats,
                rows.size(), requested, granted));
    }

    private void finishRecoveryUi() {
        mRestoring = false;
        if (!mLoading) mProgress.hide();
        invalidateOptionsMenu();
    }

    private void updateMenuState() {
        if (mRestoreCriticalMenu != null) {
            mRestoreCriticalMenu.setEnabled(!mLoading && !mRestoring);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRecoveryExecutor.shutdownNow();
    }
}
