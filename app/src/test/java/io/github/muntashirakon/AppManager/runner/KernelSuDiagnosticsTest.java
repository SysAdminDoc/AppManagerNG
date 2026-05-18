// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class KernelSuDiagnosticsTest {
    @Test
    public void parseProbeOutput_collectsSeccompAndSulogDenials() {
        KernelSuDiagnostics.Result result = KernelSuDiagnostics.parseProbeOutput(Arrays.asList(
                "SECCOMP=2",
                "SULOG_STATUS=readable",
                "APP_PROFILE_UID=0",
                "APP_PROFILE_GID=0",
                "APP_PROFILE_GROUPS=0 1000 2000",
                "APP_PROFILE_CONTEXT=u:r:ksu:s0",
                "APP_PROFILE_CAPEFF=0000003fffffffff",
                "APP_PROFILE_ID=uid=0(root) gid=0(root) groups=0(root) context=u:r:ksu:s0",
                "SULOG_LINE=deny uid=2000 name=app_process",
                "SULOG_LINE=avc: denied { read } for pid=123"),
                RootManagerInfo.Source.MARKER);

        assertEquals(KernelSuDiagnostics.State.ACTIVE, result.state);
        assertEquals("2", result.seccompMode);
        assertEquals(KernelSuDiagnostics.SulogState.READABLE, result.sulogState);
        assertEquals(2, result.sulogDenials.size());
        assertEquals("deny uid=2000 name=app_process", result.sulogDenials.get(0));
        assertEquals(KernelSuDiagnostics.AppProfileState.DEFAULT_ROOT, result.appProfile.state);
        assertEquals(0, result.appProfile.uid);
        assertEquals(0, result.appProfile.gid);
        assertEquals(3, result.appProfile.groups.size());
        assertEquals("u:r:ksu:s0", result.appProfile.selinuxContext);
        assertTrue(result.appProfile.missingExpectedCapabilities.isEmpty());
    }

    @Test
    public void parseProbeOutput_usesInjectedPrctlSeccompMode() {
        KernelSuDiagnostics.Result result = KernelSuDiagnostics.parseProbeOutput(Arrays.asList(
                "SULOG_STATUS=missing"),
                RootManagerInfo.Source.MARKER,
                "2");

        assertEquals(KernelSuDiagnostics.State.ACTIVE, result.state);
        assertEquals("2", result.seccompMode);
        assertEquals(KernelSuDiagnostics.SulogState.MISSING, result.sulogState);
    }

    @Test
    public void parseProbeOutput_reportsMissingSeccomp() {
        KernelSuDiagnostics.Result result = KernelSuDiagnostics.parseProbeOutput(
                Collections.singletonList("SULOG_STATUS=readable"),
                RootManagerInfo.Source.MARKER);

        assertEquals(KernelSuDiagnostics.State.UNKNOWN, result.state);
        assertEquals("missing Seccomp", result.error);
    }

    @Test
    public void parseProbeOutput_reportsRestrictedAppProfile() {
        KernelSuDiagnostics.Result result = KernelSuDiagnostics.parseProbeOutput(Arrays.asList(
                "SECCOMP=2",
                "SULOG_STATUS=missing",
                "APP_PROFILE_UID=2000",
                "APP_PROFILE_GID=2000",
                "APP_PROFILE_GROUPS=2000",
                "APP_PROFILE_CONTEXT=u:r:shell:s0",
                "APP_PROFILE_CAPEFF=00000000000000c0"),
                RootManagerInfo.Source.MARKER);

        assertEquals(KernelSuDiagnostics.State.ACTIVE, result.state);
        assertEquals(KernelSuDiagnostics.AppProfileState.RESTRICTED, result.appProfile.state);
        assertEquals(2000, result.appProfile.uid);
        assertEquals(2000, result.appProfile.gid);
        assertTrue(result.appProfile.missingExpectedCapabilities.contains("CAP_SYS_ADMIN"));
        assertTrue(result.appProfile.missingExpectedCapabilities.contains("CAP_DAC_READ_SEARCH"));
    }

    @Test
    public void formatSeccompMode_labelsKnownModes() {
        assertEquals("disabled (0)", KernelSuDiagnostics.formatSeccompMode("0"));
        assertEquals("strict (1)", KernelSuDiagnostics.formatSeccompMode("1"));
        assertEquals("filter (2)", KernelSuDiagnostics.formatSeccompMode("2"));
        assertEquals("unknown", KernelSuDiagnostics.formatSeccompMode("unknown"));
    }
}
