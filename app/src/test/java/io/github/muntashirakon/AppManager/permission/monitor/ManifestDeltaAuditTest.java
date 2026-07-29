// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.PermissionInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Covers the security-relevant manifest transitions the App Change Auditor must report, and the
 * schema-2 round trip that makes them persistable.
 */
@RunWith(RobolectricTestRunner.class)
public class ManifestDeltaAuditTest {
    private static final String PKG = "com.example.app";
    private static final String COMPONENT = "com.example.app.ExportedService";
    private static final String PERM = "com.example.app.SECRET";
    private static final String SIGNER_A = "AA:BB";
    private static final String SIGNER_B = "CC:DD";

    // --- components ---------------------------------------------------------------------------

    @Test
    public void aComponentBecomingExportedIsReported() {
        ComponentChangeDiff.Result diff = ComponentChangeDiff.compute(PKG,
                snapshot(1, record(false, true, null)),
                snapshot(2, record(true, true, null)));
        assertEquals(Collections.singleton(COMPONENT), diff.newlyExported);
        assertTrue(diff.isEscalation());
        assertTrue(diff.addedComponents.isEmpty());
    }

    @Test
    public void anExportedComponentBeingEnabledIsReported() {
        ComponentChangeDiff.Result diff = ComponentChangeDiff.compute(PKG,
                snapshot(1, record(true, false, null)),
                snapshot(2, record(true, true, null)));
        assertEquals(Collections.singleton(COMPONENT), diff.newlyExported);
    }

    @Test
    public void aDroppedGuardPermissionIsReported() {
        ComponentChangeDiff.Result diff = ComponentChangeDiff.compute(PKG,
                snapshot(1, record(true, true, PERM)),
                snapshot(2, record(true, true, null)));
        assertEquals(Collections.singleton(COMPONENT), diff.weakenedGuards);
        assertTrue(diff.isEscalation());
    }

    @Test
    public void aSwappedGuardPermissionIsReported() {
        ComponentChangeDiff.Result diff = ComponentChangeDiff.compute(PKG,
                snapshot(1, record(true, true, PERM)),
                snapshot(2, record(true, true, "com.example.app.OTHER")));
        assertEquals(Collections.singleton(COMPONENT), diff.weakenedGuards);
    }

    @Test
    public void anUnchangedComponentIsNotReported() {
        ComponentChangeDiff.Result diff = ComponentChangeDiff.compute(PKG,
                snapshot(1, record(true, true, PERM)),
                snapshot(2, record(true, true, PERM)));
        assertFalse(diff.isInteresting());
        assertFalse(diff.isEscalation());
    }

    @Test
    public void aGuardAddedToAnInternalComponentIsNotAnEscalation() {
        ComponentChangeDiff.Result diff = ComponentChangeDiff.compute(PKG,
                snapshot(1, record(false, true, null)),
                snapshot(2, record(false, true, PERM)));
        assertFalse(diff.isEscalation());
    }

    @Test
    public void componentRecordsSurviveTheStoreRoundTrip() {
        Map<String, ComponentSnapshot> all = new HashMap<>();
        all.put(PKG, snapshot(7, new ComponentRecord(ComponentRecord.TYPE_PROVIDER, true, false, PERM)));
        Map<String, ComponentSnapshot> parsed = ComponentSnapshotStore.parse(
                ComponentSnapshotStore.serialize(all));
        ComponentRecord record = parsed.get(PKG).records.get(COMPONENT);
        assertEquals(ComponentRecord.TYPE_PROVIDER, record.type);
        assertTrue(record.exported);
        assertFalse(record.enabled);
        assertEquals(PERM, record.permission);
        assertEquals(7, parsed.get(PKG).versionCode);
    }

    @Test
    public void aVersionOneComponentStoreIsDiscardedSoItReprimesInsteadOfAlerting() {
        String legacy = "{\"schema_version\":1,\"snapshots\":{\"" + PKG + "\":{\"version_code\":1,"
                + "\"components\":[\"" + COMPONENT + "\"],\"tracker_components\":[]}}}";
        assertTrue(ComponentSnapshotStore.parse(legacy).isEmpty());
    }

