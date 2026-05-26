// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class GlossaryPreferencesTest {

    private static final String[] KEYS = new String[] {
            "glossary_mode_of_operations",
            "glossary_shizuku",
            "glossary_root",
            "glossary_adb",
            "glossary_appops",
            "glossary_freezing",
            "glossary_component_blocking",
            "glossary_trackers",
            "glossary_debloater",
            "glossary_backup_encryption",
            "glossary_scheduled_backup",
            "glossary_pro_mode",
            "glossary_finder",
            "glossary_intent_interceptor",
    };

    @Test
    public void everyGlossaryKeyResolvesToANonZeroBodyStringRes() {
        for (String key : KEYS) {
            int body = GlossaryPreferences.resolveBodyRes(key);
            assertNotEquals("Glossary key '" + key + "' must map to a real body string", 0, body);
        }
    }

    @Test
    public void unknownKeysResolveToZero() {
        assertEquals(0, GlossaryPreferences.resolveBodyRes("glossary_not_a_real_topic"));
        assertEquals(0, GlossaryPreferences.resolveBodyRes(""));
    }

    @Test
    public void bodyStringsAreDistinctAndNonEmpty() {
        Set<CharSequence> seen = new HashSet<>();
        for (String key : KEYS) {
            int body = GlossaryPreferences.resolveBodyRes(key);
            CharSequence text = ApplicationProvider.getApplicationContext().getText(body);
            assertTrue("Body for " + key + " must not be empty", text != null && text.length() > 0);
            seen.add(text);
        }
        assertEquals("Every glossary body should be unique copy", KEYS.length, seen.size());
    }

    @Test
    public void glossaryEntriesAreReachableThroughSettingsSearch() {
        // The "glossary" source is registered in SettingsSearchIndex; ensure the row
        // titles are indexed so a search like "Shizuku" hits a glossary entry rather
        // than (or in addition to) Mode-of-operations-only matches.
        SettingsSearchIndex.invalidate();
        SettingsSearchIndex index = SettingsSearchIndex.get(ApplicationProvider.getApplicationContext());
        List<SettingsSearchIndex.Entry> matches = index.search("shizuku");
        boolean foundGlossaryRow = false;
        for (SettingsSearchIndex.Entry entry : matches) {
            if ("glossary".equals(entry.parentKey)
                    && "glossary_shizuku".equals(entry.targetKey)) {
                foundGlossaryRow = true;
                break;
            }
        }
        assertTrue("Expected the Shizuku glossary entry in 'shizuku' search results", foundGlossaryRow);
    }
}
