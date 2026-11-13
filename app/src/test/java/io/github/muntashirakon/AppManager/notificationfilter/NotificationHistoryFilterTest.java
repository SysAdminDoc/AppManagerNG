// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.misc.TextQuery;

public class NotificationHistoryFilterTest {
    private static final String CHAT = "com.example.chat";
    private static final String SHOP = "com.example.shop";

    private static final List<NotificationHistoryEntry> ENTRIES = Arrays.asList(
            entry(CHAT, "Chat", "Carol", "Running late, order 1234 is fine", "Carol", "SILENT"),
            entry(SHOP, "Shop", "Order 5678 shipped", "Arrives Friday", "Shop", "BLOCK"),
            entry(CHAT, "Chat", "Book club", "Chapter 3 tonight", "Dana", "VIBRATE"),
            entry(SHOP, "Shop", "Flash sale", "Shoes at half price", "Shop", NotificationHistoryEntry.ACTION_NONE));

    @NonNull
    private static NotificationHistoryEntry entry(@NonNull String packageName, @NonNull String label,
                                                  @NonNull String title, @NonNull String text,
                                                  @NonNull String sender, @NonNull String action) {
        return new NotificationHistoryEntry(1L, packageName, label, new NotificationFields(title, text, sender),
                action, null, false);
    }

    @NonNull
    private static List<String> titles(@NonNull List<NotificationHistoryEntry> entries) {
        List<String> titles = new ArrayList<>();
        for (NotificationHistoryEntry entry : entries) {
            titles.add(entry.title);
        }
        return titles;
    }

    @NonNull
    private static List<String> filter(@Nullable String packageName, @Nullable String action,
                                       @NonNull TextQuery query) {
        return titles(NotificationHistoryFilter.filter(ENTRIES, packageName, action, query));
    }

    @Test
    public void noFilterGivesTheSameList() {
        assertSame(ENTRIES, NotificationHistoryFilter.filter(ENTRIES, null, null, TextQuery.empty()));
    }

    @Test
    public void plainSearchLooksAtTitleTextSenderAppAndPackage() {
        assertEquals(Arrays.asList("Carol"), filter(null, null, TextQuery.of("running LATE", false)));
        assertEquals(Arrays.asList("Book club"), filter(null, null, TextQuery.of("dana", false)));
        assertEquals(Arrays.asList("Order 5678 shipped", "Flash sale"),
                filter(null, null, TextQuery.of("example.shop", false)));
        assertEquals(Arrays.asList("Carol", "Book club"), filter(null, null, TextQuery.of("chat", false)));
    }

    @Test
    public void regexSearchIsCaseInsensitive() {
        assertEquals(Arrays.asList("Carol", "Order 5678 shipped"),
                filter(null, null, TextQuery.of("ORDER \\d{4}", true)));
        assertEquals(Arrays.asList("Order 5678 shipped"), filter(null, null, TextQuery.of("^order", true)));
    }

    @Test
    public void brokenRegexShowsNothing() {
        assertTrue(filter(null, null, TextQuery.of("(order", true)).isEmpty());
    }

    @Test
    public void appAndActionNarrowTheSearch() {
        assertEquals(Arrays.asList("Carol", "Book club"), filter(CHAT, null, TextQuery.empty()));
        assertEquals(Arrays.asList("Order 5678 shipped"), filter(null, "BLOCK", TextQuery.empty()));
        assertEquals(Arrays.asList("Flash sale"),
                filter(SHOP, NotificationHistoryEntry.ACTION_NONE, TextQuery.empty()));
        assertTrue(filter(CHAT, "BLOCK", TextQuery.empty()).isEmpty());
        assertEquals(Arrays.asList("Book club"), filter(CHAT, null, TextQuery.of("chapter", false)));
    }

    @Test
    public void appsAreListedOnceByNameWithTheirCounts() {
        List<NotificationHistoryFilter.AppChoice> apps = NotificationHistoryFilter.apps(ENTRIES);

        assertEquals(2, apps.size());
        assertEquals(CHAT, apps.get(0).packageName);
        assertEquals("Chat", apps.get(0).label);
        assertEquals(2, apps.get(0).count);
        assertEquals(SHOP, apps.get(1).packageName);
        assertEquals(2, apps.get(1).count);
    }

    @Test
    public void appFilterFallsAwayOnceTheAppHasNothingLeft() {
        List<NotificationHistoryFilter.AppChoice> apps = NotificationHistoryFilter.apps(ENTRIES.subList(0, 1));

        assertEquals(CHAT, NotificationHistoryFilter.keepIfPresent(CHAT, apps));
        assertNull(NotificationHistoryFilter.keepIfPresent(SHOP, apps));
        assertNull(NotificationHistoryFilter.keepIfPresent(null, apps));
    }
}
