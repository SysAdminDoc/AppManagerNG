// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;

public class LocalServerBootstrapSignatureTest {
    @Test
    public void buildBootstrapSignature_includesDeviceModeAndCauseForFailures() {
        IOException failure = new IOException("handshake timed out\nwaiting for socket",
                new IllegalStateException("binder died"));

        String signature = LocalServer.buildBootstrapSignature("failed", failure, 1234, null, null,
                "Google", "oriole", "oriole", 36, "BP2A.260101.001", "23.2",
                "adb_wifi", 2000, 10345);

        assertTrue(signature.startsWith("LocalServer bootstrap failed: Google/oriole/oriole "
                + "(sdk=36, id=BP2A.260101.001, mode=adb_wifi, uid=2000, appUid=10345) "
                + "[LineageOS 23.2]"));
        assertTrue(signature.contains("IOException: handshake timed out waiting for socket"));
        assertTrue(signature.contains("(caused by IllegalStateException: binder died)"));
        assertTrue(signature.contains("elapsed=1234ms"));
    }

    @Test
    public void buildBootstrapSignature_includesProbeResultForSuccesses() {
        String signature = LocalServer.buildBootstrapSignature("succeeded", null, 42, 0, "2000\n",
                "Samsung", "dm1q", "dm1q", 35, "AP3A.250905.015", "",
                "root", 0, 10345);

        assertTrue(signature.startsWith("LocalServer bootstrap succeeded: Samsung/dm1q/dm1q "
                + "(sdk=35, id=AP3A.250905.015, mode=root, uid=0, appUid=10345)"));
        assertTrue(signature.contains("OK"));
        assertTrue(signature.contains("elapsed=42ms"));
        assertTrue(signature.contains("probeExit=0"));
        assertTrue(signature.contains("probeOutput=2000"));
        assertFalse(signature.contains("LineageOS"));
    }
}
