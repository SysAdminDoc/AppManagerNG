// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.trigger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
public class RoutineWorkerTest {
    private Context mContext;
    private ProfileTriggerStore mStore;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mStore = new ProfileTriggerStore(mContext);
        for (ProfileTrigger trigger : mStore.all()) {
            mStore.remove(trigger.id);
        }
    }

    @Test
    public void missingTriggerIsNoOp() {
        AtomicBoolean called = new AtomicBoolean(false);

        assertNotNull(RoutineWorker.executeTrigger(mContext, mStore, "missing", profile -> called.set(true)));

        assertFalse(called.get());
    }

    @Test
    public void disabledTriggerDoesNotStartProfile() {
        ProfileTrigger trigger = new ProfileTrigger.Builder("profile-a", ProfileTrigger.TYPE_ON_CHARGING)
                .enabled(false)
                .build();
        mStore.put(trigger);
        AtomicBoolean called = new AtomicBoolean(false);

        assertNotNull(RoutineWorker.executeTrigger(mContext, mStore, trigger.id, profile -> called.set(true)));

        assertFalse(called.get());
        ProfileTrigger reread = mStore.find(trigger.id);
        assertNotNull(reread);
        assertFalse(reread.enabled);
    }

    @Test
    public void missingProfileDisablesTrigger() {
        ProfileTrigger trigger = new ProfileTrigger.Builder("profile-does-not-exist", ProfileTrigger.TYPE_ON_CHARGING)
                .build();
        mStore.put(trigger);

        assertNotNull(RoutineWorker.executeTrigger(mContext, mStore, trigger.id, profile -> {
            throw new AssertionError("starter should not run for a missing profile");
        }));

        ProfileTrigger reread = mStore.find(trigger.id);
        assertNotNull(reread);
        assertFalse(reread.enabled);
    }
}
