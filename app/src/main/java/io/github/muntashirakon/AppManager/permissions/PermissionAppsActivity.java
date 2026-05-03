// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

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
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.widget.RecyclerView;

/**
 * Drilldown screen: every installed app that requests any permission in the
 * selected group, with a per-app toggle and a master "Revoke for all" menu
 * action. No confirmation dialogs by design.
 */
public class PermissionAppsActivity extends BaseActivity {
    public static final String EXTRA_GROUP_ID = "group_id";

    private PermissionAppsViewModel mViewModel;
    private PermissionAppsAdapter mAdapter;
    private LinearProgressIndicator mProgress;
    private MaterialTextView mEmptyView;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        String groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        PermissionGroupCatalog.Group group = groupId != null
                ? PermissionGroupCatalog.requireById(groupId)
                : null;
        if (group == null) {
            finish();
            return;
        }

        setContentView(R.layout.activity_permission_apps);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(group.labelRes);
        }

        mProgress = findViewById(R.id.progress_linear);
        mProgress.setVisibilityAfterHide(View.GONE);
        mEmptyView = findViewById(R.id.empty_view);

        RecyclerView recycler = findViewById(R.id.recycler_view);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new PermissionAppsAdapter(row -> mViewModel.togglePermission(row));
        recycler.setAdapter(mAdapter);

        mViewModel = new ViewModelProvider(this).get(PermissionAppsViewModel.class);
        mViewModel.setGroup(group);
        mViewModel.getRows().observe(this, rows -> {
            mAdapter.submit(rows);
            mEmptyView.setVisibility(rows == null || rows.isEmpty() ? View.VISIBLE : View.GONE);
        });
        mViewModel.getLoading().observe(this, loading -> {
            if (Boolean.TRUE.equals(loading)) mProgress.show();
            else mProgress.hide();
        });
        mViewModel.getToast().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
        mViewModel.load();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_permission_apps_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@androidx.annotation.NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_revoke_all) {
            if (mViewModel != null) mViewModel.revokeForAll();
            return true;
        } else if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
