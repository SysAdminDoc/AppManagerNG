// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.AppManager.apk.parser.ManifestSdkLibrary;

public class SdkSandboxInfoTest {
    @Test
    public void belowAndroid14DoesNotReportSdkRuntimeDeclarations() {
        SdkSandboxInfo info = SdkSandboxInfo.fromRaw(Build.VERSION_CODES.TIRAMISU,
                Collections.singletonList(SdkSandboxInfo.SdkLibrary.fromRaw(
                        "com.example.sdk", 7, "com.example.sdk.provider")));

        assertFalse(info.isSupported());
        assertFalse(info.hasDeclaredSdkLibraries());
        assertTrue(info.declaredSdkLibraries.isEmpty());
    }

    @Test
    public void android14KeepsDeclaredSdkRuntimeLibraries() {
        SdkSandboxInfo info = SdkSandboxInfo.fromManifest(Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                Arrays.asList(
                        new ManifestSdkLibrary("com.example.ads", 3, "ABCDEF"),
                        new ManifestSdkLibrary("com.example.analytics", -1, null)));

        assertTrue(info.isSupported());
        assertTrue(info.hasDeclaredSdkLibraries());
        assertEquals(2, info.declaredSdkLibraries.size());
    }

    @Test
    public void displayStringIncludesVersionWhenPresent() {
        assertEquals("com.example.ads v42",
                SdkSandboxInfo.SdkLibrary.fromRaw("com.example.ads", 42,
                        "ABCDEF").toDisplayString());
        assertEquals("com.example.analytics",
                SdkSandboxInfo.SdkLibrary.fromRaw("com.example.analytics",
                        SdkSandboxInfo.VERSION_UNDEFINED, null).toDisplayString());
    }
}
