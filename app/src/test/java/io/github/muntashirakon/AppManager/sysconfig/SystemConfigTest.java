// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    private static XmlPullParser parser(String xml) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(xml));
        assertEquals(XmlPullParser.START_TAG, parser.nextTag());
        return parser;
    }
}
