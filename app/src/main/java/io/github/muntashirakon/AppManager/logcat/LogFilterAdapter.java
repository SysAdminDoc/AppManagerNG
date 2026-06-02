// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

// Copyright 2012 Nolan Lawson
public class LogFilterAdapter extends RecyclerView.Adapter<LogFilterAdapter.ViewHolder> {

    private OnClickListener mListener;
    private final List<LogFilter> mItems;

    public void setOnItemClickListener(OnClickListener listener) {
        mListener = listener;
    }

    public LogFilterAdapter(@NonNull List<LogFilter> items) {
        mItems = items;
    }

    public void add(@NonNull LogFilter filter) {
        int previousSize = mItems.size();
        mItems.add(filter);
        Collections.sort(mItems, LogFilter.COMPARATOR);
        int currentSize = mItems.size();
        AdapterUtils.notifyDataSetChanged(this, previousSize, currentSize);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_title_action, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final LogFilter logFilter = mItems.get(position);
        holder.textView.setText(logFilter.name);
        // Resolve the LIVE adapter position at click time via the ViewHolder rather
        // than capturing the stale bind-time `position`: after any row is deleted,
        // notifyItemRemoved only shifts/animates the other rows (it does not rebind
        // them), so their captured positions go stale — deleting a second row then
        // removed the wrong element or threw IndexOutOfBoundsException.
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (mListener != null) {
                mListener.onClick(holder.itemView, pos, mItems.get(pos));
            }
        });
        holder.actionButton.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            LogFilter toDelete = mItems.get(pos);
            ThreadUtils.postOnBackgroundThread(() -> AppsDb.getInstance().logFilterDao().delete(toDelete));
            mItems.remove(pos);
            notifyItemRemoved(pos);
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public interface OnClickListener {
        void onClick(View view, int position, LogFilter logFilter);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        MaterialButton actionButton;

        public ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.item_title);
            actionButton = itemView.findViewById(R.id.item_action);
            actionButton.setContentDescription(itemView.getContext().getString(R.string.item_remove));
        }
    }
}
