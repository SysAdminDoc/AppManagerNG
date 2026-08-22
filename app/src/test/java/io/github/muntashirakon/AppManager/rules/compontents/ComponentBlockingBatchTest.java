// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

@RunWith(RobolectricTestRunner.class)
public class ComponentBlockingBatchTest {
    @Test
    public void duplicatePackagesShareOneWriterTransaction() {
        UserPackagePair first = new UserPackagePair("com.example.one", 0);
        UserPackagePair second = new UserPackagePair("com.example.two", 0);
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger components = new AtomicInteger();

        ComponentBlockingBatch.Result result = ComponentBlockingBatch.execute(
                Arrays.asList(first, first, second),
                pair -> componentsFor(3),
                (pair, entries) -> {
                    writes.incrementAndGet();
                    components.addAndGet(entries.size());
                });

        assertTrue(result.isSuccessful());
        assertEquals(2, result.getPackageCount());
        assertEquals(6, result.getComponentCount());
        assertEquals(2, result.getCommitCount());
        assertEquals(2, writes.get());
        assertEquals(6, components.get());
    }

    @Test
    public void sourceFailureIsReportedWithoutWritingThatPackage() {
        UserPackagePair pair = new UserPackagePair("com.example.failed", 0);
        AtomicInteger writes = new AtomicInteger();

        ComponentBlockingBatch.Result result = ComponentBlockingBatch.execute(
                Collections.singletonList(pair),
                ignored -> {
                    throw new IllegalStateException("fixture failure");
                },
                (ignored, entries) -> writes.incrementAndGet());

        assertEquals(1, result.getPackageCount());
        assertEquals(0, result.getComponentCount());
        assertEquals(0, result.getCommitCount());
        assertEquals(1, result.getFailures().size());
        assertEquals(0, writes.get());
    }

    private static HashMap<String, RuleType> componentsFor(int count) {
        HashMap<String, RuleType> components = new HashMap<>();
        for (int i = 0; i < count; ++i) {
            components.put("Component" + i, RuleType.ACTIVITY);
        }
        return components;
    }
}
