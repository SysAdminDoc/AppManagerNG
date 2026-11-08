// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.server.common.IRootServiceManager;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

@RunWith(RobolectricTestRunner.class)
public class RootServiceManagerTest {
    @Test
    public void onlyRootModeRunsTheServiceFromTheAppsOwnCache() throws java.io.IOException {
        // A rooted phone in ADB mode still launches through the shell, which SELinux keeps out of
        // the app's data. hasRoot() only says su works, and asking can prompt.
        String source = new String(java.nio.file.Files.readAllBytes(findRepoRoot().resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/ipc/RootServiceManager.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        int branch = source.indexOf("classPath = prepareMainJar(context).getAbsolutePath();");
        String condition = source.substring(source.lastIndexOf("if (", branch), branch);
        assertTrue(condition, condition.contains("Ops.isDirectRoot()"));
        assertFalse(condition, condition.contains("hasRoot()"));
    }

    private static java.nio.file.Path findRepoRoot() {
        java.nio.file.Path cursor = java.nio.file.Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (java.nio.file.Files.isDirectory(cursor.resolve("app/src/main/java"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }

    // Issue 19: switching to ADB mode from an automation broadcast hung, then failed after 45
    // seconds. The launched process broadcasts its binder, and Android 14 queued that behind the
    // broadcast the app was still holding. The process quit after ten seconds, and the launch that
    // never answered then kept every later bind in that app process from launching another.

    @Test
    public void aBindThatWaitsForALaunchedProcessLetsHeldBroadcastsGo() {
        AtomicInteger released = new AtomicInteger();
        Runnable holder = released::incrementAndGet;
        HeldBroadcasts.hold(holder);
        try {
            assertNotNull(new RootServiceManager().createBindTask(serviceIntent(), Runnable::run,
                    new RecordingConnection()));
            assertEquals(1, released.get());
        } finally {
            HeldBroadcasts.done(holder);
        }
    }

    @Test
    public void aBindToARunningProcessHoldsNothingUp() throws RemoteException {
        RootServiceManager manager = new RootServiceManager();
        RecordingConnection first = new RecordingConnection();
        manager.createBindTask(serviceIntent(), Runnable::run, first);
        manager.onRemoteAnswered(new FakeRemote(), false);
        AtomicInteger released = new AtomicInteger();
        Runnable holder = released::incrementAndGet;
        HeldBroadcasts.hold(holder);
        try {
            assertNull(manager.createBindTask(serviceIntent(), Runnable::run, new RecordingConnection()));
            assertEquals(0, released.get());
        } finally {
            HeldBroadcasts.done(holder);
        }
    }

    @Test
    public void aLaunchThatNeverAnswersIsLaunchedAgain() {
        RootServiceManager manager = new RootServiceManager();
        RecordingConnection conn = new RecordingConnection();

        assertNotNull(manager.createBindTask(serviceIntent(), Runnable::run, conn));
        // Still has time to answer
        assertNull(manager.createBindTask(serviceIntent(), Runnable::run, conn));

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(RootServiceManager.LAUNCH_ANSWER_TIMEOUT_MILLIS));
        assertNotNull("A lost launch has to be tried again",
                manager.createBindTask(serviceIntent(), Runnable::run, conn));
    }

    @Test
    public void aBindRetriedAfterALostLaunchConnectsOnce() throws RemoteException {
        RootServiceManager manager = new RootServiceManager();
        RecordingConnection conn = new RecordingConnection();
        manager.createBindTask(serviceIntent(), Runnable::run, conn);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(RootServiceManager.LAUNCH_ANSWER_TIMEOUT_MILLIS));
        manager.createBindTask(serviceIntent(), Runnable::run, conn);
        FakeRemote remote = new FakeRemote();

        manager.onRemoteAnswered(remote, false);

        assertEquals(1, conn.connected);
        assertEquals(1, remote.binds);
    }

    @Test
    public void theBinderHandoffReachesAnAppInTheBackground() {
        Context saved = ContextUtils.rootContext;
        ContextUtils.rootContext = ApplicationProvider.getApplicationContext();
        try {
            Intent handoff = RootServiceManager.getBroadcastIntent(new Binder(), false);
            assertTrue(Integer.toHexString(handoff.getFlags()),
                    (handoff.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND) != 0);
        } finally {
            ContextUtils.rootContext = saved;
        }
    }

    @NonNull
    private static Intent serviceIntent() {
        return new Intent().setComponent(new ComponentName(ContextUtils.getContext().getPackageName(),
                AMService.class.getName()));
    }

    private static final class RecordingConnection implements ServiceConnection {
        int connected;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ++connected;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }

    private static final class FakeRemote extends IRootServiceManager.Stub {
        int binds;

        @Override
        public void broadcast(int uid) {
        }

        @Override
        public void stop(ComponentName name, int uid) {
        }

        @Override
        public void connect(IBinder binder) {
        }

        @Override
        public IBinder bind(Intent intent) {
            ++binds;
            return new Binder();
        }

        @Override
        public void unbind(ComponentName name) {
        }
    }

    @Test
    public void mainJarStagingPathUsesInternalDeviceProtectedCache() {
        Context context = ApplicationProvider.getApplicationContext();
        File mainJar = RootServiceManager.getMainJarFile(context);

        assertEquals("main.jar", mainJar.getName());
        assertEquals(ContextUtils.getDeContext(context).getCacheDir(), mainJar.getParentFile());
        String mainJarPath = mainJar.getAbsolutePath();
        File[] externalCacheDirs = context.getExternalCacheDirs();
        assertNotNull(externalCacheDirs);
        for (File externalCacheDir : externalCacheDirs) {
            if (externalCacheDir == null) {
                continue;
            }
            assertFalse(mainJarPath.startsWith(externalCacheDir.getAbsolutePath()));
        }
    }
}
