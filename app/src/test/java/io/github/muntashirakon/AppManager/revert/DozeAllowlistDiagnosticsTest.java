// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.revert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class DozeAllowlistDiagnosticsTest {
    @Test
    public void parseKeyValueListHandlesNullAndMalformedEntries() {
        assertTrue(DozeAllowlistDiagnostics.parseKeyValueList(null).isEmpty());
        assertTrue(DozeAllowlistDiagnostics.parseKeyValueList("null").isEmpty());

        Map<String, String> parsed = DozeAllowlistDiagnostics.parseKeyValueList(
                "inactive_to=1800000, malformed, idle_to = 3600000 ");

        assertEquals("1800000", parsed.get("inactive_to"));
        assertEquals("3600000", parsed.get("idle_to"));
        assertEquals(2, parsed.size());
    }

    @Test
    public void buildOneLineDiffReportsChangedDeviceIdleConstants() {
        DozeAllowlistDiagnostics.Snapshot before = snapshot(
                "inactive_to=1800000,idle_to=3600000",
                Collections.emptyMap());
        DozeAllowlistDiagnostics.Snapshot after = snapshot(
                "inactive_to=900000,idle_to=3600000",
                Collections.emptyMap());

        assertEquals("device_idle_constants changed inactive_to: 1800000 -> 900000",
                DozeAllowlistDiagnostics.buildOneLineDiff(before, after));
    }

    @Test
    public void buildOneLineDiffFallsBackToDeviceConfigWhenGlobalIsEmpty() {
        Map<String, String> deviceConfig = new HashMap<>();
        deviceConfig.put("inactive_to", "1800000");
        deviceConfig.put("idle_to", "3600000");
        DozeAllowlistDiagnostics.Snapshot before = snapshot(null, deviceConfig);
        DozeAllowlistDiagnostics.Snapshot after = snapshot(null, deviceConfig);

        assertEquals("device_idle_constants empty; DeviceConfig device_idle unchanged "
                        + "(idle_to=3600000, inactive_to=1800000)",
                DozeAllowlistDiagnostics.buildOneLineDiff(before, after));
    }

    @Test
    public void buildOneLineDiffReportsUnchangedGlobalSummary() {
        DozeAllowlistDiagnostics.Snapshot before = snapshot(
                "inactive_to=1800000,idle_to=3600000",
                Collections.emptyMap());
        DozeAllowlistDiagnostics.Snapshot after = snapshot(
                "inactive_to=1800000,idle_to=3600000",
                Collections.emptyMap());

        assertEquals("device_idle_constants unchanged (idle_to=3600000, inactive_to=1800000)",
                DozeAllowlistDiagnostics.buildOneLineDiff(before, after));
    }

    @Test
    public void buildOneLineDiffReportsDeviceConfigChangesAfterUnchangedGlobal() {
        Map<String, String> beforeDeviceConfig = new HashMap<>();
        beforeDeviceConfig.put("inactive_to", "1800000");
        Map<String, String> afterDeviceConfig = new HashMap<>();
        afterDeviceConfig.put("inactive_to", "900000");

        DozeAllowlistDiagnostics.Snapshot before = snapshot(null, beforeDeviceConfig);
        DozeAllowlistDiagnostics.Snapshot after = snapshot(null, afterDeviceConfig);

        assertEquals("device_idle_constants unchanged (empty); DeviceConfig device_idle changed "
                        + "inactive_to: 1800000 -> 900000",
                DozeAllowlistDiagnostics.buildOneLineDiff(before, after));
    }

    private static DozeAllowlistDiagnostics.Snapshot snapshot(String deviceIdleConstants,
                                                             Map<String, String> deviceConfigValues) {
        return new DozeAllowlistDiagnostics.Snapshot(deviceIdleConstants, deviceConfigValues,
                DozeAllowlistDiagnostics.PACKAGE_KIND_USER, "Google");
    }
}
