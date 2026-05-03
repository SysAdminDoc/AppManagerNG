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
    private MaterialTextView mFailedAppsTitle;
    private MaterialTextView mEmptyAppsView;
    private MaterialButton mLogToggler;
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
        mFailedAppsTitle = findViewById(R.id.batch_result_failed_apps_title);
        mEmptyAppsView = findViewById(R.id.batch_result_empty_apps);
        mRecyclerView = findViewById(R.id.list);
        mRecyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        mLogToggler = findViewById(R.id.action_view_logs);
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
        String failedAppsSummary = getResources().getQuantityString(R.plurals.batch_results_failed_app_count,
                failedAppCount, failedAppCount);
        String queueTitle = mBatchQueueItem != null ? mBatchQueueItem.getTitle() : null;
        if (TextUtils.isEmpty(queueTitle)) {
            queueTitle = getString(R.string.batch_ops);
        }
        mFailedAppsTitle.setText(failedAppsSummary);
        mRecyclerView.setVisibility(failedAppCount > 0 ? View.VISIBLE : View.GONE);
        mEmptyAppsView.setVisibility(failedAppCount > 0 ? View.GONE : View.VISIBLE);
        if (TextUtils.isEmpty(failureMessage)) {
            mStatusSummary.setText(R.string.batch_results_unknown_failure_summary);
        } else {
            mStatusSummary.setText(getString(R.string.batch_results_failure_summary,
                    failureMessage, failedAppsSummary, queueTitle));
        }
    }

    private void setLogs(@Nullable String logs) {
        mHasLogs = !TextUtils.isEmpty(logs);
        mLogViewer.setText(mHasLogs ? getFormattedLogs(logs) : getString(R.string.batch_results_no_logs_message));
        mLogToggler.setEnabled(mHasLogs);
        setLogsVisible(false);
    }

    private void setLogsVisible(boolean visible) {
        mLogsVisible = visible && mHasLogs;
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
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_retry) {
            if (mBatchQueueItem != null) {
                Intent BatchOpsIntent = BatchOpsService.getServiceIntent(this, mBatchQueueItem);
                ContextCompat.startForegroundService(this, BatchOpsIntent);
                item.setEnabled(false);
                UIUtils.displayShortToast(R.string.operation_running);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
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
