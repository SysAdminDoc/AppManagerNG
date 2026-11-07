// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_A;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_B;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_C;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.app;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import io.github.muntashirakon.AppManager.sync.lan.SyncComparator.Status;

public class SyncComparatorTest {
    private static final String PKG = "com.example.app";

    @Test
    public void bucketsFollowVersionCodesWhenSignersMatch() {
        SyncApp remote = app(PKG, 5, SIGNER_A);
        assertEquals(Status.NOT_INSTALLED, SyncComparator.compare(remote, null));
        assertEquals(Status.NEWER, SyncComparator.compare(remote, app(PKG, 4, SIGNER_A)));
        assertEquals(Status.SAME, SyncComparator.compare(remote, app(PKG, 5, SIGNER_A)));
        assertEquals(Status.OLDER, SyncComparator.compare(remote, app(PKG, 6, SIGNER_A)));
    }

    @Test
    public void versionCodesCompareAsLongs() {
        long high = (5L << 32) | 1;
        SyncApp remote = app(PKG, high, SIGNER_A);
        assertEquals(Status.NEWER, SyncComparator.compare(remote, app(PKG, high - 1, SIGNER_A)));
        assertEquals(Status.OLDER, SyncComparator.compare(remote, app(PKG, high + (1L << 32), SIGNER_A)));
    }

    @Test
    public void signerMismatchWinsOverVersion() {
        SyncApp remote = app(PKG, 99, SIGNER_B);
        assertEquals(Status.SIGNATURE_DIFFERS, SyncComparator.compare(remote, app(PKG, 1, SIGNER_A)));
        assertFalse(SyncComparator.canPull(Status.SIGNATURE_DIFFERS));
    }

    @Test
    public void onlyNewerAndMissingAppsCanBePulled() {
        assertTrue(SyncComparator.canPull(Status.NEWER));
        assertTrue(SyncComparator.canPull(Status.NOT_INSTALLED));
        assertFalse(SyncComparator.canPull(Status.SAME));
        assertFalse(SyncComparator.canPull(Status.OLDER));
        assertFalse(SyncComparator.canPull(Status.SIGNATURE_DIFFERS));
    }

    @Test
    public void rotatedKeyWhoseLineageHoldsTheInstalledKeyMatches() {
        SyncApp rotated = app(PKG, 7, Collections.singletonList(SIGNER_B), Arrays.asList(SIGNER_A, SIGNER_B),
                Collections.singletonList(new SyncApp.ApkFileInfo("base.apk", 1)));
        assertEquals(Status.NEWER, SyncComparator.compare(rotated, app(PKG, 6, SIGNER_A)));
        assertEquals(Status.SIGNATURE_DIFFERS, SyncComparator.compare(rotated, app(PKG, 6, SIGNER_C)));
    }

    @Test
    public void lineageDoesNotApplyToMultipleSigners() {
        assertFalse(SyncComparator.signersMatch(Arrays.asList(SIGNER_A, SIGNER_B), Arrays.asList(SIGNER_A, SIGNER_C),
                Collections.singletonList(SIGNER_C)));
        assertTrue(SyncComparator.signersMatch(Arrays.asList(SIGNER_B, SIGNER_A), Collections.<String>emptyList(),
                Arrays.asList(SIGNER_A, SIGNER_B)));
        assertFalse(SyncComparator.signersMatch(Arrays.asList(SIGNER_A, SIGNER_B), Collections.<String>emptyList(),
                Collections.singletonList(SIGNER_A)));
    }

    @Test
    public void emptySignerSetsNeverMatch() {
        assertFalse(SyncComparator.signersMatch(Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.<String>emptyList()));
        assertFalse(SyncComparator.signersMatch(Collections.<String>emptyList(), Collections.singletonList(SIGNER_A),
                Collections.singletonList(SIGNER_A)));
        assertFalse(SyncComparator.signersMatch(Collections.singletonList(SIGNER_A), Collections.<String>emptyList(),
                Collections.<String>emptyList()));
    }

    @Test
    public void digestCaseDoesNotMatter() {
        assertTrue(SyncComparator.signersMatch(Collections.singletonList(SIGNER_A.toUpperCase(Locale.ROOT)),
                Collections.<String>emptyList(), Collections.singletonList(SIGNER_A)));
    }
}
