// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class ComponentSnapshotStoreTest {
    @Test
    public void malformedJsonReturnsEmptyMap() {
        assertTrue(ComponentSnapshotStore.parse("not json {").isEmpty());
    }

    @Test
    public void roundTripPreservesComponentsAndTrackers() {
        Map<String, ComponentSnapshot> in = new HashMap<>();
        in.put("com.foo", ComponentSnapshot.of(42,
                new String[]{"com.foo.MainActivity", "com.foo.AnalyticsService"},
                new String[]{"com.foo.AnalyticsService"}));

        Map<String, ComponentSnapshot> out = ComponentSnapshotStore.parse(ComponentSnapshotStore.serialize(in));

        ComponentSnapshot snapshot = out.get("com.foo");
        assertNotNull(snapshot);
        assertEquals(42, snapshot.versionCode);
        assertTrue(snapshot.components.contains("com.foo.MainActivity"));
        assertTrue(snapshot.components.contains("com.foo.AnalyticsService"));
        assertTrue(snapshot.trackerComponents.contains("com.foo.AnalyticsService"));
    }

    @Test
    public void parserDropsNullAndEmptyArrayEntries() {
        String json = "{\"schema_version\":1,\"snapshots\":{"
                + "\"com.foo\":{\"version_code\":1,"
                + "\"components\":[null,\"\",\"com.foo.Main\"],"
                + "\"tracker_components\":[null,\"\",\"com.foo.Tracker\"]}}}";

        ComponentSnapshot snapshot = ComponentSnapshotStore.parse(json).get("com.foo");

        assertNotNull(snapshot);
        assertEquals(1, snapshot.components.size());
        assertEquals(1, snapshot.trackerComponents.size());
    }
}
