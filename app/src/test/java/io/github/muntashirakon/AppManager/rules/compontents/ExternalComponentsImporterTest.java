// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.rules.RuleType;

@RunWith(RobolectricTestRunner.class)
public class ExternalComponentsImporterTest {
    @Test
    public void parseMyAndroidToolsPlainRulesHandlesTypedEntries() throws IOException {
        String backup = "# MyAndroidTools backup\n"
                + "com.example/.BootReceiver 3r\n"
                + "com.example/com.example.SyncService ss\n"
                + "com.example/com.example.SettingsActivity 3a\n"
                + "com.example/DataProvider 3p\n"
                + "com.legacy/.UntypedComponent\n"
                + "not-a-component\n";

        HashMap<String, HashMap<String, RuleType>> parsed =
                ExternalComponentsImporter.parseMyAndroidToolsPlainRules(backup);

        assertEquals(RuleType.RECEIVER, parsed.get("com.example").get("com.example.BootReceiver"));
        assertEquals(RuleType.SERVICE, parsed.get("com.example").get("com.example.SyncService"));
        assertEquals(RuleType.ACTIVITY, parsed.get("com.example").get("com.example.SettingsActivity"));
        assertEquals(RuleType.PROVIDER, parsed.get("com.example").get("com.example.DataProvider"));
        assertTrue(parsed.containsKey("com.legacy"));
        assertNull(parsed.get("com.legacy").get("com.legacy.UntypedComponent"));
    }

    @Test
    public void parseMyAndroidToolsPlainRulesRejectsConflictingTypes() throws IOException {
        try {
            ExternalComponentsImporter.parseMyAndroidToolsPlainRules(
                    "com.example/.Receiver 3r\n"
                            + "com.example/.Receiver 3s\n");
            fail("Expected conflicting MyAndroidTools component type to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Conflicting MyAndroidTools component type"));
        }
    }

    @Test
    public void parseMyAndroidToolsIfwRulesReadsPackageArchives() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("com.example$.xml"));
            zip.write(("<rules>\n"
                    + "  <service block=\"true\" log=\"false\">\n"
                    + "    <component-filter name=\"com.example/.SyncService\" />\n"
                    + "  </service>\n"
                    + "  <broadcast block=\"true\" log=\"false\">\n"
                    + "    <component-filter name=\"com.example/com.example.BootReceiver\" />\n"
                    + "  </broadcast>\n"
                    + "</rules>\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("ignored.xml"));
            zip.write("<rules />".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        HashMap<String, HashMap<String, RuleType>> parsed =
                ExternalComponentsImporter.parseMyAndroidToolsIfwRules(new ByteArrayInputStream(out.toByteArray()));

        assertEquals(1, parsed.size());
        assertEquals(RuleType.SERVICE, parsed.get("com.example").get("com.example.SyncService"));
        assertEquals(RuleType.RECEIVER, parsed.get("com.example").get("com.example.BootReceiver"));
    }
}
