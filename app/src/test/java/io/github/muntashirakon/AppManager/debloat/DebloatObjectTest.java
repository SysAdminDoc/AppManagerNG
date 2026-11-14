// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import java.util.Collections;

public class DebloatObjectTest {

    @Test
    public void installFlagsAccumulateAcrossUsersWithoutLastWriteWins() {
        DebloatObject.InstallFlags flags = new DebloatObject.InstallFlags();
        // user 0: installed, system, not updated-system, not frozen
        flags.accumulate(true, true, false, false);
        // user 10: not installed, not system, not updated-system, frozen
        flags.accumulate(false, false, false, true);

        assertTrue("installed for any user must stay true", flags.installed);
        assertTrue("system for any user must stay true", flags.systemApp);
        assertFalse(flags.updatedSystemApp);
        assertTrue("frozen for any user is reflected", flags.frozen);
    }

    @Test
    public void installFlagsDefaultToUnsetAndFalse() {
        DebloatObject.InstallFlags flags = new DebloatObject.InstallFlags();
        assertFalse(flags.installed);

        flags.accumulate(false, false, false, false);
        assertFalse(flags.installed);
        assertEquals(Boolean.FALSE, flags.systemApp);
        assertEquals(Boolean.FALSE, flags.frozen);
    }

    @Test
    public void aCopyKeepsTheDefinitionAndStartsWithNoInstallState() {
        Gson gson = new Gson();
        DebloatObject original = gson.fromJson("{\"id\":\"com.example\",\"label\":\"Example\",\"type\":\"oem\","
                + "\"description\":\"test\",\"removal\":\"caution\",\"warning\":\"careful\","
                + "\"dependencies\":[\"com.example.dep\"],\"required_by\":[\"com.example.parent\"],"
                + "\"web\":[\"https://example.com\"],\"suggestions\":\"s1\"}", DebloatObject.class);
        SuggestionObject suggestion = gson.fromJson(
                "{\"_id\":\"s1\",\"id\":\"org.example.alt\",\"label\":\"Alt\",\"reason\":\"open\",\"source\":\"f\"}",
                SuggestionObject.class);
        suggestion.addUser(0);
        original.setSuggestions(Collections.singletonList(suggestion));
        original.setId(7);
        original.setInstalledForTesting(true);

        DebloatObject copy = original.copyDefinition(null);

        assertNotSame(original, copy);
        assertEquals("com.example", copy.packageName);
        assertEquals("oem", copy.type);
        assertEquals("Example", copy.getLabelOrPackageName().toString());
        assertEquals("test", copy.getDescription());
        assertEquals(DebloatObject.REMOVAL_CAUTION, copy.getRemoval());
        assertEquals("careful", copy.getWarning());
        assertArrayEquals(new String[]{"com.example.dep"}, copy.getDependencies());
        assertArrayEquals(new String[]{"com.example.parent"}, copy.getRequiredBy());
        assertArrayEquals(new String[]{"https://example.com"}, copy.getWebRefs());
        assertEquals("s1", copy.getSuggestionId());
        assertEquals(7, copy.getId());
        assertFalse(copy.isInstalled());
        assertNull(copy.getUsers());
        assertNull(copy.getIcon());

        SuggestionObject suggestionCopy = copy.getSuggestions().get(0);
        assertNotSame(suggestion, suggestionCopy);
        assertEquals("org.example.alt", suggestionCopy.packageName);
        assertEquals("Alt", suggestionCopy.getLabel());
        assertEquals("open", suggestionCopy.getReason());
        assertTrue(suggestionCopy.isInFDroidMarket());
        assertNull(suggestionCopy.getUsers());
        // Filling in the copy leaves the original alone.
        suggestionCopy.addUser(10);
        assertArrayEquals(new int[]{0}, suggestion.getUsers());
        assertTrue(original.isInstalled());
    }

    private static DebloatObject parse(String removal) {
        String json = "{\"id\":\"com.example\",\"type\":\"oem\",\"description\":\"test\",\"removal\":\"" + removal + "\"}";
        return new Gson().fromJson(json, DebloatObject.class);
    }

    @Test
    public void removalSafeMapsCorrectly() {
        assertEquals(DebloatObject.REMOVAL_SAFE, parse("safe").getRemoval());
    }

    @Test
    public void removalDeleteMapsToSafe() {
        assertEquals(DebloatObject.REMOVAL_SAFE, parse("delete").getRemoval());
    }

    @Test
    public void removalReplaceMapsCorrectly() {
        assertEquals(DebloatObject.REMOVAL_REPLACE, parse("replace").getRemoval());
    }

    @Test
    public void removalCautionMapsCorrectly() {
        assertEquals(DebloatObject.REMOVAL_CAUTION, parse("caution").getRemoval());
    }

    @Test
    public void removalUnsafeMapsCorrectly() {
        assertEquals(DebloatObject.REMOVAL_UNSAFE, parse("unsafe").getRemoval());
    }

    @Test
    public void removalUnknownDefaultsToUnsafe() {
        assertEquals(DebloatObject.REMOVAL_UNSAFE, parse("unknown").getRemoval());
    }

    @Test
    public void removalNullDefaultsToUnsafe() {
        String json = "{\"id\":\"com.example\",\"type\":\"oem\",\"description\":\"test\"}";
        DebloatObject obj = new Gson().fromJson(json, DebloatObject.class);
        assertEquals(DebloatObject.REMOVAL_UNSAFE, obj.getRemoval());
    }
}
