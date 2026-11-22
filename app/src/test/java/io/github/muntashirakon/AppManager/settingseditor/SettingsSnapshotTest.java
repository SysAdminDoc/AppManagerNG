// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Issue 48: "Export snapshot" writes every readable key, and says which table it couldn't read. */
@RunWith(RobolectricTestRunner.class)
public class SettingsSnapshotTest {
    @Test
    public void everyTableAndValueIsKept() throws Exception {
        Map<SettingsTable, List<SettingEntry>> tables = new EnumMap<>(SettingsTable.class);
        tables.put(SettingsTable.SYSTEM, Arrays.asList(new SettingEntry("screen_brightness", "128"),
                new SettingEntry("empty", ""), new SettingEntry("unset", null)));
        tables.put(SettingsTable.GLOBAL, Collections.singletonList(new SettingEntry("multi", "a\nb=\"c\"")));
        Map<SettingsTable, String> unreadable = new EnumMap<>(SettingsTable.class);
        unreadable.put(SettingsTable.SECURE, "Permission denial");

        JSONObject json = new JSONObject(SettingsSnapshot.toJson(tables, unreadable, 0L, 36, 10).toString(2));

        assertEquals(SettingsSnapshot.FORMAT_VERSION, json.getInt("format"));
        assertEquals("1970-01-01T00:00:00Z", json.getString("created"));
        assertEquals(36, json.getInt("android_api"));
        assertEquals(10, json.getInt("user"));
        JSONObject system = json.getJSONObject("tables").getJSONObject("system");
        assertEquals("128", system.getString("screen_brightness"));
        assertEquals("", system.getString("empty"));
        assertTrue("a key with no value stays, as null", system.isNull("unset") && system.has("unset"));
        assertEquals("a\nb=\"c\"", json.getJSONObject("tables").getJSONObject("global").getString("multi"));
        assertFalse(json.getJSONObject("tables").has("secure"));
        assertEquals("Permission denial", json.getJSONObject("unreadable").getString("secure"));
    }

    @Test
    public void fileNamesSortByTime() {
        String name = SettingsSnapshot.fileName(0L);

        assertTrue(name, name.matches("settings-snapshot-\\d{8}-\\d{6}\\.json"));
    }
}
