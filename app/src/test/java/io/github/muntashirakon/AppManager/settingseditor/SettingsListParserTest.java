// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Issue 48: reading {@code settings list <table>}. */
public class SettingsListParserTest {
    @Test
    public void emptyValuesStayEmpty() {
        List<SettingEntry> entries = SettingsListParser.parse(Collections.singletonList("ringtone="));

        assertEquals(Collections.singletonList(new SettingEntry("ringtone", "")), entries);
    }

    @Test
    public void onlyTheFirstEqualsSignEndsTheKey() {
        List<SettingEntry> entries = SettingsListParser.parse(Collections.singletonList(
                "enabled_notification_listeners=a/b.C=1:x=y"));

        assertEquals("enabled_notification_listeners", entries.get(0).key);
        assertEquals("a/b.C=1:x=y", entries.get(0).value);
    }

    @Test
    public void nullMeansNoValue() {
        List<SettingEntry> entries = SettingsListParser.parse(Arrays.asList("a=null", "b=nullable", "c= null"));

        assertNull(entries.get(0).value);
        assertEquals("nullable", entries.get(1).value);
        assertEquals(" null", entries.get(2).value);
    }

    @Test
    public void aValueCanRunOverSeveralLines() {
        List<SettingEntry> entries = SettingsListParser.parse(Arrays.asList(
                "lock_screen_owner_info=Line one", "Line two", "", "Line four", "next_key=1", ""));

        assertEquals(2, entries.size());
        assertEquals("Line one\nLine two\n\nLine four", entries.get(0).value);
        // A blank line at the very end is the shell's, not the value's.
        assertEquals("1", entries.get(1).value);
    }

    @Test
    public void shellChatterBeforeTheFirstRowIsIgnored() {
        List<SettingEntry> entries = SettingsListParser.parse(Arrays.asList(
                "WARNING: linker: something odd", "", "adb_enabled=1"));

        assertEquals(Collections.singletonList(new SettingEntry("adb_enabled", "1")), entries);
    }

    @Test
    public void rowsAreSortedAndTheLastCopyOfAKeyWins() {
        List<SettingEntry> entries = SettingsListParser.parse(Arrays.asList(
                "wifi_on=1", "Airplane_mode_radios=cell", "adb_enabled=0", "adb_enabled=1"));

        assertEquals(Arrays.asList(new SettingEntry("adb_enabled", "1"),
                new SettingEntry("Airplane_mode_radios", "cell"), new SettingEntry("wifi_on", "1")), entries);
    }

    @Test
    public void nothingListedIsAnEmptyTable() {
        assertTrue(SettingsListParser.parse(Collections.emptyList()).isEmpty());
        assertTrue(SettingsListParser.parse(Arrays.asList("", "=no key")).isEmpty());
    }
}
