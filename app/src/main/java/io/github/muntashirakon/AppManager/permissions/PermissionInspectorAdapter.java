// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import android.content.res.Resources;
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

class PermissionInspectorAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    interface OnRowClick {
        void onClick(@NonNull PermissionInspectorViewModel.Row row);
    }

    private static final int TYPE_SECTION = 0;
    private static final int TYPE_ROW = 1;

    /** Rows, with an Integer section title before each run of rows from the same section. */
    private final List<Object> mItems = new ArrayList<>();
    private final OnRowClick mClick;

    PermissionInspectorAdapter(OnRowClick click) {
        mClick = click;
    }

    void submit(List<PermissionInspectorViewModel.Row> rows) {
        mItems.clear();
        if (rows != null) {
            int section = 0;
            for (PermissionInspectorViewModel.Row row : rows) {
                if (row.sectionRes != section) {
                    section = row.sectionRes;
                    mItems.add(section);
                }
                mItems.add(row);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position) instanceof Integer ? TYPE_SECTION : TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SECTION) {
            return new SectionVH(inflater.inflate(R.layout.item_permission_section, parent, false));
        }
        return new VH(inflater.inflate(R.layout.item_permission_group, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = mItems.get(position);
        if (holder instanceof SectionVH) {
            ((SectionVH) holder).title.setText((Integer) item);
            return;
        }
        VH h = (VH) holder;
        PermissionInspectorViewModel.Row r = (PermissionInspectorViewModel.Row) item;
        Resources res = h.itemView.getResources();
        String label = res.getString(r.labelRes);
        String groupSummary = res.getString(r.summaryRes);
        String countSummary;
        if (r.stateKnown) {
            countSummary = res.getString(R.string.perm_inspector_count_fmt, r.grantedCount, r.requestedCount);
            h.count.setText(res.getString(R.string.perm_inspector_count_short_fmt, r.grantedCount,
                    r.requestedCount));
        } else {
            countSummary = res.getQuantityString(R.plurals.perm_inspector_count_unknown, r.requestedCount,
                    r.requestedCount);
            h.count.setText(res.getString(R.string.perm_inspector_count_unknown_short_fmt, r.requestedCount));
        }
        h.icon.setImageResource(r.iconRes);
        h.label.setText(label);
        h.summary.setText(groupSummary);
        h.itemView.setContentDescription(res.getString(R.string.perm_inspector_group_a11y, label, countSummary,
                groupSummary));
        h.itemView.setOnClickListener(v -> {
            if (mClick != null) mClick.onClick(r);
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class SectionVH extends RecyclerView.ViewHolder {
        final MaterialTextView title;

        SectionVH(@NonNull View v) {
            super(v);
            title = (MaterialTextView) v;
        }
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
