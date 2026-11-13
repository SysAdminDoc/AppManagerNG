// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

/**
 * APKs that came from another device over Local sync. They can be installed again while that
 * device is gone, and they go on their own after the time the user picks.
 */
public class ReceivedApksActivity extends BaseActivity {
    private ReceivedApksViewModel mViewModel;
    private LinearProgressIndicator mProgress;
    private ItemsAdapter mAdapter;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_received_apks);
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        mViewModel = new ViewModelProvider(this).get(ReceivedApksViewModel.class);
        mProgress = findViewById(R.id.progress_linear);
        mProgress.setVisibilityAfterHide(View.GONE);
        mProgress.show();

        LanSyncSettings settings = mViewModel.getSettings();
        ChipGroup retention = findViewById(R.id.retention_chips);
        retention.check(chipFor(settings.getRetention()));
        retention.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                mViewModel.setRetention(retentionFor(checkedIds.get(0)));
            }
        });
        MaterialSwitch keep = findViewById(R.id.keep_installed);
        keep.setChecked(settings.isKeepAfterInstall());
        keep.setOnCheckedChangeListener((button, checked) -> mViewModel.setKeepAfterInstall(checked));

        RecyclerView listView = findViewById(android.R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        View emptyView = findViewById(android.R.id.empty);
        ((ImageView) emptyView.findViewById(R.id.empty_state_icon)).setImageResource(R.drawable.ic_sync);
        ((TextView) emptyView.findViewById(R.id.empty_state_title)).setText(R.string.received_apks_empty);
        ((TextView) emptyView.findViewById(R.id.empty_state_summary)).setText(R.string.received_apks_empty_summary);
        emptyView.findViewById(R.id.empty_state_action).setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        listView.setEmptyView(emptyView);
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        mAdapter = new ItemsAdapter(new ItemsAdapter.Listener() {
            @Override
            public void onInstall(@NonNull ReceivedApkStore.Entry entry) {
                mProgress.show();
                mViewModel.install(entry);
            }

            @Override
            public void onDelete(@NonNull ReceivedApkStore.Entry entry) {
                mViewModel.delete(entry);
            }
        });
        listView.setAdapter(mAdapter);

        mViewModel.getItems().observe(this, items -> {
            mProgress.hide();
            if (items != null) {
                mAdapter.setItems(items);
            }
        });
        mViewModel.getMessage().observe(this, message -> {
            if (message != null) {
                mProgress.hide();
                UIUtils.displayLongToast(message);
                mViewModel.onMessageShown();
            }
        });
        mViewModel.getReady().observe(this, result -> {
            if (result == null) {
                return;
            }
            mProgress.hide();
            mViewModel.onInstallHandedOff();
            startActivity(PackageInstallerActivity.getLaunchableInstance(this, LanSyncActivity.sourceOf(result)));
        });
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            // Signed in after the screen was already showing, so onStart() had nothing to load
            mViewModel.load();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mViewModel != null) {
            // Back from the installer: an app that went on may take its APK with it
            mViewModel.load();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static int chipFor(int retention) {
        switch (retention) {
            case LanSyncSettings.RETENTION_2_HOURS:
                return R.id.retention_2_hours;
            case LanSyncSettings.RETENTION_7_DAYS:
                return R.id.retention_7_days;
            case LanSyncSettings.RETENTION_24_HOURS:
            default:
                return R.id.retention_24_hours;
        }
    }

    private static int retentionFor(int chipId) {
        if (chipId == R.id.retention_2_hours) {
            return LanSyncSettings.RETENTION_2_HOURS;
        } else if (chipId == R.id.retention_7_days) {
            return LanSyncSettings.RETENTION_7_DAYS;
        }
        return LanSyncSettings.RETENTION_24_HOURS;
    }

    static final class ItemsAdapter extends RecyclerView.Adapter<ItemsAdapter.ViewHolder> {
        interface Listener {
            void onInstall(@NonNull ReceivedApkStore.Entry entry);

            void onDelete(@NonNull ReceivedApkStore.Entry entry);
        }

        @NonNull
        private final Listener mListener;
        @NonNull
        private final List<ReceivedApksViewModel.Item> mItems = new ArrayList<>();

        ItemsAdapter(@NonNull Listener listener) {
            mListener = listener;
        }

        @SuppressLint("NotifyDataSetChanged")
        void setItems(@NonNull List<ReceivedApksViewModel.Item> items) {
            mItems.clear();
            mItems.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_received_apk, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ReceivedApksViewModel.Item item = mItems.get(position);
            ReceivedApkStore.Entry entry = item.entry;
            Context context = holder.itemView.getContext();
            holder.label.setText(entry.app.label);
            holder.packageName.setText(entry.app.packageName);
            holder.version.setText(context.getString(R.string.received_apks_version, entry.app.versionName,
                    entry.app.versionCode));
            holder.detail.setText(detailFor(context, item, System.currentTimeMillis()));
            holder.install.setVisibility(entry.complete ? View.VISIBLE : View.GONE);
            holder.install.setOnClickListener(v -> mListener.onInstall(entry));
            holder.delete.setOnClickListener(v -> mListener.onDelete(entry));
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        @NonNull
        static String detailFor(@NonNull Context context, @NonNull ReceivedApksViewModel.Item item, long now) {
            long total = item.entry.app.totalSize();
            if (!item.entry.complete) {
                return context.getString(R.string.received_apks_partial,
                        Formatter.formatShortFileSize(context, item.bytes),
                        Formatter.formatShortFileSize(context, total));
            }
            CharSequence when = DateUtils.getRelativeTimeSpanString(item.entry.updatedAt, now,
                    DateUtils.MINUTE_IN_MILLIS);
            return context.getString(R.string.received_apks_detail, Formatter.formatShortFileSize(context, total),
                    when);
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {
            final TextView label;
            final TextView packageName;
            final TextView version;
            final TextView detail;
            final MaterialButton install;
            final MaterialButton delete;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                label = itemView.findViewById(R.id.label);
                packageName = itemView.findViewById(R.id.package_name);
                version = itemView.findViewById(R.id.version);
                detail = itemView.findViewById(R.id.detail);
                install = itemView.findViewById(R.id.install);
                delete = itemView.findViewById(R.id.delete);
            }
        }
    }
}
