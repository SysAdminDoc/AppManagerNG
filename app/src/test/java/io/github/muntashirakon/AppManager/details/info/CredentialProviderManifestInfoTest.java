// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.AppManager.apk.parser.ManifestComponent;

public class CredentialProviderManifestInfoTest {
    @Test
    public void belowAndroid14DoesNotReportCredentialProviderServices() {
        CredentialProviderManifestInfo info = CredentialProviderManifestInfo.fromRaw(
                Build.VERSION_CODES.TIRAMISU,
                null,
                null);

        assertFalse(info.isSupported());
        assertFalse(info.hasProviderServices());
        assertTrue(info.providerServices.isEmpty());
    }

    @Test
    public void detectsProviderAndSystemProviderServiceActions() {
        CredentialProviderManifestInfo info = CredentialProviderManifestInfo.fromRawRecords(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                Arrays.asList(
                        serviceRecord("com.example", ".Provider", ManifestComponent.TYPE_SERVICE,
                                CredentialProviderManifestInfo.SERVICE_INTERFACE,
                                CredentialProviderManifestInfo.BIND_PERMISSION),
                        serviceRecord("com.example", ".SystemProvider", ManifestComponent.TYPE_SERVICE,
                                CredentialProviderManifestInfo.SYSTEM_SERVICE_INTERFACE,
                                CredentialProviderManifestInfo.BIND_PERMISSION)));

        assertTrue(info.isSupported());
        assertTrue(info.hasProviderServices());
        assertEquals(2, info.providerServices.size());
        assertEquals(1, info.getSystemProviderServiceCount());
        assertTrue(info.providerServices.get(0).hasRequiredBindPermission);
    }

    @Test
    public void flagsProviderServicesMissingRequiredBindPermission() {
        CredentialProviderManifestInfo info = CredentialProviderManifestInfo.fromRawRecords(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                Collections.singletonList(serviceRecord("com.example", ".Provider",
                        ManifestComponent.TYPE_SERVICE, CredentialProviderManifestInfo.SERVICE_INTERFACE, null)));

        assertEquals(1, info.providerServices.size());
        assertFalse(info.providerServices.get(0).hasRequiredBindPermission);
    }

    @Test
    public void ignoresNonServiceComponentsAndUnrelatedActions() {
        CredentialProviderManifestInfo info = CredentialProviderManifestInfo.fromRawRecords(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                Collections.singletonList(serviceRecord("com.example", ".Activity",
                        ManifestComponent.TYPE_ACTIVITY, "android.intent.action.VIEW", null)));

        assertFalse(info.hasProviderServices());
    }

    private static CredentialProviderManifestInfo.ServiceRecord serviceRecord(String packageName, String className,
                                                                             String type, String action,
                                                                             String permission) {
        return CredentialProviderManifestInfo.ServiceRecord.fromRaw(packageName, className, type,
                Collections.singletonList(action), permission);
    }
}
