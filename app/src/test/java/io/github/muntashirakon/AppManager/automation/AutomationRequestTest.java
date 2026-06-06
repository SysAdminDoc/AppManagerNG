// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.profiles.ProfileApplierReceiver;

@RunWith(RobolectricTestRunner.class)
public class AutomationRequestTest {
    @Test
    public void parsesPublicFreezeUri() throws Exception {
        AutomationRequest request = AutomationRequest.fromUri(Uri.parse(
                "am://freeze/com.example.app?user=10&dry_run=1"));

        assertNotNull(request);
        assertEquals(AutomationIntents.ACTION_FREEZE, request.action);
        assertEquals("com.example.app", request.packages.get(0));
        assertEquals(Integer.valueOf(10), request.users.get(0));
        assertTrue(request.dryRun);
    }

    @Test
    public void parsesProfileUriWithTargetPackageAndBackupOverrides() throws Exception {
        AutomationRequest request = AutomationRequest.fromUri(Uri.parse(
                "am://profile/nightly/run?state=off&package=com.example.app"
                        + "&backup_flags=123&backup_name=tasker"));

        assertNotNull(request);
        assertEquals(AutomationIntents.ACTION_RUN_PROFILE, request.action);
        assertEquals("nightly", request.profileId);
        assertEquals("off", request.profileState);
        assertNotNull(request.profileOverrides);
        assertEquals("com.example.app", request.profileOverrides.getJSONArray("packages").getString(0));
        assertEquals(123, request.profileOverrides.getJSONObject("backup_data").getInt("flags"));
        assertEquals("tasker", request.profileOverrides.getJSONObject("backup_data").getString("name"));
    }

    @Test
    public void parsesTaskerStyleActivityIntentExtras() throws Exception {
        Intent intent = new Intent(AutomationIntents.ACTION_BACKUP)
                .putExtra(AutomationIntents.EXTRA_PACKAGE, "com.example.app")
                .putExtra(AutomationIntents.EXTRA_USER, 10)
                .putExtra(AutomationIntents.EXTRA_BACKUP_NAME, "manual")
                .putExtra(AutomationIntents.EXTRA_BACKUP_FLAGS, "7");

        AutomationRequest request = AutomationRequest.fromIntent(intent);

        assertNotNull(request);
        assertEquals(AutomationIntents.ACTION_BACKUP, request.action);
        assertEquals("com.example.app", request.packages.get(0));
        assertEquals(Integer.valueOf(10), request.users.get(0));
        assertEquals("manual", request.backupName);
        assertEquals(7, request.backupFlags);
        assertTrue(request.hasBackupFlags);
        assertFalse(request.dryRun);
    }

    @Test
    public void mapsProfileOnlyBackupIntentToProfileRun() throws Exception {
        Intent intent = new Intent(AutomationIntents.ACTION_BACKUP)
                .putExtra(AutomationIntents.EXTRA_PROFILE_ID, "nightly")
                .putExtra(AutomationIntents.EXTRA_PROFILE_STATE, "on");

        AutomationRequest request = AutomationRequest.fromIntent(intent);

        assertNotNull(request);
        assertEquals(AutomationIntents.ACTION_RUN_PROFILE, request.action);
        assertEquals("nightly", request.profileId);
        assertEquals("on", request.profileState);
    }

    @Test
    public void mapsExtraPkgProfileIntentToPackageOverride() throws Exception {
        Intent intent = new Intent(AutomationIntents.ACTION_RUN_PROFILE)
                .putExtra(AutomationIntents.EXTRA_PROFILE_ID, "nightly")
                .putExtra(ProfileApplierReceiver.EXTRA_RUNTIME_PACKAGE, "com.example.dynamic");

        AutomationRequest request = AutomationRequest.fromIntent(intent);

        assertNotNull(request);
        assertEquals(AutomationIntents.ACTION_RUN_PROFILE, request.action);
        assertEquals("nightly", request.profileId);
        assertNotNull(request.profileOverrides);
        assertEquals("com.example.dynamic",
                request.profileOverrides.getJSONArray("packages").getString(0));
    }

    @Test
    public void rejectsInvalidPackage() {
        assertThrows(IllegalArgumentException.class, () ->
                AutomationRequest.fromUri(Uri.parse("am://freeze/not a package")));
    }

    @Test
    public void rejectsNegativeUriUser() {
        assertThrows(IllegalArgumentException.class, () ->
                AutomationRequest.fromUri(Uri.parse("am://freeze/com.example.app?user=-1")));
    }

    @Test
    public void rejectsNegativeIntentUser() {
        Intent intent = new Intent(AutomationIntents.ACTION_FREEZE)
                .putExtra(AutomationIntents.EXTRA_PACKAGE, "com.example.app")
                .putExtra(AutomationIntents.EXTRA_USER, -1);

        assertThrows(IllegalArgumentException.class, () -> AutomationRequest.fromIntent(intent));
    }

    @Test
    public void rejectsNegativeIntentUserListEntry() {
        Intent intent = new Intent(AutomationIntents.ACTION_FREEZE)
                .putExtra(AutomationIntents.EXTRA_PACKAGE, "com.example.app")
                .putExtra(AutomationIntents.EXTRA_USERS, "-1");

        assertThrows(IllegalArgumentException.class, () -> AutomationRequest.fromIntent(intent));
    }
}
