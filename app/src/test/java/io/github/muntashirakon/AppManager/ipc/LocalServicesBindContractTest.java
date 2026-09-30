// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalServicesBindContractTest {
    @Test
    public void aliveCheckAndBindShareOneLock() throws IOException {
        // Two threads checking outside the lock both saw the services down, and the second one's
        // bindServices() unbound the binding the first had just made (DeadObjectException in
        // SelfPermissions.init on the first switch to ADB mode).
        String source = read("app/src/main/java/io/github/muntashirakon/AppManager/ipc/LocalServices.java");
        Matcher method = Pattern.compile("public static void bindServicesIfNotAlready\\(\\)[^{]*\\{(.*?)\\n    }",
                Pattern.DOTALL).matcher(source);
        assertTrue(method.find());
        String body = method.group(1);

        int lock = body.indexOf("synchronized (sBindLock)");
        int check = body.indexOf("activeServicesAlive()");
        assertTrue(body, lock != -1 && check > lock);
        assertTrue(body, body.indexOf("bindServices()") > check);
    }

    @Test
    public void bindsHoldNoWrapperMonitorWhileTheyWait() throws IOException {
        // unbindServices() takes these monitors on the main thread, and a bind waits up to 45 s
        String source = read("app/src/main/java/io/github/muntashirakon/AppManager/ipc/LocalServices.java");
        Matcher binds = Pattern.compile("private static void bind\\w+\\(\\)[^{]*\\{(.*?)\\n    }", Pattern.DOTALL)
                .matcher(source);
        int count = 0;
        while (binds.find()) {
            ++count;
            assertFalse(binds.group(1), binds.group(1).contains("synchronized"));
        }
        assertEquals(4, count);
    }

    @Test
    public void bothServicesMustBeUpAndAFailedBindLeavesNeitherRunning() throws IOException {
        String source = read("app/src/main/java/io/github/muntashirakon/AppManager/ipc/LocalServices.java");
        String alive = body(source, "public static boolean alive()");
        String activeAlive = body(source, "private static boolean activeServicesAlive()");
        String bind = body(source, "public static void bindServices()");

        assertTrue(alive, alive.contains("sAMServiceConnectionWrapper.isBinderActive() && sFileSystemServiceConnectionWrapper.isBinderActive()"));
        assertTrue(activeAlive, activeAlive.contains("sShizukuFileSystemServiceConnectionWrapper.isBinderActive()"));
        assertTrue(activeAlive, activeAlive.contains("sFileSystemServiceConnectionWrapper.isBinderActive()"));
        assertTrue(bind, bind.contains("!activeServicesAlive()"));
        assertTrue(bind, bind.indexOf("stopServices();") > bind.indexOf("catch (RemoteException | RuntimeException e)"));
    }

    @Test
    public void serverStatusReceiverNeverBlocksTheMainThread() throws IOException {
        String receiver = read("app/src/main/java/io/github/muntashirakon/AppManager/servermanager/ServerStatusChangeReceiver.java");
        String onReceive = body(receiver, "public void onReceive(Context context, @NonNull Intent intent)");

        // die() waits for a connect in progress
        assertFalse(onReceive, onReceive.contains("LocalServer.die();"));
        assertTrue(onReceive, onReceive.contains("ThreadUtils.postOnBackgroundThread(LocalServer::die)"));
        assertFalse(onReceive, onReceive.contains("bindServices"));
    }

    @Test
    public void leavingAServerModeCancelsAPendingServerStart() throws IOException {
        String ops = read("app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java");

        assertTrue(body(ops, "private static int initNoRoot()").contains("cancelPendingServerStart()"));
        String init = body(ops, "private static int init(@NonNull Context context, boolean force, @NonNull @Mode String mode,");
        int rootBranch = init.indexOf("case MODE_ROOT:");
        int fallback = init.indexOf("catch (Throwable e)");
        assertTrue(init.indexOf("cancelPendingServerStart()", rootBranch) > rootBranch);
        assertTrue(init.indexOf("cancelPendingServerStart()", fallback) > fallback);
    }

    private static String body(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(signature, start != -1);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); ++i) {
            char c = source.charAt(i);
            if (c == '{') ++depth;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new AssertionError("Unbalanced braces after " + signature);
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(findRepoRoot().resolve(path)), StandardCharsets.UTF_8);
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main/java"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
