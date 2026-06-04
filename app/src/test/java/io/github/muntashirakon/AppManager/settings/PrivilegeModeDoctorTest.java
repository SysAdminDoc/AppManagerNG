// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void reportKeepsStructuredFixTargetsForProbeRows() {
        Context context = RuntimeEnvironment.getApplication();
        PrivilegeModeDoctor.Probe shizuku = PrivilegeModeDoctor.Probe.warn("Shizuku binder",
                "binder=false", "Start Shizuku.", PrivilegeModeDoctor.FixTarget.SHIZUKU_SETTINGS);
        PrivilegeModeDoctor.Report report = new PrivilegeModeDoctor.Report(context, Ops.MODE_SHIZUKU,
                Ops.MODE_NO_ROOT, 10345, Arrays.asList(shizuku));

        assertEquals(1, report.probes.size());
        assertEquals(PrivilegeModeDoctor.FixTarget.SHIZUKU_SETTINGS, report.probes.get(0).fixTarget);
        assertTrue(report.text.contains("WARN - Shizuku binder: binder=false"));
        assertTrue(PrivilegeModeDoctor.buildSupportPreamble(report.text)
                .startsWith("Mode Doctor probe\n=================\nAppManagerNG mode doctor"));
    }
}
