// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sysadmindoc.appmanagerng.benchmark;

import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import kotlin.Unit;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BaselineProfileGenerator {
    @Rule
    public final BaselineProfileRule baselineProfileRule = new BaselineProfileRule();

    @Test
    public void generateCoreJourneyProfile() {
        baselineProfileRule.collect(BenchmarkConfig.TARGET_PACKAGE,
                10,
                3,
                "appmanagerng-core-journeys",
                true,
                false,
                rule -> rule.contains("Lio/github/muntashirakon/AppManager/"),
                scope -> {
                    BenchmarkJourneys.launchMainList(scope);
                    BenchmarkJourneys.scrollMainList(scope.getDevice());
                    scope.pressHome();
                    scope.startActivityAndWait(BenchmarkJourneys.backupSettingsIntent());
                    BenchmarkJourneys.waitForBackupSettings(scope.getDevice());
                    return Unit.INSTANCE;
                });
    }
}
