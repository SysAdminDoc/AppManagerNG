// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class SysConfigActivityTest {
    @Test
    public void configurationPicker_exposesUnknownRuntimeFeatures() {
        String[] types = RuntimeEnvironment.getApplication().getResources()
                .getStringArray(R.array.sys_config_names);

        assertTrue(Arrays.asList(types).contains(SysConfigType.TYPE_UNKNOWN_FEATURE));
    }

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

    @Test
    public void formatPermissionReference_includesResolvedLabel() {
        CharSequence reference = SysConfigActivity.SysConfigRecyclerAdapter.formatPermissionReference(
                "android.permission.CAMERA", "Camera");

        assertEquals("Camera (android.permission.CAMERA)", reference.toString());
    }

    @Test
    public void formatPermissionReference_fallsBackToPermissionNameForMatchingLabel() {
        CharSequence reference = SysConfigActivity.SysConfigRecyclerAdapter.formatPermissionReference(
                "android.permission.CAMERA", "android.permission.CAMERA");

        assertEquals("android.permission.CAMERA", reference.toString());
    }

    @Test
    public void formatPermissionReference_fallsBackToPermissionNameForBlankLabel() {
        CharSequence reference = SysConfigActivity.SysConfigRecyclerAdapter.formatPermissionReference(
                "android.permission.CAMERA", "  ");

        assertEquals("android.permission.CAMERA", reference.toString());
    }
}
