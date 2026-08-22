// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppChangeFeedStoreTest {
    @Test
    public void malformedJsonReturnsEmptyList() {
        assertTrue(AppChangeFeedStore.parse("not json {").isEmpty());
    }

    @Test
    public void appendCreatesFileFromEmptyStore() throws Exception {
        File file = File.createTempFile("app-change-feed", ".json");
        assertTrue(file.delete());
        AppChangeFeedStore store = new AppChangeFeedStore(file);

        store.append(new AppChangeFeedEntry("components", "com.foo", 100L, "Foo changed", "body"));

        List<AppChangeFeedEntry> out = store.readAll();
        assertEquals(1, out.size());
        assertEquals("com.foo", out.get(0).packageName);
        assertTrue(file.delete());
    }

    @Test
    public void roundTripPreservesNewestFirstEntries() {
        List<AppChangeFeedEntry> in = new ArrayList<>();
        in.add(new AppChangeFeedEntry("components", "com.foo", 100L, "Foo changed", "Components: +1/-0"));
        in.add(new AppChangeFeedEntry("permissions", "com.bar", 50L, "Bar changed", "CAMERA"));

        List<AppChangeFeedEntry> out = AppChangeFeedStore.parse(AppChangeFeedStore.serialize(in));

        assertEquals(2, out.size());
        assertEquals("components", out.get(0).kind);
        assertEquals("com.foo", out.get(0).packageName);
        assertEquals(100L, out.get(0).timestampMillis);
        assertEquals("Foo changed", out.get(0).title);
        assertEquals("Components: +1/-0", out.get(0).body);
    }

    @Test
    public void serializeCapsEntries() {
        List<AppChangeFeedEntry> in = new ArrayList<>();
        for (int i = 0; i < AppChangeFeedStore.MAX_ENTRIES + 5; ++i) {
            in.add(new AppChangeFeedEntry("components", "pkg" + i, i, "title" + i, "body" + i));
        }

        List<AppChangeFeedEntry> out = AppChangeFeedStore.parse(AppChangeFeedStore.serialize(in));

        assertEquals(AppChangeFeedStore.MAX_ENTRIES, out.size());
        assertEquals("pkg0", out.get(0).packageName);
    }

    @Test
    public void importDeduplicatesAndKeepsNewestEntries() throws Exception {
        File file = File.createTempFile("app-change-feed-import", ".json");
        assertTrue(file.delete());
        AppChangeFeedStore store = new AppChangeFeedStore(file);
        AppChangeFeedEntry existing = new AppChangeFeedEntry("update", "com.foo", 100L,
                "Foo", "body", 1L, 2L);
        assertTrue(store.append(existing));

        int imported = store.importEntries(Arrays.asList(existing,
                new AppChangeFeedEntry("update", "com.bar", 200L, "Bar", "body", 2L, 3L)));

        assertEquals(1, imported);
        List<AppChangeFeedEntry> entries = store.readAll();
        assertEquals(2, entries.size());
        assertEquals("com.bar", entries.get(0).packageName);
        assertEquals(2L, entries.get(0).beforeVersionCode);
        assertTrue(file.delete());
    }
}
