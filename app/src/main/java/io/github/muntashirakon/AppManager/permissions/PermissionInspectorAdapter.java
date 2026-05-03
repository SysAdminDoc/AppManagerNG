// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

class PermissionInspectorAdapter extends RecyclerView.Adapter<PermissionInspectorAdapter.VH> {
    interface OnGroupClick {
        void onClick(@NonNull PermissionGroupCatalog.Group group);
    }

    private final List<PermissionInspectorViewModel.Row> mRows = new ArrayList<>();
    private final OnGroupClick mClick;

    PermissionInspectorAdapter(OnGroupClick click) {
        mClick = click;
    }

    void submit(List<PermissionInspectorViewModel.Row> rows) {
        mRows.clear();
        if (rows != null) mRows.addAll(rows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_permission_group, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PermissionInspectorViewModel.Row r = mRows.get(position);
        String label = h.itemView.getResources().getString(r.group.labelRes);
        String groupSummary = h.itemView.getResources().getString(r.group.summaryRes);
        String countSummary = h.itemView.getResources().getString(
                R.string.perm_inspector_count_fmt, r.grantedCount, r.requestedCount);
        h.icon.setImageResource(r.group.iconRes);
        h.label.setText(label);
        h.summary.setText(groupSummary);
        h.count.setText(h.itemView.getResources().getString(
                R.string.perm_inspector_count_short_fmt, r.grantedCount, r.requestedCount));
        h.itemView.setContentDescription(h.itemView.getResources().getString(
                R.string.perm_inspector_group_a11y, label, countSummary, groupSummary));
        h.itemView.setOnClickListener(v -> {
            if (mClick != null) mClick.onClick(r.group);
        });
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final MaterialTextView label;
        final MaterialTextView summary;
        final MaterialTextView count;

        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            summary = v.findViewById(R.id.summary);
            count = v.findViewById(R.id.granted_count);
        }
    }
}
