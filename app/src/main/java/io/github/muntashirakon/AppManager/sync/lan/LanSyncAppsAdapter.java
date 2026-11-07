// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

/**
 * Rows of the other device's apps. A pull button shows only where a pull can work: the other
 * copy is newer or missing here, and the keys match.
 */
class LanSyncAppsAdapter extends RecyclerView.Adapter<LanSyncAppsAdapter.ViewHolder> {
    interface OnPullListener {
        void onPull(@NonNull LanSyncViewModel.AppRow row);
    }

    @NonNull
    private final OnPullListener mListener;
    @NonNull
    private final List<LanSyncViewModel.AppRow> mRows = new ArrayList<>();
    private boolean mEnabled = true;

    LanSyncAppsAdapter(@NonNull OnPullListener listener) {
        mListener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    void setRows(@NonNull List<LanSyncViewModel.AppRow> rows, boolean enabled) {
        mRows.clear();
        mRows.addAll(rows);
        mEnabled = enabled;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lan_sync_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LanSyncViewModel.AppRow row = mRows.get(position);
        Context context = holder.itemView.getContext();
        holder.label.setText(row.remote.label);
        holder.packageName.setText(row.remote.packageName);
        String there = context.getString(R.string.lan_sync_version_there, row.remote.versionName,
                row.remote.versionCode);
        holder.versions.setText(row.local == null ? there : there + "\n" + context.getString(
                R.string.lan_sync_version_here, row.local.versionName, row.local.versionCode));
        holder.note.setText(noteFor(context, row));
        boolean canPull = SyncComparator.canPull(row.status);
        holder.pull.setVisibility(canPull ? View.VISIBLE : View.GONE);
        holder.pull.setEnabled(canPull && mEnabled);
        holder.pull.setOnClickListener(canPull ? v -> mListener.onPull(row) : null);
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    @NonNull
    static String noteFor(@NonNull Context context, @NonNull LanSyncViewModel.AppRow row) {
        switch (row.status) {
            case SIGNATURE_DIFFERS:
                return context.getString(R.string.lan_sync_note_signature);
            case SAME:
                return context.getString(R.string.lan_sync_note_same);
            case OLDER:
                return context.getString(R.string.lan_sync_note_older);
            case NEWER:
            case NOT_INSTALLED:
            default:
                return context.getString(R.string.lan_sync_note_size,
                        Formatter.formatShortFileSize(context, row.remote.totalSize()));
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView label;
        final TextView packageName;
        final TextView versions;
        final TextView note;
        final MaterialButton pull;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.label);
            packageName = itemView.findViewById(R.id.package_name);
            versions = itemView.findViewById(R.id.versions);
            note = itemView.findViewById(R.id.note);
            pull = itemView.findViewById(R.id.pull);
        }
    }
}
