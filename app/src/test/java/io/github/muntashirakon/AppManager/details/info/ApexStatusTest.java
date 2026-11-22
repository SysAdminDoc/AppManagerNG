// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Issue 42: an APEX package or a Mainline module gets a row of its own.
 */
public class ApexStatusTest {
    @Test
    public void thePackageFlagMarksAnApex() {
        assertEquals(ApexStatus.APEX, ApexStatus.classify(true, "/system/apex/com.android.tzdata.apex", false));
    }

    @Test
    public void aPathInsideApexMarksAnApexOnAnAndroidWithoutTheFlag() {
        assertEquals(ApexStatus.APEX, ApexStatus.classify(false, "/apex/com.android.art/javalib/x.apk", false));
    }

    @Test
    public void aMainlineModuleIsNamedAsOne() {
        assertEquals(ApexStatus.MAINLINE_APEX, ApexStatus.classify(true, "/apex/com.android.media", true));
        assertEquals(ApexStatus.MAINLINE_APK,
                ApexStatus.classify(false, "/system/priv-app/GooglePermissionController/x.apk", true));
    }

    @Test
    public void anOrdinaryAppIsNeither() {
        assertEquals(ApexStatus.REGULAR, ApexStatus.classify(false, "/data/app/~~x/com.example-y/base.apk", false));
        assertEquals(ApexStatus.REGULAR, ApexStatus.classify(false, null, false));
        assertEquals(ApexStatus.REGULAR, ApexStatus.classify(false, "/data/apexdata/x.apk", false));
    }
}
