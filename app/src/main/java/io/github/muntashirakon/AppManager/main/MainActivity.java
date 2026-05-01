// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreeze;
import io.github.muntashirakon.AppManager.apk.dexopt.DexOptDialog;
import io.github.muntashirakon.AppManager.apk.list.ListExporter;
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.batchops.struct.BatchFreezeOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchNetPolicyOptions;
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.debloat.DebloaterActivity;
import io.github.muntashirakon.AppManager.filters.FinderActivity;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.HelpActivity;
import io.github.muntashirakon.AppManager.misc.LabsActivity;
import io.github.muntashirakon.AppManager.oneclickops.OneClickOpsActivity;
import io.github.muntashirakon.AppManager.onboarding.OnboardingFragment;
import io.github.muntashirakon.AppManager.profiles.AddToProfileDialogFragment;
import io.github.muntashirakon.AppManager.profiles.ProfilesActivity;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;
import io.github.muntashirakon.AppManager.usage.AppUsageActivity;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.SearchableFlagsDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.multiselection.MultiSelectionActionsView;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class MainActivity extends BaseActivity implements AdvancedSearchView.OnQueryTextListener,
        SwipeRefreshLayout.OnRefreshListener, MultiSelectionActionsView.OnItemSelectedListener,
        MultiSelectionView.OnSelectionModeChangeListener {
    private static final String PACKAGE_NAME_APK_UPDATER = "com.apkupdater";
    private static final String ACTIVITY_NAME_APK_UPDATER = "com.apkupdater.activity.MainActivity";

    private static boolean SHOW_DISCLAIMER = true;

    MainViewModel viewModel;

    private MainRecyclerAdapter mAdapter;
    private AdvancedSearchView mSearchView;
    private LinearProgressIndicator mProgressIndicator;
    private SwipeRefreshLayout mSwipeRefresh;
    private MultiSelectionView mMultiSelectionView;
    private View mEmptyState;
    private TextView mEmptyStateTitle;
    private TextView mEmptyStateSummary;
    private MaterialButton mEmptyStateAction;
    private TextView mListStatusView;
    MainBatchOpsHandler mBatchOpsHandler;

    /** Async breakdown computation; cancelled when superseded by a new list. */
    @Nullable
    private java.util.concurrent.Future<?> mCategoryBreakdownFuture;

    /**
     * Per-package tracker-category counts cached across observer fires so that filter/
     * sort changes only resum cached vectors instead of refetching every app's tracker
     * components. Keyed by packageName (a package's tracker SDK list doesn't change
     * unless the app is reinstalled). Values are small int[] sized to TrackerCategory
     * .values().length to keep memory in check (~2KB at 600 apps × 8 categories × 4
     * bytes). The cache lives for the activity's lifetime — package install/uninstall
     * is rare and a stale entry just means a missed update we'll catch next session.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, int[]> mTrackerCategoryCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private MenuItem mAppUsageMenu;

    private final StoragePermission mStoragePermission = StoragePermission.init(this);

    private final ActivityResultLauncher<String> mBatchExportRules = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/tab-separated-values"),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                if (viewModel == null) {
                    // Invalid state
                    return;
                }
                RulesTypeSelectionDialogFragment dialogFragment = new RulesTypeSelectionDialogFragment();
                Bundle args = new Bundle();
                args.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_EXPORT);
                args.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, uri);
                args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, new ArrayList<>(viewModel.getSelectedPackages().keySet()));
                args.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, Users.getUsersIds());
                dialogFragment.setArguments(args);
                dialogFragment.show(getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
            });

    private final ActivityResultLauncher<String> mExportAppListCsv = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                mProgressIndicator.show();
                viewModel.saveExportedAppList(ListExporter.EXPORT_TYPE_CSV, Paths.get(uri));
            });
    private final ActivityResultLauncher<String> mExportAppListJson = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                mProgressIndicator.show();
                viewModel.saveExportedAppList(ListExporter.EXPORT_TYPE_JSON, Paths.get(uri));
            });
    private final ActivityResultLauncher<String> mExportAppListXml = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/xml"),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                mProgressIndicator.show();
                viewModel.saveExportedAppList(ListExporter.EXPORT_TYPE_XML, Paths.get(uri));
            });
    private final ActivityResultLauncher<String> mExportAppListMarkdown = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/markdown"),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                mProgressIndicator.show();
                viewModel.saveExportedAppList(ListExporter.EXPORT_TYPE_MARKDOWN, Paths.get(uri));
            });

    private final BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showProgressIndicator(false);
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

    @SuppressLint("RestrictedApi")
    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        getOnBackPressedDispatcher().addCallback(this, mOnBackPressedCallback);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
            AdvancedSearchView searchView = new AdvancedSearchView(actionBar.getThemedContext());
            searchView.setId(R.id.action_search);
            searchView.setOnQueryTextListener(this);
            // Set layout params
            ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.CENTER;
            actionBar.setCustomView(searchView, layoutParams);
            mSearchView = searchView;
            mSearchView.setIconifiedByDefault(false);
            mSearchView.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    UiUtils.hideKeyboard(v);
                }
            });
            // Check for market://search/?q=<query>
            Uri marketUri = getIntent().getData();
            if (marketUri != null && "market".equals(marketUri.getScheme()) && "search".equals(marketUri.getHost())) {
                String query = marketUri.getQueryParameter("q");
                if (query != null) {
                    mSearchView.setQuery(query, true);
                }
            }
        }

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mListStatusView = findViewById(R.id.list_status);
        mEmptyState = findViewById(android.R.id.empty);
        mEmptyStateTitle = mEmptyState.findViewById(R.id.empty_state_title);
        mEmptyStateSummary = mEmptyState.findViewById(R.id.empty_state_summary);
        mEmptyStateAction = mEmptyState.findViewById(R.id.empty_state_action);
        mEmptyStateAction.setOnClickListener(v -> handleEmptyStateAction());
        RecyclerView recyclerView = findViewById(R.id.item_list);
        recyclerView.requestFocus(); // Initially (the view isn't actually focusable)
        recyclerView.setEmptyView(mEmptyState);
        mSwipeRefresh = findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        mSwipeRefresh.setOnChildScrollUpCallback((parent, child) -> recyclerView.canScrollVertically(-1));

        mAdapter = new MainRecyclerAdapter(MainActivity.this);
        mAdapter.setHasStableIds(true);
        recyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        recyclerView.setAdapter(mAdapter);
        mMultiSelectionView = findViewById(R.id.selection_view);
        mMultiSelectionView.setOnItemSelectedListener(this);
        mMultiSelectionView.setOnSelectionModeChangeListener(this);
        mMultiSelectionView.setAdapter(mAdapter);
        mMultiSelectionView.updateCounter(true);
        mBatchOpsHandler = new MainBatchOpsHandler(mMultiSelectionView, viewModel);
        mMultiSelectionView.setOnSelectionChangeListener(mBatchOpsHandler);

        bindQuickFilterChips();

        if (SHOW_DISCLAIMER && AppPref.getBoolean(AppPref.PrefKey.PREF_SHOW_DISCLAIMER_BOOL)) {
            // Disclaimer will only be shown the first time it is loaded.
            SHOW_DISCLAIMER = false;
            View view = View.inflate(this, R.layout.dialog_disclaimer, null);
            new MaterialAlertDialogBuilder(this)
                    .setView(view)
                    .setCancelable(false)
                    .setPositiveButton(R.string.disclaimer_agree, (dialog, which) -> {
                        if (((MaterialCheckBox) view.findViewById(R.id.agree_forever)).isChecked()) {
                            AppPref.set(AppPref.PrefKey.PREF_SHOW_DISCLAIMER_BOOL, false);
                        }
                        maybeShowOnboarding();
                    })
                    .setNegativeButton(R.string.disclaimer_exit, (dialog, which) -> finishAndRemoveTask())
                    .show();
        } else {
            // Disclaimer already accepted on a prior launch — still surface onboarding
            // once for users who upgraded from a pre-onboarding build.
            maybeShowOnboarding();
        }

        // Set observer
        showProgressIndicator(true);
        updateMainListState(0, 0, 0);
        viewModel.getApplicationItems().observe(this, applicationItems -> {
            if (mAdapter != null) mAdapter.setDefaultList(applicationItems);
            showProgressIndicator(false);
            int trackerSum = 0;
            int permGrantedSum = 0;
            for (io.github.muntashirakon.AppManager.main.ApplicationItem item : applicationItems) {
                if (item.trackerCount != null) trackerSum += item.trackerCount;
                if (item.dangerousPermGranted != null) permGrantedSum += item.dangerousPermGranted;
            }
            updateMainListState(applicationItems.size(), trackerSum, permGrantedSum);
            refreshSortChipLabel();
            scheduleAggregateCategoryBreakdown(applicationItems, trackerSum);
        });
        viewModel.getOperationStatus().observe(this, status -> {
            mProgressIndicator.hide();
            if (status) {
                UIUtils.displayShortToast(R.string.done);
            } else {
                UIUtils.displayLongToast(R.string.failed);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_actions, menu);
        mAppUsageMenu = menu.findItem(R.id.action_app_usage);
        MenuItem apkUpdaterMenu = menu.findItem(R.id.action_apk_updater);
        try {
            if (!getPackageManager().getApplicationInfo(PACKAGE_NAME_APK_UPDATER, 0).enabled)
                throw new PackageManager.NameNotFoundException();
            apkUpdaterMenu.setVisible(true);
        } catch (PackageManager.NameNotFoundException e) {
            apkUpdaterMenu.setVisible(false);
        }
        MenuItem finderMenu = menu.findItem(R.id.action_finder);
        finderMenu.setVisible(BuildConfig.DEBUG);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mAppUsageMenu.setVisible(FeatureController.isUsageAccessEnabled());
        return true;
    }

    @SuppressLint("InflateParams")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_instructions) {
            Intent helpIntent = new Intent(this, HelpActivity.class);
            helpIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(helpIntent);
        } else if (id == R.id.action_list_options) {
            MainListOptions listOptions = new MainListOptions();
            listOptions.setListOptionActions(viewModel);
            listOptions.show(getSupportFragmentManager(), MainListOptions.TAG);
        } else if (id == R.id.action_refresh) {
            if (viewModel != null) {
                showProgressIndicator(true);
                viewModel.loadApplicationItems();
            }
        } else if (id == R.id.action_settings) {
            Intent settingsIntent = SettingsActivity.getSettingsIntent(this);
            startActivity(settingsIntent);
        } else if (id == R.id.action_app_usage) {
            Intent usageIntent = new Intent(this, AppUsageActivity.class);
            startActivity(usageIntent);
        } else if (id == R.id.action_one_click_ops) {
            Intent onClickOpsIntent = new Intent(this, OneClickOpsActivity.class);
            startActivity(onClickOpsIntent);
        } else if (id == R.id.action_finder) {
            Intent intent = new Intent(this, FinderActivity.class);
            startActivity(intent);
        } else if (id == R.id.action_apk_updater) {
            try {
                if (!getPackageManager().getApplicationInfo(PACKAGE_NAME_APK_UPDATER, 0).enabled)
                    throw new PackageManager.NameNotFoundException();
                Intent intent = new Intent();
                intent.setClassName(PACKAGE_NAME_APK_UPDATER, ACTIVITY_NAME_APK_UPDATER);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ignored) {
            }
        } else if (id == R.id.action_running_apps) {
            Intent runningAppsIntent = new Intent(this, RunningAppsActivity.class);
            startActivity(runningAppsIntent);
        } else if (id == R.id.action_profiles) {
            Intent profilesIntent = new Intent(this, ProfilesActivity.class);
            startActivity(profilesIntent);
        } else if (id == R.id.action_labs) {
            Intent intent = new Intent(getApplicationContext(), LabsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else if (id == R.id.action_debloater) {
            Intent intent = new Intent(getApplicationContext(), DebloaterActivity.class);
            startActivity(intent);
        } else return super.onOptionsItemSelected(item);
        return true;
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
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_backup) {
            if (viewModel != null) {
                BackupRestoreDialogFragment fragment = BackupRestoreDialogFragment.getInstance(viewModel.getSelectedPackagesWithUsers());
                fragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
                fragment.setOnActionCompleteListener((mode, failedPackages) -> showProgressIndicator(false));
                fragment.show(getSupportFragmentManager(), BackupRestoreDialogFragment.TAG);
            }
        } else if (id == R.id.action_save_apk) {
            mStoragePermission.request(granted -> {
                if (granted) handleBatchOp(BatchOpsManager.OP_BACKUP_APK);
            });
        } else if (id == R.id.action_block_unblock_trackers) {
            showTrackerBatchDialog();
        } else if (id == R.id.action_clear_data_cache) {
            showClearDataCacheDialog();
        } else if (id == R.id.action_freeze_unfreeze) {
            showFreezeUnfreezeDialog(Prefs.Blocking.getDefaultFreezingMethod());
        } else if (id == R.id.action_disable_background) {
            showDisableBackgroundDialog();
        } else if (id == R.id.action_net_policy) {
            ArrayMap<Integer, String> netPolicyMap = NetworkPolicyManagerCompat.getAllReadablePolicies(this);
            Integer[] polices = new Integer[netPolicyMap.size()];
            String[] policyStrings = new String[netPolicyMap.size()];
            Collection<ApplicationItem> applicationItems = viewModel.getSelectedPackages().values();
            Iterator<ApplicationItem> it = applicationItems.iterator();
            int selectedPolicies = applicationItems.size() == 1 && it.hasNext() ?
                    NetworkPolicyManagerCompat.getUidPolicy(it.next().uid) : 0;
            for (int i = 0; i < netPolicyMap.size(); ++i) {
                polices[i] = netPolicyMap.keyAt(i);
                policyStrings[i] = netPolicyMap.valueAt(i);
            }
            new SearchableFlagsDialogBuilder<>(this, polices, policyStrings, selectedPolicies)
                    .setTitle(R.string.net_policy)
                    .showSelectAll(false)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.apply, (dialog, which, selections) -> {
                        int flags = 0;
                        for (int flag : selections) {
                            flags |= flag;
                        }
                        BatchNetPolicyOptions options = new BatchNetPolicyOptions(flags);
                        handleBatchOp(BatchOpsManager.OP_NET_POLICY, options);
                    })
                    .show();
        } else if (id == R.id.action_optimize) {
            DexOptDialog dialog = DexOptDialog.getInstance(viewModel.getSelectedPackages().keySet().toArray(new String[0]));
            dialog.show(getSupportFragmentManager(), DexOptDialog.TAG);
        } else if (id == R.id.action_export_blocking_rules) {
            final String fileName = "app_manager_rules_export-" + DateUtils.formatDateTime(this, System.currentTimeMillis()) + ".am.tsv";
            mBatchExportRules.launch(fileName);
        } else if (id == R.id.action_export_app_list) {
            List<Integer> exportTypes = Arrays.asList(ListExporter.EXPORT_TYPE_CSV,
                    ListExporter.EXPORT_TYPE_JSON,
                    ListExporter.EXPORT_TYPE_XML,
                    ListExporter.EXPORT_TYPE_MARKDOWN);
            new SearchableSingleChoiceDialogBuilder<>(this, exportTypes, R.array.export_app_list_options)
                    .setTitle(R.string.export_app_list_select_format)
                    .setOnSingleChoiceClickListener((dialog, which, item1, isChecked) -> {
                        if (!isChecked) {
                            return;
                        }
                        String filename = "app_manager_app_list-" + DateUtils.formatLongDateTime(this, System.currentTimeMillis()) + ".am";
                        switch (item1) {
                            case ListExporter.EXPORT_TYPE_CSV:
                                mExportAppListCsv.launch(filename + ".csv");
                                break;
                            case ListExporter.EXPORT_TYPE_JSON:
                                mExportAppListJson.launch(filename + ".json");
                                break;
                            case ListExporter.EXPORT_TYPE_XML:
                                mExportAppListXml.launch(filename + ".xml");
                                break;
                            case ListExporter.EXPORT_TYPE_MARKDOWN:
                                mExportAppListMarkdown.launch(filename + ".md");
                                break;
                        }
                    })
                    .setNegativeButton(R.string.close, null)
                    .show();
        } else if (id == R.id.action_force_stop) {
            showForceStopDialog();
        } else if (id == R.id.action_uninstall) {
            showUninstallDialog();
        } else if (id == R.id.action_add_to_profile) {
            AddToProfileDialogFragment dialog = AddToProfileDialogFragment.getInstance(viewModel.getSelectedPackages()
                    .keySet().toArray(new String[0]));
            dialog.show(getSupportFragmentManager(), AddToProfileDialogFragment.TAG);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void onRefresh() {
        showProgressIndicator(true);
        if (viewModel != null) viewModel.loadApplicationItems();
        mSwipeRefresh.setRefreshing(false);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Set filter
        if (viewModel != null && mSearchView != null && !TextUtils.isEmpty(viewModel.getSearchQuery())) {
            if (mSearchView.isIconified()) {
                mSearchView.setIconified(false);
            }
            mSearchView.setQuery(viewModel.getSearchQuery(), false);
        }
        // Show/hide app usage menu
        if (mAppUsageMenu != null) {
            mAppUsageMenu.setVisible(FeatureController.isUsageAccessEnabled());
        }
        // Check for backup volume
        if (!Prefs.BackupRestore.backupDirectoryExists()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.backup_volume)
                    .setMessage(R.string.backup_volume_unavailable_warning)
                    .setPositiveButton(R.string.close, null)
                    .setNeutralButton(R.string.change_backup_volume, (dialog, which) -> {
                        Intent intent = SettingsActivity.getSettingsIntent(this, "backup_restore_prefs", "backup_volume");
                        startActivity(intent);
                    })
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.onResume();
        if (mAdapter != null && mBatchOpsHandler != null && mAdapter.isInSelectionMode()) {
            mBatchOpsHandler.updateConstraints();
            mMultiSelectionView.updateCounter(false);
        }
        refreshQuickFilterChips();
        ContextCompat.registerReceiver(this, mBatchOpsBroadCastReceiver,
                new IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBatchOpsBroadCastReceiver);
    }

    private static final int[][] QUICK_FILTER_CHIPS = {
            {R.id.chip_user, MainListOptions.FILTER_USER_APPS},
            {R.id.chip_system, MainListOptions.FILTER_SYSTEM_APPS},
            {R.id.chip_frozen, MainListOptions.FILTER_FROZEN_APPS},
            {R.id.chip_running, MainListOptions.FILTER_RUNNING_APPS},
            {R.id.chip_backups, MainListOptions.FILTER_APPS_WITH_BACKUPS},
            {R.id.chip_stopped, MainListOptions.FILTER_STOPPED_APPS},
            {R.id.chip_trackers, MainListOptions.FILTER_APPS_WITH_TRACKERS},
            {R.id.chip_rules, MainListOptions.FILTER_APPS_WITH_RULES},
            {R.id.chip_granted_perms, MainListOptions.FILTER_APPS_WITH_GRANTED_PERMS},
    };

    /**
     * Walk the visible apps on a worker thread, fetch each one's tracker components,
     * categorize via TrackerCategory, and append a breakdown ("320 ad · 280 analytics
     * · 191 push") to the list status line. Runs on a background thread because each
     * app needs a PackageInfo lookup + Aho-Corasick search per component — for a
     * 600-app list that's a noticeable burst we don't want on the UI thread.
     *
     * <p>Cancels any in-flight computation before starting a new one so a fast filter
     * switch doesn't queue redundant passes. No caching — the visible set changes on
     * every filter/sort, so a fresh pass is always correct.
     */
    private void scheduleAggregateCategoryBreakdown(
            @NonNull java.util.List<io.github.muntashirakon.AppManager.main.ApplicationItem> items,
            int trackerSum) {
        if (mCategoryBreakdownFuture != null) {
            mCategoryBreakdownFuture.cancel(true);
            mCategoryBreakdownFuture = null;
        }
        if (trackerSum == 0 || items.isEmpty()) return;
        // Snapshot the list for the worker thread.
        java.util.List<io.github.muntashirakon.AppManager.main.ApplicationItem> snapshot =
                new java.util.ArrayList<>(items);
        // Show a placeholder progress indicator on the status line so users know
        // the breakdown is in flight rather than missing. Replaced by the real
        // breakdown the moment the worker finishes.
        showBreakdownProgress();
        final int categoryCount = io.github.muntashirakon.AppManager.rules.compontents
                .TrackerCategory.values().length;
        mCategoryBreakdownFuture = io.github.muntashirakon.AppManager.utils.ThreadUtils
                .postOnBackgroundThread(() -> {
            int[] totals = new int[categoryCount];
            for (io.github.muntashirakon.AppManager.main.ApplicationItem appItem : snapshot) {
                if (Thread.currentThread().isInterrupted()) return;
                if (appItem.trackerCount == null || appItem.trackerCount == 0
                        || !appItem.isInstalled) continue;
                int[] perApp = mTrackerCategoryCache.get(appItem.packageName);
                if (perApp == null) {
                    perApp = computeTrackerCategoriesForPackage(appItem, categoryCount);
                    if (perApp != null) {
                        mTrackerCategoryCache.put(appItem.packageName, perApp);
                    }
                }
                if (perApp == null) continue;
                for (int i = 0; i < categoryCount; i++) {
                    totals[i] += perApp[i];
                }
            }
            if (Thread.currentThread().isInterrupted()) return;
            StringBuilder sb = new StringBuilder();
            io.github.muntashirakon.AppManager.rules.compontents.TrackerCategory[] cats =
                    io.github.muntashirakon.AppManager.rules.compontents.TrackerCategory.values();
            for (int i = 0; i < categoryCount; i++) {
                if (cats[i] == io.github.muntashirakon.AppManager.rules.compontents
                        .TrackerCategory.OTHER) continue;
                if (totals[i] == 0) continue;
                if (sb.length() > 0) sb.append(" · ");
                sb.append(totals[i]).append(' ').append(getString(cats[i].getLabelRes())
                        .toLowerCase(java.util.Locale.ROOT));
            }
            String breakdown = sb.length() == 0 ? null : sb.toString();
            io.github.muntashirakon.AppManager.utils.ThreadUtils.postOnMainThread(() -> {
                if (mListStatusView == null) return;
                if (breakdown == null) {
                    // Only OTHER: clear the placeholder and leave the base line.
                    clearBreakdownProgress();
                    return;
                }
                CharSequence current = mListStatusView.getText();
                if (current == null || current.length() == 0) return;
                // Strip our placeholder line if present before appending.
                String currentStr = current.toString();
                int newline = currentStr.indexOf('\n');
                String base = newline >= 0 ? currentStr.substring(0, newline) : currentStr;
                String statusWithBreakdown = getString(R.string.main_status_with_category_breakdown,
                        base, breakdown);
                mListStatusView.setText(statusWithBreakdown);
                mListStatusView.setContentDescription(statusWithBreakdown);
            });
        });
    }

    /**
     * Compute per-package category vector. Indexed by {@link io.github.muntashirakon
     * .AppManager.rules.compontents.TrackerCategory#ordinal()} so the caller can sum
     * cheaply across many apps. Returns {@code null} on PackageInfo lookup failure.
     */
    @Nullable
    private int[] computeTrackerCategoriesForPackage(
            @NonNull io.github.muntashirakon.AppManager.main.ApplicationItem appItem,
            int categoryCount) {
        int userId = appItem.userIds != null && appItem.userIds.length > 0
                ? appItem.userIds[0]
                : android.os.UserHandleHidden.myUserId();
        android.content.pm.PackageInfo pi;
        try {
            pi = io.github.muntashirakon.AppManager.compat.PackageManagerCompat.getPackageInfo(
                    appItem.packageName,
                    android.content.pm.PackageManager.GET_ACTIVITIES
                            | android.content.pm.PackageManager.GET_SERVICES
                            | android.content.pm.PackageManager.GET_RECEIVERS
                            | android.content.pm.PackageManager.GET_PROVIDERS,
                    userId);
        } catch (Throwable t) {
            return null;
        }
        if (pi == null) return null;
        int[] vec = new int[categoryCount];
        java.util.Map<String, io.github.muntashirakon.AppManager.rules.RuleType> trackers =
                io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils
                        .getTrackerComponentsForPackage(pi);
        for (String componentName : trackers.keySet()) {
            String vendor = io.github.muntashirakon.AppManager.rules.compontents
                    .ComponentUtils.getTrackerLabel(componentName);
            io.github.muntashirakon.AppManager.rules.compontents.TrackerCategory cat =
                    io.github.muntashirakon.AppManager.rules.compontents
                            .TrackerCategory.categorize(vendor);
            vec[cat.ordinal()]++;
        }
        return vec;
    }

    /**
     * Append a "Calculating breakdown…" placeholder to the status line so the user
     * knows the second-line breakdown is in flight, not missing. Replaced by the
     * real categories the moment the worker finishes; cleared when categorization
     * yields nothing useful.
     */
    private void showBreakdownProgress() {
        if (mListStatusView == null) return;
        CharSequence current = mListStatusView.getText();
        if (current == null || current.length() == 0) return;
        String currentStr = current.toString();
        // Avoid stacking multiple placeholders if observer fires re-entrantly.
        int newline = currentStr.indexOf('\n');
        String base = newline >= 0 ? currentStr.substring(0, newline) : currentStr;
        String status = getString(R.string.main_status_with_category_breakdown,
                base, getString(R.string.main_status_breakdown_calculating));
        mListStatusView.setText(status);
        mListStatusView.setContentDescription(status);
    }

    private void clearBreakdownProgress() {
        if (mListStatusView == null) return;
        CharSequence current = mListStatusView.getText();
        if (current == null) return;
        String currentStr = current.toString();
        int newline = currentStr.indexOf('\n');
        if (newline >= 0) {
            String base = currentStr.substring(0, newline);
            mListStatusView.setText(base);
            mListStatusView.setContentDescription(base);
        }
    }

    private void maybeShowOnboarding() {
        if (!OnboardingFragment.shouldShow()) {
            // Onboarding already done — surface the main-list tour for users who
            // haven't seen it yet (covers fresh installs after onboarding picks
            // and upgraders from a pre-tour build).
            maybeShowMainListTour();
            return;
        }
        if (getSupportFragmentManager().findFragmentByTag(OnboardingFragment.TAG) != null) return;
        OnboardingFragment fragment = new OnboardingFragment();
        // After onboarding dismisses, the dialog-listener fires once on the
        // activity context — chain the tour off it so users see it on the same
        // first launch.
        fragment.setOnDismissCallback(this::maybeShowMainListTour);
        fragment.show(getSupportFragmentManager(), OnboardingFragment.TAG);
    }

    /**
     * One-shot 'quick tour' alert listing the main-list features users might miss
     * (chip row, badge tap, sort indicator, CTA card). Tracked via PREF_MAIN_TOUR
     * _SHOWN_BOOL so it never re-prompts. Called after onboarding completes or
     * directly on first launch for users who already had onboarding when this
     * shipped. Cancellable; cancel still marks shown so dismiss-without-reading
     * doesn't trap the user.
     */
    private void maybeShowMainListTour() {
        if (AppPref.getBoolean(AppPref.PrefKey.PREF_MAIN_TOUR_SHOWN_BOOL)) return;
        AppPref.set(AppPref.PrefKey.PREF_MAIN_TOUR_SHOWN_BOOL, true);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.main_tour_title)
                .setMessage(R.string.main_tour_message)
                .setPositiveButton(R.string.got_it, null)
                .show();
    }

    private void bindQuickFilterChips() {
        for (int[] entry : QUICK_FILTER_CHIPS) {
            bindQuickFilterChip(entry[0], entry[1]);
        }
        Chip clearChip = findViewById(R.id.chip_clear_filters);
        if (clearChip != null) {
            View.OnClickListener clearListener = v -> {
                if (viewModel != null) viewModel.clearFilters();
                refreshQuickFilterChips();
            };
            clearChip.setOnClickListener(clearListener);
            clearChip.setOnCloseIconClickListener(clearListener);
        }
        Chip sortChip = findViewById(R.id.chip_sort);
        if (sortChip != null) {
            sortChip.setOnClickListener(v -> {
                if (viewModel == null) return;
                MainListOptions listOptions = new MainListOptions();
                listOptions.setListOptionActions(viewModel);
                listOptions.show(getSupportFragmentManager(), MainListOptions.TAG);
            });
        }
        refreshQuickFilterChips();
        refreshSortChipLabel();
    }

    /**
     * Resolve the human-readable name of the active sort order from MainListOptions'
     * canonical sort id -> string-res map and display it on the sort chip with a
     * downward arrow when the order is reversed. The chip stays in sync via the
     * applicationItems observer, which fires whenever sortBy changes.
     */
    private void refreshSortChipLabel() {
        Chip sortChip = findViewById(R.id.chip_sort);
        if (sortChip == null || viewModel == null) return;
        java.util.LinkedHashMap<Integer, Integer> sortMap = new MainListOptions().getSortIdLocaleMap();
        if (sortMap == null) return;
        Integer labelRes = sortMap.get(viewModel.getSortBy());
        String label = labelRes != null ? getString(labelRes) : getString(R.string.sort);
        sortChip.setText(viewModel.isReverseSort()
                ? getString(R.string.main_sort_chip_label_reversed, label)
                : getString(R.string.main_sort_chip_label, label));
    }

    private void bindQuickFilterChip(int chipId, @MainListOptions.Filter int flag) {
        Chip chip = findViewById(chipId);
        if (chip == null || viewModel == null) return;
        chip.setOnCheckedChangeListener(null);
        chip.setChecked(viewModel.hasFilterFlag(flag));
        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (viewModel == null) return;
            if (isChecked) viewModel.addFilterFlag(flag);
            else viewModel.removeFilterFlag(flag);
            refreshQuickFilterChips();
        });
    }

    /**
     * Build a comma-separated list of the chip labels that are currently active so the
     * filtered-empty-state can tell users <em>which</em> filters are hiding their apps,
     * not just that "filters are active". Returns {@code null} if no quick chip is on
     * (the view model may still report active filters from the long-form sort/filter
     * sheet — fall back to the generic message in that case).
     */
    @Nullable
    private String describeActiveQuickFilters() {
        if (viewModel == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int[] entry : QUICK_FILTER_CHIPS) {
            if (!viewModel.hasFilterFlag(entry[1])) continue;
            Chip chip = findViewById(entry[0]);
            if (chip == null) continue;
            CharSequence label = chip.getText();
            if (label == null || label.length() == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(label);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private void refreshQuickFilterChips() {
        if (viewModel == null) return;
        boolean anyActive = false;
        for (int[] entry : QUICK_FILTER_CHIPS) {
            Chip chip = findViewById(entry[0]);
            if (chip == null) continue;
            boolean active = viewModel.hasFilterFlag(entry[1]);
            if (chip.isChecked() != active) {
                chip.setOnCheckedChangeListener(null);
                chip.setChecked(active);
                final int flag = entry[1];
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (viewModel == null) return;
                    if (isChecked) viewModel.addFilterFlag(flag);
                    else viewModel.removeFilterFlag(flag);
                    refreshQuickFilterChips();
                });
            }
            if (active) anyActive = true;
        }
        Chip clearChip = findViewById(R.id.chip_clear_filters);
        if (clearChip != null) {
            clearChip.setVisibility(anyActive ? View.VISIBLE : View.GONE);
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
                    BatchFreezeOptions options = new BatchFreezeOptions(selectedItem, checkBox.isChecked());
                    handleBatchOp(BatchOpsManager.OP_ADVANCED_FREEZE, options);
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.unfreeze, (dialog, which, selectedItem) ->
                        handleBatchOp(BatchOpsManager.OP_UNFREEZE))
                .show();
    }

    private void showTrackerBatchDialog() {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_cctv_off)
                .setTitle(R.string.block_unblock_trackers)
                .setMessage(getString(R.string.batch_tracker_dialog_message, getSelectedAppCountLabel()))
                .setPositiveButton(R.string.block, (dialog, which) ->
                        handleBatchOp(BatchOpsManager.OP_BLOCK_TRACKERS))
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.unblock, (dialog, which) ->
                        handleBatchOp(BatchOpsManager.OP_UNBLOCK_TRACKERS))
                .show();
    }

    private void showClearDataCacheDialog() {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_brush)
                .setTitle(R.string.batch_clear_dialog_title)
                .setMessage(getString(R.string.batch_clear_dialog_message, getSelectedAppCountLabel()))
                .setPositiveButton(R.string.clear_cache, (dialog, which) ->
                        handleBatchOp(BatchOpsManager.OP_CLEAR_CACHE))
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.clear_data, (dialog, which) ->
                        handleBatchOp(BatchOpsManager.OP_CLEAR_DATA))
                .show();
    }

    private void showDisableBackgroundDialog() {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_block)
                .setTitle(R.string.batch_disable_background_dialog_title)
                .setMessage(getString(R.string.batch_disable_background_dialog_message,
                        getSelectedAppCountLabel()))
                .setPositiveButton(R.string.disable_background, (dialog, which) ->
                        handleBatchOp(BatchOpsManager.OP_DISABLE_BACKGROUND))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showForceStopDialog() {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_power_settings)
                .setTitle(R.string.batch_force_stop_dialog_title)
                .setMessage(getString(R.string.batch_force_stop_dialog_message, getSelectedAppCountLabel()))
                .setPositiveButton(R.string.force_stop, (dialog, which) ->
                        handleBatchOp(BatchOpsManager.OP_FORCE_STOP))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showUninstallDialog() {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_trash_can)
                .setTitle(R.string.batch_uninstall_dialog_title)
                .setMessage(getString(R.string.batch_uninstall_dialog_message, getSelectedAppCountLabel()))
                .setPositiveButton(R.string.uninstall, (dialog, which) ->
                        handleBatchOp(BatchOpsManager.OP_UNINSTALL))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @NonNull
    private String getSelectedAppCountLabel() {
        int count = viewModel == null ? 0 : viewModel.getSelectedPackages().size();
        return getResources().getQuantityString(R.plurals.batch_selected_app_count, count, count);
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op) {
        handleBatchOp(op, null);
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op, @Nullable IBatchOpOptions options) {
        if (viewModel == null) return;
        showProgressIndicator(true);
        BatchOpsManager.Result input = new BatchOpsManager.Result(viewModel.getSelectedPackagesWithUsers());
        BatchQueueItem item = BatchQueueItem.getBatchOpQueue(op, input.getFailedPackages(), input.getAssociatedUsers(), options);
        Intent intent = BatchOpsService.getServiceIntent(this, item);
        ContextCompat.startForegroundService(this, intent);
    }

    void showProgressIndicator(boolean show) {
        if (show) {
            mProgressIndicator.show();
            if (mEmptyState != null) {
                mEmptyState.setVisibility(View.GONE);
            }
            if (mListStatusView != null) {
                mListStatusView.setVisibility(View.VISIBLE);
                mListStatusView.setText(R.string.main_status_loading);
                mListStatusView.setContentDescription(getString(R.string.main_status_loading));
                mListStatusView.setOnClickListener(null);
                mListStatusView.setClickable(false);
            }
        } else mProgressIndicator.hide();
    }

    private void updateMainListState(int displayedItemCount, int trackerSum, int permGrantedSum) {
        if (viewModel == null || mEmptyState == null) {
            return;
        }
        int totalItemCount = viewModel.getApplicationItemCount();
        boolean hasSearchQuery = !TextUtils.isEmpty(viewModel.getSearchQuery());
        boolean hasFilters = viewModel.hasActiveFilters();
        if (mListStatusView != null) {
            mListStatusView.setVisibility(View.VISIBLE);
            String base;
            if (displayedItemCount == totalItemCount) {
                base = getString(R.string.main_status_all_apps, displayedItemCount);
            } else {
                base = getString(R.string.main_status_showing_apps, displayedItemCount, totalItemCount);
            }
            // Build suffix incrementally so we can chain '· N trackers · M dangerous perms'.
            StringBuilder line = new StringBuilder(base);
            if (trackerSum > 0) {
                line.append(" · ").append(getResources().getQuantityString(
                        R.plurals.main_status_tracker_suffix, trackerSum, trackerSum));
            }
            if (permGrantedSum > 0) {
                line.append(" · ").append(getResources().getQuantityString(
                        R.plurals.main_status_perm_suffix, permGrantedSum, permGrantedSum));
            }
            String statusLine = line.toString();
            mListStatusView.setText(statusLine);
            // Tap-to-filter shortcut: prefers trackers when present (the bigger user
            // goal), falls back to perms-granted otherwise. No-op when both filters
            // are already on; user uses the Clear chip to undo.
            if (trackerSum > 0
                    && !viewModel.hasFilterFlag(MainListOptions.FILTER_APPS_WITH_TRACKERS)) {
                mListStatusView.setClickable(true);
                mListStatusView.setFocusable(true);
                mListStatusView.setContentDescription(statusLine + ". "
                        + getString(R.string.main_status_filter_trackers_a11y));
                mListStatusView.setOnClickListener(v -> {
                    if (viewModel == null) return;
                    viewModel.addFilterFlag(MainListOptions.FILTER_APPS_WITH_TRACKERS);
                    refreshQuickFilterChips();
                });
            } else if (permGrantedSum > 0
                    && !viewModel.hasFilterFlag(MainListOptions.FILTER_APPS_WITH_GRANTED_PERMS)) {
                mListStatusView.setClickable(true);
                mListStatusView.setFocusable(true);
                mListStatusView.setContentDescription(statusLine + ". "
                        + getString(R.string.main_status_filter_permissions_a11y));
                mListStatusView.setOnClickListener(v -> {
                    if (viewModel == null) return;
                    viewModel.addFilterFlag(MainListOptions.FILTER_APPS_WITH_GRANTED_PERMS);
                    refreshQuickFilterChips();
                });
            } else {
                mListStatusView.setOnClickListener(null);
                mListStatusView.setClickable(false);
                mListStatusView.setContentDescription(statusLine);
            }
        }
        if (displayedItemCount > 0) {
            return;
        }
        if (hasSearchQuery) {
            mEmptyStateTitle.setText(R.string.main_empty_title_no_matches);
            mEmptyStateSummary.setText(R.string.main_empty_message_no_matches);
            mEmptyStateAction.setText(R.string.main_empty_action_clear_search);
            mEmptyStateAction.setIconResource(com.google.android.material.R.drawable.mtrl_ic_cancel);
        } else if (hasFilters) {
            mEmptyStateTitle.setText(R.string.main_empty_title_no_matches);
            String activeLabels = describeActiveQuickFilters();
            if (activeLabels != null) {
                mEmptyStateSummary.setText(getString(R.string.main_empty_message_no_matches_with_filters, activeLabels));
            } else {
                mEmptyStateSummary.setText(R.string.main_empty_message_no_matches);
            }
            mEmptyStateAction.setText(R.string.main_empty_action_clear_filters);
            mEmptyStateAction.setIconResource(R.drawable.ic_filter_list);
        } else {
            mEmptyStateTitle.setText(R.string.main_empty_title_no_apps);
            mEmptyStateSummary.setText(R.string.main_empty_message_no_apps);
            mEmptyStateAction.setText(R.string.refresh);
            mEmptyStateAction.setIconResource(R.drawable.ic_refresh);
        }
    }

    private void handleEmptyStateAction() {
        if (viewModel == null) {
            return;
        }
        if (!TextUtils.isEmpty(viewModel.getSearchQuery())) {
            if (mSearchView != null) {
                mSearchView.setQuery("", true);
            } else {
                viewModel.setSearchQuery("", AdvancedSearchView.SEARCH_TYPE_CONTAINS);
            }
        } else if (viewModel.hasActiveFilters()) {
            showProgressIndicator(true);
            viewModel.clearFilters();
            refreshQuickFilterChips();
        } else {
            showProgressIndicator(true);
            viewModel.loadApplicationItems();
        }
    }

    @Override
    public boolean onQueryTextChange(String searchQuery, @AdvancedSearchView.SearchType int type) {
        if (viewModel != null) viewModel.setSearchQuery(searchQuery, type);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query, int type) {
        return false;
    }
}
