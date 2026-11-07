// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ToastHistoryFilterTest {
    private static final String ONE = "com.example.one";
    private static final String TWO = "com.example.two";

    @NonNull
    private static ToastEntry entry(long time, @NonNull String packageName, @NonNull String label) {
        return new ToastEntry(time, packageName, label, "Text " + time);
    }

    private final List<ToastEntry> mEntries = Arrays.asList(
            entry(4L, TWO, "Zebra"),
            entry(3L, ONE, "apple"),
            entry(2L, TWO, "Zebra"),
            entry(1L, ONE, "An old name"));

    @Test
    public void eachAppIsListedOnceByNameWithItsCount() {
        List<ToastHistoryFilter.AppChoice> apps = ToastHistoryFilter.apps(mEntries);

        assertEquals(2, apps.size());
        assertEquals(ONE, apps.get(0).packageName);
        assertEquals(2, apps.get(0).count);
        assertEquals(TWO, apps.get(1).packageName);
        assertEquals(2, apps.get(1).count);
    }

    @Test
    public void theNewestNameIsTheOneShown() {
        assertEquals("apple", ToastHistoryFilter.apps(mEntries).get(0).label);
    }

    @Test
    public void appsWithTheSameNameStayApart() {
        List<ToastHistoryFilter.AppChoice> apps = ToastHistoryFilter.apps(Arrays.asList(
                entry(2L, TWO, "Same"), entry(1L, ONE, "Same")));

        assertEquals(ONE, apps.get(0).packageName);
        assertEquals(TWO, apps.get(1).packageName);
    }

    @Test
    public void noToastsMeansNoApps() {
        assertTrue(ToastHistoryFilter.apps(Collections.emptyList()).isEmpty());
    }

    @Test
    public void aFilterIsKeptWhileItsAppHasToasts() {
        assertEquals(ONE, ToastHistoryFilter.keepIfPresent(ONE, ToastHistoryFilter.apps(mEntries)));
    }

    @Test
    public void aFilterGoesWhenItsAppHasNoToastsLeft() {
        assertNull(ToastHistoryFilter.keepIfPresent(ONE, Collections.emptyList()));
        assertNull(ToastHistoryFilter.keepIfPresent(null, ToastHistoryFilter.apps(mEntries)));
    }

    @Test
    public void copiedTextHasThePreciseTimeTheAppAndTheFullText() {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));

        assertEquals("1970-01-01 00:00:01.234  Example (com.example.one)\nLine one\nLine two",
                new ToastEntry(1_234L, ONE, "Example", "Line one\nLine two").toCopyText(format));
    }

    @Test
    public void theScreensTimeFormatShowsMilliseconds() {
        DateFormat format = ToastEntry.newTimeFormat();
        format.setTimeZone(TimeZone.getTimeZone("UTC"));

        assertTrue(format.format(new java.util.Date(1_234L)).endsWith(":01.234"));
    }

    @Test
    public void copyingSeveralKeepsThemApart() {
        String text = ToastEntry.toCopyText(Arrays.asList(entry(2L, ONE, "One"), entry(1L, TWO, "Two")));

        assertTrue(text.contains("One (com.example.one)\nText 2\n\n"));
        assertTrue(text.endsWith("Two (com.example.two)\nText 1"));
    }
}
