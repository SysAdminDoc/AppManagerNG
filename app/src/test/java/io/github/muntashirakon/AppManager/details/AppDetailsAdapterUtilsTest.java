// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;

public class AppDetailsAdapterUtilsTest {
    @Test
    public void findIdentityPositionIgnoresNameBasedEquality() {
        AppDetailsItem<String> first = new AppDetailsItem<>("first");
        first.name = "same";
        AppDetailsItem<String> second = new AppDetailsItem<>("second");
        second.name = "same";
        AppDetailsItem<String> missing = new AppDetailsItem<>("missing");
        missing.name = "same";

        assertEquals(0, AppDetailsAdapterUtils.findIdentityPosition(Arrays.asList(first, second), first));
        assertEquals(1, AppDetailsAdapterUtils.findIdentityPosition(Arrays.asList(first, second), second));
        assertEquals(-1, AppDetailsAdapterUtils.findIdentityPosition(Arrays.asList(first, second), missing));
    }
}
