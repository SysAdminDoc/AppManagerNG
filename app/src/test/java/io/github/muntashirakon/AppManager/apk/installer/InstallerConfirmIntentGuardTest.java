// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The confirmation intent reaches us through a mutable {@code PendingIntent}, so whoever can make
 * the broadcast fire also chooses this payload. It must be rejected rather than re-forwarded
 * unless it names a foreign target, and it must never carry the caller's URI grants along.
 */
@RunWith(RobolectricTestRunner.class)
public class InstallerConfirmIntentGuardTest {
    private static final String SELF = "io.github.sysadmindoc.AppManagerNG";
    private static final String INSTALLER = "com.google.android.packageinstaller";

    @Test
    public void aNullPayloadIsRejected() {
        assertNull(InstallerConfirmIntentGuard.sanitize(null, SELF));
    }

    @Test
    public void aFullyImplicitPayloadIsRejected() {
        Intent implicit = new Intent(Intent.ACTION_VIEW);
        assertNull(InstallerConfirmIntentGuard.sanitize(implicit, SELF));
    }

    @Test
    public void aPayloadPointingBackAtUsIsRejected() {
        Intent selfRedirect = new Intent()
                .setComponent(new ComponentName(SELF, SELF + ".settings.SettingsActivity"));
        assertNull(InstallerConfirmIntentGuard.sanitize(selfRedirect, SELF));
    }

    @Test
    public void aPackageScopedPayloadPointingBackAtUsIsRejected() {
        Intent selfRedirect = new Intent(Intent.ACTION_VIEW).setPackage(SELF);
        assertNull(InstallerConfirmIntentGuard.sanitize(selfRedirect, SELF));
    }

    @Test
    public void aComponentTargetedPayloadIsForwarded() {
        Intent confirm = new Intent()
                .setComponent(new ComponentName(INSTALLER, INSTALLER + ".InstallStart"));
        Intent sanitized = InstallerConfirmIntentGuard.sanitize(confirm, SELF);
        assertNotNull(sanitized);
        assertEquals(confirm.getComponent(), sanitized.getComponent());
    }

    @Test
    public void aPackageScopedPayloadIsForwarded() {
        Intent confirm = new Intent(Intent.ACTION_VIEW).setPackage(INSTALLER);
        assertNotNull(InstallerConfirmIntentGuard.sanitize(confirm, SELF));
    }

    @Test
    public void uriGrantsAreStrippedFromTheForwardedPayload() {
        Intent confirm = new Intent(Intent.ACTION_VIEW)
                .setPackage(INSTALLER)
                .setData(Uri.parse("content://io.github.sysadmindoc.AppManagerNG.fm/secret"))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        assertTrue(InstallerConfirmIntentGuard.carriesUriGrants(confirm));

        Intent sanitized = InstallerConfirmIntentGuard.sanitize(confirm, SELF);
        assertNotNull(sanitized);
        assertFalse(InstallerConfirmIntentGuard.carriesUriGrants(sanitized));
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, sanitized.getFlags());
        // The payload itself is preserved: only the caller's flags are dropped.
        assertEquals(confirm.getData(), sanitized.getData());
    }

    @Test
    public void everyCallerChosenFlagIsDropped() {
        Intent confirm = new Intent(Intent.ACTION_VIEW)
                .setPackage(INSTALLER)
                .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT
                        | Intent.FLAG_ACTIVITY_NO_HISTORY
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        Intent sanitized = InstallerConfirmIntentGuard.sanitize(confirm, SELF);
        assertNotNull(sanitized);
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, sanitized.getFlags());
    }

    @Test
    public void theOriginalIntentIsNotMutated() {
        Intent confirm = new Intent(Intent.ACTION_VIEW)
                .setPackage(INSTALLER)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        InstallerConfirmIntentGuard.sanitize(confirm, SELF);
        assertTrue(InstallerConfirmIntentGuard.carriesUriGrants(confirm));
    }
}
