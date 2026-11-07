// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Notification;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import io.github.muntashirakon.AppManager.accessibility.toast.ToastEntry;
import io.github.muntashirakon.AppManager.accessibility.toast.ToastHistory;
import io.github.muntashirakon.AppManager.accessibility.toast.ToastHistoryTestAccess;
import io.github.muntashirakon.AppManager.settings.Prefs;

@RunWith(RobolectricTestRunner.class)
public class NoRootAccessibilityToastTest {
    private static final String SENDER = "com.example.sender";

    private Application mApp;
    private ToastHistory mHistory;
    private NoRootAccessibilityService mService;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        mHistory = ToastHistoryTestAccess.useInMemory(mApp);
        mService = Robolectric.buildService(NoRootAccessibilityService.class).create().get();
    }

    @After
    public void tearDown() {
        Prefs.Privacy.setToastHistoryEnabled(false);
        ToastHistoryTestAccess.reset();
    }

    @NonNull
    private static AccessibilityEvent toast() {
        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        event.setPackageName(SENDER);
        event.setClassName("android.widget.Toast");
        event.getText().add("Saved");
        return event;
    }

    @NonNull
    private List<ToastEntry> saved() {
        return mHistory.readAll();
    }

    @Test
    public void recordingIsOffByDefault() {
        assertFalse(Prefs.Privacy.isToastHistoryEnabled());

        mService.onAccessibilityEvent(toast());

        assertTrue(saved().isEmpty());
    }

    @Test
    public void aToastIsRecordedOnceTurnedOn() {
        Prefs.Privacy.setToastHistoryEnabled(true);
        long before = System.currentTimeMillis();

        mService.onAccessibilityEvent(toast());

        long after = System.currentTimeMillis();
        List<ToastEntry> saved = saved();
        assertEquals(1, saved.size());
        assertEquals(SENDER, saved.get(0).packageName);
        assertEquals("Label of " + SENDER, saved.get(0).appLabel);
        assertEquals("Saved", saved.get(0).text);
        assertTrue(saved.get(0).timestampMillis >= before && saved.get(0).timestampMillis <= after);
    }

    @Test
    public void theTimeIsWhenTheEventHappenedNotWhenItArrived() {
        Prefs.Privacy.setToastHistoryEnabled(true);
        AccessibilityEvent event = toast();
        SystemClock.setCurrentTimeMillis(SystemClock.uptimeMillis() + 5_000L);
        event.setEventTime(SystemClock.uptimeMillis() - 100L);
        long before = System.currentTimeMillis();

        mService.onAccessibilityEvent(event);

        long after = System.currentTimeMillis();
        long saved = saved().get(0).timestampMillis;
        assertTrue("expected 100 ms before the delivery but was " + saved,
                saved >= before - 100L && saved <= after - 100L);
    }

    @Test
    public void turningItOffStopsRecording() {
        Prefs.Privacy.setToastHistoryEnabled(true);
        mService.onAccessibilityEvent(toast());
        Prefs.Privacy.setToastHistoryEnabled(false);

        mService.onAccessibilityEvent(toast());

        assertEquals(1, saved().size());
    }

    @Test
    public void notificationsAreNeverRecorded() {
        Prefs.Privacy.setToastHistoryEnabled(true);
        AccessibilityEvent withParcel = toast();
        withParcel.setParcelableData(new Notification());
        AccessibilityEvent withoutParcel = toast();
        withoutParcel.setClassName("android.app.Notification");

        mService.onAccessibilityEvent(withParcel);
        mService.onAccessibilityEvent(withoutParcel);

        assertTrue(saved().isEmpty());
    }

    @Test
    public void otherEventsAreNotRecorded() {
        Prefs.Privacy.setToastHistoryEnabled(true);
        AccessibilityEvent click = toast();
        click.setEventType(AccessibilityEvent.TYPE_VIEW_CLICKED);

        mService.onAccessibilityEvent(click);

        assertTrue(saved().isEmpty());
    }

    @Test
    public void thisAppsOwnToastsAreNotRecorded() {
        Prefs.Privacy.setToastHistoryEnabled(true);
        AccessibilityEvent own = toast();
        own.setPackageName(mApp.getPackageName());

        mService.onAccessibilityEvent(own);

        assertTrue(saved().isEmpty());
    }
}
