// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.safety;

import static org.junit.Assert.*;

import org.junit.Test;

public class CriticalPackageGuardTest {
    @Test
    public void detectsEnumeratedCriticalPackages() {
        assertTrue(CriticalPackageGuard.isCriticalPackage("android"));
        assertTrue(CriticalPackageGuard.isCriticalPackage("com.android.systemui"));
        assertTrue(CriticalPackageGuard.isCriticalPackage("io.github.sysadmindoc.AppManagerNG"));
    }

    @Test
    public void detectsCriticalPackagePrefixes() {
        assertTrue(CriticalPackageGuard.isCriticalPackage("com.android.server.telecom"));
        assertTrue(CriticalPackageGuard.isCriticalPackage("com.google.android.gms.policy_sidecar"));
    }

    @Test
    public void ignoresOrdinaryApplicationPackage() {
        assertFalse(CriticalPackageGuard.isCriticalPackage("com.example.notes"));
    }
}
