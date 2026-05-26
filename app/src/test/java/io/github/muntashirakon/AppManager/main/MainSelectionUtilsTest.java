// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class MainSelectionUtilsTest {
    @Test
    public void selectOnlyClearsHiddenSelections() {
        ApplicationItem visibleOne = app("com.example.visible.one");
        ApplicationItem hidden = app("com.example.hidden");
        ApplicationItem visibleTwo = app("com.example.visible.two");
        hidden.isSelected = true;
        Map<String, ApplicationItem> selectionMap = new LinkedHashMap<>();
        selectionMap.put(hidden.packageName, hidden);

        int count = MainSelectionUtils.selectOnly(
                Arrays.asList(visibleOne, hidden, visibleTwo),
                Arrays.asList(visibleOne, visibleTwo),
                selectionMap);

        assertEquals(2, count);
        assertFalse(hidden.isSelected);
        assertTrue(visibleOne.isSelected);
        assertTrue(visibleTwo.isSelected);
        assertEquals(Arrays.asList(visibleOne.packageName, visibleTwo.packageName),
                Arrays.asList(selectionMap.keySet().toArray(new String[0])));
    }

    @Test
    public void selectOnlyUsesCanonicalItemsFromFullList() {
        ApplicationItem canonical = app("com.example.app");
        ApplicationItem visibleCopy = app("com.example.app");
        Map<String, ApplicationItem> selectionMap = new LinkedHashMap<>();

        int count = MainSelectionUtils.selectOnly(
                Arrays.asList(canonical),
                Arrays.asList(visibleCopy),
                selectionMap);

        assertEquals(1, count);
        assertTrue(canonical.isSelected);
        assertFalse(visibleCopy.isSelected);
        assertSame(canonical, selectionMap.get(canonical.packageName));
    }

    @Test
    public void selectOnlySkipsItemsMissingFromFullList() {
        ApplicationItem canonical = app("com.example.app");
        ApplicationItem missing = app("com.example.missing");
        Map<String, ApplicationItem> selectionMap = new LinkedHashMap<>();

        int count = MainSelectionUtils.selectOnly(
                Arrays.asList(canonical),
                Arrays.asList(missing),
                selectionMap);

        assertEquals(0, count);
        assertFalse(canonical.isSelected);
        assertTrue(selectionMap.isEmpty());
    }

    private static ApplicationItem app(String packageName) {
        ApplicationItem item = new ApplicationItem();
        item.packageName = packageName;
        item.label = packageName;
        return item;
    }
}
