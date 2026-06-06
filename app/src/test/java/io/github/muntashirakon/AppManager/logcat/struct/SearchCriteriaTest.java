// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SearchCriteriaTest {
    @Test
    public void messageSearchMatchesLogOutput() {
        LogLine logLine = new LogLine("raw");
        logLine.setTag("OtherTag");
        logLine.setLogOutput("payload contains needle");

        assertTrue(new SearchCriteria("needle").matches(logLine));
    }

    @Test
    public void messageSearchRejectsAbsentLogOutput() {
        LogLine logLine = new LogLine("raw");
        logLine.setTag("OtherTag");
        logLine.setLogOutput("payload");

        assertFalse(new SearchCriteria("needle").matches(logLine));
    }

    @Test
    public void overflowPidFilterIsEmpty() {
        SearchCriteria searchCriteria = new SearchCriteria("pid:999999999999");

        assertTrue(searchCriteria.isEmpty());
    }

    @Test
    public void overflowUidFilterIsEmpty() {
        SearchCriteria searchCriteria = new SearchCriteria("uid:999999999999");

        assertTrue(searchCriteria.isEmpty());
    }
}
