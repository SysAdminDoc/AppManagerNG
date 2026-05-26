// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;

public class ComponentRulesPreviewTest {
    private static final String PACKAGE_NAME = "example.pkg";

    @Test
    public void summarySeparatesIfwDisabledProviderAndPendingEntries() {
        List<ComponentRule> rules = Arrays.asList(
                new ComponentRule(PACKAGE_NAME, ".Activity", RuleType.ACTIVITY,
                        ComponentRule.COMPONENT_BLOCKED_IFW),
                new ComponentRule(PACKAGE_NAME, ".Receiver", RuleType.RECEIVER,
                        ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE),
                new ComponentRule(PACKAGE_NAME, ".Service", RuleType.SERVICE,
                        ComponentRule.COMPONENT_DISABLED),
                new ComponentRule(PACKAGE_NAME, ".Provider", RuleType.PROVIDER,
                        ComponentRule.COMPONENT_DISABLED),
                new ComponentRule(PACKAGE_NAME, ".Removed", RuleType.ACTIVITY,
                        ComponentRule.COMPONENT_TO_BE_DEFAULTED)
        );

        ComponentRulesPreview.Summary summary = ComponentRulesPreview.summarize(rules);

        assertEquals(4, summary.totalEntries);
        assertEquals(2, summary.ifwEntries);
        assertEquals(1, summary.disabledOnlyEntries);
        assertEquals(1, summary.providerEntries);
        assertEquals(1, summary.pendingEntries);
    }

    @Test
    public void buildIfwXmlRendersOnlyIfwSupportedComponentTypes() {
        List<ComponentRule> rules = Arrays.asList(
                new ComponentRule(PACKAGE_NAME, ".Activity", RuleType.ACTIVITY,
                        ComponentRule.COMPONENT_BLOCKED_IFW),
                new ComponentRule(PACKAGE_NAME, ".Receiver", RuleType.RECEIVER,
                        ComponentRule.COMPONENT_BLOCKED_IFW_DISABLE),
                new ComponentRule(PACKAGE_NAME, ".Service", RuleType.SERVICE,
                        ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW),
                new ComponentRule(PACKAGE_NAME, ".Provider", RuleType.PROVIDER,
                        ComponentRule.COMPONENT_DISABLED),
                new ComponentRule(PACKAGE_NAME, ".DisabledOnly", RuleType.ACTIVITY,
                        ComponentRule.COMPONENT_DISABLED)
        );

        String xml = ComponentRulesPreview.buildIfwXml(PACKAGE_NAME, rules);

        assertTrue(xml.contains("<activity block=\"true\" log=\"false\">"));
        assertTrue(xml.contains("<broadcast block=\"true\" log=\"false\">"));
        assertTrue(xml.contains("<service block=\"true\" log=\"false\">"));
        assertTrue(xml.contains("example.pkg/.Activity"));
        assertTrue(xml.contains("example.pkg/.Receiver"));
        assertTrue(xml.contains("example.pkg/.Service"));
        assertFalse(xml.contains(".Provider"));
        assertFalse(xml.contains(".DisabledOnly"));
    }
}
