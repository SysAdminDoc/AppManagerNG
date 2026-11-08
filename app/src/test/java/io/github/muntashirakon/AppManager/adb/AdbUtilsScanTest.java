// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import androidx.core.util.Pair;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.adb.android.AdbMdns;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Upstream 8794070cf: a scanner left running holds a multicast lock and makes later scans fail.
 */
@RunWith(RobolectricTestRunner.class)
public class AdbUtilsScanTest {
    private static final String HOST = "10.0.2.16";

    private final List<String> mCalls = new CopyOnWriteArrayList<>();

    @Test
    public void bothScannersStopAfterAResult() throws Exception {
        CountDownLatch found = new CountDownLatch(1);
        found.countDown();

        AdbUtils.scan(Arrays.asList(scanner("tcp", false, false), scanner("tls", false, false)), found::await, 1, TimeUnit.SECONDS);

        assertEquals(Arrays.asList("start tcp", "start tls", "stop tcp", "stop tls"), mCalls);
    }

    @Test
    public void theFirstScannerStopsWhenTheSecondCannotStart() {
        IOException e = assertThrows(IOException.class, () -> AdbUtils.scan(
                Arrays.asList(scanner("tcp", false, false), scanner("tls", true, false)),
                new CountDownLatch(1)::await, 1, TimeUnit.SECONDS));

        assertTrue(e.getCause() instanceof IllegalStateException);
        // It used to leak: the stop calls only ran once both had started
        assertEquals(Arrays.asList("start tcp", "start tls", "stop tcp", "stop tls"), mCalls);
    }

    @Test
    public void aScannerThatFailsToStopDoesNotKeepTheOtherRunning() {
        assertThrows(IOException.class, () -> AdbUtils.scan(
                Arrays.asList(scanner("tcp", false, true), scanner("tls", false, false)),
                new CountDownLatch(1)::await, 10, TimeUnit.MILLISECONDS));

        assertEquals(Arrays.asList("start tcp", "start tls", "stop tcp", "stop tls"), mCalls);
    }

    @Test
    public void aScanThatFindsNothingIsNotACancel() {
        // The port lookup takes an InterruptedException for a cancel and stops. A scan that only
        // timed out has to let it fall back to the saved port.
        IOException e = assertThrows(IOException.class, () -> AdbUtils.scan(
                Arrays.asList(scanner("tcp", false, false), scanner("tls", false, false)),
                new CountDownLatch(1)::await, 10, TimeUnit.MILLISECONDS));

        assertFalse(e instanceof InterruptedIOException);
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    public void aScanThatIsInterruptedSaysSo() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> AdbUtils.scan(
                    Arrays.asList(scanner("tcp", false, false), scanner("tls", false, false)),
                    new CountDownLatch(1)::await, 1, TimeUnit.SECONDS));
            assertEquals(Arrays.asList("start tcp", "start tls", "stop tcp", "stop tls"), mCalls);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void wirelessDebuggingLooksOnlyForItsOwnService() {
        // A plain port opened by adb tcpip is advertised too and could answer first
        assertEquals(Collections.singletonList(AdbMdns.SERVICE_TYPE_TLS_CONNECT),
                AdbUtils.serviceTypes(Build.VERSION_CODES.R, true));
        assertEquals(Collections.singletonList(AdbMdns.SERVICE_TYPE_TLS_CONNECT),
                AdbUtils.serviceTypes(36, true));
    }

    @Test
    public void otherModesLookForBothServices() {
        assertEquals(Arrays.asList(AdbMdns.SERVICE_TYPE_ADB, AdbMdns.SERVICE_TYPE_TLS_CONNECT),
                AdbUtils.serviceTypes(Build.VERSION_CODES.R, false));
        assertEquals(Collections.singletonList(AdbMdns.SERVICE_TYPE_ADB),
                AdbUtils.serviceTypes(Build.VERSION_CODES.O_MR1, false));
    }

    @Test
    public void thePlainPortWinsEvenWhenWirelessDebuggingAnswersFirst() throws Exception {
        // ADB over TCP with Wireless debugging also on connected to the TLS port when it answered first
        AdbUtils.DaemonChooser chooser = new AdbUtils.DaemonChooser(true, 1500);
        chooser.onFound(false, HOST, 34661);
        Thread plain = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {
                return;
            }
            chooser.onFound(true, HOST, 5555);
        });
        plain.start();

        assertTrue(chooser.await(5, TimeUnit.SECONDS));
        // Read right away: the real scan stops listening once the wait ends, so nothing that
        // arrives later can change the answer
        Pair<String, Integer> daemon = chooser.result();
        plain.join();

        assertEquals(new Pair<>(HOST, 5555), daemon);
    }

    @Test
    public void wirelessDebuggingIsTheFallbackWhenNoPlainPortAnswers() throws Exception {
        AdbUtils.DaemonChooser chooser = new AdbUtils.DaemonChooser(true, 200);
        chooser.onFound(false, HOST, 34661);
        long start = System.nanoTime();

        assertTrue(chooser.await(5, TimeUnit.SECONDS));

        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2));
        assertEquals(new Pair<>(HOST, 34661), chooser.result());
    }

    @Test
    public void aWirelessDebuggingScanDoesNotWaitForAPlainPort() throws Exception {
        AdbUtils.DaemonChooser chooser = new AdbUtils.DaemonChooser(false, 5000);
        chooser.onFound(false, HOST, 34661);
        long start = System.nanoTime();

        assertTrue(chooser.await(10, TimeUnit.SECONDS));

        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1));
        assertEquals(new Pair<>(HOST, 34661), chooser.result());
    }

    @Test
    public void aLostServiceIsNotAnAnswer() throws Exception {
        // A service that goes away reports port -1, which used to end the scan with nothing usable
        AdbUtils.DaemonChooser chooser = new AdbUtils.DaemonChooser(true, 200);
        chooser.onFound(true, null, -1);

        assertFalse(chooser.await(100, TimeUnit.MILLISECONDS));
        assertNull(chooser.result());
    }

    @Test
    public void aPortThatGoesAwayIsForgotten() throws Exception {
        AdbUtils.DaemonChooser chooser = new AdbUtils.DaemonChooser(true, 200);
        chooser.onFound(false, HOST, 34661);
        chooser.onFound(false, HOST, -1);

        chooser.await(1, TimeUnit.SECONDS);

        assertNull(chooser.result());
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
