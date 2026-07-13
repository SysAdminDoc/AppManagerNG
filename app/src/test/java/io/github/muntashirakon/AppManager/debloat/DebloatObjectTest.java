// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

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
