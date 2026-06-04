// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class MainListTagChipFormatterTest {
    @Test
    public void labelForEmptyTagsReturnsEmptyString() {
        assertEquals("", MainListTagChipFormatter.labelFor(Collections.emptyList()));
    }

    @Test
    public void labelForSingleTagReturnsTag() {
        assertEquals("work", MainListTagChipFormatter.labelFor(Collections.singletonList("work")));
    }

    @Test
    public void labelForMultipleTagsAddsRemainderCount() {
        assertEquals("critical +2", MainListTagChipFormatter.labelFor(
                Arrays.asList("critical", "review", "work")));
    }

    @Test
    public void summaryForTagsJoinsAllTags() {
        assertEquals("critical, review, work", MainListTagChipFormatter.summaryFor(
                Arrays.asList("critical", "review", "work")));
    }
}
