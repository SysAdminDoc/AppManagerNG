// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.List;

import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.util.AdapterUtils;

final class AppDetailsAdapterUtils {
    private static final int NO_POSITION = -1;

    private AppDetailsAdapterUtils() {
    }

    static boolean notifyItemChangedIfPresent(@NonNull androidx.recyclerview.widget.RecyclerView.Adapter<?> adapter,
                                              @NonNull List<AppDetailsItem<?>> adapterList,
                                              @NonNull AppDetailsItem<?> expectedItem) {
        int currentPosition;
        synchronized (adapterList) {
            currentPosition = findIdentityPosition(adapterList, expectedItem);
        }
        if (currentPosition == NO_POSITION) {
            return false;
        }
        adapter.notifyItemChanged(currentPosition, AdapterUtils.STUB);
        return true;
    }

    /**
     * Sends a long press on a row's selectable text to the row. Permission and app op rows let every line be
     * selected, so a long press there picked a word instead of opening the row's menu. A double tap still selects.
     *
     * @param rowHasAction Whether the row does anything on a long press. Without that, the text selects as usual.
     */
    static void passLongPressToRow(@NonNull ViewGroup row, boolean rowHasAction) {
        passLongPressToRow(row, row, rowHasAction);
    }

    private static void passLongPressToRow(@NonNull View row, @NonNull ViewGroup parent, boolean rowHasAction) {
        for (int i = 0; i < parent.getChildCount(); ++i) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup) {
                passLongPressToRow(row, (ViewGroup) child, rowHasAction);
            } else if (child instanceof TextView && ((TextView) child).isTextSelectable()) {
                child.setOnLongClickListener(rowHasAction ? v -> row.performLongClick() : null);
            }
        }
    }

    @VisibleForTesting
    static int findIdentityPosition(@NonNull List<AppDetailsItem<?>> adapterList,
                                    @NonNull AppDetailsItem<?> expectedItem) {
        for (int i = 0; i < adapterList.size(); ++i) {
            if (adapterList.get(i) == expectedItem) {
                return i;
            }
        }
        return NO_POSITION;
    }
}
