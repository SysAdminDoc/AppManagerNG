// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.RestartUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.AccessibilityUtils;

public class BatchOpsResultsActivity extends BaseActivity {
    private RecyclerView mRecyclerView;
    private AppCompatEditText mLogViewer;
    private MaterialTextView mStatusSummary;
    private MaterialTextView mStatusMeta;
    private MaterialTextView mFailedAppsTitle;
    private MaterialTextView mFailedAppsSummaryView;
    private MaterialTextView mLogsTitle;
    private MaterialTextView mLogsSummary;
    private View mEmptyState;
    private MaterialButton mLogToggler;
    private MaterialButton mRetryButton;
    @Nullable
    private MenuItem mRetryMenu;
    private int mFailedAppCount;
    @NonNull
    private String mFailedAppsSummary = "";
    @NonNull
    private String mQueueTitle = "";
    private boolean mRetryStarted;
    private boolean mHasLogs;
    private boolean mLogsVisible;

    @Nullable
    private BatchQueueItem mBatchQueueItem;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        if (getIntent() == null) {
            finish();
            return;
        }
        if (restartIfNeeded(getIntent())) {
            return;
        }
        setContentView(R.layout.activity_batch_ops_results);
        setSupportActionBar(findViewById(R.id.toolbar));
        findViewById(R.id.progress_linear).setVisibility(View.GONE);
        mStatusSummary = findViewById(R.id.batch_result_status_summary);
        mStatusMeta = findViewById(R.id.batch_result_status_meta);
        mFailedAppsTitle = findViewById(R.id.batch_result_failed_apps_title);
        mFailedAppsSummaryView = findViewById(R.id.batch_result_failed_apps_summary);
        mEmptyState = findViewById(android.R.id.empty);
        MaterialTextView emptyTitle = findViewById(R.id.empty_state_title);
        MaterialTextView emptySummary = findViewById(R.id.empty_state_summary);
        MaterialButton emptyAction = findViewById(R.id.empty_state_action);
        emptyTitle.setText(R.string.batch_results_no_failed_apps_title);
        emptySummary.setText(R.string.batch_results_no_failed_apps_message);
        emptyAction.setVisibility(View.GONE);
        mRecyclerView = findViewById(R.id.list);
        mRecyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        mRetryButton = findViewById(R.id.action_retry_failed);
        mRetryButton.setOnClickListener(v -> confirmRetry());
        mLogToggler = findViewById(R.id.action_view_logs);
        mLogsTitle = findViewById(R.id.batch_result_logs_title);
        mLogsSummary = findViewById(R.id.batch_result_logs_summary);
        mLogViewer = findViewById(R.id.text);
        mLogViewer.setKeyListener(null);
        mLogToggler.setOnClickListener(v -> setLogsVisible(!mLogsVisible));
        handleIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.clear();
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (restartIfNeeded(intent)) {
            return;
        }
        handleIntent(intent);
    }

    private void handleIntent(@NonNull Intent intent) {
        mBatchQueueItem = IntentCompat.getUnwrappedParcelableExtra(intent, BatchOpsService.EXTRA_QUEUE_ITEM, BatchQueueItem.class);
        if (mBatchQueueItem == null) {
            finish();
            return;
        }
        mRetryStarted = false;
        setTitle(R.string.batch_ops_results_title);
        String failureMessage = intent.getStringExtra(BatchOpsService.EXTRA_FAILURE_MESSAGE);
        ArrayList<CharSequence> packageLabels = PackageUtils.packagesToAppLabels(getPackageManager(),
                mBatchQueueItem.getPackages(), mBatchQueueItem.getUsers());
        RecyclerAdapter adapter = new RecyclerAdapter(packageLabels);
        mRecyclerView.setAdapter(adapter);
        updateResultSummary(failureMessage, adapter.getItemCount());
        setLogs(BatchOpsLogger.getAllLogs());
        intent.removeExtra(BatchOpsService.EXTRA_QUEUE_ITEM);
    }

    private void updateResultSummary(@Nullable String failureMessage, int failedAppCount) {
        mFailedAppCount = failedAppCount;
        mFailedAppsSummary = getResources().getQuantityString(R.plurals.batch_results_failed_app_count,
                failedAppCount, failedAppCount);
        mQueueTitle = mBatchQueueItem != null ? mBatchQueueItem.getTitle() : null;
        if (TextUtils.isEmpty(mQueueTitle)) {
            mQueueTitle = getString(R.string.batch_ops);
        }
        mStatusMeta.setText(getString(R.string.batch_results_meta, mFailedAppsSummary, mQueueTitle));
        mFailedAppsTitle.setText(mFailedAppsSummary);
        mFailedAppsTitle.setVisibility(failedAppCount > 0 ? View.VISIBLE : View.GONE);
        mFailedAppsSummaryView.setVisibility(failedAppCount > 0 ? View.VISIBLE : View.GONE);
        mRecyclerView.setVisibility(failedAppCount > 0 ? View.VISIBLE : View.GONE);
        mEmptyState.setVisibility(failedAppCount > 0 ? View.GONE : View.VISIBLE);
        if (TextUtils.isEmpty(failureMessage)) {
            mStatusSummary.setText(R.string.batch_results_unknown_failure_summary);
        } else {
            mStatusSummary.setText(failureMessage);
        }
        updateRetryState();
    }

    private void setLogs(@Nullable String logs) {
        mHasLogs = !TextUtils.isEmpty(logs);
        mLogViewer.setText(mHasLogs ? getFormattedLogs(logs) : getString(R.string.batch_results_no_logs_message));
        mLogToggler.setEnabled(mHasLogs);
        setLogsVisible(false);
    }

    private void setLogsVisible(boolean visible) {
        mLogsVisible = visible && mHasLogs;
        mLogsTitle.setVisibility(mLogsVisible ? View.VISIBLE : View.GONE);
        mLogsSummary.setVisibility(mLogsVisible ? View.VISIBLE : View.GONE);
        mLogViewer.setVisibility(mLogsVisible ? View.VISIBLE : View.GONE);
        if (mHasLogs) {
            mLogToggler.setText(mLogsVisible ? R.string.hide_logs : R.string.view_logs);
        } else {
            mLogToggler.setText(R.string.batch_results_no_logs_button);
        }
        if (mLogsVisible) {
            AccessibilityUtils.requestAccessibilityFocus(mLogViewer);
        }
    }

    private static boolean restartIfNeeded(@NonNull Intent intent) {
        if (intent.getBooleanExtra(BatchOpsService.EXTRA_REQUIRES_RESTART, false)) {
            RestartUtils.restart(RestartUtils.RESTART_NORMAL);
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_batch_ops_results_actions, menu);
        mRetryMenu = menu.findItem(R.id.action_retry);
        updateRetryState();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mRetryMenu = menu.findItem(R.id.action_retry);
        updateRetryState();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_retry) {
            confirmRetry();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmRetry() {
        if (!canRetry()) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.batch_results_retry_confirm_title)
                .setMessage(getString(R.string.batch_results_retry_confirm_body, mQueueTitle, mFailedAppsSummary))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.batch_results_retry_failed_apps, (dialog, which) -> retryFailedApps())
                .show();
    }

    private void retryFailedApps() {
        if (!canRetry() || mBatchQueueItem == null) {
            return;
        }
        Intent batchOpsIntent = BatchOpsService.getServiceIntent(this, mBatchQueueItem);
        ContextCompat.startForegroundService(this, batchOpsIntent);
        mRetryStarted = true;
        updateRetryState();
        UIUtils.displayShortToast(R.string.batch_results_retry_started);
    }

    private boolean canRetry() {
        return mBatchQueueItem != null && mFailedAppCount > 0 && !mRetryStarted;
    }

    private void updateRetryState() {
        boolean hasFailedApps = mFailedAppCount > 0;
        boolean canRetry = canRetry();
        if (mRetryButton != null) {
            mRetryButton.setVisibility(hasFailedApps ? View.VISIBLE : View.GONE);
            mRetryButton.setEnabled(canRetry);
            mRetryButton.setText(mRetryStarted ? R.string.operation_running : R.string.batch_results_retry_failed_apps);
        }
        if (mRetryMenu != null) {
            mRetryMenu.setVisible(hasFailedApps);
            mRetryMenu.setEnabled(canRetry);
            mRetryMenu.setTitle(mRetryStarted ? R.string.operation_running : R.string.batch_results_retry_failed_apps);
        }
    }

    @Override
    protected void onDestroy() {
        BatchOpsLogger.clearLogs();
        super.onDestroy();
    }

    public CharSequence getFormattedLogs(@Nullable String logs) {
        if (TextUtils.isEmpty(logs)) {
            return "";
        }
        SpannableString str = new SpannableString(logs);
        int fIndex = 0;
        while (true) {
            fIndex = logs.indexOf("====> ", fIndex);
            if (fIndex == -1) {
                return str;
            }
            int lIndex = logs.indexOf('\n', fIndex);
            if (lIndex == -1) {
                lIndex = logs.length();
            }
            str.setSpan(new StyleSpan(Typeface.BOLD), fIndex, lIndex, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            if (lIndex >= logs.length()) {
                return str;
            }
            fIndex = lIndex + 1;
        }
    }

    static class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ViewHolder> {
        @NonNull
        private final List<CharSequence> mAppLabels;

        public RecyclerAdapter(@Nullable List<CharSequence> appLabels) {
            mAppLabels = appLabels == null ? Collections.emptyList() : appLabels;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_batch_ops_result_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CharSequence label = mAppLabels.get(position);
            holder.labelView.setText(label);
            holder.itemView.setContentDescription(holder.itemView.getContext()
                    .getString(R.string.batch_results_failed_app_content_description, label));
        }

        @Override
        public int getItemCount() {
            return mAppLabels.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView labelView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                this.labelView = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}