    // --- permissions --------------------------------------------------------------------------

    @Test
    public void aNewNonDangerousRequestIsReportedSeparately() {
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute(PKG,
                permissions(1, set(), set("android.permission.INTERNET"), declared()),
                permissions(2, set(), set("android.permission.INTERNET", PERM), declared()));
        assertEquals(Collections.singleton(PERM), diff.newlyRequested);
        assertTrue(diff.isEscalation());
        assertFalse(diff.isInteresting());
    }

    @Test
    public void aNewDangerousRequestIsNotDoubleCounted() {
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute(PKG,
                permissions(1, set(), set(), declared()),
                permissions(2, set("android.permission.CAMERA"),
                        set("android.permission.CAMERA"), declared()));
        assertEquals(Collections.singleton("android.permission.CAMERA"), diff.added);
        assertTrue(diff.newlyRequested.isEmpty());
    }

    @Test
    public void aLoweredProtectionLevelIsReported() {
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute(PKG,
                permissions(1, set(), set(), declared(PermissionInfo.PROTECTION_SIGNATURE, SIGNER_A)),
                permissions(2, set(), set(), declared(PermissionInfo.PROTECTION_NORMAL, SIGNER_A)));
        assertEquals(Collections.singleton(PERM), diff.weakenedDeclarations);
        assertTrue(diff.isEscalation());
    }

    @Test
    public void aRaisedProtectionLevelIsNotReported() {
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute(PKG,
                permissions(1, set(), set(), declared(PermissionInfo.PROTECTION_NORMAL, SIGNER_A)),
                permissions(2, set(), set(), declared(PermissionInfo.PROTECTION_SIGNATURE, SIGNER_A)));
        assertTrue(diff.weakenedDeclarations.isEmpty());
    }

    @Test
    public void anUnknownProtectionLevelIsNeverAWeakening() {
        assertFalse(PermissionChangeDiff.isWeakerThan(-1, PermissionInfo.PROTECTION_SIGNATURE));
        assertFalse(PermissionChangeDiff.isWeakerThan(PermissionInfo.PROTECTION_NORMAL, -1));
    }

    @Test
    public void aNameClaimedByAnUnrelatedSignerIsReported() {
        Map<String, DeclaredPermission> foreign = new TreeMap<>();
        foreign.put(PERM, new DeclaredPermission(PERM, PermissionInfo.PROTECTION_SIGNATURE, SIGNER_B));
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute(PKG,
                permissions(1, set(), set(), declared()),
                permissions(2, set(), set(), declared(PermissionInfo.PROTECTION_SIGNATURE, SIGNER_A)),
                foreign, set());
        assertEquals(Collections.singleton(PERM), diff.contestedOwnership);
        assertEquals(Collections.singleton(PERM), diff.newlyDeclared);
    }

    @Test
    public void theSameSignerClaimingTheNameIsNotContested() {
        Map<String, DeclaredPermission> foreign = new TreeMap<>();
        foreign.put(PERM, new DeclaredPermission(PERM, PermissionInfo.PROTECTION_SIGNATURE, SIGNER_A));
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute(PKG,
                permissions(1, set(), set(), declared()),
                permissions(2, set(), set(), declared(PermissionInfo.PROTECTION_SIGNATURE, SIGNER_A)),
                foreign, set());
        assertTrue(diff.contestedOwnership.isEmpty());
    }

    @Test
    public void anUnknownSignerIsNeverReportedAsContested() {
        Map<String, DeclaredPermission> foreign = new TreeMap<>();
        foreign.put(PERM, new DeclaredPermission(PERM, PermissionInfo.PROTECTION_SIGNATURE, null));
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute(PKG,
                permissions(1, set(), set(), declared()),
                permissions(2, set(), set(), declared(PermissionInfo.PROTECTION_SIGNATURE, SIGNER_A)),
                foreign, set());
        assertTrue(diff.contestedOwnership.isEmpty());
    }

