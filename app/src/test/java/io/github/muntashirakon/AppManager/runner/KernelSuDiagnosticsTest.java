// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class KernelSuDiagnosticsTest {
    @Test
    public void parseProbeOutput_collectsSeccompAndSulogDenials() {
        KernelSuDiagnostics.Result result = KernelSuDiagnostics.parseProbeOutput(Arrays.asList(
                "SECCOMP=2",
                "SULOG_STATUS=readable",
                "SULOG_LINE=deny uid=2000 name=app_process",
                "SULOG_LINE=avc: denied { read } for pid=123"),
                RootManagerInfo.Source.MARKER);

        assertEquals(KernelSuDiagnostics.State.ACTIVE, result.state);
        assertEquals("2", result.seccompMode);
        assertEquals(KernelSuDiagnostics.SulogState.READABLE, result.sulogState);
        assertEquals(2, result.sulogDenials.size());
        assertEquals("deny uid=2000 name=app_process", result.sulogDenials.get(0));
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
    public void formatSeccompMode_labelsKnownModes() {
        assertEquals("disabled (0)", KernelSuDiagnostics.formatSeccompMode("0"));
        assertEquals("strict (1)", KernelSuDiagnostics.formatSeccompMode("1"));
        assertEquals("filter (2)", KernelSuDiagnostics.formatSeccompMode("2"));
        assertEquals("unknown", KernelSuDiagnostics.formatSeccompMode("unknown"));
    }
}
