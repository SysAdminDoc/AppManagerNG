// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.MutableLiveData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class LiveDataChangesTest {
    @Test
    public void onlyARealChangeRunsTheCallback() {
        Owner owner = new Owner();
        MutableLiveData<Boolean> state = new MutableLiveData<>(false);
        AtomicInteger runs = new AtomicInteger();

        LiveDataChanges.observe(state, owner, runs::incrementAndGet);
        owner.registry.setCurrentState(Lifecycle.State.RESUMED);
        // The value the screen already drew itself from
        assertEquals(0, runs.get());

        state.setValue(false);
        assertEquals(0, runs.get());
        state.setValue(true);
        assertEquals(1, runs.get());
        state.setValue(true);
        assertEquals(1, runs.get());
        state.setValue(false);
        assertEquals(2, runs.get());
    }

    @Test
    public void aChangeWhileStoppedArrivesOnceOnReturn() {
        Owner owner = new Owner();
        MutableLiveData<Integer> uid = new MutableLiveData<>(10_000);
        AtomicInteger runs = new AtomicInteger();

        LiveDataChanges.observe(uid, owner, runs::incrementAndGet);
        owner.registry.setCurrentState(Lifecycle.State.RESUMED);
        owner.registry.setCurrentState(Lifecycle.State.CREATED);
        uid.setValue(2000);
        uid.setValue(0);
        assertEquals(0, runs.get());

        owner.registry.setCurrentState(Lifecycle.State.RESUMED);
        assertEquals(1, runs.get());
    }

    @Test
    public void aChangeBeforeTheFirstDeliveryIsNotLost() {
        Owner owner = new Owner();
        MutableLiveData<Integer> uid = new MutableLiveData<>(10_000);
        AtomicInteger runs = new AtomicInteger();

        LiveDataChanges.observe(uid, owner, runs::incrementAndGet);
        // The mode changed between onCreate and onStart
        uid.setValue(0);
        owner.registry.setCurrentState(Lifecycle.State.RESUMED);

        assertEquals(1, runs.get());
    }

    private static final class Owner implements LifecycleOwner {
        final LifecycleRegistry registry = new LifecycleRegistry(this);

        Owner() {
            registry.setCurrentState(Lifecycle.State.CREATED);
        }

        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            return registry;
        }
    }
}
