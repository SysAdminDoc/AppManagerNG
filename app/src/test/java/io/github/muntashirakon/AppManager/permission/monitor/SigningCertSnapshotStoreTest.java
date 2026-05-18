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

public class SigningCertSnapshotStoreTest {

    private static final String SHA_A = "AA:11:22:33";
    private static final String SHA_B = "BB:11:22:33";

    @Test
    public void emptyJsonReturnsEmptyMap() {
        Map<String, SigningCertSnapshot> out = SigningCertSnapshotStore.parse("");
        assertTrue(out.isEmpty());
    }

    @Test
    public void malformedJsonReturnsEmptyMap() {
        Map<String, SigningCertSnapshot> out = SigningCertSnapshotStore.parse("not json {");
        assertTrue(out.isEmpty());
    }

    @Test
    public void unknownTopLevelShapeReturnsEmptyMap() {
        Map<String, SigningCertSnapshot> out = SigningCertSnapshotStore.parse("{\"unrelated\":123}");
        assertTrue(out.isEmpty());
    }

    @Test
    public void roundTripPreservesSnapshots() {
        Map<String, SigningCertSnapshot> in = new HashMap<>();
        in.put("com.foo.bar", new SigningCertSnapshot(42, new HashSet<>(Arrays.asList(SHA_A, SHA_B))));
        in.put("com.qux.zoo", new SigningCertSnapshot(7, new HashSet<>(Arrays.asList(SHA_A))));
        String json = SigningCertSnapshotStore.serialize(in);
        Map<String, SigningCertSnapshot> out = SigningCertSnapshotStore.parse(json);
        assertEquals(2, out.size());
        SigningCertSnapshot foo = out.get("com.foo.bar");
        assertNotNull(foo);
        assertEquals(42, foo.versionCode);
        assertEquals(2, foo.certShas256.size());
        assertTrue(foo.certShas256.contains(SHA_A));
        assertTrue(foo.certShas256.contains(SHA_B));
        SigningCertSnapshot qux = out.get("com.qux.zoo");
        assertNotNull(qux);
        assertEquals(7, qux.versionCode);
        assertEquals(1, qux.certShas256.size());
    }

    @Test
    public void roundTripDropsNullCertEntries() {
        String json = "{\"schema_version\":1,\"snapshots\":{"
                + "\"com.foo\":{\"version_code\":1,"
                + "\"cert_shas256\":[null,\"\",\"AA:11\"]}}}";
        Map<String, SigningCertSnapshot> out = SigningCertSnapshotStore.parse(json);
        SigningCertSnapshot foo = out.get("com.foo");
        assertNotNull(foo);
        assertEquals(1, foo.certShas256.size());
        assertTrue(foo.certShas256.contains("AA:11"));
    }

    @Test
    public void missingVersionCodeFallsBackToNegativeOne() {
        String json = "{\"schema_version\":1,\"snapshots\":{"
                + "\"com.foo\":{\"cert_shas256\":[\"AA:11\"]}}}";
        Map<String, SigningCertSnapshot> out = SigningCertSnapshotStore.parse(json);
        SigningCertSnapshot foo = out.get("com.foo");
        assertNotNull(foo);
        assertEquals(-1, foo.versionCode);
    }

    @Test
    public void emptySnapshotsMapSerializesCleanly() {
        String json = SigningCertSnapshotStore.serialize(new HashMap<>());
        Map<String, SigningCertSnapshot> roundTrip = SigningCertSnapshotStore.parse(json);
        assertTrue(roundTrip.isEmpty());
    }

    @Test
    public void unknownPackageReturnsNull() {
        Map<String, SigningCertSnapshot> out = SigningCertSnapshotStore.parse(
                "{\"schema_version\":1,\"snapshots\":{}}");
        assertNull(out.get("com.absent"));
    }

    @Test
    public void sha256HelperProducesColonSeparatedUppercaseHex() {
        // Direct test of SigningCertChangeMonitor.sha256 — exposed @VisibleForTesting.
        // Input: empty bytes -> known SHA-256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        String hex = SigningCertChangeMonitor.sha256(new byte[0]);
        assertNotNull(hex);
        assertEquals("E3:B0:C4:42:98:FC:1C:14:9A:FB:F4:C8:99:6F:B9:24:27:AE:41:E4:64:9B:93:4C:A4:95:99:1B:78:52:B8:55", hex);
    }
}
