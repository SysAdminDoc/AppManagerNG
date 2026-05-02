// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ClipboardUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

public class OpHistoryActivity extends BaseActivity {
    private static final long ONE_DAY_MILLIS = 24L * 60 * 60 * 1000;
    private static final int CLEANUP_VISIBLE = -1;
    private static final int CLEANUP_SUCCESSFUL = 0;
    private static final int CLEANUP_FAILED = 1;
    private static final int CLEANUP_OLDER_THAN_7_DAYS = 2;
    private static final int CLEANUP_OLDER_THAN_30_DAYS = 3;
    private static final int CLEANUP_OLDER_THAN_90_DAYS = 4;
    private static final int CLEANUP_OLDER_THAN_180_DAYS = 5;
    private static final int CLEANUP_OLDER_THAN_365_DAYS = 6;
    private static final int SORT_NEWEST_FIRST = 0;
    private static final int SORT_HIGHEST_RISK_FIRST = 1;
    private static final int SORT_MOST_FAILURES_FIRST = 2;
    private static final int SORT_TYPE = 3;
    private static final int SORT_LABEL = 4;

    private OpHistoryViewModel mViewModel;
    private OpHistoryAdapter mAdapter;
    private LinearProgressIndicator mProgressIndicator;
    private TextInputEditText mFilterText;
    private TextView mHistorySummary;
    private TextView mEmptyStateTitle;
    private TextView mEmptyStateSummary;
    private MaterialButton mEmptyStateAction;
    private final List<Chip> mFilterChips = new ArrayList<>();
    private List<OpHistoryItem> mCurrentOpHistories = Collections.emptyList();
    private Chip mChipSucceeded;
    private Chip mChipFailed;
    private Chip mChipHighRisk;
    private Chip mChipMediumRisk;
    private Chip mChipLowRisk;
    private Chip mChipBatch;
    private Chip mChipInstaller;
    private Chip mChipProfiles;
    private Chip mChipRoot;
    private Chip mChipAdb;
    private Chip mChipNoRoot;
    private Chip mChipReversible;
    private Chip mChipLastDay;
    private Chip mChipLastWeek;
    private Chip mChipClearFilters;
    private int mSortMode = SORT_NEWEST_FIRST;
    @Nullable
    private String mPendingExport;

