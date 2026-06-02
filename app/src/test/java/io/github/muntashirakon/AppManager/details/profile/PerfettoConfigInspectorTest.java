// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PerfettoConfigInspectorTest {

    @Test
    public void formatDurationRendersSubSecondInsteadOfZero() {
        // The MIN_DURATION_MS = 500ms floor used to integer-divide to "0s".
        assertEquals("0.5s", PerfettoConfigInspector.formatDuration(500L));
        assertEquals("10s", PerfettoConfigInspector.formatDuration(10_000L));
        assertEquals("1.5s", PerfettoConfigInspector.formatDuration(1500L));
        assertEquals("1s", PerfettoConfigInspector.formatDuration(1000L));
    }

    private static String sampleConfig() {
        return "duration_ms: 10000\n"
                + "buffers: {\n"
                + "    size_kb: 65536\n"
                + "}\n"
                + "data_sources: {\n"
                + "    config: {\n"
                + "        name: \"linux.ftrace\"\n"
                + "        ftrace_config: {\n"
                + "            atrace_apps: \"com.example\"\n"
                + "            ftrace_events: \"sched/sched_switch\"\n"
                + "            ftrace_events: \"power/cpu_frequency\"\n"
                + "        }\n"
                + "    }\n"
                + "}\n"
                + "data_sources: {\n"
                + "    config: {\n"
                + "        name: \"linux.process_stats\"\n"
                + "    }\n"
                + "}\n";
    }

    @Test
    public void inspectExtractsDurationAndBufferSize() {
        PerfettoConfigInspector.Inspection i = PerfettoConfigInspector.inspect(sampleConfig());
        assertEquals(10_000L, i.durationMillis);
        assertEquals(65_536, i.bufferKb);
    }

    @Test
    public void inspectExtractsTargetPackages() {
        PerfettoConfigInspector.Inspection i = PerfettoConfigInspector.inspect(sampleConfig());
        assertEquals(1, i.targetPackages.size());
        assertEquals("com.example", i.targetPackages.get(0));
    }

    @Test
    public void inspectExtractsAllFtraceEvents() {
        PerfettoConfigInspector.Inspection i = PerfettoConfigInspector.inspect(sampleConfig());
        assertEquals(2, i.ftraceEvents.size());
        assertTrue(i.ftraceEvents.contains("sched/sched_switch"));
        assertTrue(i.ftraceEvents.contains("power/cpu_frequency"));
    }

    @Test
    public void inspectExtractsAllDataSources() {
        PerfettoConfigInspector.Inspection i = PerfettoConfigInspector.inspect(sampleConfig());
        assertEquals(2, i.dataSources.size());
        assertTrue(i.dataSources.contains("linux.ftrace"));
        assertTrue(i.dataSources.contains("linux.process_stats"));
    }

    @Test
    public void inspectionFromActualBuilderRoundTrips() {
        // Treat the actual builder as the input source - the inspector must
        // be able to read whatever the builder writes, otherwise the preview
        // chip in App Details will be empty.
        String cfg = PerfettoTraceConfigBuilder.buildTextProto("com.example.app", 5_000L);
        PerfettoConfigInspector.Inspection i = PerfettoConfigInspector.inspect(cfg);
        assertTrue(i.isValid());
        assertEquals(5_000L, i.durationMillis);
        assertEquals(1, i.targetPackages.size());
        assertEquals("com.example.app", i.targetPackages.get(0));
        assertTrue(i.ftraceEvents.size() > 0);
    }

    @Test
    public void inspectionWithMultipleTargetPackagesIsListedInOrder() {
        String cfg = "duration_ms: 1000\n"
                + "buffers: { size_kb: 1024 }\n"
                + "data_sources: { config: { name: \"linux.ftrace\"\n"
                + "    ftrace_config: {\n"
                + "        atrace_apps: \"com.a\"\n"
                + "        atrace_apps: \"com.b\"\n"
                + "    }\n"
                + "}}\n";
        PerfettoConfigInspector.Inspection i = PerfettoConfigInspector.inspect(cfg);
        assertEquals(2, i.targetPackages.size());
        assertEquals("com.a", i.targetPackages.get(0));
        assertEquals("com.b", i.targetPackages.get(1));
    }

    @Test
    public void emptyInputProducesInvalidInspection() {
        PerfettoConfigInspector.Inspection i = PerfettoConfigInspector.inspect("");
        assertFalse(i.isValid());
        assertEquals(0L, i.durationMillis);
        assertEquals(0, i.bufferKb);
        assertTrue(i.targetPackages.isEmpty());
    }

    @Test
    public void nullInputProducesInvalidInspection() {
        PerfettoConfigInspector.Inspection i = PerfettoConfigInspector.inspect(null);
        assertFalse(i.isValid());
    }

    @Test
    public void inspectionWithMissingTargetIsInvalid() {
        String cfg = "duration_ms: 1000\nbuffers: { size_kb: 1024 }\n";
        PerfettoConfigInspector.Inspection i = PerfettoConfigInspector.inspect(cfg);
        assertFalse(i.isValid());
    }

    @Test
    public void inspectionListsAreImmutable() {
        PerfettoConfigInspector.Inspection i = PerfettoConfigInspector.inspect(sampleConfig());
        try {
            i.targetPackages.add("intruder");
            // Some JDK impls don't throw on the deprecated path; just assert
            // the original list size is preserved.
            assertEquals(1, i.targetPackages.size());
        } catch (UnsupportedOperationException expected) {
            // Acceptable: list is unmodifiable.
        }
    }

    @Test
    public void oneLineSummaryRendersCanonicalShape() {
        String cfg = PerfettoTraceConfigBuilder.buildTextProto("com.example", 5_000L);
        PerfettoConfigInspector.Inspection i = PerfettoConfigInspector.inspect(cfg);
        String summary = PerfettoConfigInspector.oneLineSummary(i);
        assertTrue("expected 5s duration: " + summary, summary.startsWith("5s "));
        assertTrue("expected com.example in summary: " + summary,
                summary.contains("com.example"));
    }

    @Test
    public void oneLineSummaryFallsBackOnInvalid() {
        assertEquals("Invalid trace config",
                PerfettoConfigInspector.oneLineSummary(
                        PerfettoConfigInspector.inspect("")));
    }
}
