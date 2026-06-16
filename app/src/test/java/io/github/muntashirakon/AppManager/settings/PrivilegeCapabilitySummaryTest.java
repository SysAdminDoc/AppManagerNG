// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class PrivilegeCapabilitySummaryTest {
    @Test
    public void fullPowerSummaryHasNoLimitedCapabilities() {
        Context context = RuntimeEnvironment.getApplication();

        String summary = PrivilegeCapabilitySummary.buildSummary(context,
                new PrivilegeCapabilitySummary.Snapshot(
                        true, true, true, true, true, true, true, true, true)).toString();

        assertTrue(summary.contains(context.getString(R.string.privilege_health_capability_feature_install)));
        assertTrue(summary.contains("Limited: " + context.getString(R.string.none)));
        assertTrue(summary.contains(context.getString(R.string.privilege_health_capability_next_full_power)));
    }

    @Test
    public void rootOnlyGapsGetRootRemediation() {
        Context context = RuntimeEnvironment.getApplication();

        String summary = PrivilegeCapabilitySummary.buildSummary(context,
                new PrivilegeCapabilitySummary.Snapshot(
                        true, true, true, true, true, true, true, false, false)).toString();

        assertTrue(summary.contains(context.getString(
                R.string.privilege_health_capability_feature_private_data)));
        assertTrue(summary.contains(context.getString(
                R.string.privilege_health_capability_feature_ifw_rules)));
        assertTrue(summary.contains(context.getString(R.string.privilege_health_capability_next_root)));
        assertFalse(summary.contains(context.getString(R.string.privilege_health_capability_next_reconnect)));
    }

    @Test
    public void partialPrivilegeWithRootOnlyCoverageGetsReconnectRemediation() {
        Context context = RuntimeEnvironment.getApplication();

        String summary = PrivilegeCapabilitySummary.buildSummary(context,
                new PrivilegeCapabilitySummary.Snapshot(
                        true, false, true, true, true, true, true, true, true)).toString();

        assertTrue(summary.contains(context.getString(
                R.string.privilege_health_capability_feature_app_ops)));
        assertTrue(summary.contains(context.getString(R.string.privilege_health_capability_next_reconnect)));
        assertFalse(summary.contains(context.getString(R.string.privilege_health_capability_next_root)));
    }
}
