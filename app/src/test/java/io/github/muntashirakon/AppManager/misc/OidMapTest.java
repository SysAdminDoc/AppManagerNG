// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class OidMapTest {
    @Test
    public void knownOidsHaveDescriptions() {
        for (String oid : OidMap.getKnownOids()) {
            String description = OidMap.getDescription(oid);
            assertNotNull("Missing description for " + oid, description);
            assertFalse("Empty description for " + oid, description.trim().isEmpty());
        }
    }
}
