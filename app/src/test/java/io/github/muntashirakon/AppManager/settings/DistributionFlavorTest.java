// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.muntashirakon.AppManager.BuildConfig;

public class DistributionFlavorTest {
    @Test
    public void optionalNetworkFeatureFlagMatchesDistributionFlavor() {
        assertTrue("floss".equals(BuildConfig.FLAVOR) || "full".equals(BuildConfig.FLAVOR));

        if ("full".equals(BuildConfig.FLAVOR)) {
            assertTrue(BuildConfig.ALLOW_OPTIONAL_NETWORK_FEATURES);
            assertTrue(FeatureController.areOptionalNetworkFeaturesAvailable());
        } else {
            assertFalse(BuildConfig.ALLOW_OPTIONAL_NETWORK_FEATURES);
            assertFalse(FeatureController.areOptionalNetworkFeaturesAvailable());
        }
    }
}
