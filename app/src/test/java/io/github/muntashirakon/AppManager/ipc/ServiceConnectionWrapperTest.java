// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

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
