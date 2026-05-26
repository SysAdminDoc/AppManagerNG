// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ChangelogAutoDisplayTest {

    @Test
    public void emptyListPassesThrough() {
        List<ChangelogItem> empty = new ArrayList<>();
        assertSame(empty, ChangelogAutoDisplay.latestReleaseItems(empty));
    }

    @Test
    public void firstReleaseBlockOnlyReturned() {
        List<ChangelogItem> all = new ArrayList<>();
        all.add(new ChangelogHeader("v0.5.0", 7, "stable release", "25 May 2026"));
        all.add(new ChangelogItem("New: Settings search", ChangelogItem.NEW));
        all.add(new ChangelogItem("Improve: changelog viewer", ChangelogItem.IMPROVE));
        all.add(new ChangelogHeader("v0.4.2", 6, "stable release", "13 May 2026"));
        all.add(new ChangelogItem("New: AES v7", ChangelogItem.NEW));

        List<ChangelogItem> latest = ChangelogAutoDisplay.latestReleaseItems(all);

        assertEquals(3, latest.size());
        assertTrue(latest.get(0) instanceof ChangelogHeader);
        assertEquals("v0.5.0", ((ChangelogHeader) latest.get(0)).getVersionName());
    }

    @Test
    public void singleReleaseReturnedWhole() {
        List<ChangelogItem> all = new ArrayList<>();
        all.add(new ChangelogHeader("v0.5.0", 7, "stable release", "25 May 2026"));
        all.add(new ChangelogItem("New: Settings search", ChangelogItem.NEW));
        all.add(new ChangelogItem("Fix: deep-link crash", ChangelogItem.FIX));

        List<ChangelogItem> latest = ChangelogAutoDisplay.latestReleaseItems(all);

        assertEquals(3, latest.size());
    }

    @Test
    public void leadingItemsBeforeFirstHeaderAreSkipped() {
        // Parser should produce header-first lists; guard the unusual case anyway.
        List<ChangelogItem> all = new ArrayList<>();
        all.add(new ChangelogItem("Orphan note before any header", ChangelogItem.NOTE));
        all.add(new ChangelogHeader("v0.5.0", 7, "stable release", "25 May 2026"));
        all.add(new ChangelogItem("New: Settings search", ChangelogItem.NEW));
        all.add(new ChangelogHeader("v0.4.2", 6, "stable release", "13 May 2026"));
        all.add(new ChangelogItem("New: AES v7", ChangelogItem.NEW));

        List<ChangelogItem> latest = ChangelogAutoDisplay.latestReleaseItems(all);

        assertEquals(2, latest.size());
        assertTrue(latest.get(0) instanceof ChangelogHeader);
        assertEquals("v0.5.0", ((ChangelogHeader) latest.get(0)).getVersionName());
    }

    @Test
    public void listWithNoHeaderFallsThrough() {
        List<ChangelogItem> all = new ArrayList<>();
        all.add(new ChangelogItem("Orphan note", ChangelogItem.NOTE));

        List<ChangelogItem> latest = ChangelogAutoDisplay.latestReleaseItems(all);

        assertEquals(1, latest.size());
    }
}
