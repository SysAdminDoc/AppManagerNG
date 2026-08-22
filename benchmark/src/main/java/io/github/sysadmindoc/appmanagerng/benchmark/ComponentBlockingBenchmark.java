// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sysadmindoc.appmanagerng.benchmark;

import static org.junit.Assert.assertEquals;

import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.compat.BatchCommitCoalescer;

/**
 * Synthetic component-rule benchmark. It exercises the same package coalescer
 * used by batch block/unblock operations without requiring root or a fixture
 * APK on the device.
 */
@RunWith(AndroidJUnit4.class)
public class ComponentBlockingBenchmark {
    @Rule
    public final BenchmarkRule benchmarkRule = new BenchmarkRule();

    @Test
    public void blockComponentsAcrossPackages() {
        List<String> packages = fixturePackages();
        Map<String, Map<String, Integer>> components = fixtureComponents(packages);
        while (benchmarkRule.getState().keepRunning()) {
            AtomicInteger commits = new AtomicInteger();
            BatchCommitCoalescer.Result<String> result = BatchCommitCoalescer.executeValues(
                    packages,
                    components::get,
                    (pair, entries) -> commits.incrementAndGet(),
                    Map::size);
            assertEquals(BenchmarkConfig.COMPONENT_BLOCKING_PACKAGE_COUNT,
                    result.getKeyCount());
            assertEquals(BenchmarkConfig.COMPONENT_BLOCKING_PACKAGE_COUNT
                            * BenchmarkConfig.COMPONENTS_PER_PACKAGE,
                    result.getValueCount());
            assertEquals(BenchmarkConfig.COMPONENT_BLOCKING_PACKAGE_COUNT,
                    result.getCommitCount());
            assertEquals(BenchmarkConfig.COMPONENT_BLOCKING_PACKAGE_COUNT, commits.get());
        }
    }

    private static List<String> fixturePackages() {
        List<String> packages = new ArrayList<>(
                BenchmarkConfig.COMPONENT_BLOCKING_PACKAGE_COUNT);
        for (int i = 0; i < BenchmarkConfig.COMPONENT_BLOCKING_PACKAGE_COUNT; ++i) {
            packages.add("com.example.benchmark." + i);
        }
        return packages;
    }

    private static Map<String, Map<String, Integer>> fixtureComponents(List<String> packages) {
        Map<String, Map<String, Integer>> components = new HashMap<>();
        for (String packageName : packages) {
            Map<String, Integer> entries = new HashMap<>(BenchmarkConfig.COMPONENTS_PER_PACKAGE);
            for (int i = 0; i < BenchmarkConfig.COMPONENTS_PER_PACKAGE; ++i) {
                entries.put(packageName + ".Component" + i, i);
            }
            components.put(packageName, entries);
        }
        return components;
    }
}
