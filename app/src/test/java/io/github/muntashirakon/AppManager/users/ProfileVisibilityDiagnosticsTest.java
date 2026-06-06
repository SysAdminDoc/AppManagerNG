// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProfileVisibilityDiagnosticsTest {
    @Test
    public void privateUserTypeClassifiesAsQuietPrivateProfile() {
        ProfileVisibilityDiagnostics.ProfileFacts facts = ProfileVisibilityDiagnostics.buildFacts(10,
                android.content.pm.UserInfo.FLAG_QUIET_MODE,
                0,
                ProfileVisibilityDiagnostics.USER_TYPE_PROFILE_PRIVATE);

        assertEquals(ProfileVisibilityDiagnostics.ProfileKind.PRIVATE, facts.kind);
        assertTrue(facts.profile);
        assertTrue(facts.quietModeEnabled);
        assertTrue(facts.enabled);
    }

    @Test
    public void managedFlagClassifiesAsWorkProfile() {
        ProfileVisibilityDiagnostics.ProfileFacts facts = ProfileVisibilityDiagnostics.buildFacts(11,
                android.content.pm.UserInfo.FLAG_MANAGED_PROFILE,
                0,
                null);

        assertEquals(ProfileVisibilityDiagnostics.ProfileKind.MANAGED, facts.kind);
        assertTrue(facts.profile);
    }

    @Test
    public void plainSecondaryUserDoesNotBecomeProfile() {
        ProfileVisibilityDiagnostics.ProfileFacts facts = ProfileVisibilityDiagnostics.buildFacts(12,
                0,
                android.content.pm.UserInfo.NO_PROFILE_GROUP_ID,
                null);

        assertEquals(ProfileVisibilityDiagnostics.ProfileKind.USER, facts.kind);
        assertFalse(facts.profile);
    }

    @Test
    public void disabledEphemeralStateIsTracked() {
        ProfileVisibilityDiagnostics.ProfileFacts facts = ProfileVisibilityDiagnostics.buildFacts(13,
                android.content.pm.UserInfo.FLAG_DISABLED | android.content.pm.UserInfo.FLAG_EPHEMERAL,
                android.content.pm.UserInfo.NO_PROFILE_GROUP_ID,
                null);

        assertFalse(facts.enabled);
        assertTrue(facts.ephemeral);
    }

    @Test
    public void hiddenProfileAccessRequiresPermissionAndHomeRoleOnAndroid15() {
        assertEquals(ProfileVisibilityDiagnostics.HiddenProfileAccess.NOT_VISIBLE_FROM_CURRENT_MODE_STATE,
                ProfileVisibilityDiagnostics.getHiddenProfileAccess(35, true, false));
        assertEquals(ProfileVisibilityDiagnostics.HiddenProfileAccess.NOT_VISIBLE_FROM_CURRENT_MODE_STATE,
                ProfileVisibilityDiagnostics.getHiddenProfileAccess(35, false, true));
        assertEquals(ProfileVisibilityDiagnostics.HiddenProfileAccess.AVAILABLE_WHEN_UNLOCKED,
                ProfileVisibilityDiagnostics.getHiddenProfileAccess(35, true, true));
    }

    @Test
    public void hiddenProfileAccessIsNotApplicableBeforeAndroid15() {
        assertEquals(ProfileVisibilityDiagnostics.HiddenProfileAccess.NOT_APPLICABLE,
                ProfileVisibilityDiagnostics.getHiddenProfileAccess(34, false, false));
    }
}
