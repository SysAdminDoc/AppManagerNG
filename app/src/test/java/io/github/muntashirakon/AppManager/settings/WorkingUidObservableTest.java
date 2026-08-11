// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Process;

import androidx.lifecycle.Observer;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

/**
 * The privileged server can stop, crash, or drop its connection at any time. Each of those paths
 * ends up calling {@link Ops#setWorkingUid(int)}, so that is where a status surface has to be able
 * to find out — otherwise the only way to notice is to poll, and until something polls the UI keeps
 * showing a privilege the app no longer has.
 */
@RunWith(RobolectricTestRunner.class)
public class WorkingUidObservableTest {
    private final List<Observer<Integer>> mObservers = new ArrayList<>();

    @After
    public void tearDown() {
        for (Observer<Integer> observer : mObservers) {
            Ops.getWorkingUidLiveData().removeObserver(observer);
        }
        mObservers.clear();
        Ops.setWorkingUid(Process.myUid());
        ShadowLooper.idleMainLooper();
    }

    private List<Integer> observe() {
        List<Integer> seen = new ArrayList<>();
        Observer<Integer> observer = seen::add;
        mObservers.add(observer);
        Ops.getWorkingUidLiveData().observeForever(observer);
        seen.clear(); // drop the current value; we only care about subsequent changes
        return seen;
    }

    @Test
    public void gainingPrivilegeIsPublished() {
        List<Integer> seen = observe();

        Ops.setWorkingUid(Ops.ROOT_UID);
        ShadowLooper.idleMainLooper();

        assertEquals(1, seen.size());
        assertEquals(Integer.valueOf(Ops.ROOT_UID), seen.get(0));
    }

    @Test
    public void losingTheServerIsPublished() {
        Ops.setWorkingUid(Ops.ROOT_UID);
        ShadowLooper.idleMainLooper();
        List<Integer> seen = observe();

        // What the receiver does for ACTION_SERVER_STOPPED and ACTION_SERVER_DISCONNECTED.
        Ops.setWorkingUid(Process.myUid());
        ShadowLooper.idleMainLooper();

        assertEquals("a status surface must be told the privileged server went away",
                1, seen.size());
        assertEquals(Integer.valueOf(Process.myUid()), seen.get(0));
    }

    @Test
    public void anUnchangedUidIsNotRepublished() {
        Ops.setWorkingUid(Ops.ROOT_UID);
        ShadowLooper.idleMainLooper();
        List<Integer> seen = observe();

        Ops.setWorkingUid(Ops.ROOT_UID);
        ShadowLooper.idleMainLooper();

        assertTrue("re-asserting the same uid should not churn observers", seen.isEmpty());
    }

    @Test
    public void theObservableAgreesWithTheGetter() {
        Ops.setWorkingUid(Ops.SHELL_UID);
        ShadowLooper.idleMainLooper();

        assertEquals(Integer.valueOf(Ops.getWorkingUid()), Ops.getWorkingUidLiveData().getValue());
        assertEquals(Ops.SHELL_UID, Ops.getWorkingUid());
    }

    @Test
    public void aNewObserverSeesTheCurrentStateImmediately() {
        Ops.setWorkingUid(Ops.SHELL_UID);
        ShadowLooper.idleMainLooper();

        List<Integer> seen = new ArrayList<>();
        Observer<Integer> observer = seen::add;
        mObservers.add(observer);
        Ops.getWorkingUidLiveData().observeForever(observer);

        // A surface that starts observing after the change still has to render the right state.
        assertEquals(1, seen.size());
        assertEquals(Integer.valueOf(Ops.SHELL_UID), seen.get(0));
    }
}
