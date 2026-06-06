// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openintents.openpgp.util.OpenPgpApi;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import io.github.muntashirakon.AppManager.settings.Prefs;

@RunWith(RobolectricTestRunner.class)
public class CryptoUtilsTest {
    private static final String OPEN_PGP_PROVIDER = "org.example.openpgp";
    private static final String OTHER_PROVIDER = "org.example.otheropenpgp";

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        Prefs.Encryption.setOpenPgpProvider("");
        Prefs.Encryption.setOpenPgpKeyIds("");
    }

    @Test
    public void openPgpModeRequiresConfiguredKeyIdsAndProviderService() {
        Prefs.Encryption.setOpenPgpKeyIds("123456");
        Prefs.Encryption.setOpenPgpProvider(OPEN_PGP_PROVIDER);

        assertFalse(CryptoUtils.isAvailable(CryptoUtils.MODE_OPEN_PGP));

        addOpenPgpProvider(OPEN_PGP_PROVIDER);

        assertTrue(CryptoUtils.isAvailable(CryptoUtils.MODE_OPEN_PGP));
    }

    @Test
    public void openPgpModeRejectsProviderWithoutKeyIds() {
        Prefs.Encryption.setOpenPgpProvider(OPEN_PGP_PROVIDER);
        addOpenPgpProvider(OPEN_PGP_PROVIDER);

        assertFalse(CryptoUtils.isAvailable(CryptoUtils.MODE_OPEN_PGP));
    }

    @Test
    public void openPgpProviderCheckRequiresConfiguredPackageMatch() {
        addOpenPgpProvider(OTHER_PROVIDER);

        assertFalse(CryptoUtils.isOpenPgpProviderAvailable(mContext, ""));
        assertFalse(CryptoUtils.isOpenPgpProviderAvailable(mContext, OPEN_PGP_PROVIDER));
        assertTrue(CryptoUtils.isOpenPgpProviderAvailable(mContext, OTHER_PROVIDER));
    }

    @SuppressWarnings("deprecation")
    private void addOpenPgpProvider(String providerPackage) {
        Intent intent = new Intent(OpenPgpApi.SERVICE_INTENT_2);
        intent.setPackage(providerPackage);

        ResolveInfo resolveInfo = new ResolveInfo();
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = providerPackage;
        serviceInfo.name = providerPackage + ".OpenPgpService";
        resolveInfo.serviceInfo = serviceInfo;

        Shadows.shadowOf(mContext.getPackageManager()).addResolveInfoForIntent(intent, resolveInfo);
    }
}
