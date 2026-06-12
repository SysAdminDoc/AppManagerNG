// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.tags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AppNoteStoreTest {
    private static final String PKG = "com.example.app";
    private AppNoteStore mStore;

    @Before
    public void setUp() {
        mStore = new AppNoteStore(ApplicationProvider.getApplicationContext());
        mStore.clear(PKG);
        mStore.clear("com.example.other");
    }

    @Test
    public void unknownPackageReturnsNull() {
        assertNull(mStore.getNote(PKG));
        assertFalse(mStore.hasNote(PKG));
    }

    @Test
    public void normalizeTrimsAndNormalizesNewlines() {
        assertEquals("Line 1\nLine 2", AppNoteStore.normalizeNote("  Line 1\r\nLine 2  "));
        assertNull(AppNoteStore.normalizeNote(""));
        assertNull(AppNoteStore.normalizeNote("   "));
        assertNull(AppNoteStore.normalizeNote(null));
    }

    @Test
    public void setNotePersistsAcrossStoreInstances() {
        mStore.setNote(PKG, " Keep before freezing ");

        AppNoteStore other = new AppNoteStore(ApplicationProvider.getApplicationContext());

        assertTrue(other.hasNote(PKG));
        assertEquals("Keep before freezing", other.getNote(PKG));
    }

    @Test
    public void emptyNoteClearsPackageRow() {
        mStore.setNote(PKG, "Temporary note");

        mStore.setNote(PKG, " ");

        assertNull(mStore.getNote(PKG));
        assertFalse(mStore.snapshot().containsKey(PKG));
    }

    @Test
    public void snapshotReturnsOnlyStoredNotes() {
        mStore.setNote(PKG, "Primary");
        mStore.setNote("com.example.other", "Other");

        assertEquals(2, mStore.snapshot().size());
        assertEquals("Primary", mStore.snapshot().get(PKG));
        assertEquals("Other", mStore.snapshot().get("com.example.other"));
    }

}
