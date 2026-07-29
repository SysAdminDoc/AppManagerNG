// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The gate has exactly two definite answers and the detector must not manufacture a third. The
 * dangerous direction is reporting ALLOWED for an app it could not actually query — that would
 * tell a user their app is fine when it is silently blocked.
 */
@RunWith(RobolectricTestRunner.class)
public class RestrictedSettingsDetectorTest {
    @Test
    public void onlyAnAllowedModeMeansTheUserLiftedTheGate() {
        assertEquals(RestrictedSettingsDetector.Status.ALLOWED,
                RestrictedSettingsDetector.classify(Build.VERSION_CODES.TIRAMISU, AppOpsManager.MODE_ALLOWED));
    }

    @Test
    public void theSideloadDefaultReadsAsRestricted() {
        // MODE_ERRORED is what the platform sets for an app installed outside a store.
        assertEquals(RestrictedSettingsDetector.Status.RESTRICTED,
                RestrictedSettingsDetector.classify(Build.VERSION_CODES.TIRAMISU, AppOpsManager.MODE_ERRORED));
    }

    @Test
    public void everyOtherBlockingModeAlsoReadsAsRestricted() {
        for (int mode : new int[]{AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_DEFAULT}) {
            assertEquals("mode " + mode, RestrictedSettingsDetector.Status.RESTRICTED,
                    RestrictedSettingsDetector.classify(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, mode));
        }
    }

    @Test
    public void anUnrecognisedModeIsNeverFoldedIntoADefiniteAnswer() {
        assertEquals(RestrictedSettingsDetector.Status.UNKNOWN,
                RestrictedSettingsDetector.classify(Build.VERSION_CODES.TIRAMISU, 9999));
        assertEquals(RestrictedSettingsDetector.Status.UNKNOWN,
                RestrictedSettingsDetector.classify(Build.VERSION_CODES.TIRAMISU, -1));
    }

    @Test
    public void aPlatformWithoutTheGateIsNeverDescribedAsRestricted() {
        for (int sdk : new int[]{Build.VERSION_CODES.LOLLIPOP, Build.VERSION_CODES.S,
                Build.VERSION_CODES.S_V2}) {
            assertEquals("sdk " + sdk, RestrictedSettingsDetector.Status.UNSUPPORTED,
                    RestrictedSettingsDetector.classify(sdk, AppOpsManager.MODE_ERRORED));
        }
    }

    @Test
    public void theGateIsRecognisedFromAndroid13Onwards() {
        assertEquals(RestrictedSettingsDetector.Status.RESTRICTED,
                RestrictedSettingsDetector.classify(Build.VERSION_CODES.TIRAMISU, AppOpsManager.MODE_ERRORED));
        assertEquals(RestrictedSettingsDetector.Status.ALLOWED,
                RestrictedSettingsDetector.classify(36, AppOpsManager.MODE_ALLOWED));
    }

    @Test
    public void supportTracksThePlatformAndQueryabilityTracksTheOp() {
        assertEquals(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                RestrictedSettingsDetector.isSupported());
        if (!RestrictedSettingsDetector.isSupported()) {
            assertFalse("an unsupported platform can never be queryable",
                    RestrictedSettingsDetector.isQueryable());
        }
    }

    @Test
    public void anUnqueryableGateReportsUnknownRatherThanAllowed() {
        if (RestrictedSettingsDetector.isQueryable()) {
            // The op resolved on this host; the query path is exercised on-device instead.
            return;
        }
        RestrictedSettingsDetector.Status status =
                RestrictedSettingsDetector.getStatus(10000, "com.example.app");
        assertTrue("never claim the gate is lifted when it cannot be read",
                status == RestrictedSettingsDetector.Status.UNKNOWN
                        || status == RestrictedSettingsDetector.Status.UNSUPPORTED);
    }

    @Test
    public void theOpIsIdentifiedByNameNotByANumberThatMoves() {
        assertEquals("android:access_restricted_settings",
                RestrictedSettingsDetector.OPSTR_ACCESS_RESTRICTED_SETTINGS);
    }
}
