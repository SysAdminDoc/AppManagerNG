// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class PermissionSnapshotStoreTest {

    @Test
    public void emptyJsonReturnsEmptyMap() {
        Map<String, PermissionSnapshot> out = PermissionSnapshotStore.parse("");
        assertTrue(out.isEmpty());
    }

    @Test
    public void malformedJsonReturnsEmptyMap() {
        Map<String, PermissionSnapshot> out = PermissionSnapshotStore.parse("not json {");
        assertTrue(out.isEmpty());
    }

    @Test
    public void unknownTopLevelShapeReturnsEmptyMap() {
        Map<String, PermissionSnapshot> out = PermissionSnapshotStore.parse(
                "{\"unrelated\":123}");
        assertTrue(out.isEmpty());
    }

    @Test
    public void roundTripPreservesSnapshots() {
        Map<String, PermissionSnapshot> in = new HashMap<>();
        in.put("com.foo.bar", new PermissionSnapshot(42, new HashSet<>(Arrays.asList(
                "android.permission.CAMERA", "android.permission.RECORD_AUDIO"))));
        in.put("com.qux.zoo", new PermissionSnapshot(7, new HashSet<>(Arrays.asList(
                "android.permission.LOCATION"))));
        String json = PermissionSnapshotStore.serialize(in);
        Map<String, PermissionSnapshot> out = PermissionSnapshotStore.parse(json);
        assertEquals(2, out.size());
        PermissionSnapshot foo = out.get("com.foo.bar");
        assertNotNull(foo);
        assertEquals(42, foo.versionCode);
        assertEquals(2, foo.dangerousPermissions.size());
        assertTrue(foo.dangerousPermissions.contains("android.permission.CAMERA"));
        assertTrue(foo.dangerousPermissions.contains("android.permission.RECORD_AUDIO"));
        PermissionSnapshot qux = out.get("com.qux.zoo");
        assertNotNull(qux);
        assertEquals(7, qux.versionCode);
        assertEquals(1, qux.dangerousPermissions.size());
    }

    @Test
    public void roundTripDropsNullPermissionEntries() {
        // Hand-crafted JSON with a null perm in the array — serializer would
        // never write one, but the parser tolerates pathological input.
        String json = "{\"schema_version\":1,\"snapshots\":{"
                + "\"com.foo\":{\"version_code\":1,"
                + "\"dangerous_perms\":[null,\"\",\"android.permission.CAMERA\"]}}}";
        Map<String, PermissionSnapshot> out = PermissionSnapshotStore.parse(json);
        PermissionSnapshot foo = out.get("com.foo");
        assertNotNull(foo);
        assertEquals(1, foo.dangerousPermissions.size());
    }

    @Test
    public void missingVersionCodeFallsBackToNegativeOne() {
        // A snapshot file written by an older schema variant might omit
        // version_code. The parser must still surface the row so the diff
        // works on the next update.
        String json = "{\"schema_version\":1,\"snapshots\":{"
                + "\"com.foo\":{\"dangerous_perms\":[\"android.permission.CAMERA\"]}}}";
        Map<String, PermissionSnapshot> out = PermissionSnapshotStore.parse(json);
        PermissionSnapshot foo = out.get("com.foo");
        assertNotNull(foo);
        assertEquals(-1, foo.versionCode);
        assertTrue(foo.dangerousPermissions.contains("android.permission.CAMERA"));
    }

    @Test
    public void emptySnapshotsMapSerializesCleanly() {
        String json = PermissionSnapshotStore.serialize(new HashMap<>());
        Map<String, PermissionSnapshot> roundTrip = PermissionSnapshotStore.parse(json);
        assertTrue(roundTrip.isEmpty());
    }

    @Test
    public void unknownPackageReturnsNull() {
        Map<String, PermissionSnapshot> out = PermissionSnapshotStore.parse(
                "{\"schema_version\":1,\"snapshots\":{}}");
        assertNull(out.get("com.absent"));
    }
}
