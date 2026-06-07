// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.activity;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TrackerWindowTest {
    @Test
    public void formatClipboardLabelFlattensAndDefusesControlText() {
        assertEquals("' =Label", TrackerWindow.formatClipboardLabel("\t=Label"));
        assertEquals("text", TrackerWindow.formatClipboardLabel("\n\t"));
    }

    @Test
    public void formatClipboardContentPreservesLinesAndDefusesEachLine() {
        assertEquals("Activity\n'@WEBSERVICE(\"http://evil/\")\nplain line",
                TrackerWindow.formatClipboardContent(
                        "Activity\n@WEBSERVICE(\"http://evil/\")\nplain\rline"));
    }
}
