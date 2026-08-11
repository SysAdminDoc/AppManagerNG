// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class SearchViewDebouncerTest {
    private List<String> mDelivered;
    private SearchViewDebouncer mDebouncer;

    @Before
    public void setUp() {
        mDelivered = new ArrayList<>();
        mDebouncer = new SearchViewDebouncer((query, type) -> mDelivered.add(query + "/" + type));
    }

    /** Advance past the debounce window so any due callback runs. */
    private void idle() {
        ShadowLooper.idleMainLooper(SearchViewDebouncer.DEFAULT_DEBOUNCE_MS + 50, TimeUnit.MILLISECONDS);
    }

    @Test
    public void queryIsNotDeliveredUntilTheDebounceWindowElapses() {
        mDebouncer.onQueryTextChange("ab", 0);
        assertTrue(mDelivered.isEmpty());
        assertTrue(mDebouncer.hasPending());
        idle();
        assertEquals(1, mDelivered.size());
        assertEquals("ab/0", mDelivered.get(0));
        assertFalse(mDebouncer.hasPending());
    }

    @Test
    public void onlyTheLastKeystrokeInABurstIsDelivered() {
        mDebouncer.onQueryTextChange("a", 0);
        mDebouncer.onQueryTextChange("ab", 0);
        mDebouncer.onQueryTextChange("abc", 0);
        idle();
        assertEquals(1, mDelivered.size());
        assertEquals("abc/0", mDelivered.get(0));
    }

    @Test
    public void submitDeliversImmediatelyAndCancelsThePendingPass() {
        mDebouncer.onQueryTextChange("partial", 0);
        mDebouncer.onQueryTextSubmit("final", 1);
        // Delivered without waiting, and the superseded keystroke never arrives afterwards.
        assertEquals(1, mDelivered.size());
        assertEquals("final/1", mDelivered.get(0));
        assertFalse(mDebouncer.hasPending());
        idle();
        assertEquals(1, mDelivered.size());
    }

    @Test
    public void cancelDropsThePendingPassSoItCannotFireAfterTeardown() {
        mDebouncer.onQueryTextChange("gone", 0);
        mDebouncer.cancel();
        assertFalse(mDebouncer.hasPending());
        idle();
        assertTrue(mDelivered.isEmpty());
    }

    @Test
    public void nullQueryIsForwardedRatherThanSwallowed() {
        // Clearing a search view emits a null/empty query; the list has to be restored, so the
        // debouncer must not treat it as "nothing to do".
        mDebouncer.onQueryTextChange(null, 2);
        idle();
        assertEquals(1, mDelivered.size());
        assertEquals("null/2", mDelivered.get(0));
    }

    @Test
    public void searchTypeChangeWithTheSameTextIsStillDelivered() {
        mDebouncer.onQueryTextChange("q", 0);
        idle();
        mDebouncer.onQueryTextChange("q", 1);
        idle();
        assertEquals(2, mDelivered.size());
        assertEquals("q/1", mDelivered.get(1));
    }
}
