// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;
import android.util.Xml;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;

@RunWith(RobolectricTestRunner.class)
public class SystemConfigTest {
    @Test
    public void shouldAddFeature_allowsNormalFeatureOnLowRamDevice() throws Exception {
        assertTrue(SystemConfig.shouldAddFeature(parser("<feature name=\"android.hardware.camera\" />"), true));
    }

    @Test
    public void shouldAddFeature_blocksNotLowRamFeatureOnLowRamDevice() throws Exception {
        assertFalse(SystemConfig.shouldAddFeature(parser(
                "<feature name=\"android.hardware.vulkan.level\" notLowRam=\"true\" />"), true));
    }

    @Test
    public void shouldAddFeature_allowsNotLowRamFeatureOnNormalDevice() throws Exception {
        assertTrue(SystemConfig.shouldAddFeature(parser(
                "<feature name=\"android.hardware.vulkan.level\" notLowRam=\"true\" />"), false));
    }

    @Test
    public void getRamFeatureName_mapsLowRamStateToPackageFeature() {
        assertEquals(PackageManager.FEATURE_RAM_LOW, SystemConfig.getRamFeatureName(true));
        assertEquals(PackageManager.FEATURE_RAM_NORMAL, SystemConfig.getRamFeatureName(false));
    }

    @Test
    public void addRamFeature_addsLowRamFeature() {
        SystemConfig systemConfig = new SystemConfig(false);

        systemConfig.addRamFeature(true);

        assertTrue(systemConfig.getAvailableFeatures().containsKey(PackageManager.FEATURE_RAM_LOW));
        assertFalse(systemConfig.getAvailableFeatures().containsKey(PackageManager.FEATURE_RAM_NORMAL));
    }

    @Test
    public void addRamFeature_addsNormalRamFeature() {
        SystemConfig systemConfig = new SystemConfig(false);

        systemConfig.addRamFeature(false);

        assertTrue(systemConfig.getAvailableFeatures().containsKey(PackageManager.FEATURE_RAM_NORMAL));
        assertFalse(systemConfig.getAvailableFeatures().containsKey(PackageManager.FEATURE_RAM_LOW));
    }

    private static XmlPullParser parser(String xml) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(xml));
        assertEquals(XmlPullParser.START_TAG, parser.nextTag());
        return parser;
    }
}
