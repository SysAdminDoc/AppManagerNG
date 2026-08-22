// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public class BatchCommitCoalescerTest {
    @Test
    public void duplicateKeysUseOneWriterInvocation() {
        AtomicInteger writes = new AtomicInteger();
        BatchCommitCoalescer.Result<String> result = BatchCommitCoalescer.executeValues(
                Arrays.asList("first", "first", "second"),
                key -> Collections.singletonList(key),
                (key, value) -> writes.incrementAndGet(),
                value -> value.size());

        assertTrue(result.isSuccessful());
        assertEquals(2, result.getKeyCount());
        assertEquals(2, result.getValueCount());
        assertEquals(2, result.getCommitCount());
        assertEquals(2, writes.get());
    }

    @Test
    public void failuresDoNotStopFollowingKeys() {
        AtomicInteger writes = new AtomicInteger();
        BatchCommitCoalescer.Result<String> result = BatchCommitCoalescer.executeValues(
                Arrays.asList("failed", "written"),
                key -> Collections.singletonList(key),
                (key, value) -> {
                    if ("failed".equals(key)) {
                        throw new IllegalStateException("fixture failure");
                    }
                    writes.incrementAndGet();
                },
                value -> value.size());

        assertEquals(2, result.getKeyCount());
        assertEquals(2, result.getValueCount());
        assertEquals(2, result.getCommitCount());
        assertEquals(1, result.getFailures().size());
        assertEquals("failed", result.getFailures().get(0).getKey());
        assertEquals(1, writes.get());
    }
}
