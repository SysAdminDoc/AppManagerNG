// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

public class OpHistoryActivity extends BaseActivity {
    private static final long ONE_DAY_MILLIS = 24L * 60 * 60 * 1000;

    private OpHistoryViewModel mViewModel;
    private OpHistoryAdapter mAdapter;
    private LinearProgressIndicator mProgressIndicator;
    private TextInputEditText mFilterText;
    private TextView mEmptyStateTitle;
    private TextView mEmptyStateSummary;
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

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_op_history);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(OpHistoryViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
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
        MaterialButton action = emptyView.findViewById(R.id.empty_state_action);
        action.setVisibility(View.GONE);
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
        mAdapter.setDefaultList(filteredList);
        boolean hasActiveFilters = hasActiveFilters();
        mChipClearFilters.setVisibility(hasActiveFilters ? View.VISIBLE : View.GONE);
        if (mCurrentOpHistories.isEmpty() || !hasActiveFilters) {
            mEmptyStateTitle.setText(R.string.op_history_empty_title);
            mEmptyStateSummary.setText(R.string.op_history_empty_message);
        } else {
            mEmptyStateTitle.setText(R.string.op_history_empty_filtered_title);
            mEmptyStateSummary.setText(R.string.op_history_empty_filtered_message);
        }
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

    private void showHistoryDetails(@NonNull OpHistoryItem history) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(history.getLabel(this))
                .setMessage(history.getDetailMessage(this))
                .setPositiveButton(R.string.close, null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else return super.onOptionsItemSelected(item);
        return true;
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
        }

        void setDefaultList(@NonNull List<OpHistoryItem> list) {
            synchronized (mAdapterList) {
                AdapterUtils.notifyDataSetChanged(this, mAdapterList, list);
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
                return mAdapterList.get(position).hashCode();
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
                // TODO: 1/26/25 Possible long click options
                //  1. Apply
                //  2. Delete
                //  3. Add as a profile (for profile and batch op)
                //  4. Export (for profile)
                //  5. Create shortcut
                return true;
            });
            holder.execBtn.setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.title_confirm_execution)
                    .setMessage(OperationPreflight.fromHistory(mActivity, history)
                            .getConfirmationMessage(mActivity, history))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.action_run, (dialog, which) ->
                            mActivity.mViewModel.getServiceLauncherIntent(history))
                    .show());
            holder.execBtn.setEnabled(history.isReplayable());
        }
    }

    public static class OpHistoryViewModel extends AndroidViewModel {
        private final MutableLiveData<List<OpHistoryItem>> mOpHistoriesLiveData = new MutableLiveData<>();
        private final MutableLiveData<Boolean> mClearHistoryLiveData = new MutableLiveData<>();
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

        public void loadOpHistories() {
            if (mOpHistoriesResult != null) {
                mOpHistoriesResult.cancel(true);
            }
            mOpHistoriesResult = ThreadUtils.postOnBackgroundThread(() -> {
                synchronized (mOpHistoriesLiveData) {
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
                    mOpHistoriesLiveData.postValue(opHistoryItems);
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
