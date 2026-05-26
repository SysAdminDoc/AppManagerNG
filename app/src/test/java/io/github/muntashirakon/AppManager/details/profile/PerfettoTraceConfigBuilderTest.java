// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.profile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PerfettoTraceConfigBuilderTest {

    @Test
    public void buildsCanonicalAppTargetedConfig() {
        String proto = PerfettoTraceConfigBuilder.buildTextProto("com.example.app", 5_000L);
        assertTrue("duration_ms must be present", proto.contains("duration_ms: 5000"));
        assertTrue("buffers block must be present", proto.contains("buffers: {"));
        assertTrue("ftrace data source must be present",
                proto.contains("name: \"linux.ftrace\""));
        assertTrue("process_stats data source must be present",
                proto.contains("name: \"linux.process_stats\""));
        assertTrue("atrace must target the package",
                proto.contains("atrace_apps: \"com.example.app\""));
        assertTrue("ring buffer policy", proto.contains("fill_policy: RING_BUFFER"));
    }

    @Test
    public void durationClampsToBounds() {
        assertEquals(PerfettoTraceConfigBuilder.DEFAULT_DURATION_MS,
                PerfettoTraceConfigBuilder.clampDuration(0));
        assertEquals(PerfettoTraceConfigBuilder.DEFAULT_DURATION_MS,
                PerfettoTraceConfigBuilder.clampDuration(-100));
        assertEquals(PerfettoTraceConfigBuilder.MIN_DURATION_MS,
                PerfettoTraceConfigBuilder.clampDuration(100));
        assertEquals(PerfettoTraceConfigBuilder.MAX_DURATION_MS,
                PerfettoTraceConfigBuilder.clampDuration(999_999L));
        assertEquals(7_500L, PerfettoTraceConfigBuilder.clampDuration(7_500L));
    }

    @Test
    public void durationClampReflectsInProto() {
        String proto = PerfettoTraceConfigBuilder.buildTextProto("com.example.app", 999_999L);
        assertTrue(proto.contains("duration_ms: " + PerfettoTraceConfigBuilder.MAX_DURATION_MS));
    }

    @Test
    public void bufferClampsToBounds() {
        assertEquals(1024, PerfettoTraceConfigBuilder.clampBuffer(500));
        assertEquals(262_144, PerfettoTraceConfigBuilder.clampBuffer(500_000));
        assertEquals(32_768, PerfettoTraceConfigBuilder.clampBuffer(32_768));
    }

    @Test
    public void bufferClampReflectsInProto() {
        String proto = PerfettoTraceConfigBuilder.buildTextProto(
                "com.example.app", 5_000L, 500_000);
        assertTrue(proto.contains("size_kb: 262144"));
    }

    @Test
    public void rejectsMalformedPackageName() {
        assertThrows(IllegalArgumentException.class,
                () -> PerfettoTraceConfigBuilder.buildTextProto("", 5_000L));
        assertThrows(IllegalArgumentException.class,
                () -> PerfettoTraceConfigBuilder.buildTextProto("nodot", 5_000L));
        assertThrows(IllegalArgumentException.class,
                () -> PerfettoTraceConfigBuilder.buildTextProto("path/with/slash", 5_000L));
        assertThrows(IllegalArgumentException.class,
                () -> PerfettoTraceConfigBuilder.buildTextProto("a\"b.c", 5_000L));
    }

    @Test
    public void includesExpectedFtraceEvents() {
        String proto = PerfettoTraceConfigBuilder.buildTextProto("com.example.app", 5_000L);
        assertTrue("sched_switch is the minimum useful event",
                proto.contains("ftrace_events: \"sched/sched_switch\""));
        assertTrue("cpu_frequency for power inspection",
                proto.contains("ftrace_events: \"power/cpu_frequency\""));
    }

    @Test
    public void traceConfigContainsNoBlankFtraceLines() {
        String proto = PerfettoTraceConfigBuilder.buildTextProto("com.example.app", 5_000L);
        // Each ftrace_events line must be followed by a non-empty event name.
        for (String line : proto.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("ftrace_events:")) {
                assertTrue("malformed ftrace event line: " + line,
                        trimmed.matches("ftrace_events: \"[^\"]+\""));
            }
        }
    }

    @Test
    public void perfettoCommandBuilderProducesCanonicalArgv() {
        assertArrayEquals(new String[]{
                "perfetto",
                "-c", "/data/local/tmp/perfetto.cfg",
                "--txt",
                "-o", "/sdcard/Download/trace.perfetto-trace"
        }, PerfettoCommandBuilder.buildRecord(
                "/data/local/tmp/perfetto.cfg",
                "/sdcard/Download/trace.perfetto-trace"));
    }

    @Test
    public void perfettoCommandRejectsUnsafePaths() {
        assertThrows(IllegalArgumentException.class,
                () -> PerfettoCommandBuilder.buildRecord(
                        "/tmp/foo;rm -rf .cfg", "/sdcard/trace.perfetto-trace"));
        assertThrows(IllegalArgumentException.class,
                () -> PerfettoCommandBuilder.buildRecord(
                        "/tmp/foo.cfg", "/sdcard/$(rm).perfetto-trace"));
    }

    @Test
    public void perfettoUiUrlIsStable() {
        assertEquals("https://ui.perfetto.dev/", PerfettoCommandBuilder.perfettoUiUrl());
    }
}
