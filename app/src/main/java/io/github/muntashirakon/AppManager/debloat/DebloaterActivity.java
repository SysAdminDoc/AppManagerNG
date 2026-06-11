// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreeze;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.batchops.struct.BatchFreezeOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchSafetyOptions;
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.profiles.AddToProfileDialogFragment;
import io.github.muntashirakon.AppManager.safety.CriticalPackageGuard;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.multiselection.MultiSelectionActionsView;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.RecyclerView;

public class DebloaterActivity extends BaseActivity implements MultiSelectionView.OnSelectionChangeListener,
        MultiSelectionActionsView.OnItemSelectedListener, AdvancedSearchView.OnQueryTextListener,
        MultiSelectionView.OnSelectionModeChangeListener {
    DebloaterViewModel viewModel;

    private LinearProgressIndicator mProgressIndicator;
    private MultiSelectionView mMultiSelectionView;
    private DebloaterRecyclerViewAdapter mAdapter;
    private AdvancedSearchView mSearchView;
    private MaterialTextView mListSummaryView;
    @Nullable
    private Menu mSelectionMenu;
    private View mEmptyState;
    private TextView mEmptyStateTitle;
    private TextView mEmptyStateSummary;
    private MaterialButton mEmptyStateAction;

    private final StoragePermission mStoragePermission = StoragePermission.init(this);
    private final BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mProgressIndicator != null) {
                mProgressIndicator.hide();
            }
            // A batch op (uninstall/freeze) just changed install/frozen state — reload it so
            // the rows, filters, and "Put back" affordance no longer act on stale data.
            if (viewModel != null) {
                viewModel.loadPackages(true);
            }
        }
    };
    private final OnBackPressedCallback mOnBackPressedCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (mAdapter != null && mMultiSelectionView != null && mAdapter.isInSelectionMode()) {
                mMultiSelectionView.cancel();
                return;
            }
            setEnabled(false);
            getOnBackPressedDispatcher().onBackPressed();
        }
    };

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_debloater);
        setSupportActionBar(findViewById(R.id.toolbar));
        getOnBackPressedDispatcher().addCallback(this, mOnBackPressedCallback);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowCustomEnabled(true);
            mSearchView = UIUtils.setupAdvancedSearchView(actionBar, this);
        }
        viewModel = new ViewModelProvider(this).get(DebloaterViewModel.class);

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.show();
        mListSummaryView = findViewById(R.id.debloater_summary);
        mListSummaryView.setVisibility(View.VISIBLE);
        mListSummaryView.setText(R.string.debloater_status_loading);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        mEmptyState = findViewById(android.R.id.empty);
        configureEmptyState();
        recyclerView.setEmptyView(mEmptyState);
        recyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        mAdapter = new DebloaterRecyclerViewAdapter(this);
        mAdapter.setHasStableIds(true);
        recyclerView.setAdapter(mAdapter);
        mEmptyState.setVisibility(View.GONE);
        mMultiSelectionView = findViewById(R.id.selection_view);
        mMultiSelectionView.setAdapter(mAdapter);
        mMultiSelectionView.hide();
        mMultiSelectionView.setOnItemSelectedListener(this);
        mMultiSelectionView.setOnSelectionModeChangeListener(this);
        mMultiSelectionView.setOnSelectionChangeListener(this);
        mSelectionMenu = mMultiSelectionView.getMenu();

        viewModel.getDebloatObjectListLiveData().observe(this, debloatObjects -> {
            mProgressIndicator.hide();
            mAdapter.setAdapterList(debloatObjects);
            updateDebloaterState(debloatObjects.size());
        });
        viewModel.loadPackages();
    }

    private void configureEmptyState() {
        ImageView icon = mEmptyState.findViewById(R.id.empty_state_icon);
        icon.setImageResource(R.drawable.ic_package);
        mEmptyStateTitle = mEmptyState.findViewById(R.id.empty_state_title);
        mEmptyStateSummary = mEmptyState.findViewById(R.id.empty_state_summary);
        mEmptyStateAction = mEmptyState.findViewById(R.id.empty_state_action);
        mEmptyStateAction.setOnClickListener(v -> handleEmptyStateAction());
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(this, mBatchOpsBroadCastReceiver,
                new IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBatchOpsBroadCastReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_debloater_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_list_options) {
            DebloaterListOptions dialog = new DebloaterListOptions();
            dialog.show(getSupportFragmentManager(), DebloaterListOptions.TAG);
            return true;
        } else if (id == R.id.action_debloat_presets) {
            showDebloatPresetPicker();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSelectionModeEnabled() {
        mOnBackPressedCallback.setEnabled(true);
    }

    @Override
    public void onSelectionModeDisabled() {
        mOnBackPressedCallback.setEnabled(false);
    }

    @Override
    public boolean onSelectionChange(int selectionCount) {
        updateSelectionActions();
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_uninstall) {
            showDebloatUninstallDialog();
        } else if (id == R.id.action_put_back) {
            showDebloatPutBackDialog();
        } else if (id == R.id.action_freeze_unfreeze) {
            showFreezeUnfreezeDialog(Prefs.Blocking.getDefaultFreezingMethod());
        } else if (id == R.id.action_save_apk) {
            mStoragePermission.request(granted -> {
                if (granted) handleBatchOp(BatchOpsManager.OP_BACKUP_APK);
            });
        } else if (id == R.id.action_block_unblock_trackers) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.block_unblock_trackers)
                    .setMessage(R.string.choose_what_to_do)
                    .setPositiveButton(R.string.block, (dialog, which) ->
                            handleBatchOp(BatchOpsManager.OP_BLOCK_TRACKERS))
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.unblock, (dialog, which) ->
                            handleBatchOp(BatchOpsManager.OP_UNBLOCK_TRACKERS))
                    .show();
        } else if (id == R.id.action_add_to_profile) {
            AddToProfileDialogFragment dialog = AddToProfileDialogFragment.getInstance(viewModel.getSelectedPackages()
                    .keySet().toArray(new String[0]));
            dialog.show(getSupportFragmentManager(), AddToProfileDialogFragment.TAG);
        } else return false;
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText, int type) {
        if (mProgressIndicator != null) {
            mProgressIndicator.show();
        }
        viewModel.setQuery(newText, type);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query, int type) {
        return false;
    }

    private void updateDebloaterState(int displayedItemCount) {
        if (viewModel == null) {
            return;
        }
        int totalItemCount = viewModel.getTotalItemCount();
        if (mListSummaryView != null) {
            String summary;
            if (displayedItemCount == totalItemCount) {
                summary = getResources().getQuantityString(R.plurals.debloater_status_all_recommendations,
                        displayedItemCount, displayedItemCount);
            } else {
                summary = getResources().getQuantityString(R.plurals.debloater_status_showing_recommendations,
                        totalItemCount, displayedItemCount, totalItemCount);
            }
            if (viewModel.hasActiveFilters()) {
                summary = summary + " · " + getString(R.string.debloater_status_filters_active);
            }
            mListSummaryView.setVisibility(View.VISIBLE);
            mListSummaryView.setText(summary);
        }
        if (mEmptyState != null) {
            mEmptyState.setVisibility(displayedItemCount > 0 ? View.GONE : View.VISIBLE);
        }
        if (displayedItemCount > 0 || mEmptyState == null) {
            return;
        }
        if (viewModel.hasSearchQuery()) {
            mEmptyStateTitle.setText(R.string.debloater_empty_title_no_matches);
            mEmptyStateSummary.setText(R.string.debloater_empty_message_search);
            mEmptyStateAction.setText(R.string.debloater_empty_action_clear_search);
            mEmptyStateAction.setIconResource(com.google.android.material.R.drawable.mtrl_ic_cancel);
        } else if (viewModel.hasActiveFilters()) {
            mEmptyStateTitle.setText(R.string.debloater_empty_title_no_matches);
            mEmptyStateSummary.setText(R.string.debloater_empty_message_filters);
            mEmptyStateAction.setText(R.string.clear_filters);
            mEmptyStateAction.setIconResource(R.drawable.ic_filter_list);
        } else {
            mEmptyStateTitle.setText(R.string.debloater_empty_title_no_recommendations);
            mEmptyStateSummary.setText(R.string.debloater_empty_message_no_recommendations);
            mEmptyStateAction.setText(R.string.refresh);
            mEmptyStateAction.setIconResource(R.drawable.ic_refresh);
        }
    }

    private void updateSelectionActions() {
        if (mSelectionMenu == null || viewModel == null) {
            return;
        }
        DebloaterPutBackPlan putBackPlan = DebloaterPutBackPlan.fromSelection(viewModel.getSelectedDebloatObjects());
        MenuItem putBackItem = mSelectionMenu.findItem(R.id.action_put_back);
        putBackItem.setVisible(putBackPlan.hasRestorableTargets());
        putBackItem.setEnabled(putBackPlan.hasRestorableTargets() && SelfPermissions.canInstallExistingPackages());
    }

    private void handleEmptyStateAction() {
        if (viewModel == null) {
            return;
        }
        if (mProgressIndicator != null) {
            mProgressIndicator.show();
        }
        if (viewModel.hasSearchQuery()) {
            if (mSearchView != null) {
                mSearchView.setQuery("", true);
            } else {
                viewModel.setQuery("", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
            }
        } else if (viewModel.hasActiveFilters()) {
            viewModel.clearFilters();
        } else {
            // No search/filter to clear: this is a genuine refresh, so recompute install state.
            viewModel.loadPackages(true);
        }
    }

    private void showFreezeUnfreezeDialog(int freezeType) {
        View view = View.inflate(this, R.layout.item_checkbox, null);
        MaterialCheckBox checkBox = view.findViewById(R.id.checkbox);
        checkBox.setText(R.string.freeze_prefer_per_app_option);
        FreezeUnfreeze.getFreezeDialog(this, freezeType)
                .setIcon(R.drawable.ic_snowflake)
                .setTitle(R.string.freeze_unfreeze)
                .setView(view)
                .setPositiveButton(R.string.freeze, (dialog, which, selectedItem) -> {
                    if (selectedItem == null) {
                        return;
                    }
                    List<DebloatObject> selectedObjects = viewModel != null
                            ? viewModel.getSelectedDebloatObjects() : java.util.Collections.emptyList();
                    runWithCriticalPackageConfirmation(selectedObjects, allowCriticalPackages -> {
                        BatchFreezeOptions options = new BatchFreezeOptions(selectedItem, checkBox.isChecked(),
                                allowCriticalPackages);
                        handleBatchOp(BatchOpsManager.OP_ADVANCED_FREEZE, options);
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.unfreeze, (dialog, which, selectedItem) ->
                        handleBatchOp(BatchOpsManager.OP_UNFREEZE))
                .show();
    }

    private void showDebloatPresetPicker() {
        if (viewModel == null) return;
        DebloatPreset[] presets = DebloatPreset.values();
        String[] labels = new String[presets.length];
        int[] counts = new int[presets.length];
        for (int i = 0; i < presets.length; ++i) {
            DebloatPreset preset = presets[i];
            counts[i] = viewModel.countPresetMatches(preset);
            labels[i] = getString(preset.getTitleRes()) + "\n"
                    + getResources().getQuantityString(R.plurals.debloat_preset_match_count, counts[i], counts[i]);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.debloat_presets)
                .setMessage(R.string.debloat_presets_intro)
                .setItems(labels, (dialog, which) -> showDebloatPresetPreview(presets[which], counts[which]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDebloatPresetPreview(@NonNull DebloatPreset preset, int matchCount) {
        if (matchCount == 0) {
            showNoDebloatPresetMatches(preset);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.debloat_preset_preview_title, getString(preset.getTitleRes())))
                .setMessage(buildDebloatPresetPreviewMessage(preset, matchCount))
                .setPositiveButton(getDebloatPresetActionLabel(preset), (dialog, which) -> {
                    if (!selectDebloatPreset(preset)) {
                        showNoDebloatPresetMatches(preset);
                        return;
                    }
                    if (preset.getRecommendedAction() == DebloatPreset.ACTION_FREEZE) {
                        showFreezeUnfreezeDialog(Prefs.Blocking.getDefaultFreezingMethod());
                    } else {
                        showDebloatUninstallDialog();
                    }
                })
                .setNeutralButton(R.string.review_selection, (dialog, which) -> selectDebloatPreset(preset))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @NonNull
    private String buildDebloatPresetPreviewMessage(@NonNull DebloatPreset preset, int matchCount) {
        String action = getString(preset.getRecommendedAction() == DebloatPreset.ACTION_FREEZE
                ? R.string.freeze : R.string.remove_for_user);
        return getString(preset.getSummaryRes()) + "\n\n"
                + getResources().getQuantityString(R.plurals.debloat_preset_match_count, matchCount, matchCount)
                + "\n"
                + getString(R.string.debloat_preset_recommended_action, action);
    }

    private int getDebloatPresetActionLabel(@NonNull DebloatPreset preset) {
        return preset.getRecommendedAction() == DebloatPreset.ACTION_FREEZE
                ? R.string.debloat_preset_action_freeze
                : R.string.debloat_preset_action_remove;
    }

    private boolean selectDebloatPreset(@NonNull DebloatPreset preset) {
        int selected = viewModel.selectPreset(preset);
        if (selected == 0) {
            return false;
        }
        mAdapter.notifyDataSetChanged();
        mMultiSelectionView.show();
        mMultiSelectionView.updateCounter(false);
        return true;
    }

    private void showNoDebloatPresetMatches(@NonNull DebloatPreset preset) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.debloat_preset_no_matches_title)
                .setMessage(getString(R.string.debloat_preset_no_matches_message,
                        getString(preset.getTitleRes())))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showDebloatUninstallDialog() {
        if (viewModel == null) return;
        List<DebloatObject> selectedObjects = viewModel.getSelectedDebloatObjects();
        if (selectedObjects.isEmpty()) return;
        boolean usesPmUninstall = RootlessDebloat.canUsePmUninstall();
        boolean hasOemFallbacks = hasOemUninstallFallbacks(selectedObjects);
        // NF-16 — compute role-loss impact on a worker thread first; the
        // RoleManager calls below add up to ~10 IPCs on API 29+.
        List<String> selectedPackages = new ArrayList<>(selectedObjects.size());
        for (DebloatObject obj : selectedObjects) {
            selectedPackages.add(obj.packageName);
        }
        DebloatImpactPreview.Result impact = DebloatImpactPreview.compute(this, selectedPackages);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rootless_debloat_uninstall_title)
                .setMessage(buildDebloatUninstallMessage(selectedObjects, usesPmUninstall, impact))
                .setPositiveButton(hasOemFallbacks ? R.string.debloat_oem_safe_action
                                : (usesPmUninstall ? R.string.remove_for_user : R.string.uninstall),
                        (dialog, which) -> {
                            runWithCriticalPackageConfirmation(selectedObjects, allowCriticalPackages -> {
                                if (hasOemFallbacks) {
                                    handleDebloatWithOemFallback(selectedObjects, allowCriticalPackages);
                                } else {
                                    handleBatchOp(BatchOpsManager.OP_UNINSTALL,
                                            allowCriticalPackages ? new BatchSafetyOptions(true) : null);
                                }
                            });
                        })
                .setNegativeButton(R.string.cancel, null);
        if (hasOemFallbacks) {
            builder.setNeutralButton(usesPmUninstall ? R.string.remove_for_user : R.string.uninstall,
                    (dialog, which) -> runWithCriticalPackageConfirmation(selectedObjects,
                            allowCriticalPackages -> handleBatchOp(BatchOpsManager.OP_UNINSTALL,
                                    allowCriticalPackages ? new BatchSafetyOptions(true) : null)));
        }
        builder.show();
    }

    private void showDebloatPutBackDialog() {
        if (viewModel == null) return;
        DebloaterPutBackPlan putBackPlan = DebloaterPutBackPlan.fromSelection(viewModel.getSelectedDebloatObjects());
        if (!putBackPlan.hasRestorableTargets()) {
            UIUtils.displayShortToast(R.string.debloat_put_back_no_restorable_selection);
            return;
        }
        if (!SelfPermissions.canInstallExistingPackages()) {
            UIUtils.displayShortToast(R.string.debloat_put_back_requires_privileged_mode);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.system_app_put_back)
                .setMessage(putBackPlan.buildConfirmationMessage(this))
                .setPositiveButton(R.string.system_app_put_back, (dialog, which) -> {
                    startBatchOp(BatchOpsManager.OP_INSTALL_EXISTING,
                            viewModel.getPackagesWithUsers(putBackPlan.getRestorableObjects()), null);
                    mMultiSelectionView.cancel();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @NonNull
    private String buildDebloatUninstallMessage(@NonNull List<DebloatObject> selectedObjects,
                                                boolean usesPmUninstall) {
        return buildDebloatUninstallMessage(selectedObjects, usesPmUninstall,
                new DebloatImpactPreview.Result(java.util.Collections.emptyMap()));
    }

    @NonNull
    private String buildDebloatUninstallMessage(@NonNull List<DebloatObject> selectedObjects,
                                                boolean usesPmUninstall,
                                                @NonNull DebloatImpactPreview.Result impact) {
        int safe = 0;
        int replace = 0;
        int caution = 0;
        int unsafe = 0;
        int dependencyWarnings = 0;
        int updatedSystemApps = 0;
        int oemFallbacks = 0;
        List<String> reviewFirst = new ArrayList<>();
        List<String> oemFallbackExamples = new ArrayList<>();
        for (DebloatObject debloatObject : selectedObjects) {
            switch (debloatObject.getRemoval()) {
                case DebloatObject.REMOVAL_SAFE:
                    ++safe;
                    break;
                case DebloatObject.REMOVAL_REPLACE:
                    ++replace;
                    break;
                case DebloatObject.REMOVAL_UNSAFE:
                    ++unsafe;
                    break;
                default:
                case DebloatObject.REMOVAL_CAUTION:
                    ++caution;
                    break;
            }
            boolean hasDependencyWarning = debloatObject.getDependencies().length > 0
                    || debloatObject.getRequiredBy().length > 0;
            if (hasDependencyWarning) {
                ++dependencyWarnings;
            }
            if (debloatObject.isUpdatedSystemApp()) {
                ++updatedSystemApps;
            }
            if (OemBloatRiskTable.getUninstallFallback(debloatObject.packageName) != null) {
                ++oemFallbacks;
                if (oemFallbackExamples.size() < 5) {
                    oemFallbackExamples.add(debloatObject.getLabelOrPackageName().toString());
                }
            }
            if ((debloatObject.getRemoval() >= DebloatObject.REMOVAL_CAUTION || hasDependencyWarning)
                    && reviewFirst.size() < 5) {
                reviewFirst.add(debloatObject.getLabelOrPackageName().toString());
            }
        }
        StringBuilder message = new StringBuilder();
        if (usesPmUninstall) {
            message.append(getString(R.string.rootless_debloat_confirmation_intro_pm,
                    RootlessDebloat.getProviderLabel(this)));
        } else {
            message.append(getString(R.string.rootless_debloat_confirmation_intro_standard));
        }
        message.append("\n\n")
                .append(getString(R.string.rootless_debloat_confirmation_safety,
                        safe, replace, caution, unsafe));
        if (dependencyWarnings > 0) {
            message.append("\n")
                    .append(getResources().getQuantityString(
                            R.plurals.rootless_debloat_dependency_warnings,
                            dependencyWarnings, dependencyWarnings));
        }
        if (updatedSystemApps > 0) {
            message.append("\n")
                    .append(getResources().getQuantityString(
                            R.plurals.rootless_debloat_factory_reset_updates,
                            updatedSystemApps, updatedSystemApps));
        }
        if (oemFallbacks > 0) {
            message.append("\n")
                    .append(getResources().getQuantityString(
                            R.plurals.rootless_debloat_oem_disable_fallbacks,
                            oemFallbacks, oemFallbacks, TextUtils.join(", ", oemFallbackExamples)));
        }
        if (!reviewFirst.isEmpty()) {
            message.append("\n")
                    .append(getString(R.string.rootless_debloat_confirmation_risky_examples,
                            TextUtils.join(", ", reviewFirst)));
        }
        // NF-16 — surface default-app role losses inline so users see when a
        // selection includes the current SMS / dialer / browser / home handler.
        if (impact.hasAny()) {
            message.append("\n\n")
                    .append(getResources().getQuantityString(
                            R.plurals.debloat_impact_preview_header,
                            impact.roleLosses.size(),
                            impact.roleLosses.size()))
                    .append("\n")
                    .append(DebloatImpactPreview.render(impact));
        }
        message.append("\n\n").append(getString(oemFallbacks > 0
                ? R.string.rootless_debloat_confirmation_oem_footer
                : R.string.rootless_debloat_confirmation_footer));
        return message.toString();
    }

    private boolean hasOemUninstallFallbacks(@NonNull List<DebloatObject> selectedObjects) {
        for (DebloatObject debloatObject : selectedObjects) {
            if (OemBloatRiskTable.getUninstallFallback(debloatObject.packageName) != null) {
                return true;
            }
        }
        return false;
    }

    private void handleDebloatWithOemFallback(@NonNull List<DebloatObject> selectedObjects,
                                              boolean allowCriticalPackages) {
        if (viewModel == null) return;
        List<DebloatObject> uninstallObjects = new ArrayList<>();
        List<DebloatObject> freezeObjects = new ArrayList<>();
        for (DebloatObject debloatObject : selectedObjects) {
            if (OemBloatRiskTable.getUninstallFallback(debloatObject.packageName) != null) {
                freezeObjects.add(debloatObject);
            } else {
                uninstallObjects.add(debloatObject);
            }
        }
        if (!uninstallObjects.isEmpty()) {
            startBatchOp(BatchOpsManager.OP_UNINSTALL, viewModel.getPackagesWithUsers(uninstallObjects),
                    allowCriticalPackages ? new BatchSafetyOptions(true) : null);
        }
        if (!freezeObjects.isEmpty()) {
            BatchFreezeOptions options = new BatchFreezeOptions(Prefs.Blocking.getDefaultFreezingMethod(), false,
                    allowCriticalPackages);
            startBatchOp(BatchOpsManager.OP_ADVANCED_FREEZE, viewModel.getPackagesWithUsers(freezeObjects), options);
        }
        mMultiSelectionView.cancel();
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op) {
        handleBatchOp(op, null);
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op, @Nullable IBatchOpOptions options) {
        if (viewModel == null) return;
        startBatchOp(op, viewModel.getSelectedPackagesWithUsers(), options);
        mMultiSelectionView.cancel();
    }

    private void startBatchOp(@BatchOpsManager.OpType int op, @NonNull ArrayList<UserPackagePair> userPackagePairs,
                              @Nullable IBatchOpOptions options) {
        if (userPackagePairs.isEmpty()) return;
        if (mProgressIndicator != null) {
            mProgressIndicator.show();
        }
        BatchOpsManager.Result input = new BatchOpsManager.Result(userPackagePairs);
        BatchQueueItem item = BatchQueueItem.getBatchOpQueue(op, input.getFailedPackages(), input.getAssociatedUsers(), options);
        ContextCompat.startForegroundService(this, BatchOpsService.getServiceIntent(this, item));
    }

    private void runWithCriticalPackageConfirmation(@NonNull List<DebloatObject> selectedObjects,
                                                    @NonNull CriticalPackageAction action) {
        List<String> criticalPackages = new ArrayList<>();
        int criticalPackageCount = 0;
        for (DebloatObject debloatObject : selectedObjects) {
            if (CriticalPackageGuard.isCriticalPackage(debloatObject.packageName)) {
                ++criticalPackageCount;
                if (criticalPackages.size() < 5) {
                    criticalPackages.add(debloatObject.getLabelOrPackageName().toString());
                }
            }
        }
        if (criticalPackageCount == 0) {
            action.run(false);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.debloat_critical_package_confirm_title)
                .setMessage(getResources().getQuantityString(
                        R.plurals.debloat_critical_package_confirm_message,
                        criticalPackageCount,
                        criticalPackageCount,
                        TextUtils.join(", ", criticalPackages)))
                .setPositiveButton(R.string.action_continue, (dialog, which) -> action.run(true))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private interface CriticalPackageAction {
        void run(boolean allowCriticalPackages);
    }
}
