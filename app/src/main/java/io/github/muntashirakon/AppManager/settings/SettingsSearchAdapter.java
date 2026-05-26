// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

/** Compact RecyclerView adapter for the Settings search overlay. */
public final class SettingsSearchAdapter extends RecyclerView.Adapter<SettingsSearchAdapter.Holder> {

    public interface OnResultClickListener {
        void onResultClicked(@NonNull SettingsSearchIndex.Entry entry);
    }

    @NonNull
    private final List<SettingsSearchIndex.Entry> mEntries = new ArrayList<>();
    @Nullable
    private OnResultClickListener mListener;

    public void setListener(@Nullable OnResultClickListener listener) {
        mListener = listener;
    }

    public void submit(@NonNull List<SettingsSearchIndex.Entry> entries) {
        mEntries.clear();
        mEntries.addAll(entries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_settings_search, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SettingsSearchIndex.Entry entry = mEntries.get(position);
        holder.title.setText(entry.title);
        if (entry.summary != null && entry.summary.length() > 0) {
            holder.summary.setText(entry.summary);
            holder.summary.setVisibility(View.VISIBLE);
        } else {
            holder.summary.setVisibility(View.GONE);
        }
        holder.breadcrumb.setText(entry.parentLabel);
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onResultClicked(entry);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mEntries.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView breadcrumb;
        final TextView title;
        final TextView summary;

        Holder(@NonNull View itemView) {
            super(itemView);
            breadcrumb = itemView.findViewById(R.id.settings_search_breadcrumb);
            title = itemView.findViewById(R.id.settings_search_title);
            summary = itemView.findViewById(R.id.settings_search_summary);
        }
    }
}