    @Test
    public void aRequestNobodyDeclaresIsReportedAsOrphaned() {
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute(PKG,
                permissions(1, set(), set(), declared()),
                permissions(2, set(), set(PERM, "android.permission.INTERNET"), declared()),
                Collections.emptyMap(), set("android.permission.INTERNET"));
        assertEquals(Collections.singleton(PERM), diff.orphanedRequests);
    }

    @Test
    public void theOrphanCheckIsSkippedWhenTheDeclaredUniverseIsUnknown() {
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute(PKG,
                permissions(1, set(), set(), declared()),
                permissions(2, set(), set(PERM), declared()),
                Collections.emptyMap(), set());
        assertTrue(diff.orphanedRequests.isEmpty());
    }

    @Test
    public void aPackageIsNotOrphanedByItsOwnDeclaration() {
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute(PKG,
                permissions(1, set(), set(), declared()),
                permissions(2, set(), set(PERM),
                        declared(PermissionInfo.PROTECTION_SIGNATURE, SIGNER_A)),
                Collections.emptyMap(), set("android.permission.INTERNET"));
        assertTrue(diff.orphanedRequests.isEmpty());
    }

    @Test
    public void permissionRecordsSurviveTheStoreRoundTrip() {
        Map<String, PermissionSnapshot> all = new HashMap<>();
        all.put(PKG, permissions(9, set("android.permission.CAMERA"),
                set("android.permission.CAMERA", PERM),
                declared(PermissionInfo.PROTECTION_SIGNATURE, SIGNER_A)));
        Map<String, PermissionSnapshot> parsed = PermissionSnapshotStore.parse(
                PermissionSnapshotStore.serialize(all));
        PermissionSnapshot snapshot = parsed.get(PKG);
        assertEquals(9, snapshot.versionCode);
        assertEquals(set("android.permission.CAMERA"), snapshot.dangerousPermissions);
        assertEquals(set("android.permission.CAMERA", PERM), snapshot.requestedPermissions);
        DeclaredPermission declared = snapshot.declaredPermissions.get(PERM);
        assertEquals(PermissionInfo.PROTECTION_SIGNATURE, declared.protectionLevel);
        assertEquals(SIGNER_A, declared.ownerSigner);
    }

    @Test
    public void aVersionOnePermissionStoreIsDiscardedSoItReprimesInsteadOfAlerting() {
        String legacy = "{\"schema_version\":1,\"snapshots\":{\"" + PKG + "\":{\"version_code\":1,"
                + "\"dangerous_perms\":[\"android.permission.CAMERA\"]}}}";
        assertTrue(PermissionSnapshotStore.parse(legacy).isEmpty());
    }

    // --- helpers ------------------------------------------------------------------------------

    private static ComponentRecord record(boolean exported, boolean enabled, String permission) {
        return new ComponentRecord(ComponentRecord.TYPE_SERVICE, exported, enabled, permission);
    }

    private static ComponentSnapshot snapshot(long versionCode, ComponentRecord record) {
        Map<String, ComponentRecord> records = new TreeMap<>();
        records.put(COMPONENT, record);
        return new ComponentSnapshot(versionCode, records, Collections.emptySet());
    }

    private static PermissionSnapshot permissions(long versionCode, Set<String> dangerous,
                                                  Set<String> requested,
                                                  Map<String, DeclaredPermission> declared) {
        return new PermissionSnapshot(versionCode, dangerous, requested, declared);
    }

    private static Map<String, DeclaredPermission> declared() {
        return Collections.emptyMap();
    }

    private static Map<String, DeclaredPermission> declared(int protection, String signer) {
        Map<String, DeclaredPermission> out = new TreeMap<>();
        out.put(PERM, new DeclaredPermission(PERM, protection, signer));
        return out;
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    @SuppressWarnings("unused")
    private static Set<String> emptyHashSet() {
        return new HashSet<>();
    }
}
