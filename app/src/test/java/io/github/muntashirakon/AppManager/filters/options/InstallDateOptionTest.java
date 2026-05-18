// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.filters.options.FilterOption.TestResult;
import io.github.muntashirakon.AppManager.main.ApplicationItem;

@RunWith(RobolectricTestRunner.class)
public class InstallDateOptionTest {
    @Test
    public void afterMatchesAppsInstalledOnOrAfterThreshold() {
        InstallDateOption option = new InstallDateOption();
        option.setKeyValue("after", "1000");

        assertFalse(option.test(app(999, true), new TestResult()).isMatched());
        assertTrue(option.test(app(1000, true), new TestResult()).isMatched());
        assertTrue(option.test(app(1500, true), new TestResult()).isMatched());
    }

    @Test
    public void beforeMatchesAppsInstalledOnOrBeforeThreshold() {
        InstallDateOption option = new InstallDateOption();
        option.setKeyValue("before", "1000");

        assertTrue(option.test(app(500, true), new TestResult()).isMatched());
        assertTrue(option.test(app(1000, true), new TestResult()).isMatched());
        assertFalse(option.test(app(1001, true), new TestResult()).isMatched());
    }

    @Test
    public void dateRangePredicatesIgnoreUninstalledRows() {
        InstallDateOption option = new InstallDateOption();
        option.setKeyValue("after", "1000");

        assertFalse(option.test(app(1500, false), new TestResult()).isMatched());
    }

    private static ApplicationItem app(long firstInstallTime, boolean installed) {
        ApplicationItem item = new ApplicationItem();
        item.packageName = "com.example";
        item.label = "Example";
        item.firstInstallTime = firstInstallTime;
        item.isInstalled = installed;
        return item;
    }
}
