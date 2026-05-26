// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import androidx.annotation.NonNull;

import java.util.Collection;

import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;

public final class ComponentRulesPreview {
    private ComponentRulesPreview() {
    }

    @NonNull
    public static Summary summarize(@NonNull Collection<ComponentRule> rules) {
        Summary summary = new Summary();
        for (ComponentRule rule : rules) {
            if (rule.toBeRemoved()) {
                continue;
            }
            ++summary.totalEntries;
            if (rule.isIfw() && rule.type != RuleType.PROVIDER) {
                ++summary.ifwEntries;
            } else if (rule.type == RuleType.PROVIDER) {
                ++summary.providerEntries;
            } else {
                ++summary.disabledOnlyEntries;
            }
            if (!rule.isApplied()) {
                ++summary.pendingEntries;
            }
        }
        return summary;
    }

    @NonNull
    public static String buildIfwXml(@NonNull String packageName, @NonNull Collection<ComponentRule> rules) {
        StringBuilder activities = new StringBuilder();
        StringBuilder services = new StringBuilder();
        StringBuilder receivers = new StringBuilder();
        for (ComponentRule rule : rules) {
            if (rule.toBeRemoved() || !rule.isIfw() || rule.type == RuleType.PROVIDER) {
                continue;
            }
            String componentFilter = "  <component-filter name=\"" + escapeXml(packageName)
                    + "/" + escapeXml(rule.name) + "\"/>\n";
            switch (rule.type) {
                case ACTIVITY:
                    activities.append(componentFilter);
                    break;
                case RECEIVER:
                    receivers.append(componentFilter);
                    break;
                case SERVICE:
                    services.append(componentFilter);
                    break;
                case PROVIDER:
                default:
                    break;
            }
        }
        return "<rules>\n"
                + wrapIfPresent(ComponentUtils.TAG_ACTIVITY, activities)
                + wrapIfPresent(ComponentUtils.TAG_SERVICE, services)
                + wrapIfPresent(ComponentUtils.TAG_BROADCAST, receivers)
                + "</rules>";
    }

    @NonNull
    private static String wrapIfPresent(@NonNull String tag, @NonNull StringBuilder body) {
        if (body.length() == 0) {
            return "";
        }
        return "<" + tag + " block=\"true\" log=\"false\">\n" + body + "</" + tag + ">\n";
    }

    @NonNull
    private static String escapeXml(@NonNull String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static final class Summary {
        public int totalEntries;
        public int ifwEntries;
        public int disabledOnlyEntries;
        public int providerEntries;
        public int pendingEntries;
    }
}
