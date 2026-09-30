// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Upstream 8794070cf: a scanner left running holds a multicast lock and makes later scans fail.
 */
@RunWith(RobolectricTestRunner.class)
public class AdbUtilsScanTest {
    private final List<String> mCalls = new CopyOnWriteArrayList<>();

    @Test
    public void bothScannersStopAfterAResult() throws Exception {
        CountDownLatch found = new CountDownLatch(1);
        found.countDown();

        AdbUtils.scan(Arrays.asList(scanner("tcp", false, false), scanner("tls", false, false)), found, 1, TimeUnit.SECONDS);

        assertEquals(Arrays.asList("start tcp", "start tls", "stop tcp", "stop tls"), mCalls);
    }

    @Test
    public void theFirstScannerStopsWhenTheSecondCannotStart() {
        IOException e = assertThrows(IOException.class, () -> AdbUtils.scan(
                Arrays.asList(scanner("tcp", false, false), scanner("tls", true, false)),
                new CountDownLatch(1), 1, TimeUnit.SECONDS));

        assertTrue(e.getCause() instanceof IllegalStateException);
        // It used to leak: the stop calls only ran once both had started
        assertEquals(Arrays.asList("start tcp", "start tls", "stop tcp", "stop tls"), mCalls);
    }

    @Test
    public void aScannerThatFailsToStopDoesNotKeepTheOtherRunning() {
        assertThrows(InterruptedException.class, () -> AdbUtils.scan(
                Arrays.asList(scanner("tcp", false, true), scanner("tls", false, false)),
                new CountDownLatch(1), 10, TimeUnit.MILLISECONDS));

        assertEquals(Arrays.asList("start tcp", "start tls", "stop tcp", "stop tls"), mCalls);
    }

    private AdbUtils.Scanner scanner(String name, boolean failStart, boolean failStop) {
        return new AdbUtils.Scanner() {
            @Override
            public void start() {
                mCalls.add("start " + name);
                if (failStart) throw new IllegalStateException("start " + name);
            }

            @Override
            public void stop() {
                mCalls.add("stop " + name);
                if (failStop) throw new IllegalArgumentException("listener not registered");
            }
        };
    }
}
