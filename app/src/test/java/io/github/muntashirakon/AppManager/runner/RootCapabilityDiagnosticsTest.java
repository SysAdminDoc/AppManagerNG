// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class RootCapabilityDiagnosticsTest {
    @Test
    public void parseProbeOutput_detectsDroppedNonRootCapabilities() {
        RootCapabilityDiagnostics.Result result = RootCapabilityDiagnostics.parseProbeOutput(Arrays.asList(
                "UID=2000",
                "CapEff:\t0000000000000000"));

        assertEquals(RootCapabilityDiagnostics.State.DROPPED, result.state);
        assertEquals(2000, result.uid);
        assertEquals("0000000000000000", result.capEff);
    }

    @Test
    public void parseProbeOutput_detectsPresentNonRootCapabilities() {
        RootCapabilityDiagnostics.Result result = RootCapabilityDiagnostics.parseProbeOutput(Arrays.asList(
                "UID=1000",
                "CapEff:\t0000000000003000"));

        assertEquals(RootCapabilityDiagnostics.State.PRESENT, result.state);
        assertEquals(1000, result.uid);
        assertEquals("0000000000003000", result.capEff);
    }

    @Test
    public void parseProbeOutput_treatsUidZeroAsRoot() {
        RootCapabilityDiagnostics.Result result = RootCapabilityDiagnostics.parseProbeOutput(Arrays.asList(
                "UID=0",
                "CapEff:\t0000003fffffffff"));

        assertEquals(RootCapabilityDiagnostics.State.ROOT, result.state);
        assertEquals(0, result.uid);
        assertEquals("0000003fffffffff", result.capEff);
    }

    @Test
    public void parseProbeOutput_rejectsMissingFields() {
        RootCapabilityDiagnostics.Result result = RootCapabilityDiagnostics.parseProbeOutput(Collections.singletonList(
                "UID=2000"));

        assertEquals(RootCapabilityDiagnostics.State.UNKNOWN, result.state);
        assertEquals("missing UID or CapEff", result.error);
    }
}
