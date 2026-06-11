// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.github.muntashirakon.AppManager.R;

public class SelfBatteryOptimizationTest {
    @Test
    public void getDiagnosticsLabelResLabelsUnsupportedState() {
        assertEquals(R.string.self_battery_optimization_diagnostics_unsupported,
                SelfBatteryOptimization.getDiagnosticsLabelRes(false, false, false));
    }

    @Test
    public void getDiagnosticsLabelResLabelsExemptStateBeforeAutoFixState() {
        assertEquals(R.string.self_battery_optimization_diagnostics_exempt,
                SelfBatteryOptimization.getDiagnosticsLabelRes(true, true, true));
    }

    @Test
    public void getDiagnosticsLabelResLabelsRestrictedManualState() {
        assertEquals(R.string.self_battery_optimization_diagnostics_restricted_manual,
                SelfBatteryOptimization.getDiagnosticsLabelRes(true, false, false));
    }

    @Test
    public void getDiagnosticsLabelResLabelsRestrictedAutoFixState() {
        assertEquals(R.string.self_battery_optimization_diagnostics_restricted_autofix,
                SelfBatteryOptimization.getDiagnosticsLabelRes(true, false, true));
    }
}
