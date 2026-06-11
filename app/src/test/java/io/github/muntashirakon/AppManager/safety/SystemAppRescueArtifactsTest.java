// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.safety;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.types.UserPackagePair;

public class SystemAppRescueArtifactsTest {
    @Test
    public void buildInstallExistingScriptIncludesAdbCommandsPerUser() {
        String script = SystemAppRescueArtifacts.buildInstallExistingScript(Arrays.asList(
                new UserPackagePair("com.android.phone", 0),
                new UserPackagePair("com.samsung.android.location", 10)));

        assertTrue(script.contains("adb shell cmd package install-existing --user 0 com.android.phone"));
        assertTrue(script.contains("adb shell cmd package install-existing --user 10 com.samsung.android.location"));
    }

    @Test
    public void buildInstallExistingScriptSkipsUnsafePackageNames() {
        String script = SystemAppRescueArtifacts.buildInstallExistingScript(Arrays.asList(
                new UserPackagePair("com.example.good", 0),
                new UserPackagePair("com.example.bad;reboot", 0)));

        assertTrue(script.contains("adb shell cmd package install-existing --user 0 com.example.good"));
        assertTrue(script.contains("# Skipped unsafe package name for user 0: com.example.bad;reboot"));
        assertFalse(script.contains("adb shell cmd package install-existing --user 0 com.example.bad;reboot"));
    }
}
