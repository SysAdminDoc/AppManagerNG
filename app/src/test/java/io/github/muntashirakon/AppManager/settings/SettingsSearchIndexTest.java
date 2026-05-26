// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class SettingsSearchIndexTest {

    @Test
    public void indexBuildsFromBundledPreferenceXml() {
        SettingsSearchIndex.invalidate();
        SettingsSearchIndex index = SettingsSearchIndex.get(ApplicationProvider.getApplicationContext());
        List<SettingsSearchIndex.Entry> entries = index.all();
        assertTrue("Expected at least a dozen indexable preferences; got " + entries.size(),
                entries.size() >= 12);
        for (SettingsSearchIndex.Entry entry : entries) {
            assertNotNull(entry.title);
            assertTrue(entry.title.length() > 0);
            assertNotNull(entry.parentKey);
            assertNotNull(entry.parentLabel);
        }
    }

    @Test
    public void emptyQueryReturnsEmptyList() {
        SettingsSearchIndex.invalidate();
        SettingsSearchIndex index = SettingsSearchIndex.get(ApplicationProvider.getApplicationContext());
        assertEquals(0, index.search("").size());
        assertEquals(0, index.search("   ").size());
        assertEquals(0, index.search(null).size());
    }

    @Test
    public void substringMatchesAreCaseInsensitive() {
        SettingsSearchIndex.invalidate();
        SettingsSearchIndex index = SettingsSearchIndex.get(ApplicationProvider.getApplicationContext());
        // The string "About" lives in preferences_about.xml; both casings must match.
        List<SettingsSearchIndex.Entry> lower = index.search("about");
        List<SettingsSearchIndex.Entry> upper = index.search("ABOUT");
        assertEquals(lower.size(), upper.size());
        assertTrue("Expected at least one 'about' match", lower.size() >= 1);
    }

    @Test
    public void entriesCarryParentKeyAndLabel() {
        SettingsSearchIndex.invalidate();
        SettingsSearchIndex index = SettingsSearchIndex.get(ApplicationProvider.getApplicationContext());
        boolean foundParentKey = false;
        for (SettingsSearchIndex.Entry entry : index.all()) {
            if ("about".equals(entry.parentKey)) {
                foundParentKey = true;
                break;
            }
        }
        assertTrue("Expected at least one entry under the 'about' parent", foundParentKey);
    }

    @Test
    public void entryConstructorPreservesFields() {
        SettingsSearchIndex.Entry entry = new SettingsSearchIndex.Entry(
                "App theme", "Follow system / dark / light", "app_theme",
                "appearance_prefs", "Appearance");
        assertEquals("App theme", entry.title);
        assertEquals("Follow system / dark / light", entry.summary);
        assertEquals("app_theme", entry.targetKey);
        assertEquals("appearance_prefs", entry.parentKey);
        assertEquals("Appearance", entry.parentLabel);
    }

    @Test
    public void entryAllowsNullTargetAndSummary() {
        SettingsSearchIndex.Entry entry = new SettingsSearchIndex.Entry(
                "Section header", null, null,
                "about", "About");
        assertNull(entry.summary);
        assertNull(entry.targetKey);
    }
}
