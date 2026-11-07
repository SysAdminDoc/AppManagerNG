// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.accessibility.topapp.TopAppTracker;

@RunWith(RobolectricTestRunner.class)
public class NoRootAccessibilityTopAppTest {
    private static final class RecordingListener implements AccessibilityMultiplexer.TopAppListener {
        final List<String> focused = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger walks = new AtomicInteger();
        volatile CountDownLatch walked = new CountDownLatch(1);

        @Override
        public void onWindowStateChanged(@NonNull String packageName) {
            focused.add(packageName);
        }

        @Override
        public void onWindowsChanged(@NonNull List<TopAppTracker.Window> windowsTopFirst) {
            walks.incrementAndGet();
            walked.countDown();
        }
    }

    private final AccessibilityMultiplexer mMultiplexer = AccessibilityMultiplexer.getInstance();
    private final RecordingListener mListener = new RecordingListener();
    private NoRootAccessibilityService mService;

    @Before
    public void setUp() {
        mService = Robolectric.buildService(NoRootAccessibilityService.class).create().get();
        mMultiplexer.setTopAppListener(mListener);
    }

    @After
    public void tearDown() {
        mMultiplexer.setTopAppListener(null);
        mMultiplexer.setServiceConnected(false);
        mMultiplexer.clearFlags();
    }

    @NonNull
    private static AccessibilityEvent event(int type, @NonNull String packageName) {
        AccessibilityEvent event = AccessibilityEvent.obtain(type);
        event.setPackageName(packageName);
        return event;
    }

    @Test
    public void aFocusedWindowIsPassedOnWithItsPackage() {
        mService.onAccessibilityEvent(event(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.example.ad"));

        assertEquals(Collections.singletonList("com.example.ad"), mListener.focused);
    }

    @Test
    public void otherEventsAreNotPassedOn() {
        mService.onAccessibilityEvent(event(AccessibilityEvent.TYPE_VIEW_CLICKED, "com.example.app"));
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);

        assertTrue(mListener.focused.isEmpty());
        assertEquals(0, mListener.walks.get());
    }

    @Test
    public void aBurstOfWindowChangesIsOneWalk() throws InterruptedException {
        for (int i = 0; i < 20; ++i) {
            mService.onAccessibilityEvent(event(AccessibilityEvent.TYPE_WINDOWS_CHANGED, "com.example.app"));
        }
        assertEquals("nothing should read windows on the event itself", 0, mListener.walks.get());

        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);

        assertTrue(mListener.walked.await(5, TimeUnit.SECONDS));
        assertEquals(1, mListener.walks.get());
    }

    @Test
    public void aChangeAfterAWalkAsksForAnother() throws InterruptedException {
        mService.onAccessibilityEvent(event(AccessibilityEvent.TYPE_WINDOWS_CHANGED, "com.example.app"));
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        assertTrue(mListener.walked.await(5, TimeUnit.SECONDS));

        mListener.walked = new CountDownLatch(1);
        mService.onAccessibilityEvent(event(AccessibilityEvent.TYPE_WINDOWS_CHANGED, "com.example.app"));
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);

        assertTrue(mListener.walked.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void withNoListenerNoWalkIsQueued() {
        mMultiplexer.setTopAppListener(null);

        mService.onAccessibilityEvent(event(AccessibilityEvent.TYPE_WINDOWS_CHANGED, "com.example.app"));
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);

        assertEquals(0, mListener.walks.get());
    }

    @Test
    public void connectingAndUnbindingAreReported() {
        mService.onServiceConnected();
        assertTrue(mMultiplexer.isServiceConnected());

        mService.onUnbind(new Intent());
        assertFalse(mMultiplexer.isServiceConnected());
    }

    @Test
    public void clearingTheAutomationFlagsKeepsTheBubbleFed() {
        mMultiplexer.clearFlags();

        mService.onAccessibilityEvent(event(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, "com.example.ad"));

        assertEquals(Collections.singletonList("com.example.ad"), mListener.focused);
    }
}
