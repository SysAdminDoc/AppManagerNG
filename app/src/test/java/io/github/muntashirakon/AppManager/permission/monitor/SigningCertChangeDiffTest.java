// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SigningCertChangeDiffTest {

    private static final String SHA_A = "AA:11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE";
    private static final String SHA_B = "BB:11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE";
    private static final String SHA_C = "CC:11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE";

    @Test
    public void unchangedReturnsUnchanged() {
        SigningCertSnapshot before = SigningCertSnapshot.of(10, SHA_A);
        SigningCertSnapshot after = SigningCertSnapshot.of(11, SHA_A);
        SigningCertChangeDiff.Result diff = SigningCertChangeDiff.compute("com.foo", before, after);
        assertEquals(SigningCertChangeDiff.Kind.UNCHANGED, diff.kind);
        assertFalse(diff.isInteresting());
        assertTrue(diff.added.isEmpty());
        assertTrue(diff.removed.isEmpty());
    }

    @Test
    public void addedSignerWithExistingPreservedReturnsRotated() {
        // V3 rotation: new signer added, existing signer still present.
        SigningCertSnapshot before = SigningCertSnapshot.of(10, SHA_A);
        SigningCertSnapshot after = SigningCertSnapshot.of(11, SHA_A, SHA_B);
        SigningCertChangeDiff.Result diff = SigningCertChangeDiff.compute("com.foo", before, after);
        assertEquals(SigningCertChangeDiff.Kind.ROTATED_ADDITIVE, diff.kind);
        assertTrue(diff.isInteresting());
        assertEquals(1, diff.added.size());
        assertTrue(diff.added.contains(SHA_B));
        assertTrue(diff.removed.isEmpty());
    }

    @Test
    public void completeKeyChangeReturnsReplaced() {
        // Old signer gone, new signer in its place — the alarming case.
        SigningCertSnapshot before = SigningCertSnapshot.of(10, SHA_A);
        SigningCertSnapshot after = SigningCertSnapshot.of(11, SHA_B);
        SigningCertChangeDiff.Result diff = SigningCertChangeDiff.compute("com.foo", before, after);
        assertEquals(SigningCertChangeDiff.Kind.REPLACED, diff.kind);
        assertTrue(diff.isInteresting());
        assertTrue(diff.added.contains(SHA_B));
        assertTrue(diff.removed.contains(SHA_A));
    }

    @Test
    public void partialOverlapPartialRemovalReturnsReplaced() {
        // The replaced classification is conservative: any removed signer
        // tips the diff into REPLACED, even if other signers stayed.
        SigningCertSnapshot before = SigningCertSnapshot.of(10, SHA_A, SHA_B);
        SigningCertSnapshot after = SigningCertSnapshot.of(11, SHA_A, SHA_C);
        SigningCertChangeDiff.Result diff = SigningCertChangeDiff.compute("com.foo", before, after);
        assertEquals(SigningCertChangeDiff.Kind.REPLACED, diff.kind);
        assertTrue(diff.added.contains(SHA_C));
        assertTrue(diff.removed.contains(SHA_B));
    }

    @Test
    public void diffPreservesMetadata() {
        SigningCertSnapshot before = SigningCertSnapshot.of(10, SHA_A);
        SigningCertSnapshot after = SigningCertSnapshot.of(11, SHA_B);
        SigningCertChangeDiff.Result diff = SigningCertChangeDiff.compute("com.foo.bar", before, after);
        assertEquals("com.foo.bar", diff.packageName);
        assertEquals(10, diff.beforeVersionCode);
        assertEquals(11, diff.afterVersionCode);
    }

    @Test
    public void emptyBothSidesIsUnchanged() {
        // Edge case — package was never observed with a usable signature
        // (e.g., system package with no public signing material). Should not
        // alarm the user.
        SigningCertSnapshot before = SigningCertSnapshot.of(10);
        SigningCertSnapshot after = SigningCertSnapshot.of(11);
        SigningCertChangeDiff.Result diff = SigningCertChangeDiff.compute("com.foo", before, after);
        assertEquals(SigningCertChangeDiff.Kind.UNCHANGED, diff.kind);
        assertFalse(diff.isInteresting());
    }

    @Test
    public void emptyBeforeNonEmptyAfterIsRotated() {
        // Snapshot store had no certs (legacy / first-seen), package now
        // reports one signer. Classify as additive rotation, not REPLACED,
        // so the user doesn't get an alarming notification on a benign
        // first-snapshot-comparison case.
        SigningCertSnapshot before = SigningCertSnapshot.of(10);
        SigningCertSnapshot after = SigningCertSnapshot.of(11, SHA_A);
        SigningCertChangeDiff.Result diff = SigningCertChangeDiff.compute("com.foo", before, after);
        assertEquals(SigningCertChangeDiff.Kind.ROTATED_ADDITIVE, diff.kind);
        assertTrue(diff.added.contains(SHA_A));
        assertTrue(diff.removed.isEmpty());
    }
}
