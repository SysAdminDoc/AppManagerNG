// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.lifecycle.ViewModelProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;

@RunWith(AndroidJUnit4.class)
public class LogViewerLifecycleInstrumentationTest {
    @Before
    public void authenticateTestSession() {
        Context context = ApplicationProvider.getApplicationContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                Ops.setAuthenticated(context, true));
    }

    @Test
    public void destroyViewAfterRestartRetiresOwnedSession() {
        try (ActivityScenario<LogViewerActivity> scenario = ActivityScenario.launch(LogViewerActivity.class)) {
            AtomicReference<LogViewerViewModel.LogcatSession> sessionRef = new AtomicReference<>();
            scenario.onActivity(activity -> {
                LiveLogViewerFragment fragment = requireLiveFragment(activity);
                sessionRef.set(fragment.getLogcatSession());
                new ViewModelProvider(activity).get(LogViewerViewModel.class).restartLogcat();
                activity.getSupportFragmentManager().beginTransaction()
                        .replace(R.id.main_layout, new Fragment())
                        .commitNow();
            });

            assertNotNull(sessionRef.get());
            assertFalse(LogViewerViewModel.isSessionActive(sessionRef.get()));
        }
    }

    @Test
    public void rotationAndFinishRetireEachViewSession() {
        AtomicReference<LogViewerViewModel.LogcatSession> firstSession = new AtomicReference<>();
        AtomicReference<LogViewerViewModel.LogcatSession> rotatedSession = new AtomicReference<>();
        try (ActivityScenario<LogViewerActivity> scenario = ActivityScenario.launch(LogViewerActivity.class)) {
            scenario.onActivity(activity -> firstSession.set(requireLiveFragment(activity).getLogcatSession()));
            scenario.recreate();
            assertNotNull(firstSession.get());
            assertFalse(LogViewerViewModel.isSessionActive(firstSession.get()));

            scenario.onActivity(activity -> {
                rotatedSession.set(requireLiveFragment(activity).getLogcatSession());
                activity.finish();
            });
            scenario.moveToState(Lifecycle.State.DESTROYED);
            assertNotNull(rotatedSession.get());
            assertFalse(LogViewerViewModel.isSessionActive(rotatedSession.get()));
        }
    }

    private static LiveLogViewerFragment requireLiveFragment(LogViewerActivity activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
        LiveLogViewerFragment fragment = (LiveLogViewerFragment) activity
                .getSupportFragmentManager().findFragmentByTag(LiveLogViewerFragment.TAG);
        assertNotNull(fragment);
        return fragment;
    }
}
