// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AppChangeFeedTransferTest {
    @Test
    public void exportRoundTripPreservesVersionContextAndSortsNewestFirst() {
        List<AppChangeFeedEntry> input = Arrays.asList(
                new AppChangeFeedEntry("permissions", "com.old", 10L, "Old", "body"),
                new AppChangeFeedEntry("update", "com.new", 30L, "New", "body", 4L, 5L));

        AppChangeFeedTransfer.ParseResult result = AppChangeFeedTransfer.parse(
                AppChangeFeedTransfer.serialize(input));

        assertTrue(result.isValid());
        assertEquals(2, result.entries.size());
        assertEquals("com.new", result.entries.get(0).packageName);
        assertEquals(4L, result.entries.get(0).beforeVersionCode);
        assertEquals(5L, result.entries.get(0).afterVersionCode);
    }

    @Test
    public void importRejectsUnknownSchemaAndMalformedEntries() {
        AppChangeFeedTransfer.ParseResult unknown = AppChangeFeedTransfer.parse(
                "{\"schema_version\":2,\"entries\":[]}");
        AppChangeFeedTransfer.ParseResult malformed = AppChangeFeedTransfer.parse(
                "{\"schema_version\":1,\"entries\":[{\"kind\":\"update\"}]}" );

        assertFalse(unknown.isValid());
        assertFalse(malformed.isValid());
    }

    @Test
    public void filterMatchesPackageKindAndTimeWithoutExceedingBound() {
        List<AppChangeFeedEntry> input = Arrays.asList(
                new AppChangeFeedEntry("permissions", "com.example.mail", 30L, "Mail", "body"),
                new AppChangeFeedEntry("components", "com.example.maps", 20L, "Maps", "body"),
                new AppChangeFeedEntry("permissions", "com.example.mail", 10L, "Mail", "body"));

        List<AppChangeFeedEntry> result = AppChangeFeedTransfer.filter(
                input, "MAIL", "perm", 15L, 30L);

        assertEquals(1, result.size());
        assertEquals(30L, result.get(0).timestampMillis);
    }
}
