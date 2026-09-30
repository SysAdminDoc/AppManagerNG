// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class ServiceConnectionWrapperTest {
    private static final ComponentName COMPONENT = new ComponentName("pkg", "Service");

    @Test
    public void callbacksWithoutActiveWatcherDoNotThrow() throws Exception {
        ServiceConnectionWrapper wrapper = new ServiceConnectionWrapper(COMPONENT);
        ServiceConnection connection = getServiceConnection(wrapper);

        connection.onServiceDisconnected(COMPONENT);
        connection.onBindingDied(COMPONENT);
        connection.onNullBinding(COMPONENT);

        assertNull(getField(wrapper, "mIBinder"));
    }

    @Test
    public void connectedCallbackCountsActiveWatcherAndStoresBinder() throws Exception {
        ServiceConnectionWrapper wrapper = new ServiceConnectionWrapper(COMPONENT);
        ServiceConnection connection = getServiceConnection(wrapper);
        CountDownLatch watcher = new CountDownLatch(1);
        IBinder binder = new Binder();
        setField(wrapper, "mServiceBoundWatcher", watcher);

        connection.onServiceConnected(COMPONENT, binder);

        assertEquals(0, watcher.getCount());
        assertSame(binder, getField(wrapper, "mIBinder"));
    }

    @Test
    public void bindWaitingForTheServiceHoldsNoLock() throws Exception {
        // unbindService() takes this lock on the main thread. Holding it through the 45 second wait
        // froze the UI whenever a mode switch overlapped a bind.
        ServiceConnectionWrapper wrapper = new ServiceConnectionWrapper(COMPONENT);
        Object lock = getServiceConnection(wrapper);
        Thread binder = new Thread(() -> {
            try {
                wrapper.bindService();
            } catch (RemoteException ignore) {
            }
        });
        binder.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (getField(wrapper, "mServiceBoundWatcher") == null && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertNotNull(getField(wrapper, "mServiceBoundWatcher"));

        CountDownLatch locked = new CountDownLatch(1);
        Thread other = new Thread(() -> {
            synchronized (lock) {
                locked.countDown();
            }
        });
        other.start();
        assertTrue(locked.await(2, TimeUnit.SECONDS));

        wrapper.stopDaemon();
        binder.join(5_000);
        assertFalse(binder.isAlive());
    }

    @Test
    public void stopReleasesABindStillWaiting() throws Exception {
        ServiceConnectionWrapper wrapper = new ServiceConnectionWrapper(COMPONENT);
        CountDownLatch watcher = new CountDownLatch(1);
        setField(wrapper, "mServiceBoundWatcher", watcher);

        wrapper.stopDaemon();

        assertEquals(0, watcher.getCount());
        assertNull(getField(wrapper, "mServiceBoundWatcher"));
    }

    @Test
    public void connectionArrivingAfterStopIsNotKept() throws Exception {
        ServiceConnectionWrapper wrapper = new ServiceConnectionWrapper(COMPONENT);
        ServiceConnection connection = getServiceConnection(wrapper);

        wrapper.stopDaemon();
        connection.onServiceConnected(COMPONENT, new Binder());

        assertNull(getField(wrapper, "mIBinder"));
        assertFalse(wrapper.isBinderActive());
    }

    private static ServiceConnection getServiceConnection(ServiceConnectionWrapper wrapper) throws Exception {
        return (ServiceConnection) getField(wrapper, "mServiceConnection");
    }

    private static Object getField(ServiceConnectionWrapper wrapper, String name) throws Exception {
        Field field = ServiceConnectionWrapper.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(wrapper);
    }

    private static void setField(ServiceConnectionWrapper wrapper, String name, Object value) throws Exception {
        Field field = ServiceConnectionWrapper.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(wrapper, value);
    }
}
