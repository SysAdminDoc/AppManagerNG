// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Upstream a488f27a2: pairing gives up after a bounded time, and a waiter only takes an outcome
 * from after it started waiting.
 */
@RunWith(RobolectricTestRunner.class)
public class AdbPairingTimeoutTest {
    @Test
    public void aPairingThatHangsIsCancelledAndReportedAsATimeout() {
        FutureTask<Boolean> pairing = new FutureTask<>(() -> true);

        SocketTimeoutException e = assertThrows(SocketTimeoutException.class,
                () -> AdbConnectionManager.awaitPairingAttempt(pairing, 1, TimeUnit.MILLISECONDS));

        assertTrue(pairing.isCancelled());
        assertEquals("ADB pairing timed out", e.getMessage());
        assertEquals(30_000, AdbConnectionManager.PAIRING_ATTEMPT_TIMEOUT_MILLIS);
    }

    @Test
    public void aFailedPairingKeepsItsOwnError() {
        IOException wrongCode = new IOException("Wrong pairing code");
        FutureTask<Boolean> pairing = new FutureTask<>(() -> {
            throw wrongCode;
        });
        pairing.run();

        IOException e = assertThrows(IOException.class,
                () -> AdbConnectionManager.awaitPairingAttempt(pairing, 1, TimeUnit.SECONDS));
        assertSame(wrongCode, e);
    }

    @Test
    public void everyOutcomeIsNumbered() {
        long before = AdbConnectionManager.getLastPairingResultNumber();
        Exception cancelled = new Exception("Pairing cancelled");

        AdbConnectionManager.publishPairingResult(cancelled);
        ShadowLooper.idleMainLooper();

        AdbConnectionManager.PairingResult result = AdbConnectionManager.getPairingObserver().getValue();
        assertEquals(before + 1, AdbConnectionManager.getLastPairingResultNumber());
        assertEquals(before + 1, result.number);
        assertSame(cancelled, result.error);
    }

    @Test
    public void aTimedOutPairingCanBeRetried() {
        AdbPairingState timedOut = AdbPairingState.timedOut(37123);

        assertTrue(timedOut.canSubmitCode("123456"));
        // The wait for a result carries on while the pairing notification offers a retry
        assertTrue(AdbPairingSession.shouldWaitForRetry(timedOut, true));
        assertFalse(AdbPairingSession.shouldWaitForRetry(timedOut, false));
    }

    @Test
    public void theWaitForAPairingIgnoresEarlierOutcomes() throws IOException {
        String ops = read("app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java");
        int since = ops.indexOf("long since = AdbConnectionManager.getLastPairingResultNumber();");
        int observe = ops.indexOf("AdbConnectionManager.getPairingObserver().observeForever(observer)");
        assertTrue(since != -1 && observe > since);
        assertTrue(ops.contains("if (result.number <= since) {"));

        String service = read("app/src/main/java/io/github/muntashirakon/AppManager/adb/AdbPairingService.java");
        assertTrue(service.contains("timedOut = e instanceof SocketTimeoutException;"));
        assertTrue(service.contains("AdbPairingSession.timedOut(port);"));
    }

    private static String read(String path) throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("app/src/main/java"))) {
            cursor = cursor.getParent();
        }
        return new String(Files.readAllBytes(cursor.resolve(path)), StandardCharsets.UTF_8);
    }
}
