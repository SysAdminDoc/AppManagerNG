// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.details.info.DomainLinkConflictDetector;

public class DomainLinksOptionTest {
    @Test
    public void matchesConflictAndHostPredicates() {
        DomainLinkConflictDetector.AppDomainClaims target = DomainLinkConflictDetector.claim(
                "com.example.target", "Target", 0, hosts("example.com", 2, "login.example", 1));
        DomainLinkConflictDetector.AppDomainClaims other = DomainLinkConflictDetector.claim(
                "com.example.other", "Other App", 0, hosts("example.com", 1));
        Map<String, Map<String, List<DomainLinkConflictDetector.Conflict>>> conflictIndex =
                DomainLinkConflictDetector.findConflictsByPackageUser(Arrays.asList(target, other));
        Map<String, List<DomainLinkConflictDetector.Conflict>> conflicts = conflictIndex.get(
                DomainLinkConflictDetector.packageUserKey("com.example.target", 0));

        assertTrue(DomainLinksOption.matches(target.hostStates, conflicts, "has_domains", null, null));
        assertTrue(DomainLinksOption.matches(target.hostStates, conflicts, "conflicted", null, null));
        assertTrue(DomainLinksOption.matches(target.hostStates, conflicts, "host_eq", "EXAMPLE.COM", null));
        assertTrue(DomainLinksOption.matches(target.hostStates, conflicts, "host_contains", "login", null));
        assertTrue(DomainLinksOption.matches(target.hostStates, conflicts, "host_regex",
                null, Pattern.compile(".*\\.example")));
        assertTrue(DomainLinksOption.matches(target.hostStates, conflicts, "conflict_package_contains",
                "other app", null));
    }

    @Test
    public void rejectsAppsWithoutDomainsOrConflicts() {
        assertFalse(DomainLinksOption.matches(Collections.emptyMap(), Collections.emptyMap(),
                "has_domains", null, null));
        assertFalse(DomainLinksOption.matches(hosts("solo.example", 1), Collections.emptyMap(),
                "conflicted", null, null));
        assertFalse(DomainLinksOption.matches(hosts("solo.example", 1), Collections.emptyMap(),
                "conflict_package_contains", "other", null));
    }

    private static Map<String, Integer> hosts(Object... pairs) {
        Map<String, Integer> hosts = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            hosts.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return hosts;
    }
}
