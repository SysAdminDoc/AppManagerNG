// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.model.ResourceEntry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class FrameworkTablesTest {
    /** android:backgroundPermission in the API 30 to 32 tables, autofillSaveCustomSubtitleMaxHeight from 33 on. */
    private static final int PRIVATE_ATTR_2040 = 0x01120017;
    /** android:label, public since API 1 */
    private static final int ATTR_LABEL = 0x01010001;

    @Test
    public void thePackagedDataCoversEveryApiFrom21ToTheNewest() throws IOException {
        FrameworkTables.Data data = packagedData();

        assertTrue(data.newest, data.newest.matches("\\d+(\\.\\d+)?"));
        int newestMajor = Integer.parseInt(data.newest.split("\\.")[0]);
        List<Integer> expected = new ArrayList<>();
        for (int api = 21; api <= newestMajor; ++api) {
            expected.add(api);
        }
        assertEquals(expected, new ArrayList<>(data.sections.keySet()));
        for (Map.Entry<Integer, FrameworkTables.Section> section : data.sections.entrySet()) {
            String table = section.getValue().table;
            int api = section.getKey();
            // ARSCLib bundles tables from 23 on, and the newest SDK's comes with the app
            assertTrue(api + " -> " + table, table.equals("newest") || Integer.parseInt(table) == Math.max(api, 23));
        }
    }

    @Test
    public void everyTableTheDataNamesLoadsWithItsOwnVersion() throws IOException {
        for (int api : packagedData().sections.keySet()) {
            PackageBlock block = FrameworkTables.forApi(api).packageBlock();
            assertNotNull("API " + api, block);
            assertEquals("API " + api, "android", block.getName());
        }
        assertNotNull(FrameworkTables.forApi(FrameworkTables.UNKNOWN_API).packageBlock());
    }

    @Test
    public void upstream2040ThePrivateIdIsNamedByTheTableItWasCompiledAgainst() {
        FrameworkTables.Choice api31 = FrameworkTables.forApi(31);
        assertTrue(api31.canNameAttribute(PRIVATE_ATTR_2040));
        assertEquals("backgroundPermission", name(api31, PRIVATE_ATTR_2040));

        FrameworkTables.Choice api33 = FrameworkTables.forApi(33);
        assertTrue(api33.canNameAttribute(PRIVATE_ATTR_2040));
        assertEquals("autofillSaveCustomSubtitleMaxHeight", name(api33, PRIVATE_ATTR_2040));

        // Without compile metadata the tables disagree, so no name is certain
        assertFalse(FrameworkTables.forApi(FrameworkTables.UNKNOWN_API).canNameAttribute(PRIVATE_ATTR_2040));
    }

    @Test
    public void appIdsAreAlwaysNameable() {
        for (int api : new int[]{FrameworkTables.UNKNOWN_API, 5, 21, 31, 37, 99}) {
            FrameworkTables.Choice choice = FrameworkTables.forApi(api);
            assertTrue("API " + api, choice.canNameAttribute(0x7f010000));
            assertTrue("API " + api, choice.canNameReference(0x7f0e0001));
        }
    }

    @Test
    public void anApiOutsideTheDataNamesOnlyPublicIds() {
        for (int api : new int[]{5, 99}) {
            FrameworkTables.Choice choice = FrameworkTables.forApi(api);
            assertEquals("newest", choice.table);
            assertTrue("API " + api, choice.canNameAttribute(ATTR_LABEL));
            assertTrue("API " + api, choice.canNameReference(ATTR_LABEL));
            assertFalse("API " + api, choice.canNameAttribute(PRIVATE_ATTR_2040));
            assertFalse("API " + api, choice.canNameReference(PRIVATE_ATTR_2040));
        }
    }

    @Test
    public void aMinimalFileParses() throws IOException {
        FrameworkTables.Data data = parse("# comment\n"
                + "newest 37.2\n"
                + "api 21 table 23\n"
                + "01050010\n"
                + "01120000-011200ff\n"
                + "api 22 table newest\n"
                + "any table newest\n"
                + "ffffff00-ffffffff\n"
                + "any references\n"
                + "01050010\n");

        assertEquals("37.2", data.newest);
        FrameworkTables.IdSet disputed = data.sections.get(21).disputed;
        assertTrue(disputed.contains(0x01050010));
        assertTrue(disputed.contains(0x01120000));
        assertTrue(disputed.contains(0x011200ff));
        assertFalse(disputed.contains(0x01050011));
        assertFalse(disputed.contains(0x01120100));
        assertEquals(2, disputed.rangeCount());
        assertEquals("newest", data.sections.get(22).table);
        assertEquals(0, data.sections.get(22).disputed.rangeCount());
        // Above 0x7fffffff the comparison stays unsigned
        assertTrue(data.anyNames.contains(0xffffffff));
        assertFalse(data.anyNames.contains(0x01050010));
        assertTrue(data.anyReferences.contains(0x01050010));
    }

    @Test
    public void aDamagedFileIsRefused() {
        String tail = "any table newest\nany references\n";
        String[] damaged = {
                // Out of order
                "newest 37.2\napi 21 table 23\n01050010\n0105000f\n" + tail,
                // Overlapping
                "newest 37.2\napi 21 table 23\n01050000-01050010\n01050010\n" + tail,
                // Backwards
                "newest 37.2\napi 21 table 23\n01050011-01050010\n" + tail,
                // A range outside any section
                "newest 37.2\n01050010\napi 21 table 23\n" + tail,
                // Upper-case hex isn't a range line, so it's an unknown line
                "newest 37.2\napi 21 table 23\n0105001A\n" + tail,
                // The same API twice
                "newest 37.2\napi 21 table 23\napi 21 table 23\n" + tail,
                // A table that isn't a number or "newest"
                "newest 37.2\napi 21 table old\n" + tail,
                // Missing sections
                "newest 37.2\napi 21 table 23\nany table newest\n",
                "api 21 table 23\n" + tail,
                "newest 37.2\n" + tail,
        };
        for (String text : damaged) {
            assertThrows(text, RuntimeException.class, () -> parse(text));
        }
    }

    @NonNull
    private static String name(@NonNull FrameworkTables.Choice choice, int id) {
        ResourceEntry entry = choice.packageBlock().getResource(id);
        assertNotNull(String.format("0x%08x in table %s", id, choice.table), entry);
        return entry.getName();
    }

    @NonNull
    private static FrameworkTables.Data packagedData() throws IOException {
        try (InputStream in = FrameworkTables.class.getResourceAsStream(
                "/io/github/muntashirakon/AppManager/apk/parser/frameworks/disputed-ids.txt")) {
            assertNotNull(in);
            return FrameworkTables.parse(in);
        }
    }

    @NonNull
    private static FrameworkTables.Data parse(@NonNull String text) throws IOException {
        return FrameworkTables.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
}
