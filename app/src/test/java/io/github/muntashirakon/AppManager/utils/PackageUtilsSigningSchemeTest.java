// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PackageUtilsSigningSchemeTest {
    @Test
    public void v1OnlyIsWeak() {
        assertTrue(PackageUtils.isV1SchemeOnlySigning(true, false, false, false, false));
    }

    @Test
    public void v1PlusStrongerSchemeIsNotWeak() {
        assertFalse(PackageUtils.isV1SchemeOnlySigning(true, true, false, false, false));
        assertFalse(PackageUtils.isV1SchemeOnlySigning(true, false, true, false, false));
        assertFalse(PackageUtils.isV1SchemeOnlySigning(true, false, false, true, false));
        assertFalse(PackageUtils.isV1SchemeOnlySigning(true, false, false, false, true));
    }

    @Test
    public void modernSchemesWithoutV1AreNotWeak() {
        assertFalse(PackageUtils.isV1SchemeOnlySigning(false, true, true, false, false));
        assertFalse(PackageUtils.isV1SchemeOnlySigning(false, false, true, true, false));
    }

    @Test
    public void noSchemeIsNotFlaggedWeak() {
        assertFalse(PackageUtils.isV1SchemeOnlySigning(false, false, false, false, false));
    }
}
