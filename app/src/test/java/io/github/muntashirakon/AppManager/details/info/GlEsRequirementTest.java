// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.pm.FeatureInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Issue 42: the OpenGL ES version an app requires comes from the uses-feature without a name, packed as
 * major in the high half and minor in the low half.
 */
@RunWith(RobolectricTestRunner.class)
public class GlEsRequirementTest {
    @Test
    public void thePackedVersionReadsAsMajorDotMinor() {
        assertEquals("3.2", GlEsRequirement.fromFeatures(new FeatureInfo[]{
                feature("android.hardware.camera", 0), feature(null, 0x00030002)}));
        assertEquals("2.0", GlEsRequirement.fromFeatures(new FeatureInfo[]{feature(null, 0x00020000)}));
    }

    @Test
    public void theHighestOfSeveralWins() {
        assertEquals("3.1", GlEsRequirement.fromFeatures(new FeatureInfo[]{
                feature(null, 0x00020000), feature(null, 0x00030001)}));
    }

    @Test
    public void anAppThatAsksForNoVersionHasNone() {
        assertNull(GlEsRequirement.fromFeatures(null));
        assertNull(GlEsRequirement.fromFeatures(new FeatureInfo[0]));
        assertNull(GlEsRequirement.fromFeatures(new FeatureInfo[]{feature("android.hardware.camera", 0)}));
    }

    @NonNull
    private static FeatureInfo feature(@Nullable String name, int reqGlEsVersion) {
        FeatureInfo info = new FeatureInfo();
        info.name = name;
        info.reqGlEsVersion = reqGlEsVersion;
        return info;
    }
}
