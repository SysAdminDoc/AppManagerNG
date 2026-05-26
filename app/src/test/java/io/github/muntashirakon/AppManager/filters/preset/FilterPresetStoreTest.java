// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.preset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.filters.FilterItem;

@RunWith(RobolectricTestRunner.class)
public class FilterPresetStoreTest {
    private FilterPresetStore mStore;

    @Before
    public void setUp() {
        mStore = new FilterPresetStore(ApplicationProvider.getApplicationContext());
        for (FilterPresetStore.Preset preset : mStore.all()) {
            mStore.remove(preset.id);
        }
    }

    @Test
    public void normaliseAcceptsAllowedShapes() {
        assertEquals("work", FilterPresetStore.normaliseName("Work"));
        assertEquals("pre update", FilterPresetStore.normaliseName("Pre Update"));
        assertEquals("backup-essentials", FilterPresetStore.normaliseName("Backup-Essentials"));
    }

    @Test
    public void normaliseRejectsEmptyAndReservedCharacters() {
        assertNull(FilterPresetStore.normaliseName(""));
        assertNull(FilterPresetStore.normaliseName("   "));
        assertNull(FilterPresetStore.normaliseName("with,comma"));
        assertNull(FilterPresetStore.normaliseName("with:colon"));
        assertNull(FilterPresetStore.normaliseName("-leading"));
    }

    @Test
    public void saveRejectsInvalidNamesAndDuplicates() {
        FilterItem filter = new FilterItem();
        assertNotNull(mStore.save("Power Users", filter));
        assertNull("duplicate name", mStore.save("power users", filter));
        assertNull("invalid name", mStore.save(",", filter));
    }

    @Test
    public void saveRoundTripsAcrossFreshInstance() {
        FilterItem filter = new FilterItem();
        FilterPresetStore.Preset saved = mStore.save("Critical", filter);
        assertNotNull(saved);
        FilterPresetStore other = new FilterPresetStore(ApplicationProvider.getApplicationContext());
        FilterPresetStore.Preset reread = other.find(saved.id);
        assertNotNull(reread);
        assertEquals("critical", reread.name);
        assertEquals(saved.createdAt, reread.createdAt);
        assertNotNull(reread.filter);
    }

    @Test
    public void allReturnsSortedByName() {
        FilterItem filter = new FilterItem();
        mStore.save("Zeta", filter);
        mStore.save("alpha", filter);
        mStore.save("mid", filter);
        java.util.List<FilterPresetStore.Preset> all = mStore.all();
        assertEquals(3, all.size());
        assertEquals("alpha", all.get(0).name);
        assertEquals("mid", all.get(1).name);
        assertEquals("zeta", all.get(2).name);
    }

    @Test
    public void renameKeepsIdAndRejectsConflict() {
        FilterItem filter = new FilterItem();
        FilterPresetStore.Preset original = mStore.save("work", filter);
        FilterPresetStore.Preset other = mStore.save("home", filter);
        assertNotNull(original);
        assertNotNull(other);

        // Conflicting rename returns null without mutating.
        assertNull(mStore.rename(original.id, "home"));
        assertEquals("work", mStore.find(original.id).name);

        // Non-conflicting rename updates.
        FilterPresetStore.Preset renamed = mStore.rename(original.id, "office");
        assertNotNull(renamed);
        assertEquals(original.id, renamed.id);
        assertEquals("office", renamed.name);
    }

    @Test
    public void removeReturnsFalseWhenAbsent() {
        assertFalse(mStore.remove("does-not-exist"));
    }

    @Test
    public void hasAnyTracksLifecycle() {
        FilterItem filter = new FilterItem();
        assertFalse(mStore.hasAny());
        FilterPresetStore.Preset preset = mStore.save("snap", filter);
        assertNotNull(preset);
        assertTrue(mStore.hasAny());
        mStore.remove(preset.id);
        assertFalse(mStore.hasAny());
    }
}
