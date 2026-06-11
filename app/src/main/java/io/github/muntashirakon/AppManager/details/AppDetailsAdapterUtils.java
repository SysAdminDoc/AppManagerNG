// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

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
