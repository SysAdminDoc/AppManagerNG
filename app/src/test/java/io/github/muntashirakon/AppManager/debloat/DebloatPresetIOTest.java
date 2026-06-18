// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public class DebloatPresetIOTest {
    @Test
    public void writePresetIncludesVersionedEntries() throws IOException {
        Map<String, int[]> selectedPackages = new LinkedHashMap<>();
        selectedPackages.put("com.example.alpha", new int[]{0, 10});

        StringWriter writer = new StringWriter();
        DebloatPresetIO.writePreset(writer, selectedPackages);
        DebloatPresetIO.DebloatPresetData data = DebloatPresetIO.readPreset(
                new StringReader(writer.toString()));

        assertEquals(1, data.version);
        assertEquals(1, data.entries.size());
        assertEquals("com.example.alpha", data.entries.get(0).packageName);
        assertArrayEquals(new int[]{0, 10}, data.entries.get(0).userIds);
    }

    @Test
    public void readPresetRejectsMalformedJsonAsIOException() {
        IOException exception = assertThrows(IOException.class,
                () -> DebloatPresetIO.readPreset(new StringReader("{not-json")));

        assertTrue(exception.getMessage().contains("Invalid debloat preset file"));
    }

    @Test
    public void readPresetRejectsUnsupportedVersion() {
        IOException exception = assertThrows(IOException.class,
                () -> DebloatPresetIO.readPreset(new StringReader("{\"version\":2,\"entries\":[]}")));

        assertTrue(exception.getMessage().contains("Unsupported debloat preset version"));
    }
}
