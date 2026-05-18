// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.muntashirakon.AppManager.R;

public class DefaultAppRoleBackupHelperTest {
    @Test
    public void sanitizeRoleNamesKeepsSupportedRolesInOrder() {
        assertArrayEquals(new String[]{
                        DefaultAppRoleBackupHelper.ROLE_SMS,
                        DefaultAppRoleBackupHelper.ROLE_DIALER,
                        DefaultAppRoleBackupHelper.ROLE_BROWSER,
                },
                DefaultAppRoleBackupHelper.sanitizeRoleNames(new String[]{
                        DefaultAppRoleBackupHelper.ROLE_SMS,
                        "android.app.role.NOTES",
                        DefaultAppRoleBackupHelper.ROLE_DIALER,
                        DefaultAppRoleBackupHelper.ROLE_SMS,
                        null,
                        "",
                        DefaultAppRoleBackupHelper.ROLE_BROWSER,
                }));
    }

    @Test
    public void supportedRoleFilterOnlyAcceptsRestoreDefaults() {
        assertTrue(DefaultAppRoleBackupHelper.isSupportedRole(DefaultAppRoleBackupHelper.ROLE_DIALER));
        assertTrue(DefaultAppRoleBackupHelper.isSupportedRole(DefaultAppRoleBackupHelper.ROLE_SMS));
        assertTrue(DefaultAppRoleBackupHelper.isSupportedRole(DefaultAppRoleBackupHelper.ROLE_HOME));
        assertTrue(DefaultAppRoleBackupHelper.isSupportedRole(DefaultAppRoleBackupHelper.ROLE_BROWSER));
        assertFalse(DefaultAppRoleBackupHelper.isSupportedRole("android.app.role.NOTES"));
    }

    @Test
    public void roleLabelsMapToExpectedStrings() {
        assertEquals(R.string.default_app_role_phone,
                DefaultAppRoleBackupHelper.getRoleLabelRes(DefaultAppRoleBackupHelper.ROLE_DIALER));
        assertEquals(R.string.default_app_role_sms,
                DefaultAppRoleBackupHelper.getRoleLabelRes(DefaultAppRoleBackupHelper.ROLE_SMS));
        assertEquals(R.string.default_app_role_home,
                DefaultAppRoleBackupHelper.getRoleLabelRes(DefaultAppRoleBackupHelper.ROLE_HOME));
        assertEquals(R.string.default_app_role_browser,
                DefaultAppRoleBackupHelper.getRoleLabelRes(DefaultAppRoleBackupHelper.ROLE_BROWSER));
        assertEquals(R.string.state_unknown,
                DefaultAppRoleBackupHelper.getRoleLabelRes("android.app.role.NOTES"));
    }
}
