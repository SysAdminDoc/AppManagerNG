// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sysadmindoc.appmanagerng.benchmark;

import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.FrameTimingMetric;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.StartupTimingMetric;
import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import kotlin.Unit;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class StartupMacrobenchmark {
    @Rule
    public final MacrobenchmarkRule benchmarkRule = new MacrobenchmarkRule();

    @Test
    public void coldStartup() {
        benchmarkRule.measureRepeated(BenchmarkConfig.TARGET_PACKAGE,
                Collections.singletonList(new StartupTimingMetric()),
                CompilationMode.DEFAULT,
                StartupMode.COLD,
                5,
                scope -> {
                    scope.pressHome();
                    return Unit.INSTANCE;
                },
                scope -> {
                    scope.startActivityAndWait();
                    return Unit.INSTANCE;
                });
    }

    @Test
    public void mainListScroll() {
        benchmarkRule.measureRepeated(BenchmarkConfig.TARGET_PACKAGE,
                Collections.singletonList(new FrameTimingMetric()),
                CompilationMode.DEFAULT,
                StartupMode.WARM,
                5,
                scope -> {
                    BenchmarkJourneys.launchMainList(scope);
                    return Unit.INSTANCE;
                },
                scope -> {
                    BenchmarkJourneys.scrollMainList(scope.getDevice());
                    return Unit.INSTANCE;
                });
    }

    @Test
    public void backupSettingsTimeToInteractive() {
        benchmarkRule.measureRepeated(BenchmarkConfig.TARGET_PACKAGE,
                Arrays.asList(new StartupTimingMetric(), new FrameTimingMetric()),
                CompilationMode.DEFAULT,
                StartupMode.COLD,
                5,
                scope -> {
                    scope.pressHome();
                    return Unit.INSTANCE;
                },
                scope -> {
                    scope.startActivityAndWait(BenchmarkJourneys.backupSettingsIntent());
                    BenchmarkJourneys.waitForBackupSettings(scope.getDevice());
                    return Unit.INSTANCE;
                });
    }
}
