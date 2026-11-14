// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OverlayListTest {
    private String mQuery = "";
    private final List<List<String>> mShown = new ArrayList<>();
    private final OverlayList<String> mList = new OverlayList<>(this::filterAndSort, mShown::add);

    @Test
    public void aReadThatFinishesAfterALaterOneIsDropped() {
        long older = mList.startRead();
        long newer = mList.startRead();

        // The newer read saw the overlay turned on, the older one saw it before the change.
        assertTrue(mList.finishRead(newer, Arrays.asList("b:on", "a:on")));
        assertFalse(mList.finishRead(older, Arrays.asList("b:off", "a:on")));

        assertEquals(Collections.singletonList(Arrays.asList("a:on", "b:on")), mShown);
        // Search and sort keep working from the newer read.
        mList.refilter();
        assertEquals(Arrays.asList("a:on", "b:on"), last());
    }

    @Test
    public void readsFinishingInOrderAreAllShown() {
        long first = mList.startRead();
        assertTrue(mList.finishRead(first, Collections.singletonList("a:off")));
        long second = mList.startRead();
        assertTrue(mList.finishRead(second, Collections.singletonList("a:on")));

        assertEquals(Arrays.asList(Collections.singletonList("a:off"), Collections.singletonList("a:on")), mShown);
    }

    @Test
    public void searchStartsFromEveryOverlayNotFromWhatItShowedBefore() {
        mList.finishRead(mList.startRead(), Arrays.asList("gamma", "alpha", "beta"));

        mQuery = "alp";
        mList.refilter();
        assertEquals(Collections.singletonList("alpha"), last());

        // Clearing the search brings back what the earlier search hid.
        mQuery = "";
        mList.refilter();
        assertEquals(Arrays.asList("alpha", "beta", "gamma"), last());
    }

    @Test
    public void aReloadWhileSearchingKeepsTheSearch() {
        mList.finishRead(mList.startRead(), Arrays.asList("alpha", "beta"));
        mQuery = "bet";
        mList.refilter();

        mList.finishRead(mList.startRead(), Arrays.asList("alpha", "beta", "beta2"));

        assertEquals(Arrays.asList("beta", "beta2"), last());
    }

    @Test
    public void whatsShownCantChangeTheListBehindIt() {
        mList.finishRead(mList.startRead(), new ArrayList<>(Arrays.asList("b", "a")));
        last().clear();

        mList.refilter();

        assertEquals(Arrays.asList("a", "b"), last());
    }

    @NonNull
    private List<String> filterAndSort(@NonNull List<String> all) {
        List<String> result = new ArrayList<>();
        for (String item : all) {
            if (item.contains(mQuery)) {
                result.add(item);
            }
        }
        Collections.sort(result);
        return result;
    }

    @NonNull
    private List<String> last() {
        return mShown.get(mShown.size() - 1);
    }
}
