// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.components;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;

import java.util.Collections;
import java.util.List;

public class CursorTableAdapter extends RecyclerView.Adapter<CursorTableAdapter.ViewHolder> {
    private static final int CELL_WIDTH_DP = 160;
    private static final int CELL_MIN_HEIGHT_DP = 36;

    @NonNull
    private String[] mColumns = new String[0];
    @NonNull
    private List<List<String>> mRows = Collections.emptyList();

    public void submitResult(@NonNull ProviderQueryUtils.QueryResult result) {
        mColumns = result.columns;
        mRows = result.rows;
        notifyDataSetChanged();
    }

    public void clear() {
        mColumns = new String[0];
        mRows = Collections.emptyList();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayoutCompat row = new LinearLayoutCompat(parent.getContext());
        row.setOrientation(LinearLayoutCompat.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setMinimumHeight(dp(parent.getContext(), CELL_MIN_HEIGHT_DP));
        return new ViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position == 0) {
            holder.bind(mColumns, true);
        } else {
            holder.bind(mRows.get(position - 1), false);
        }
    }

    @Override
    public int getItemCount() {
        return mColumns.length == 0 ? 0 : mRows.size() + 1;
    }

    private static int dp(@NonNull Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        @NonNull
        private final LinearLayoutCompat mRow;

        ViewHolder(@NonNull LinearLayoutCompat itemView) {
            super(itemView);
            mRow = itemView;
        }

        void bind(@NonNull String[] values, boolean header) {
            mRow.removeAllViews();
            for (String value : values) {
                mRow.addView(createCell(value, header));
            }
        }

        void bind(@NonNull List<String> values, boolean header) {
            mRow.removeAllViews();
            for (String value : values) {
                mRow.addView(createCell(value, header));
            }
        }

        @NonNull
        private MaterialTextView createCell(@NonNull String value, boolean header) {
            Context context = mRow.getContext();
            MaterialTextView textView = new MaterialTextView(context);
            LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(dp(context, CELL_WIDTH_DP),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            textView.setLayoutParams(params);
            int paddingHorizontal = dp(context, 8);
            int paddingVertical = dp(context, 6);
            textView.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
            textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setMaxLines(header ? 2 : 3);
            textView.setTextIsSelectable(true);
            textView.setText(value);
            if (header) {
                textView.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                textView.setTypeface(Typeface.MONOSPACE);
            }
            return textView;
        }
    }
}
