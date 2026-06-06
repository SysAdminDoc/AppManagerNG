// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SysConfigActivityTest {
    @Test
    public void formatPackageReference_includesResolvedLabel() {
        CharSequence reference = SysConfigActivity.SysConfigRecyclerAdapter.formatPackageReference(
                "com.example.carrier", "Carrier Services");

        assertEquals("Carrier Services (com.example.carrier)", reference.toString());
    }

    @Test
    public void formatPackageReference_fallsBackToPackageNameForMatchingLabel() {
        CharSequence reference = SysConfigActivity.SysConfigRecyclerAdapter.formatPackageReference(
                "com.example.carrier", "com.example.carrier");

        assertEquals("com.example.carrier", reference.toString());
    }

    @Test
    public void formatPackageReference_fallsBackToPackageNameForBlankLabel() {
        CharSequence reference = SysConfigActivity.SysConfigRecyclerAdapter.formatPackageReference(
                "com.example.carrier", "   ");

        assertEquals("com.example.carrier", reference.toString());
    }

    @Test
    public void formatPackageReference_fallsBackToPackageNameForMissingLabel() {
        CharSequence reference = SysConfigActivity.SysConfigRecyclerAdapter.formatPackageReference(
                "com.example.carrier", null);

        assertEquals("com.example.carrier", reference.toString());
    }
}
