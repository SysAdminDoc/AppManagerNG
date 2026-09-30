// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowSystemProperties;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import io.github.muntashirakon.AppManager.servermanager.AdbFailure;

/**
 * Port of upstream 2f2b31e89: mode changes, connects and the boot-time reconnect each set the mode
 * flags and restart the server, so they take turns.
 */
@RunWith(RobolectricTestRunner.class)
public class OpsTransitionLockTest {
    @Test
    public void aModeChangeWaitsForOneInProgress() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ReentrantLock lock = transitionLock();
        Thread caller = new Thread(() -> Ops.init(context, false, Ops.MODE_NO_ROOT));
        lock.lock();
        try {
            caller.start();
            long deadline = System.currentTimeMillis() + 5_000;
            while (!lock.hasQueuedThread(caller) && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(lock.hasQueuedThread(caller));
        } finally {
            lock.unlock();
        }
        caller.join(10_000);
        assertFalse(caller.isAlive());
    }

    @Test(timeout = 30_000)
    public void aShizukuConnectWaitsForAModeChangeInProgress() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ReentrantLock lock = transitionLock();
        Thread caller = new Thread(() -> Ops.connectShizuku(context));
        lock.lock();
        try {
            caller.start();
            awaitQueued(lock, caller);
        } finally {
            lock.unlock();
        }
        caller.join(10_000);
        assertFalse(caller.isAlive());
    }

    @Test(timeout = 30_000)
    public void stoppingTheBackgroundReconnectEndsItsWaitForTheLock() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ReentrantLock lock = transitionLock();
        AtomicInteger status = new AtomicInteger(Integer.MIN_VALUE);
        AtomicBoolean stillInterrupted = new AtomicBoolean();
        Thread reconnect = new Thread(() -> {
            status.set(Ops.autoConnectWirelessDebuggingInBackground(context));
            stillInterrupted.set(Thread.currentThread().isInterrupted());
        });
        lock.lock();
        try {
            reconnect.start();
            awaitQueued(lock, reconnect);
            // What stopping the Wi-Fi wait service does to its attempt
            reconnect.interrupt();
            reconnect.join(5_000);
            // It gave up while the lock was still held, instead of connecting once it was free
            assertFalse(reconnect.isAlive());
        } finally {
            lock.unlock();
        }
        assertEquals(Ops.STATUS_FAILURE, status.get());
        assertTrue(stillInterrupted.get());
    }

    @Test(timeout = 30_000)
    public void theBackgroundReconnectChecksTheModeAgainOnceItHasTheLock() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ReentrantLock lock = transitionLock();
        String before = Ops.getMode();
        // Without it an ADB mode reads as auto
        shadowOf((Application) context).grantPermissions(Manifest.permission.INTERNET);
        // The connect then fails at once, with nothing on the network
        ShadowSystemProperties.override("init.svc.adbd", "stopped");
        try {
            // Positive control: still wireless ADB, so it goes on to connect and leaves a failure
            assertNotNull(reconnectWhileTheModeBecomes(context, lock, Ops.MODE_ADB_WIFI));
            // The mode changed while it waited, so it doesn't try at all
            assertNull(reconnectWhileTheModeBecomes(context, lock, Ops.MODE_ROOT));
        } finally {
            Ops.setMode(before);
            AdbFailure.clear();
        }
    }

    @Test
    public void aCancelledAttemptKeepsTheLastRealFailure() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Method record = Ops.class.getDeclaredMethod("recordAdbFailure", Context.class, Throwable.class, boolean.class);
        record.setAccessible(true);
        try {
            AdbFailure.clear();
            record.invoke(null, context, new ConnectException("refused"), false);
            AdbFailure refused = AdbFailure.getLast();
            assertNotNull(refused);
            assertEquals("CONNECTION_REFUSED", refused.describe());

            Thread.currentThread().interrupt();
            try {
                record.invoke(null, context, new InterruptedIOException("Cancelled"), false);
            } finally {
                Thread.interrupted();
            }
            assertEquals("CONNECTION_REFUSED", AdbFailure.getLast().describe());
        } finally {
            AdbFailure.clear();
        }
    }

    @Test
    public void connectsTakeTheSameLockAndPutTheFlagsBackOnAnyFailure() throws IOException {
        String ops = read("app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java");
        for (String entry : new String[]{
                "public static int autoConnectWirelessDebugging(@NonNull Context context)",
                "public static int connectAdb(@NonNull Context context, int port, @Status int returnCodeOnFailure)"}) {
            String body = body(ops, entry);
            int lock = body.indexOf("sTransitionLock.lock();");
            assertTrue(entry, lock != -1 && body.indexOf("sTransitionLock.unlock();", lock) != -1);
        }
        // A RuntimeException, such as a port mDNS reported as 0, left the process marked as ADB
        for (String entry : new String[]{
                "private static int autoConnectWirelessDebuggingLocked(",
                "private static int connectAdbLocked("}) {
            assertTrue(entry, body(ops, entry).contains("| RuntimeException e)"));
        }
        // Waiting for the user to type a pairing code must not hold up everything else
        assertFalse(body(ops, "public static int pairAdb(@NonNull Context context)").contains("sTransitionLock"));
    }

    private static AdbFailure reconnectWhileTheModeBecomes(Context context, ReentrantLock lock, String mode)
            throws InterruptedException {
        Ops.setMode(Ops.MODE_ADB_WIFI);
        assertEquals(Ops.MODE_ADB_WIFI, Ops.getMode());
        AdbFailure.clear();
        AtomicInteger status = new AtomicInteger(Integer.MIN_VALUE);
        Thread reconnect = new Thread(() -> status.set(Ops.autoConnectWirelessDebuggingInBackground(context)));
        lock.lock();
        try {
            reconnect.start();
            awaitQueued(lock, reconnect);
            Ops.setMode(mode);
        } finally {
            lock.unlock();
        }
        reconnect.join(20_000);
        assertFalse(reconnect.isAlive());
        assertNotEquals(Ops.STATUS_SUCCESS, status.get());
        return AdbFailure.getLast();
    }

    private static void awaitQueued(ReentrantLock lock, Thread thread) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!lock.hasQueuedThread(thread) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(lock.hasQueuedThread(thread));
    }

    private static ReentrantLock transitionLock() throws ReflectiveOperationException {
        Field field = Ops.class.getDeclaredField("sTransitionLock");
        field.setAccessible(true);
        return (ReentrantLock) field.get(null);
    }

    private static String body(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(signature, start != -1);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); ++i) {
            char c = source.charAt(i);
            if (c == '{') {
                ++depth;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open, i + 1);
            }
        }
        throw new AssertionError("Unbalanced braces after " + signature);
    }

    private static String read(String path) throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("app/src/main/java"))) {
            cursor = cursor.getParent();
        }
        return new String(Files.readAllBytes(cursor.resolve(path)), StandardCharsets.UTF_8);
    }
}
