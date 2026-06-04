// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

public class AppInfoActionOrderResolverTest {
    @Test
    public void resolveUsesDefaultPriorityForAvailableActions() {
        List<ActionItem> availableActions = Arrays.asList(
                action(AppInfoActionOrderResolver.ACTION_UNINSTALL),
                action(AppInfoActionOrderResolver.ACTION_CLEAR_DATA),
                action(AppInfoActionOrderResolver.ACTION_FORCE_STOP),
                action(AppInfoActionOrderResolver.ACTION_FREEZE));

        List<ActionItem> orderedActions = AppInfoActionOrderResolver.resolve(availableActions, null);

        assertEquals(Arrays.asList(
                AppInfoActionOrderResolver.ACTION_FREEZE,
                AppInfoActionOrderResolver.ACTION_FORCE_STOP,
                AppInfoActionOrderResolver.ACTION_UNINSTALL,
                AppInfoActionOrderResolver.ACTION_CLEAR_DATA), ids(orderedActions));
    }

    @Test
    public void resolveMovesUserPriorityBeforeDefaultOrderAndPrunesUnknownIds() {
        List<String> priority = AppInfoActionOrderResolver.parsePriority("clear_data,missing,force_stop,clear_data");
        List<ActionItem> availableActions = Arrays.asList(
                action(AppInfoActionOrderResolver.ACTION_UNINSTALL),
                action(AppInfoActionOrderResolver.ACTION_FORCE_STOP),
                action(AppInfoActionOrderResolver.ACTION_CLEAR_DATA));

        List<ActionItem> orderedActions = AppInfoActionOrderResolver.resolve(availableActions, priority);

        assertEquals(Arrays.asList(
                AppInfoActionOrderResolver.ACTION_CLEAR_DATA,
                AppInfoActionOrderResolver.ACTION_FORCE_STOP,
                AppInfoActionOrderResolver.ACTION_UNINSTALL), ids(orderedActions));
        assertEquals("clear_data,force_stop", AppInfoActionOrderResolver.serializePriority(priority));
    }

    private static ActionItem action(String actionId) {
        return new ActionItem(actionId, R.string.done, R.drawable.ic_sort);
    }

    private static List<String> ids(List<ActionItem> actionItems) {
        List<String> ids = new ArrayList<>();
        for (ActionItem actionItem : actionItems) {
            ids.add(actionItem.getActionId());
        }
        return ids;
    }
}
