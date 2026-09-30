// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantLock;

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
