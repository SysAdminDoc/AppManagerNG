// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DomainLinkConflictDetectorTest {
    @Test
    public void findConflictsGroupsByNormalizedHostAndUser() {
        DomainLinkConflictDetector.AppDomainClaims target = DomainLinkConflictDetector.claim(
                "com.example.target", "Target", 0, hosts("Example.COM.", 2, "alone.example", 1));
        DomainLinkConflictDetector.AppDomainClaims other = DomainLinkConflictDetector.claim(
                "com.example.other", "Other", 0, hosts("example.com", 1));
        DomainLinkConflictDetector.AppDomainClaims otherUser = DomainLinkConflictDetector.claim(
                "com.example.work", "Work", 10, hosts("example.com", 1));

        Map<String, Map<String, List<DomainLinkConflictDetector.Conflict>>> conflicts =
                DomainLinkConflictDetector.findConflictsByPackageUser(Arrays.asList(target, other, otherUser));

        Map<String, List<DomainLinkConflictDetector.Conflict>> targetConflicts = conflicts.get(
                DomainLinkConflictDetector.packageUserKey("com.example.target", 0));
        assertEquals(1, targetConflicts.size());
        assertTrue(targetConflicts.containsKey("example.com"));
        assertEquals("com.example.other", targetConflicts.get("example.com").get(0).packageName);
        assertFalse(targetConflicts.containsKey("alone.example"));
        assertFalse(conflicts.containsKey(DomainLinkConflictDetector.packageUserKey("com.example.work", 10)));
    }

    @Test
    public void packageWithoutSharedHostHasNoConflicts() {
        DomainLinkConflictDetector.AppDomainClaims first = DomainLinkConflictDetector.claim(
                "com.example.first", "First", 0, hosts("first.example", 1));
        DomainLinkConflictDetector.AppDomainClaims second = DomainLinkConflictDetector.claim(
                "com.example.second", "Second", 0, hosts("second.example", 1));

        Map<String, Map<String, List<DomainLinkConflictDetector.Conflict>>> conflicts =
                DomainLinkConflictDetector.findConflictsByPackageUser(Arrays.asList(first, second));

        assertTrue(conflicts.isEmpty());
    }

    private static Map<String, Integer> hosts(Object... pairs) {
        Map<String, Integer> hosts = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            hosts.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return hosts;
    }
}
