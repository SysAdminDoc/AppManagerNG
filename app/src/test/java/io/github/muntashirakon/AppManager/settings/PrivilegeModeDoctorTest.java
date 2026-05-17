// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class PrivilegeModeDoctorTest {
    @Test
    public void buildReportIncludesProbeStatusAndFixHints() {
        Context context = RuntimeEnvironment.getApplication();

        String report = PrivilegeModeDoctor.buildReport(context, Ops.MODE_AUTO, Ops.MODE_NO_ROOT, 10345,
                Arrays.asList(
                        PrivilegeModeDoctor.Probe.pass("Mode selection", "configured=auto", "No action needed."),
                        PrivilegeModeDoctor.Probe.warn("Shizuku binder", "binder=false", "Start Shizuku.")));

        assertTrue(report.contains("AppManagerNG mode doctor"));
        assertTrue(report.contains("Configured mode: auto"));
        assertTrue(report.contains("PASS - Mode selection: configured=auto"));
        assertTrue(report.contains("WARN - Shizuku binder: binder=false"));
        assertTrue(report.contains("Fix: Start Shizuku."));
    }
}
