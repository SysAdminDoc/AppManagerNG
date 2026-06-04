// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BackupExtrasCoverageTest {
    @Test
    public void missingCapabilitiesExplainEachSkippedRuleType() {
        BackupExtrasCoverage.RestoreCapabilities capabilities = new BackupExtrasCoverage.RestoreCapabilities(
                false, false, false, false, false, false, false, false);

        assertEquals(R.string.backup_extras_skip_runtime_permissions,
                BackupExtrasCoverage.getSkipReason(RuleType.PERMISSION, capabilities));
        assertEquals(R.string.backup_extras_skip_app_ops,
                BackupExtrasCoverage.getSkipReason(RuleType.APP_OP, capabilities));
        assertEquals(R.string.backup_extras_skip_network_policy,
                BackupExtrasCoverage.getSkipReason(RuleType.NET_POLICY, capabilities));
        assertEquals(R.string.backup_extras_skip_battery,
                BackupExtrasCoverage.getSkipReason(RuleType.BATTERY_OPT, capabilities));
        assertEquals(R.string.backup_extras_skip_magisk,
                BackupExtrasCoverage.getSkipReason(RuleType.MAGISK_HIDE, capabilities));
        assertEquals(R.string.backup_extras_skip_notifications,
                BackupExtrasCoverage.getSkipReason(RuleType.NOTIFICATION, capabilities));
        assertEquals(R.string.backup_extras_skip_uri_grants,
                BackupExtrasCoverage.getSkipReason(RuleType.URI_GRANT, capabilities));
        assertEquals(R.string.backup_extras_skip_ssaid_store,
                BackupExtrasCoverage.getSkipReason(RuleType.SSAID, capabilities));
        assertEquals(0, BackupExtrasCoverage.getSkipReason(RuleType.FREEZE, capabilities));
    }

    @Test
    public void formatDialogDetailsShowsIncludedRestorableAndSkippedGroups() {
        Context context = RuntimeEnvironment.getApplication();
        BackupExtrasCoverage.RestoreCapabilities capabilities = new BackupExtrasCoverage.RestoreCapabilities(
                true, true, false, false, false, false, false, false);

        CharSequence details = BackupExtrasCoverage.formatDialogDetails(context, capabilities);

        assertTrue(details.toString().contains("Includes:"));
        assertTrue(details.toString().contains("runtime permissions"));
        assertTrue(details.toString().contains("Can restore now: runtime permissions, app-op modes"));
        assertTrue(details.toString().contains("Will skip now:"));
        assertTrue(details.toString().contains("network policy control unavailable"));
    }

    @Test
    public void auditWarningNamesRuleAndReason() {
        BackupExtrasCoverage.RestoreCapabilities capabilities = new BackupExtrasCoverage.RestoreCapabilities(
                false, false, false, false, false, false, false, false);
        PermissionRule rule = new PermissionRule("com.example.app", "android.permission.CAMERA", true, 0);

        assertEquals("PERMISSION android.permission.CAMERA: runtime permission grant/revoke unavailable",
                BackupExtrasCoverage.formatAuditWarning(rule, capabilities));
    }
}
