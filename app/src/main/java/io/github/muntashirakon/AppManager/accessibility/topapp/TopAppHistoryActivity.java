// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.toast.ToastEntry;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.LiveDataChanges;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

/** Every app and overlay the top-app bubble saw, newest first, so one that flashed by can be found again. */
public class TopAppHistoryActivity extends BaseActivity {
    private TopAppHistoryViewModel mViewModel;
    private LinearProgressIndicator mProgressIndicator;
    private MaterialSwitch mRecordSwitch;
    private TextView mEmptySummary;
    private MaterialButton mEmptyAction;
    private HistoryAdapter mAdapter;
    @Nullable
    private TopAppHistoryViewModel.State mState;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_top_app_history);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(TopAppHistoryViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.show();
        mRecordSwitch = findViewById(R.id.record_switch);

        RecyclerView listView = findViewById(android.R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        View emptyView = findViewById(android.R.id.empty);
        ((ImageView) emptyView.findViewById(R.id.empty_state_icon)).setImageResource(R.drawable.ic_history);
        ((TextView) emptyView.findViewById(R.id.empty_state_title)).setText(R.string.top_app_history_empty_title);
        mEmptySummary = emptyView.findViewById(R.id.empty_state_summary);
        mEmptyAction = emptyView.findViewById(R.id.empty_state_action);
        listView.setEmptyView(emptyView);
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        mAdapter = new HistoryAdapter(this::copyRow);
        listView.setAdapter(mAdapter);

        mViewModel.getState().observe(this, this::showState);
        mViewModel.getCleared().observe(this, this::showCleared);
        // New changes show up while the screen is open.
        LiveDataChanges.observe(TopAppHistory.getInstance(this).getChanges(), this, mViewModel::load);
        mViewModel.load();
        refreshRecording();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The bubble may have been started or stopped from its tile or notification meanwhile.
        if (mRecordSwitch != null) {
            refreshRecording();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_top_app_history_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_copy_all) {
            mViewModel.copy(mState);
        } else if (id == R.id.action_clear) {
            mViewModel.clear();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    private void refreshRecording() {
        boolean recording = Prefs.Privacy.isTopAppHistoryEnabled();
        // Detached first, or setting the state to match would count as the user turning it on.
        mRecordSwitch.setOnCheckedChangeListener(null);
        mRecordSwitch.setChecked(recording);
        mRecordSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> onRecordingToggled(isChecked));
        updateGuidance();
    }

    /** The empty screen says what's missing, the setting or the bubble, with a button for it. */
    private void updateGuidance() {
        if (!Prefs.Privacy.isTopAppHistoryEnabled()) {
            mEmptySummary.setText(R.string.top_app_history_empty_off);
            mEmptyAction.setText(R.string.top_app_history_turn_on);
            mEmptyAction.setOnClickListener(v -> mRecordSwitch.setChecked(true));
            mEmptyAction.setVisibility(View.VISIBLE);
        } else if (!TopAppOverlayService.isRunning()) {
            mEmptySummary.setText(R.string.top_app_history_bubble_off);
            mEmptyAction.setText(R.string.top_app_history_start_bubble);
            mEmptyAction.setOnClickListener(v -> {
                TopAppOverlayController.toggleAndOpenSettings(this);
                updateGuidance();
            });
            mEmptyAction.setVisibility(View.VISIBLE);
        } else {
            mEmptySummary.setText(R.string.top_app_history_empty_on);
            mEmptyAction.setOnClickListener(null);
            mEmptyAction.setVisibility(View.GONE);
        }
    }

    private void onRecordingToggled(boolean recording) {
        Prefs.Privacy.setTopAppHistoryEnabled(recording);
        refreshRecording();
    }

    private void showCleared(@Nullable TopAppHistoryViewModel.Cleared cleared) {
        if (cleared == null) {
            return;
        }
        mViewModel.consumeCleared();
        List<TopAppTransition> removed = cleared.removed;
        if (removed == null) {
            UIUtils.displayShortToast(R.string.top_app_history_clear_failed);
            return;
        }
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.list), R.string.top_app_history_cleared,
                Snackbar.LENGTH_LONG);
        if (!removed.isEmpty()) {
            snackbar.setAction(R.string.undo, v -> mViewModel.undoClear(removed));
        }
        snackbar.show();
    }

    private void copyRow(@NonNull TopAppTransition entry) {
        if (mState != null) {
            mViewModel.copy(Collections.singletonList(entry), mState.labels);
        }
    }

    private void showState(@NonNull TopAppHistoryViewModel.State state) {
        mProgressIndicator.hide();
        mState = state;
        mAdapter.setState(state);
        updateGuidance();
    }

    @VisibleForTesting
    @NonNull
    List<TopAppTransition> getVisibleEntries() {
        return mState != null ? mState.entries : Collections.emptyList();
    }

    private interface OnEntryClickListener {
        void onEntryClick(@NonNull TopAppTransition entry);
    }

    private static final class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private final List<TopAppTransition> mEntries = new ArrayList<>();
        private final DateFormat mTimeFormat = ToastEntry.newTimeFormat();
        @NonNull
        private final OnEntryClickListener mListener;
        @NonNull
        private Map<String, CharSequence> mLabels = Collections.emptyMap();
        @NonNull
        private Map<String, ApplicationInfo> mAppInfos = Collections.emptyMap();

        HistoryAdapter(@NonNull OnEntryClickListener listener) {
            mListener = listener;
        }

        void setState(@NonNull TopAppHistoryViewModel.State state) {
            mLabels = state.labels;
            mAppInfos = state.appInfos;
            AdapterUtils.notifyDataSetChanged(this, mEntries, state.entries);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_top_app_history, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TopAppTransition entry = mEntries.get(position);
            Context context = holder.itemView.getContext();
            holder.text.setText(TopAppHistoryViewModel.describe(context, entry, mLabels));
            // Overlays stand out the same way they do in the bubble.
            holder.text.setTextColor(MaterialColors.getColor(holder.text, entry.isOverlay()
                    ? androidx.appcompat.R.attr.colorError : com.google.android.material.R.attr.colorOnSurface));
            holder.packageName.setText(TopAppHistoryViewModel.packagesOf(entry));
            holder.time.setText(mTimeFormat.format(new Date(entry.timestampMillis)));
            holder.icon.setTag(entry.packageName);
            ImageLoader.getInstance().displayImage(entry.packageName, mAppInfos.get(entry.packageName), holder.icon);
            holder.itemView.setOnClickListener(v -> mListener.onEntryClick(entry));
            ViewCompat.replaceAccessibilityAction(holder.itemView,
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                    context.getString(R.string.copy), null);
        }

        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView text;
            final TextView packageName;
            final TextView time;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.icon);
                text = itemView.findViewById(R.id.text);
                packageName = itemView.findViewById(R.id.package_name);
                time = itemView.findViewById(R.id.time);
            }
        }
    }
}