    private final ActivityResultLauncher<String> mExportHistory = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("*/*"),
            this::writePendingExport);

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_op_history);
        setSupportActionBar(findViewById(R.id.toolbar));
        mSortMode = normalizeSortMode(Prefs.Privacy.getOpHistorySortOrder());
        mViewModel = new ViewModelProvider(this).get(OpHistoryViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mHistorySummary = findViewById(R.id.history_summary);
        RecyclerView listView = findViewById(android.R.id.list);
        listView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        View emptyView = findViewById(android.R.id.empty);
        setupEmptyState(emptyView);
        listView.setEmptyView(emptyView);
        setupHistoryFilters();
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        mAdapter = new OpHistoryAdapter(this);
        listView.setAdapter(mAdapter);
        FloatingActionButton fab = findViewById(R.id.floatingActionButton);
        UiUtils.applyWindowInsetsAsMargin(fab);
        fab.hide();
        fab.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_history)
                .setMessage(R.string.op_history_clear_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear_history, (dialog, which) -> {
                    mProgressIndicator.show();
                    mViewModel.clearHistory();
                })
                .show());
        mViewModel.getOpHistoriesLiveData().observe(this, opHistories -> {
            mProgressIndicator.hide();
            mCurrentOpHistories = opHistories;
            if (opHistories.isEmpty()) {
                fab.hide();
            } else {
                fab.show();
            }
            applyFilters();
        });
        mViewModel.getClearHistoryLiveData().observe(this, cleared ->
                UIUtils.displayShortToast(cleared ? R.string.done : R.string.failed));
        mViewModel.getDeleteHistoryLiveData().observe(this, deleted ->
                UIUtils.displayShortToast(deleted ? R.string.done : R.string.failed));
        mViewModel.getCleanupHistoryLiveData().observe(this, deletedCount ->
                UIUtils.displayShortToast(getResources().getQuantityString(
                        R.plurals.op_history_deleted_count, deletedCount, deletedCount)));
        mViewModel.getDebugSeedLiveData().observe(this, addedCount ->
                UIUtils.displayShortToast(getResources().getQuantityString(
                        R.plurals.op_history_sample_count, addedCount, addedCount)));
        mViewModel.getServiceLauncherIntentLiveData().observe(this, intent -> {
            if (intent != null) {
                ContextCompat.startForegroundService(this, intent);
            } else {
                UIUtils.displayShortToast(R.string.failed);
            }
        });
        OpHistoryManager.getHistoryAddedLiveData().observe(this, opHistory -> {
            // New history added
            mProgressIndicator.show();
            mViewModel.loadOpHistories();
        });
        mProgressIndicator.show();
        mViewModel.loadOpHistories();
    }

    private void setupEmptyState(@NonNull View emptyView) {
        ((ImageView) emptyView.findViewById(R.id.empty_state_icon)).setImageResource(R.drawable.ic_history);
        mEmptyStateTitle = emptyView.findViewById(R.id.empty_state_title);
        mEmptyStateSummary = emptyView.findViewById(R.id.empty_state_summary);
        mEmptyStateTitle.setText(R.string.op_history_empty_title);
        mEmptyStateSummary.setText(R.string.op_history_empty_message);
        mEmptyStateAction = emptyView.findViewById(R.id.empty_state_action);
        mEmptyStateAction.setText(R.string.clear_filters);
        mEmptyStateAction.setOnClickListener(v -> clearFilters());
        mEmptyStateAction.setVisibility(View.GONE);
    }

    private void setupHistoryFilters() {
        mFilterText = findViewById(R.id.history_filter_text);
        mFilterText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        mChipSucceeded = bindFilterChip(R.id.chip_history_succeeded);
        mChipFailed = bindFilterChip(R.id.chip_history_failed);
        mChipHighRisk = bindFilterChip(R.id.chip_history_high_risk);
        mChipMediumRisk = bindFilterChip(R.id.chip_history_medium_risk);
        mChipLowRisk = bindFilterChip(R.id.chip_history_low_risk);
        mChipBatch = bindFilterChip(R.id.chip_history_batch);
        mChipInstaller = bindFilterChip(R.id.chip_history_installer);
        mChipProfiles = bindFilterChip(R.id.chip_history_profiles);
        mChipRoot = bindFilterChip(R.id.chip_history_root);
        mChipAdb = bindFilterChip(R.id.chip_history_adb);
        mChipNoRoot = bindFilterChip(R.id.chip_history_no_root);
        mChipReversible = bindFilterChip(R.id.chip_history_reversible);
        mChipLastDay = findViewById(R.id.chip_history_last_day);
        mChipLastWeek = findViewById(R.id.chip_history_last_week);
        mFilterChips.add(mChipLastDay);
        mFilterChips.add(mChipLastWeek);
        mChipLastDay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && mChipLastWeek.isChecked()) {
                mChipLastWeek.setChecked(false);
            }
            applyFilters();
        });
        mChipLastWeek.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && mChipLastDay.isChecked()) {
                mChipLastDay.setChecked(false);
            }
            applyFilters();
        });
        mChipClearFilters = findViewById(R.id.chip_history_clear_filters);
        mChipClearFilters.setOnClickListener(v -> clearFilters());
    }

    @NonNull
    private Chip bindFilterChip(int id) {
        Chip chip = findViewById(id);
        mFilterChips.add(chip);
        chip.setOnCheckedChangeListener((buttonView, isChecked) -> applyFilters());
        return chip;
    }

    private void clearFilters() {
        mFilterText.setText(null);
        for (Chip chip : mFilterChips) {
            chip.setChecked(false);
        }
        applyFilters();
    }

    private void applyFilters() {
        if (mAdapter == null) {
            return;
        }
        List<OpHistoryItem> filteredList = new ArrayList<>();
        for (OpHistoryItem history : mCurrentOpHistories) {
            if (matchesFilters(history)) {
                filteredList.add(history);
            }
        }
        sortHistory(filteredList);
        mAdapter.setDefaultList(filteredList);
        boolean hasActiveFilters = hasActiveFilters();
        boolean hasFilteredOutAllHistory = !mCurrentOpHistories.isEmpty()
                && hasActiveFilters
                && filteredList.isEmpty();
        updateHistorySummary(filteredList);
        mChipClearFilters.setVisibility(hasActiveFilters ? View.VISIBLE : View.GONE);
        invalidateOptionsMenu();
        if (hasFilteredOutAllHistory) {
            mEmptyStateTitle.setText(R.string.op_history_empty_filtered_title);
            mEmptyStateSummary.setText(R.string.op_history_empty_filtered_message);
            mEmptyStateAction.setVisibility(View.VISIBLE);
        } else {
            mEmptyStateTitle.setText(R.string.op_history_empty_title);
            mEmptyStateSummary.setText(R.string.op_history_empty_message);
            mEmptyStateAction.setVisibility(View.GONE);
        }
    }

    private void updateHistorySummary(@NonNull List<OpHistoryItem> filteredList) {
        int totalCount = mCurrentOpHistories.size();
        if (totalCount == 0) {
            mHistorySummary.setVisibility(View.GONE);
            return;
        }
        int successCount = 0;
        int highRiskCount = 0;
        for (OpHistoryItem history : filteredList) {
            if (history.getStatus()) {
                ++successCount;
            }
            if (history.getRisk() == OperationJournalMetadata.RISK_HIGH) {
                ++highRiskCount;
            }
        }
        String shown = getResources().getQuantityString(
                R.plurals.op_history_operation_count,
                filteredList.size(),
                filteredList.size());
        String total = getResources().getQuantityString(
                R.plurals.op_history_operation_count,
                totalCount,
                totalCount);
        String succeeded = getResources().getQuantityString(
                R.plurals.op_history_succeeded_count,
                successCount,
                successCount);
        String highRisk = getResources().getQuantityString(
                R.plurals.op_history_high_risk_count,
                highRiskCount,
                highRiskCount);
        mHistorySummary.setText(getString(R.string.op_history_summary, shown, total, succeeded, highRisk));
        mHistorySummary.setVisibility(View.VISIBLE);
    }

    private boolean hasActiveFilters() {
        if (!TextUtils.isEmpty(getFilterQuery())) {
            return true;
        }
        for (Chip chip : mFilterChips) {
            if (chip.isChecked()) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private String getFilterQuery() {
        Editable text = mFilterText.getText();
        return text != null ? text.toString().trim() : "";
    }

    private boolean matchesFilters(@NonNull OpHistoryItem history) {
        return matchesQuery(history)
                && matchesStatus(history)
                && matchesRisk(history)
                && matchesType(history)
                && matchesMode(history)
                && matchesReversible(history)
                && matchesDate(history);
    }

    private boolean matchesQuery(@NonNull OpHistoryItem history) {
        String query = getFilterQuery();
        return TextUtils.isEmpty(query) || history.matchesQuery(this, query);
    }

    private boolean matchesStatus(@NonNull OpHistoryItem history) {
        boolean successChecked = mChipSucceeded.isChecked();
        boolean failureChecked = mChipFailed.isChecked();
        if (successChecked == failureChecked) {
            return true;
        }
        return successChecked == history.getStatus();
    }

    private boolean matchesRisk(@NonNull OpHistoryItem history) {
        boolean lowChecked = mChipLowRisk.isChecked();
        boolean mediumChecked = mChipMediumRisk.isChecked();
        boolean highChecked = mChipHighRisk.isChecked();
        if (!lowChecked && !mediumChecked && !highChecked) {
            return true;
        }
        int risk = history.getRisk();
        return (lowChecked && risk == OperationJournalMetadata.RISK_LOW)
                || (mediumChecked && risk == OperationJournalMetadata.RISK_MEDIUM)
                || (highChecked && risk == OperationJournalMetadata.RISK_HIGH);
    }

    private boolean matchesType(@NonNull OpHistoryItem history) {
        boolean batchChecked = mChipBatch.isChecked();
        boolean installerChecked = mChipInstaller.isChecked();
        boolean profilesChecked = mChipProfiles.isChecked();
        if (!batchChecked && !installerChecked && !profilesChecked) {
            return true;
        }
        String type = history.getType();
        return (batchChecked && OpHistoryManager.HISTORY_TYPE_BATCH_OPS.equals(type))
                || (installerChecked && OpHistoryManager.HISTORY_TYPE_INSTALLER.equals(type))
                || (profilesChecked && OpHistoryManager.HISTORY_TYPE_PROFILE.equals(type));
    }

    private boolean matchesMode(@NonNull OpHistoryItem history) {
        boolean rootChecked = mChipRoot.isChecked();
        boolean adbChecked = mChipAdb.isChecked();
        boolean noRootChecked = mChipNoRoot.isChecked();
        if (!rootChecked && !adbChecked && !noRootChecked) {
            return true;
        }
        String mode = history.getModeLabel();
        return (rootChecked && getString(R.string.root).equalsIgnoreCase(mode))
                || (adbChecked && getString(R.string.adb).equalsIgnoreCase(mode))
                || (noRootChecked && getString(R.string.no_root).equalsIgnoreCase(mode));
    }

    private boolean matchesReversible(@NonNull OpHistoryItem history) {
        return !mChipReversible.isChecked() || history.isReversible();
    }

    private boolean matchesDate(@NonNull OpHistoryItem history) {
        if (!mChipLastDay.isChecked() && !mChipLastWeek.isChecked()) {
            return true;
        }
        long age = System.currentTimeMillis() - history.getTimestamp();
        if (mChipLastDay.isChecked()) {
            return age <= ONE_DAY_MILLIS;
        }
        return age <= 7 * ONE_DAY_MILLIS;
    }

    private void sortHistory(@NonNull List<OpHistoryItem> histories) {
        Collections.sort(histories, (first, second) -> {
            switch (mSortMode) {
                case SORT_HIGHEST_RISK_FIRST:
                    return firstNonZero(
                            -Integer.compare(first.getRisk(), second.getRisk()),
                            compareNewestFirst(first, second));
                case SORT_MOST_FAILURES_FIRST:
                    return firstNonZero(
                            -Integer.compare(first.getFailedCount(), second.getFailedCount()),
                            compareNewestFirst(first, second));
                case SORT_TYPE:
                    return firstNonZero(
                            compareText(first.getLocalizedType(this), second.getLocalizedType(this)),
                            compareText(first.getLabel(this), second.getLabel(this)),
                            compareNewestFirst(first, second));
                case SORT_LABEL:
                    return firstNonZero(
                            compareText(first.getLabel(this), second.getLabel(this)),
                            compareNewestFirst(first, second));
                case SORT_NEWEST_FIRST:
                default:
                    return compareNewestFirst(first, second);
            }
        });
    }

    private static int firstNonZero(int... comparisons) {
        for (int comparison : comparisons) {
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int compareNewestFirst(@NonNull OpHistoryItem first, @NonNull OpHistoryItem second) {
        return -Long.compare(first.getTimestamp(), second.getTimestamp());
    }

    private static int compareText(@NonNull String first, @NonNull String second) {
        return String.CASE_INSENSITIVE_ORDER.compare(first, second);
    }

    private void showHistoryDetails(@NonNull OpHistoryItem history) {
        String detailMessage = history.getDetailMessage(this);
        new MaterialAlertDialogBuilder(this)
                .setTitle(history.getLabel(this))
                .setMessage(detailMessage)
                .setNegativeButton(R.string.copy, (dialog, which) -> copyHistoryDetails(history, detailMessage))
                .setNeutralButton(R.string.delete, (dialog, which) -> showDeleteHistoryConfirmation(history))
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void showHistoryActions(@NonNull OpHistoryItem history) {
        List<CharSequence> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(getString(R.string.copy));
        actions.add(() -> copyHistoryDetails(history, history.getDetailMessage(this)));
        labels.add(getString(R.string.share));
        actions.add(() -> shareHistory(history));
        Intent targetIntent = history.getPrimaryTargetIntent(this);
        if (targetIntent != null) {
            labels.add(getString(R.string.op_history_open_target));
            actions.add(() -> openHistoryTarget(targetIntent));
        }
        if (history.isReplayable()) {
            labels.add(getString(R.string.op_history_action_rerun));
            actions.add(() -> showRerunConfirmation(history));
        }
        labels.add(getString(R.string.delete));
        actions.add(() -> showDeleteHistoryConfirmation(history));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.op_history_actions)
                .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void copyHistoryDetails(@NonNull OpHistoryItem history, @NonNull String detailMessage) {
        String title = history.getLabel(this);
        ClipboardUtils.copyToClipboard(this, title, title + "\n\n" + detailMessage);
        UIUtils.displayShortToast(R.string.copied_to_clipboard);
    }

    private void openHistoryTarget(@NonNull Intent intent) {
        try {
            startActivity(intent);
        } catch (Throwable e) {
            UIUtils.displayLongToast(R.string.error);
        }
    }

    private void shareHistory(@NonNull OpHistoryItem history) {
        shareHistory(Collections.singletonList(history), history.getLabel(this));
    }

    private void showRerunConfirmation(@NonNull OpHistoryItem history) {
        if (!history.isReplayable()) {
            UIUtils.displayShortToast(R.string.op_history_action_not_replayable);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_confirm_execution)
                .setMessage(OperationPreflight.fromHistory(this, history)
                        .getConfirmationMessage(this, history))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_run, (dialog, which) ->
                        mViewModel.getServiceLauncherIntent(history))
                .show();
    }

    private void showDeleteHistoryConfirmation(@NonNull OpHistoryItem history) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(R.string.op_history_delete_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    mProgressIndicator.show();
                    mViewModel.deleteHistory(history);
                })
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_op_history_actions, menu);
        menu.findItem(R.id.action_seed_history).setVisible(BuildConfig.DEBUG);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        boolean hasVisibleHistory = !getVisibleHistory().isEmpty();
        boolean hasAnyHistory = !mCurrentOpHistories.isEmpty();
        menu.findItem(R.id.action_export_json).setEnabled(hasVisibleHistory);
        menu.findItem(R.id.action_export_csv).setEnabled(hasVisibleHistory);
        menu.findItem(R.id.action_export_all_json).setEnabled(hasAnyHistory);
        menu.findItem(R.id.action_export_all_csv).setEnabled(hasAnyHistory);
        menu.findItem(R.id.action_share_history).setEnabled(hasVisibleHistory);
        menu.findItem(R.id.action_share_all_history).setEnabled(hasAnyHistory);
        MenuItem sortItem = menu.findItem(R.id.action_sort_history);
        sortItem.setEnabled(hasAnyHistory);
        if (sortItem.getSubMenu() != null) {
            sortItem.getSubMenu().findItem(getSortMenuItemId()).setChecked(true);
        }
        menu.findItem(R.id.action_cleanup_history).setEnabled(hasAnyHistory);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_export_json) {
            exportVisibleHistory(true);
        } else if (id == R.id.action_export_csv) {
            exportVisibleHistory(false);
        } else if (id == R.id.action_export_all_json) {
            exportAllHistory(true);
        } else if (id == R.id.action_export_all_csv) {
            exportAllHistory(false);
        } else if (id == R.id.action_share_history) {
            shareVisibleHistory();
        } else if (id == R.id.action_share_all_history) {
            shareAllHistory();
        } else if (id == R.id.action_cleanup_history) {
            showHistoryCleanupDialog();
        } else if (id == R.id.action_sort_history_newest) {
            setSortMode(SORT_NEWEST_FIRST);
        } else if (id == R.id.action_sort_history_risk) {
            setSortMode(SORT_HIGHEST_RISK_FIRST);
        } else if (id == R.id.action_sort_history_failures) {
            setSortMode(SORT_MOST_FAILURES_FIRST);
        } else if (id == R.id.action_sort_history_type) {
            setSortMode(SORT_TYPE);
        } else if (id == R.id.action_sort_history_label) {
            setSortMode(SORT_LABEL);
        } else if (id == R.id.action_seed_history) {
            mProgressIndicator.show();
            mViewModel.addDebugHistoryFixtures();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    private void setSortMode(int sortMode) {
        sortMode = normalizeSortMode(sortMode);
        if (mSortMode != sortMode) {
            mSortMode = sortMode;
            Prefs.Privacy.setOpHistorySortOrder(sortMode);
            applyFilters();
        }
        invalidateOptionsMenu();
    }

    private static int normalizeSortMode(int sortMode) {
        if (sortMode < SORT_NEWEST_FIRST || sortMode > SORT_LABEL) {
            return SORT_NEWEST_FIRST;
        }
        return sortMode;
    }

    private int getSortMenuItemId() {
        switch (mSortMode) {
            case SORT_HIGHEST_RISK_FIRST:
                return R.id.action_sort_history_risk;
            case SORT_MOST_FAILURES_FIRST:
                return R.id.action_sort_history_failures;
            case SORT_TYPE:
                return R.id.action_sort_history_type;
            case SORT_LABEL:
                return R.id.action_sort_history_label;
            case SORT_NEWEST_FIRST:
            default:
                return R.id.action_sort_history_newest;
        }
    }

    private void exportVisibleHistory(boolean asJson) {
        exportHistory(getVisibleHistory(), asJson);
    }

    private void exportAllHistory(boolean asJson) {
        exportHistory(getAllHistoryForAction(), asJson);
    }

    private void exportHistory(@NonNull List<OpHistoryItem> histories, boolean asJson) {
        if (histories.isEmpty()) {
            UIUtils.displayShortToast(R.string.no_history);
            return;
        }
        try {
            mPendingExport = asJson
                    ? OperationHistoryExporter.toJson(this, histories)
                    : OperationHistoryExporter.toCsv(this, histories);
            mExportHistory.launch(getString(asJson
                    ? R.string.op_history_export_filename_json
                    : R.string.op_history_export_filename_csv));
        } catch (JSONException e) {
            Log.e(TAG, "Could not export operation history.", e);
            UIUtils.displayShortToast(R.string.export_failed);
        }
    }

    private void writePendingExport(@Nullable Uri uri) {
        if (uri == null || mPendingExport == null) {
            return;
        }
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                UIUtils.displayShortToast(R.string.export_failed);
                return;
            }
            os.write(mPendingExport.getBytes(StandardCharsets.UTF_8));
            UIUtils.displayShortToast(R.string.op_history_export_success);
        } catch (IOException e) {
            Log.e(TAG, "Could not write operation history export.", e);
            UIUtils.displayShortToast(R.string.export_failed);
        } finally {
            mPendingExport = null;
        }
    }

    private void shareVisibleHistory() {
        List<OpHistoryItem> histories = getVisibleHistory();
        if (histories.isEmpty()) {
            UIUtils.displayShortToast(R.string.no_history);
            return;
        }
        shareHistory(histories, getString(R.string.op_history));
    }

    private void shareAllHistory() {
        List<OpHistoryItem> histories = getAllHistoryForAction();
        if (histories.isEmpty()) {
            UIUtils.displayShortToast(R.string.no_history);
            return;
        }
        shareHistory(histories, getString(R.string.op_history));
    }

    private void shareHistory(@NonNull List<OpHistoryItem> histories, @NonNull String subject) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, OperationHistoryExporter.toText(this, histories));
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.op_history_share_title)));
        } catch (Throwable e) {
            UIUtils.displayLongToast(R.string.error);
        }
    }

    private void showHistoryCleanupDialog() {
        int visibleCount = getVisibleHistory().size();
        List<CharSequence> labels = new ArrayList<>();
        List<Integer> cleanupTypes = new ArrayList<>();
        if (visibleCount > 0) {
            labels.add(getResources().getQuantityString(
                    R.plurals.op_history_cleanup_delete_visible, visibleCount, visibleCount));
            cleanupTypes.add(CLEANUP_VISIBLE);
        }
        labels.add(getString(R.string.op_history_cleanup_delete_successful));
        cleanupTypes.add(CLEANUP_SUCCESSFUL);
        labels.add(getString(R.string.op_history_cleanup_delete_failed));
        cleanupTypes.add(CLEANUP_FAILED);
        labels.add(getString(R.string.op_history_cleanup_delete_older_7d));
        cleanupTypes.add(CLEANUP_OLDER_THAN_7_DAYS);
        labels.add(getString(R.string.op_history_cleanup_delete_older_30d));
        cleanupTypes.add(CLEANUP_OLDER_THAN_30_DAYS);
        labels.add(getString(R.string.op_history_cleanup_delete_older_90d));
        cleanupTypes.add(CLEANUP_OLDER_THAN_90_DAYS);
        labels.add(getString(R.string.op_history_cleanup_delete_older_180d));
        cleanupTypes.add(CLEANUP_OLDER_THAN_180_DAYS);
        labels.add(getString(R.string.op_history_cleanup_delete_older_365d));
        cleanupTypes.add(CLEANUP_OLDER_THAN_365_DAYS);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.op_history_cleanup)
                .setItems(labels.toArray(new CharSequence[0]), (dialog, which) ->
                        confirmHistoryCleanup(cleanupTypes.get(which), labels.get(which)))
                .show();
    }

    private void confirmHistoryCleanup(int cleanupType, @NonNull CharSequence label) {
        int visibleCount = getVisibleHistory().size();
        new MaterialAlertDialogBuilder(this)
                .setTitle(label)
                .setMessage(cleanupType == CLEANUP_VISIBLE
                        ? getResources().getQuantityString(
                        R.plurals.op_history_cleanup_visible_confirmation, visibleCount, visibleCount)
                        : getString(R.string.op_history_cleanup_confirmation))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> runHistoryCleanup(cleanupType))
                .show();
    }

    private void runHistoryCleanup(int cleanupType) {
        mProgressIndicator.show();
        switch (cleanupType) {
            case CLEANUP_VISIBLE:
                mViewModel.deleteHistories(getVisibleHistory());
                break;
            case CLEANUP_SUCCESSFUL:
                mViewModel.deleteHistoryByStatus(OpHistoryManager.STATUS_SUCCESS);
                break;
            case CLEANUP_FAILED:
                mViewModel.deleteHistoryByStatus(OpHistoryManager.STATUS_FAILURE);
                break;
            case CLEANUP_OLDER_THAN_7_DAYS:
                mViewModel.deleteHistoryOlderThan(7);
                break;
            case CLEANUP_OLDER_THAN_30_DAYS:
                mViewModel.deleteHistoryOlderThan(30);
                break;
            case CLEANUP_OLDER_THAN_90_DAYS:
                mViewModel.deleteHistoryOlderThan(90);
                break;
            case CLEANUP_OLDER_THAN_180_DAYS:
                mViewModel.deleteHistoryOlderThan(180);
                break;
            case CLEANUP_OLDER_THAN_365_DAYS:
                mViewModel.deleteHistoryOlderThan(365);
                break;
        }
    }

    @NonNull
    private List<OpHistoryItem> getVisibleHistory() {
        return mAdapter != null ? mAdapter.getCurrentList() : Collections.emptyList();
    }

    @NonNull
    private List<OpHistoryItem> getAllHistoryForAction() {
        List<OpHistoryItem> histories = new ArrayList<>(mCurrentOpHistories);
        sortHistory(histories);
        return histories;
    }

    static class OpHistoryAdapter extends RecyclerView.Adapter<OpHistoryAdapter.ViewHolder> {
        private final List<OpHistoryItem> mAdapterList = new ArrayList<>();
        private final OpHistoryActivity mActivity;
        private final int mColorSuccess;
        private final int mColorFailure;

        static class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView itemView;
            TextView type;
            TextView title;
            TextView execTime;
            TextView metadata;
            Button execBtn;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                this.itemView = (MaterialCardView) itemView;
                type = itemView.findViewById(R.id.type);
                title = itemView.findViewById(android.R.id.title);
                execTime = itemView.findViewById(android.R.id.summary);
                metadata = itemView.findViewById(R.id.item_metadata);
                execBtn = itemView.findViewById(R.id.item_action);
            }
        }

        OpHistoryAdapter(@NonNull OpHistoryActivity activity) {
            mActivity = activity;
            mColorSuccess = ColorCodes.getSuccessColor(activity);
            mColorFailure = ColorCodes.getFailureColor(activity);
            setHasStableIds(true);
        }

        void setDefaultList(@NonNull List<OpHistoryItem> list) {
            synchronized (mAdapterList) {
                AdapterUtils.notifyDataSetChanged(this, mAdapterList, list);
            }
        }

        @NonNull
        List<OpHistoryItem> getCurrentList() {
            synchronized (mAdapterList) {
                return new ArrayList<>(mAdapterList);
            }
        }

        @Override
        public int getItemCount() {
            synchronized (mAdapterList) {
                return mAdapterList.size();
            }
        }

        @Override
        public long getItemId(int position) {
            synchronized (mAdapterList) {
                return mAdapterList.get(position).getId();
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_op_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OpHistoryItem history;
            synchronized (mAdapterList) {
                history = mAdapterList.get(position);
            }
            holder.itemView.setStrokeColor(history.getStatus() ? mColorSuccess : mColorFailure);
            holder.type.setText(history.getLocalizedType(mActivity));
            holder.title.setText(history.getLabel(mActivity));
            holder.execTime.setText(DateUtils.formatLongDateTime(mActivity, history.getTimestamp()));
            holder.metadata.setText(history.getMetadataSummary(mActivity));
            holder.itemView.setContentDescription(mActivity.getString(R.string.op_history_item_content_description,
                    history.getLabel(mActivity),
                    history.getLocalizedType(mActivity),
                    history.getMetadataSummary(mActivity)));
            holder.itemView.setOnClickListener(v -> mActivity.showHistoryDetails(history));
            holder.itemView.setOnLongClickListener(v -> {
                mActivity.showHistoryActions(history);
                return true;
            });
            boolean replayable = history.isReplayable();
            holder.execBtn.setVisibility(replayable ? View.VISIBLE : View.INVISIBLE);
            holder.execBtn.setEnabled(replayable);
            holder.execBtn.setClickable(replayable);
            holder.execBtn.setFocusable(replayable);
            holder.execBtn.setOnClickListener(replayable ? v -> mActivity.showRerunConfirmation(history) : null);
            holder.execBtn.setContentDescription(mActivity.getString(replayable
                    ? R.string.op_history_action_rerun
                    : R.string.op_history_action_not_replayable));
        }
    }

    public static class OpHistoryViewModel extends AndroidViewModel {
        private final MutableLiveData<List<OpHistoryItem>> mOpHistoriesLiveData = new MutableLiveData<>();
        private final MutableLiveData<Boolean> mClearHistoryLiveData = new MutableLiveData<>();
        private final MutableLiveData<Boolean> mDeleteHistoryLiveData = new MutableLiveData<>();
        private final MutableLiveData<Integer> mCleanupHistoryLiveData = new MutableLiveData<>();
        private final MutableLiveData<Integer> mDebugSeedLiveData = new MutableLiveData<>();
        private final MutableLiveData<Intent> mServiceLauncherIntentLiveData = new MutableLiveData<>();
        private Future<?> mOpHistoriesResult;

        public OpHistoryViewModel(@NonNull Application application) {
            super(application);
        }

        public LiveData<List<OpHistoryItem>> getOpHistoriesLiveData() {
            return mOpHistoriesLiveData;
        }

        public LiveData<Boolean> getClearHistoryLiveData() {
            return mClearHistoryLiveData;
        }

        public MutableLiveData<Intent> getServiceLauncherIntentLiveData() {
            return mServiceLauncherIntentLiveData;
        }

        public LiveData<Boolean> getDeleteHistoryLiveData() {
            return mDeleteHistoryLiveData;
        }

        public LiveData<Integer> getCleanupHistoryLiveData() {
            return mCleanupHistoryLiveData;
        }

        public LiveData<Integer> getDebugSeedLiveData() {
            return mDebugSeedLiveData;
        }

        public void loadOpHistories() {
            if (mOpHistoriesResult != null) {
                mOpHistoriesResult.cancel(true);
            }
            mOpHistoriesResult = ThreadUtils.postOnBackgroundThread(() -> {
                synchronized (mOpHistoriesLiveData) {
                    mOpHistoriesLiveData.postValue(loadOpHistoryItems());
                }
            });
        }

        public void clearHistory() {
            ThreadUtils.postOnBackgroundThread(() -> {
                synchronized (mOpHistoriesLiveData) {
                    OpHistoryManager.clearAllHistory();
                    mClearHistoryLiveData.postValue(true);
                    mOpHistoriesLiveData.postValue(Collections.emptyList());
                }
            });
        }

        public void deleteHistory(@NonNull OpHistoryItem opHistoryItem) {
            ThreadUtils.postOnBackgroundThread(() -> {
                synchronized (mOpHistoriesLiveData) {
                    OpHistoryManager.deleteHistoryItem(opHistoryItem.getId());
                    mDeleteHistoryLiveData.postValue(true);
                    mOpHistoriesLiveData.postValue(loadOpHistoryItems());
                }
            });
        }

        public void deleteHistories(@NonNull List<OpHistoryItem> opHistoryItems) {
            ThreadUtils.postOnBackgroundThread(() -> {
                synchronized (mOpHistoriesLiveData) {
                    int deletedCount = 0;
                    for (OpHistoryItem opHistoryItem : opHistoryItems) {
                        OpHistoryManager.deleteHistoryItem(opHistoryItem.getId());
                        ++deletedCount;
                    }
                    mCleanupHistoryLiveData.postValue(deletedCount);
                    mOpHistoriesLiveData.postValue(loadOpHistoryItems());
                }
            });
        }

        public void deleteHistoryByStatus(@OpHistoryManager.Status String status) {
            ThreadUtils.postOnBackgroundThread(() -> {
                synchronized (mOpHistoriesLiveData) {
                    int deletedCount = OpHistoryManager.deleteHistoryItemsByStatus(status);
                    mCleanupHistoryLiveData.postValue(deletedCount);
                    mOpHistoriesLiveData.postValue(loadOpHistoryItems());
                }
            });
        }

        public void deleteHistoryOlderThan(int days) {
            ThreadUtils.postOnBackgroundThread(() -> {
                synchronized (mOpHistoriesLiveData) {
                    int deletedCount = OpHistoryManager.pruneHistoryOlderThan(days);
                    mCleanupHistoryLiveData.postValue(deletedCount);
                    mOpHistoriesLiveData.postValue(loadOpHistoryItems());
                }
            });
        }

        public void addDebugHistoryFixtures() {
            ThreadUtils.postOnBackgroundThread(() -> {
                synchronized (mOpHistoriesLiveData) {
                    int addedCount = OpHistoryManager.addDebugHistoryFixtures(getApplication());
                    mDebugSeedLiveData.postValue(addedCount);
                    mOpHistoriesLiveData.postValue(loadOpHistoryItems());
                }
            });
        }

        @NonNull
        private List<OpHistoryItem> loadOpHistoryItems() {
            OpHistoryManager.pruneHistoryOlderThan(Prefs.Privacy.getOpHistoryRetentionDays());
            List<OpHistory> opHistories = OpHistoryManager.getAllHistoryItems();
            Collections.sort(opHistories, (o1, o2) -> -Long.compare(o1.execTime, o2.execTime));
            List<OpHistoryItem> opHistoryItems = new ArrayList<>(opHistories.size());
            for (OpHistory history : opHistories) {
                try {
                    opHistoryItems.add(new OpHistoryItem(history));
                } catch (JSONException e) {
                    Log.w(TAG, e.getMessage(), e);
                }
            }
            return opHistoryItems;
        }

        public void getServiceLauncherIntent(@NonNull OpHistoryItem opHistoryItem) {
            ThreadUtils.postOnBackgroundThread(() -> {
                try {
                    Intent intent = OpHistoryManager.getExecutableIntent(getApplication(), opHistoryItem);
                    mServiceLauncherIntentLiveData.postValue(intent);
                } catch (JSONException e) {
                    Log.w(TAG, e.getMessage(), e);
                    mServiceLauncherIntentLiveData.postValue(null);
                }
            });
        }
    }
}
