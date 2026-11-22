// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.pm.FeatureInfo;

import androidx.annotation.Nullable;

/**
 * The OpenGL ES version an app requires. The manifest's {@code <uses-feature android:glEsVersion>} has no name and
 * packs the version as the major number in the high 16 bits and the minor in the low 16.
 */
public final class GlEsRequirement {
    private GlEsRequirement() {
    }

    /**
     * The version as "3.2", the highest when the manifest asks more than once, or {@code null} when it asks for none.
     */
    @Nullable
    public static String fromFeatures(@Nullable FeatureInfo[] features) {
        if (features == null) {
            return null;
        }
        int highest = 0;
        for (FeatureInfo feature : features) {
            if (feature.name == null && feature.reqGlEsVersion != FeatureInfo.GL_ES_VERSION_UNDEFINED
                    && Integer.compareUnsigned(feature.reqGlEsVersion, highest) > 0) {
                highest = feature.reqGlEsVersion;
            }
        }
        if (highest == 0) {
            return null;
        }
        return ((highest & 0xffff0000) >>> 16) + "." + (highest & 0x0000ffff);
    }
}
