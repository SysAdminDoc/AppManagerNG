// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VerifierRefusalTest {
    @Test
    public void playProtectsRefusalIsBlockedNotCancelled() {
        // What the S25 sent after "Harmful app blocked", Got it
        assertEquals(PackageInstallerCompat.STATUS_FAILURE_BLOCKED, VerifierRefusal.classify(
                PackageInstallerCompat.STATUS_FAILURE_ABORTED,
                "INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed for file:///data/app/vmdl43563204.tmp"));
    }

    @Test
    public void aVerifierThatNeverAnsweredIsBlockedToo() {
        assertEquals(PackageInstallerCompat.STATUS_FAILURE_BLOCKED, VerifierRefusal.classify(
                PackageInstallerCompat.STATUS_FAILURE_ABORTED,
                "INSTALL_FAILED_VERIFICATION_TIMEOUT: Verification timed out for file:///data/app/vmdl1.tmp"));
    }

    @Test
    public void aUserCancelStaysACancel() {
        assertEquals(PackageInstallerCompat.STATUS_FAILURE_ABORTED, VerifierRefusal.classify(
                PackageInstallerCompat.STATUS_FAILURE_ABORTED, "INSTALL_FAILED_ABORTED: User rejected permissions"));
        assertEquals(PackageInstallerCompat.STATUS_FAILURE_ABORTED, VerifierRefusal.classify(
                PackageInstallerCompat.STATUS_FAILURE_ABORTED, null));
    }

    @Test
    public void otherStatusesKeepTheirOwn() {
        // A verifier name in a message never turns a different failure into a block
        assertEquals(PackageInstallerCompat.STATUS_FAILURE_INVALID, VerifierRefusal.classify(
                PackageInstallerCompat.STATUS_FAILURE_INVALID, "INSTALL_FAILED_VERIFICATION_FAILURE: odd"));
        assertEquals(PackageInstallerCompat.STATUS_SUCCESS, VerifierRefusal.classify(
                PackageInstallerCompat.STATUS_SUCCESS, null));
    }
}
