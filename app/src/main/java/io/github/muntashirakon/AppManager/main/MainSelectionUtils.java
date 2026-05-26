// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

final class MainSelectionUtils {
    private MainSelectionUtils() {
    }

    static int selectOnly(@NonNull Collection<ApplicationItem> allItems,
                          @NonNull Collection<ApplicationItem> selectedItems,
                          @NonNull Map<String, ApplicationItem> selectionMap) {
        Map<String, ApplicationItem> allItemsByPackage = new LinkedHashMap<>();
        for (ApplicationItem item : allItems) {
            item.isSelected = false;
            if (item.packageName != null) {
                allItemsByPackage.put(item.packageName, item);
            }
        }
        selectionMap.clear();
        for (ApplicationItem item : selectedItems) {
            ApplicationItem selectedItem = allItemsByPackage.get(item.packageName);
            if (selectedItem == null || selectedItem.packageName == null) {
                continue;
            }
            selectionMap.remove(selectedItem.packageName);
            selectionMap.put(selectedItem.packageName, selectedItem);
            selectedItem.isSelected = true;
        }
        return selectionMap.size();
    }
}
