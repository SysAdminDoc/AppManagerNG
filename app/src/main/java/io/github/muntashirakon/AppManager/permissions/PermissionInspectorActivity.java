// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.widget.RecyclerView;

/**
 * Catalog screen for the Permission Inspector. Shows every curated dangerous
 * permission group with a count of "X of Y apps granted", tap to drill in.
 */
public class PermissionInspectorActivity extends BaseActivity {
    private PermissionInspectorViewModel mViewModel;
    private PermissionInspectorAdapter mAdapter;
    private LinearProgressIndicator mProgress;

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

        RecyclerView recycler = findViewById(R.id.recycler_view);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new PermissionInspectorAdapter(group -> {
            Intent i = new Intent(this, PermissionAppsActivity.class);
            i.putExtra(PermissionAppsActivity.EXTRA_GROUP_ID, group.id);
            startActivity(i);
        });
        recycler.setAdapter(mAdapter);

        mViewModel = new ViewModelProvider(this).get(PermissionInspectorViewModel.class);
        mViewModel.getRows().observe(this, rows -> mAdapter.submit(rows));
        mViewModel.getLoading().observe(this, loading -> {
            if (Boolean.TRUE.equals(loading)) mProgress.show();
            else mProgress.hide();
        });
        mViewModel.load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mViewModel != null) mViewModel.load();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
