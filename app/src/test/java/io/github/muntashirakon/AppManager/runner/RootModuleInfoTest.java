// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class RootModuleInfoTest {
    @Test
    public void parseProbeOutputReadsMagiskAndLsposedModules() {
        RootModuleInfo.Result result = RootModuleInfo.parseProbeOutput(Arrays.asList(
                "__AM_MODULE__",
                "source=magisk",
                "path=/data/adb/modules/zygisk_lsposed/module.prop",
                "status=active",
                "id=zygisk_lsposed",
                "name=Zygisk - LSPosed",
                "version=1.9.3",
                "versionCode=7195",
                "author=LSPosed Developers",
                "description=LSPosed framework",
                "__AM_MODULE__",
                "source=lsposed",
                "path=/data/adb/lspd/modules/example/module.prop",
                "status=disabled",
                "id=example",
                "name=Example Xposed module"));

        assertEquals(RootModuleInfo.State.ACTIVE, result.state);
        assertEquals(2, result.modules.size());
        assertEquals("zygisk_lsposed", result.modules.get(0).id);
        assertEquals("Zygisk - LSPosed", result.modules.get(0).name);
        assertEquals("Magisk/MMRL", result.modules.get(0).source);
        assertEquals("active", result.modules.get(0).status);
        assertEquals("Example Xposed module", result.modules.get(1).name);
        assertEquals("LSPosed", result.modules.get(1).source);
        assertEquals("disabled", result.modules.get(1).status);
    }

    @Test
    public void parseProbeOutputFallsBackToPathSegmentWhenIdIsMissing() {
        RootModuleInfo.Result result = RootModuleInfo.parseProbeOutput(Arrays.asList(
                "__AM_MODULE__",
                "source=magisk",
                "path=/data/adb/modules/sui/module.prop",
                "name=",
                "version=13.5.4"));

        assertEquals(RootModuleInfo.State.ACTIVE, result.state);
        assertEquals("sui", result.modules.get(0).id);
        assertEquals("sui", result.modules.get(0).name);
        assertEquals("13.5.4", result.modules.get(0).version);
        assertNull(result.modules.get(0).author);
    }

    @Test
    public void parseProbeOutputReportsEmptyWhenNoModulesAreEmitted() {
        RootModuleInfo.Result result = RootModuleInfo.parseProbeOutput(Collections.emptyList());

        assertEquals(RootModuleInfo.State.EMPTY, result.state);
        assertEquals(0, result.modules.size());
    }

    @Test
    public void formatForDisplayKeepsReadOnlyMetadata() {
        RootModuleInfo.Result result = RootModuleInfo.parseProbeOutput(Arrays.asList(
                "__AM_MODULE__",
                "source=magisk",
                "path=/data/adb/modules/example/module.prop",
                "status=remove-pending",
                "id=example",
                "name=Example",
                "version=2.0",
                "versionCode=20",
                "author=Maintainer",
                "description=Read-only module details"));

        assertEquals("Example 2.0 (Magisk/MMRL, remove-pending)\n"
                        + "Author: Maintainer\n"
                        + "Version code: 20\n"
                        + "Read-only module details\n"
                        + "/data/adb/modules/example/module.prop",
                RootModuleInfo.formatForDisplay(result.modules));
    }
}
