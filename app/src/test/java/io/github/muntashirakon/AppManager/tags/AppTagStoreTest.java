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

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/** NF-08 tag-store contract. Robolectric host so SharedPreferences works. */
@RunWith(RobolectricTestRunner.class)
public class AppTagStoreTest {
    private static final String PKG = "com.example.app";
    private AppTagStore mStore;

    @Before
    public void setUp() {
        // Re-create the store every test so prior runs don't bleed in. Robolectric
        // wipes the in-memory prefs between methods, but be explicit anyway.
        mStore = new AppTagStore(ApplicationProvider.getApplicationContext());
        mStore.clear(PKG);
    }

    @Test
    public void unknownPackageReturnsEmptySet() {
        assertTrue(mStore.getTags(PKG).isEmpty());
        assertFalse(mStore.hasAnyTag(PKG));
    }

    @Test
    public void normaliseStripsCaseAndTrimsAndRejectsReservedCharacters() {
        assertEquals("work", AppTagStore.normaliseTag("  Work  "));
        assertEquals("pre-update", AppTagStore.normaliseTag("PRE-UPDATE"));
        assertEquals("critical_2", AppTagStore.normaliseTag("critical_2"));
        assertNull("empty", AppTagStore.normaliseTag(""));
        assertNull("whitespace only", AppTagStore.normaliseTag("   "));
        assertNull("comma rejected", AppTagStore.normaliseTag("a,b"));
        assertNull("colon rejected", AppTagStore.normaliseTag("a:b"));
        assertNull("space rejected", AppTagStore.normaliseTag("two words"));
        assertNull("leading hyphen rejected", AppTagStore.normaliseTag("-leading"));
        // 32 characters is the cap; one more must reject.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; ++i) sb.append('a');
        assertEquals("32-char limit", sb.toString(), AppTagStore.normaliseTag(sb.toString()));
        sb.append('a');
        assertNull("33 chars rejected", AppTagStore.normaliseTag(sb.toString()));
    }

    @Test
    public void addTagPersistsAndDedups() {
        assertTrue(mStore.addTag(PKG, "work"));
        assertTrue(mStore.addTag(PKG, "critical"));
        assertFalse("duplicate insert returns false", mStore.addTag(PKG, "WORK"));
        assertFalse("invalid insert returns false", mStore.addTag(PKG, "  "));

        Set<String> tags = mStore.getTags(PKG);
        assertEquals(2, tags.size());
        assertTrue(tags.contains("work"));
        assertTrue(tags.contains("critical"));
    }

    @Test
    public void removeTagDropsAndCollapsesEmptyEntry() {
        mStore.addTag(PKG, "work");
        mStore.addTag(PKG, "critical");
        assertTrue(mStore.removeTag(PKG, "WORK"));
        assertFalse(mStore.removeTag(PKG, "work"));

        assertTrue(mStore.hasAnyTag(PKG));
        assertTrue(mStore.removeTag(PKG, "critical"));
        assertFalse("empty package row should not linger", mStore.hasAnyTag(PKG));
    }

    @Test
    public void hasAllTagsMatchesOnlyWhenEverythingPresent() {
        mStore.addTag(PKG, "work");
        mStore.addTag(PKG, "critical");
        assertTrue(mStore.hasAllTags(PKG, Arrays.asList("work", "critical")));
        assertFalse(mStore.hasAllTags(PKG, Arrays.asList("work", "missing")));
        assertTrue("empty required-set matches everything",
                mStore.hasAllTags(PKG, Collections.emptyList()));
    }

    @Test
    public void hasAnyTagInMatchesOnFirstHit() {
        mStore.addTag(PKG, "critical");
        assertTrue(mStore.hasAnyTagIn(PKG, Arrays.asList("missing", "critical")));
        assertFalse(mStore.hasAnyTagIn(PKG, Arrays.asList("a", "b", "c")));
        assertFalse("empty list matches nothing",
                mStore.hasAnyTagIn(PKG, Collections.emptyList()));
    }

    @Test
    public void getAllKnownTagsRollsUpAcrossPackages() {
        mStore.addTag(PKG, "work");
        mStore.addTag("com.example.other", "critical");
        mStore.addTag("com.example.other", "throwaway");
        Set<String> known = mStore.getAllKnownTags();
        assertEquals(3, known.size());
        assertTrue(known.contains("work"));
        assertTrue(known.contains("critical"));
        assertTrue(known.contains("throwaway"));
    }

    @Test
    public void isValidTagMatchesNormalisation() {
        assertTrue(AppTagStore.isValidTag("work"));
        assertTrue(AppTagStore.isValidTag("pre-update"));
        assertFalse(AppTagStore.isValidTag(""));
        assertFalse(AppTagStore.isValidTag("two words"));
    }

    @Test
    public void snapshotRoundTripsAcrossNewStoreInstance() {
        mStore.addTag(PKG, "work");
        mStore.addTag(PKG, "critical");
        AppTagStore other = new AppTagStore(ApplicationProvider.getApplicationContext());
        Set<String> roundTripped = other.getTags(PKG);
        assertEquals(2, roundTripped.size());
        assertTrue(roundTripped.contains("work"));
        assertTrue(roundTripped.contains("critical"));
    }
}
