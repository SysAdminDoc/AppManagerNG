// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import io.github.muntashirakon.AppManager.rules.RuleType;

@RunWith(RobolectricTestRunner.class)
public class ReadIFWRulesTest {
    private static HashMap<String, RuleType> parse(String xml) {
        return ComponentUtils.readIFWRules(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "com.example");
    }

    @Test
    public void parsesWellFormedRules() {
        HashMap<String, RuleType> rules = parse(
                "<rules>"
                        + "<activity block=\"true\" log=\"false\">"
                        + "<component-filter name=\"com.example/.MainActivity\"/>"
                        + "</activity>"
                        + "<service block=\"true\" log=\"false\">"
                        + "<component-filter name=\"com.example/.MyService\"/>"
                        + "</service>"
                        + "</rules>");
        // ComponentName.unflattenFromString expands a leading-dot class against the package.
        assertEquals(RuleType.ACTIVITY, rules.get("com.example.MainActivity"));
        assertEquals(RuleType.SERVICE, rules.get("com.example.MyService"));
    }

    @Test
    public void orphanComponentFilterWithNoEnclosingTagIsSkippedNotNull() {
        // A <component-filter> with no recognized activity/broadcast/service parent leaves the
        // component type null. It must be skipped, not stored as a null RuleType (which would NPE
        // later in ComponentRule.flattenToString), and must not abort parsing of valid siblings.
        HashMap<String, RuleType> rules = parse(
                "<rules>"
                        + "<component-filter name=\"com.example/.OrphanActivity\"/>"
                        + "<activity block=\"true\" log=\"false\">"
                        + "<component-filter name=\"com.example/.RealActivity\"/>"
                        + "</activity>"
                        + "</rules>");
        assertFalse("orphan filter must not be stored", rules.containsKey("com.example.OrphanActivity"));
        assertNull(rules.get("com.example.OrphanActivity"));
        assertEquals("valid sibling must still parse", RuleType.ACTIVITY, rules.get("com.example.RealActivity"));
        for (RuleType type : rules.values()) {
            assertNotNull("no null RuleType may be stored", type);
        }
    }

    @Test
    public void componentsForOtherPackagesAreIgnored() {
        HashMap<String, RuleType> rules = parse(
                "<rules>"
                        + "<activity block=\"true\" log=\"false\">"
                        + "<component-filter name=\"com.other/.Foreign\"/>"
                        + "</activity>"
                        + "</rules>");
        assertTrue(rules.isEmpty());
    }
}
