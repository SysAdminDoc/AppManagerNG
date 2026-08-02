// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Optional;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.filters.preset.FilterPresetStore;
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
    private FilterPresetStore mPresetStore;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_finder);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(FinderViewModel.class);
        mPresetStore = new FilterPresetStore(this);
        Optional.ofNullable(getSupportActionBar())
                .ifPresent(actionBar -> actionBar.setDisplayHomeAsUpEnabled(true));
        mProgress = findViewById(R.id.progress_linear);
        mRecyclerView = findViewById(R.id.item_list);
        mFilterBtn = findViewById(R.id.floatingActionButton);
        mActiveFiltersChip = findViewById(R.id.finder_active_filters);
        mMultiSelectionView = findViewById(R.id.selection_view);
        UiUtils.applyWindowInsetsAsMargin(mFilterBtn);
        View emptyState = findViewById(android.R.id.empty);
        ImageView emptyIcon = emptyState.findViewById(R.id.empty_state_icon);
        emptyIcon.setImageResource(R.drawable.ic_filter_list);
        ((TextView) emptyState.findViewById(R.id.empty_state_title))
                .setText(R.string.finder_empty_title);
        ((TextView) emptyState.findViewById(R.id.empty_state_summary))
                .setText(R.string.finder_empty_message);
        emptyState.setVisibility(View.GONE);
        mAdapter = new FinderAdapter();
        mRecyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        mRecyclerView.setEmptyView(emptyState);
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

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_finder_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.action_finder_presets) {
            showPresetsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
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

    private void showPresetsDialog() {
        List<FilterPresetStore.Preset> presets = mPresetStore.all();
        String[] labels = new String[presets.size()];
        for (int i = 0; i < presets.size(); ++i) {
            labels[i] = presets.get(i).name;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.finder_saved_filters)
                .setPositiveButton(R.string.finder_save_filter, (dialog, which) -> showPresetNameDialog(null))
                .setNegativeButton(R.string.cancel, null);
        if (presets.isEmpty()) {
            builder.setMessage(R.string.finder_no_saved_filters);
        } else {
            builder.setItems(labels, (dialog, which) -> loadPreset(presets.get(which)))
                    .setNeutralButton(R.string.finder_manage_filters,
                            (dialog, which) -> showPresetManagementDialog());
        }
        builder.show();
    }

    private void loadPreset(@NonNull FilterPresetStore.Preset preset) {
        mViewModel.applyFilterItem(preset.filter);
        mViewModel.loadFilteredAppList(false);
        updateActiveFiltersChip();
        UIUtils.displayShortToast(getString(R.string.finder_filter_loaded, preset.name));
    }

    private void showPresetManagementDialog() {
        List<FilterPresetStore.Preset> presets = mPresetStore.all();
        if (presets.isEmpty()) {
            showPresetsDialog();
            return;
        }
        String[] labels = new String[presets.size()];
        for (int i = 0; i < presets.size(); ++i) {
            labels[i] = presets.get(i).name;
        }
        final int[] selected = {-1};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.finder_manage_filters)
                .setSingleChoiceItems(labels, -1, (dialog, which) -> selected[0] = which)
                .setPositiveButton(R.string.rename, (dialog, which) -> {
                    if (selected[0] >= 0) showPresetNameDialog(presets.get(selected[0]));
                    else UIUtils.displayShortToast(R.string.finder_select_filter_first);
                })
                .setNegativeButton(R.string.delete, (dialog, which) -> {
                    if (selected[0] >= 0) confirmDeletePreset(presets.get(selected[0]));
                    else UIUtils.displayShortToast(R.string.finder_select_filter_first);
                })
                .setNeutralButton(R.string.cancel, null)
                .show();
    }

    private void confirmDeletePreset(@NonNull FilterPresetStore.Preset preset) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.finder_delete_filter_title)
                .setMessage(getString(R.string.finder_delete_filter_message, preset.name))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (mPresetStore.remove(preset.id)) {
                        UIUtils.displayShortToast(R.string.finder_filter_deleted);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showPresetNameDialog(@Nullable FilterPresetStore.Preset existing) {
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint(R.string.finder_filter_name_hint);
        TextInputEditText input = new TextInputEditText(inputLayout.getContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (existing != null) input.setText(existing.name);
        inputLayout.addView(input, new LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT));
        inputLayout.setPadding(getResources().getDimensionPixelSize(R.dimen.premium_space_16), 0,
                getResources().getDimensionPixelSize(R.dimen.premium_space_16), 0);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.finder_save_filter : R.string.finder_rename_filter)
                .setView(inputLayout)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            input.selectAll();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                FilterPresetStore.Preset result = existing == null
                        ? mPresetStore.save(input.getText(), mViewModel.getFilterItem())
                        : mPresetStore.rename(existing.id, input.getText());
                if (result == null) {
                    inputLayout.setError(getString(R.string.finder_filter_name_invalid));
                    return;
                }
                dialog.dismiss();
                UIUtils.displayShortToast(existing == null
                        ? getString(R.string.finder_filter_saved, result.name)
                        : getString(R.string.finder_filter_renamed, result.name));
            });
        });
        dialog.show();
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
