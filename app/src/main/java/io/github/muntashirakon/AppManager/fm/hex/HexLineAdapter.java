// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.hex;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

public class HexLineAdapter extends RecyclerView.Adapter<HexLineAdapter.ViewHolder> {
    private static final int OFFSET_WIDTH_DP = 112;
    private static final int HEX_WIDTH_DP = 430;
    private static final int ASCII_WIDTH_DP = 160;
    private static final int CELL_MIN_HEIGHT_DP = 34;

    @NonNull
    private final List<HexViewerUtils.HexLine> mLines = new ArrayList<>();
    private long mHighlightOffset = HexViewerUtils.NO_HIGHLIGHT;

    public void submitLines(@NonNull List<HexViewerUtils.HexLine> lines, long highlightOffset) {
        mLines.clear();
        mLines.addAll(lines);
        mHighlightOffset = highlightOffset;
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
            holder.bind("Offset", "Hex", "ASCII", true, false);
            return;
        }
        HexViewerUtils.HexLine line = mLines.get(position - 1);
        holder.bind(HexViewerUtils.formatOffset(line.offset), line.hex, line.ascii, false,
                line.contains(mHighlightOffset));
    }

    @Override
    public int getItemCount() {
        return mLines.size() + 1;
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

        void bind(@NonNull String offset, @NonNull String hex, @NonNull String ascii, boolean header,
                  boolean highlighted) {
            mRow.removeAllViews();
            mRow.addView(createCell(offset, OFFSET_WIDTH_DP, header, highlighted));
            mRow.addView(createCell(hex, HEX_WIDTH_DP, header, highlighted));
            mRow.addView(createCell(ascii, ASCII_WIDTH_DP, header, highlighted));
        }

        @NonNull
        private MaterialTextView createCell(@NonNull String value, int widthDp, boolean header,
                                            boolean highlighted) {
            Context context = mRow.getContext();
            MaterialTextView textView = new MaterialTextView(context);
            textView.setLayoutParams(new LinearLayoutCompat.LayoutParams(dp(context, widthDp),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            int paddingHorizontal = dp(context, 8);
            int paddingVertical = dp(context, 5);
            textView.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
            textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setMaxLines(1);
            textView.setTextIsSelectable(true);
            textView.setText(value);
            if (header) {
                textView.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                textView.setTypeface(Typeface.MONOSPACE, highlighted ? Typeface.BOLD : Typeface.NORMAL);
            }
            return textView;
        }
    }
}
