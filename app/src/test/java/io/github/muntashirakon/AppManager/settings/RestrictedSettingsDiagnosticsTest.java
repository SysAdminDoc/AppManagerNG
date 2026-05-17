// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;

public class RestrictedSettingsDiagnosticsTest {
    @Test
    public void classifySkipsDevicesBeforeAndroid13() {
        assertEquals(RestrictedSettingsDiagnostics.STATUS_NOT_APPLICABLE,
                RestrictedSettingsDiagnostics.classify(Build.VERSION_CODES.S_V2, null));
        assertEquals(RestrictedSettingsDiagnostics.STATUS_NOT_APPLICABLE,
                RestrictedSettingsDiagnostics.classify(Build.VERSION_CODES.S_V2, "com.android.packageinstaller"));
    }

    @Test
    public void classifyTreatsUnknownSourceAsUnknownOnAndroid13Plus() {
        assertEquals(RestrictedSettingsDiagnostics.STATUS_UNKNOWN_SOURCE,
                RestrictedSettingsDiagnostics.classify(Build.VERSION_CODES.TIRAMISU, null));
        assertEquals(RestrictedSettingsDiagnostics.STATUS_UNKNOWN_SOURCE,
                RestrictedSettingsDiagnostics.classify(Build.VERSION_CODES.TIRAMISU, ""));
    }

    @Test
    public void classifySeparatesTrustedStoresAndSideloadSources() {
        assertEquals(RestrictedSettingsDiagnostics.STATUS_TRUSTED_STORE,
                RestrictedSettingsDiagnostics.classify(Build.VERSION_CODES.TIRAMISU, "com.android.vending"));
        assertEquals(RestrictedSettingsDiagnostics.STATUS_LIKELY_RESTRICTED,
                RestrictedSettingsDiagnostics.classify(Build.VERSION_CODES.TIRAMISU, "com.android.packageinstaller"));
        assertEquals(RestrictedSettingsDiagnostics.STATUS_LIKELY_RESTRICTED,
                RestrictedSettingsDiagnostics.classify(Build.VERSION_CODES.TIRAMISU, "com.android.chrome"));
    }

    @Test
    public void classifyRecommendsReviewForUnrecognizedInstallers() {
        assertEquals(RestrictedSettingsDiagnostics.STATUS_REVIEW_RECOMMENDED,
                RestrictedSettingsDiagnostics.classify(Build.VERSION_CODES.TIRAMISU, "org.fdroid.fdroid"));
        assertFalse(RestrictedSettingsDiagnostics.isTrustedStoreSource("org.fdroid.fdroid"));
        assertFalse(RestrictedSettingsDiagnostics.isLikelySideloadSource("org.fdroid.fdroid"));
        assertTrue(RestrictedSettingsDiagnostics.isLikelySideloadSource("com.google.android.apps.nbu.files"));
    }
}
