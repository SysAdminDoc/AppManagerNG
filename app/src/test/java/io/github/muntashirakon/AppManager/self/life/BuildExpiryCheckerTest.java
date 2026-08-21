// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.life;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BuildExpiryCheckerTest {
    @Test
    public void getBuildTypeClassifiesSupportedVersions() {
        assertBuildType(BuildExpiryChecker.BUILD_TYPE_STABLE, "0.6.15");
        assertBuildType(BuildExpiryChecker.BUILD_TYPE_ALPHA, "0.6.15-alpha01");
        assertBuildType(BuildExpiryChecker.BUILD_TYPE_BETA, "0.6.15-beta1");
        assertBuildType(BuildExpiryChecker.BUILD_TYPE_RC, "0.6.15-rc1");
        assertBuildType(BuildExpiryChecker.BUILD_TYPE_RC, "0.6.15-RC12");
    }

    @Test
    public void getBuildTypeFallsBackForUnknownOrMalformedSuffixes() {
        assertBuildType(BuildExpiryChecker.BUILD_TYPE_STABLE, "0.6.15-DEBUG");
        assertBuildType(BuildExpiryChecker.BUILD_TYPE_STABLE, "0.6.15-pre");
        assertBuildType(BuildExpiryChecker.BUILD_TYPE_STABLE, "0.6.15-");
        assertBuildType(BuildExpiryChecker.BUILD_TYPE_STABLE, "0.6.15-a");
        assertBuildType(BuildExpiryChecker.BUILD_TYPE_STABLE, "0.6.15-alpha");
    }

    @Test
    public void debugBuildFlagTakesPrecedenceOverVersionSuffix() {
        assertEquals(BuildExpiryChecker.BUILD_TYPE_DEBUG,
                BuildExpiryChecker.getBuildType("0.6.15-DEBUG", true));
        assertEquals(BuildExpiryChecker.BUILD_TYPE_DEBUG,
                BuildExpiryChecker.getBuildType("0.6.15-rc1", true));
    }

    private static void assertBuildType(int expected, String versionName) {
        assertEquals(expected, BuildExpiryChecker.getBuildType(versionName, false));
    }
}
