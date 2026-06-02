/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.muntashirakon.AppManager.rules.compontents;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ComponentUtilsTest {
    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    public void escapeXmlEscapesAllFivePredefinedEntities() {
        assertEquals("&amp;", ComponentUtils.escapeXml("&"));
        assertEquals("&quot;", ComponentUtils.escapeXml("\""));
        assertEquals("&apos;", ComponentUtils.escapeXml("'"));
        assertEquals("&lt;", ComponentUtils.escapeXml("<"));
        assertEquals("&gt;", ComponentUtils.escapeXml(">"));
    }

    @Test
    public void escapeXmlLeavesOrdinaryComponentNamesUnchanged() {
        String name = "com.example.app/com.example.app.MainActivity";
        assertEquals(name, ComponentUtils.escapeXml(name));
    }

    @Test
    public void escapeXmlNeutralizesIfwInjectionPayload() {
        // A component name crafted to break out of the component-filter attribute
        // and inject extra IFW elements (the privileged-writer XML-injection bug).
        String payload = "Main\"/><activity block=\"true\"><component-filter name=\"victim/x";
        String escaped = ComponentUtils.escapeXml(payload);
        // No raw angle brackets or quotes survive, so the serialized IFW rules
        // cannot gain injected elements/attributes from a hostile rule name.
        assertFalse(escaped.contains("<"));
        assertFalse(escaped.contains(">"));
        assertFalse(escaped.contains("\""));
        assertTrue(escaped.contains("&lt;"));
        assertTrue(escaped.contains("&gt;"));
        assertTrue(escaped.contains("&quot;"));
    }

    @Test
    public void getIFWRulesForPackage() {
        assert classLoader != null;
        Path ifwDir = Paths.get(classLoader.getResource("ifw").getFile());
        HashMap<String, RuleType> expectedHashMap = getExpectedHashMap();
        HashMap<String, RuleType> actualHashMap = ComponentUtils.getIFWRulesForPackage("sample.package", ifwDir);
        assertEquals(expectedHashMap.size(), actualHashMap.size());
        for (String component : expectedHashMap.keySet()) {
            assertEquals(expectedHashMap.get(component), actualHashMap.get(component));
        }
    }

    @Test
    public void readIFWRules() throws IOException {
        assert classLoader != null;
        File ifwFile = new File(classLoader.getResource("ifw").getFile(), "sample.package.xml");
        HashMap<String, RuleType> expectedHashMap = getExpectedHashMap();
        HashMap<String, RuleType> actualHashMap;
        try (InputStream is = new FileInputStream(ifwFile)) {
            actualHashMap = ComponentUtils.readIFWRules(is, "sample.package");
        }
        assertEquals(expectedHashMap.size(), actualHashMap.size());
        for (String component : expectedHashMap.keySet()) {
            assertEquals(expectedHashMap.get(component), actualHashMap.get(component));
        }
    }

    private HashMap<String, RuleType> getExpectedHashMap() {
        return new HashMap<String, RuleType>(7) {
            {
                // Although we have short class names, the reader should return full class names
                put("sample.package.NastyActivity", RuleType.ACTIVITY);
                put("sample.package.ad.AdActivity", RuleType.ACTIVITY);
                put("sample.package.log.CrashLogActivity", RuleType.ACTIVITY);
                put("sample.package.SystemBroadcastReceiver", RuleType.RECEIVER);
                put("sample.package.ThirdPartyReceiver", RuleType.RECEIVER);
                put("sample.package.MalwareService", RuleType.SERVICE);
                put("sample.package.AlwaysRunningLoggingService", RuleType.SERVICE);
            }
        };
    }
}