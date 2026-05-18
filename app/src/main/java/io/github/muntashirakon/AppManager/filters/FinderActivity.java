// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Optional;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.view.ProgressIndicatorCompat;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.RecyclerView;

public class FinderActivity extends BaseActivity implements EditFiltersDialogFragment.OnSaveDialogButtonInterface {
    private FinderViewModel mViewModel;
    private LinearProgressIndicator mProgress;
    private RecyclerView mRecyclerView;
    private FinderAdapter mAdapter;
    private FloatingActionButton mFilterBtn;
    private Chip mActiveFiltersChip;
    private MultiSelectionView mMultiSelectionView;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_finder);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(FinderViewModel.class);
        mProgress = findViewById(R.id.progress_linear);
        mRecyclerView = findViewById(R.id.item_list);
        mFilterBtn = findViewById(R.id.floatingActionButton);
        mActiveFiltersChip = findViewById(R.id.finder_active_filters);
        mMultiSelectionView = findViewById(R.id.selection_view);
        UiUtils.applyWindowInsetsAsMargin(mFilterBtn);
        mAdapter = new FinderAdapter();
        mRecyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        mRecyclerView.setAdapter(mAdapter);
        mMultiSelectionView.hide();
        mFilterBtn.setOnClickListener(v -> showFiltersDialog());
        if (mActiveFiltersChip != null) {
            View.OnClickListener clearFiltersListener = v -> {
                mViewModel.clearFilters();
                updateActiveFiltersChip();
            };
            mActiveFiltersChip.setOnClickListener(clearFiltersListener);
            mActiveFiltersChip.setOnCloseIconClickListener(clearFiltersListener);
        }
        // Watch livedata
        mViewModel.getFilteredAppListLiveData().observe(this, list -> {
            ProgressIndicatorCompat.setVisibility(mProgress, false);
            mAdapter.setDefaultList(list);
            updateActiveFiltersChip();
        });
        mViewModel.getLastUpdateTimeLiveData().observe(this, time -> {
            CharSequence subtitle;
            if (time < 0) {
                subtitle = getString(R.string.loading);
            } else subtitle = getString(R.string.finder_loaded_at, DateUtils.formatDateTime(this, time));
            Optional.ofNullable(getSupportActionBar()).ifPresent(actionBar -> actionBar.setSubtitle(subtitle));
        });
        mViewModel.loadFilteredAppList(true);
        updateActiveFiltersChip();
    }

    private void showFiltersDialog() {
        EditFiltersDialogFragment dialog = new EditFiltersDialogFragment();
        dialog.setOnSaveDialogButtonInterface(this);
        dialog.show(getSupportFragmentManager(), EditFiltersDialogFragment.TAG);
    }

    @NonNull
    @Override
    public FilterItem getFilterItem() {
        return mViewModel.getFilterItem();
    }

    @Override
    public void onItemAltered(@NonNull FilterItem item) {
        mViewModel.loadFilteredAppList(false);
        updateActiveFiltersChip();
    }

    private void updateActiveFiltersChip() {
        if (mActiveFiltersChip == null || mViewModel == null) {
            return;
        }
        int count = mViewModel.getActiveFilterCount();
        mActiveFiltersChip.setText(getResources().getQuantityString(
                R.plurals.main_active_filters_clear, count, count));
        mActiveFiltersChip.setVisibility(mViewModel.hasActiveFilters() ? View.VISIBLE : View.GONE);
    }
}
