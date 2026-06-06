// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PackageUtilsPackageNameValidationTest {

    @Test
    public void acceptsStandardPackageNames() {
        assertTrue(PackageUtils.isPlausiblePackageName("com.android.vending"));
        assertTrue(PackageUtils.isPlausiblePackageName("io.github.muntashirakon.AppManager"));
        assertTrue(PackageUtils.isPlausiblePackageName("a"));
        assertTrue(PackageUtils.isPlausiblePackageName("z9._9"));
    }

    @Test
    public void rejectsShellMetacharacters() {
        // The function feeds the result into `pm dump <pkg> | grep ...` evaluated by
        // a shell, so anything outside [A-Za-z0-9._] must be refused.
        assertFalse(PackageUtils.isPlausiblePackageName("foo;rm -rf /"));
        assertFalse(PackageUtils.isPlausiblePackageName("foo|cat"));
        assertFalse(PackageUtils.isPlausiblePackageName("foo&bar"));
        assertFalse(PackageUtils.isPlausiblePackageName("foo$(whoami)"));
        assertFalse(PackageUtils.isPlausiblePackageName("foo`id`"));
        assertFalse(PackageUtils.isPlausiblePackageName("foo bar"));
        assertFalse(PackageUtils.isPlausiblePackageName("foo\nbar"));
        assertFalse(PackageUtils.isPlausiblePackageName("foo>out"));
        assertFalse(PackageUtils.isPlausiblePackageName("foo<in"));
        assertFalse(PackageUtils.isPlausiblePackageName("foo\\bar"));
        assertFalse(PackageUtils.isPlausiblePackageName("foo'bar'"));
        assertFalse(PackageUtils.isPlausiblePackageName("foo\"bar\""));
    }

    @Test
    public void rejectsEmptyAndOversizedAndLeadingDigit() {
        assertFalse(PackageUtils.isPlausiblePackageName(""));
        assertFalse(PackageUtils.isPlausiblePackageName("1com.android.vending"));
        assertFalse(PackageUtils.isPlausiblePackageName(".com.example"));
        StringBuilder oversized = new StringBuilder("a");
        for (int i = 0; i < 256; ++i) {
            oversized.append('a');
        }
        assertFalse(PackageUtils.isPlausiblePackageName(oversized.toString()));
    }

    @Test
    public void validateNameAcceptsPlatformAndStandardPackageNames() {
        assertTrue(PackageUtils.validateName("android"));
        assertTrue(PackageUtils.validateName("com.android.vending"));
        assertTrue(PackageUtils.validateName("io.github.muntashirakon.AppManager"));
    }

    @Test
    public void validateNameRejectsEmptySegments() {
        assertFalse(PackageUtils.validateName(""));
        assertFalse(PackageUtils.validateName(".com.example"));
        assertFalse(PackageUtils.validateName("com.example."));
        assertFalse(PackageUtils.validateName("com..example"));
    }

    @Test
    public void validateNameRejectsInvalidSegmentStartAndCharacters() {
        assertFalse(PackageUtils.validateName("1com.example"));
        assertFalse(PackageUtils.validateName("com.1example"));
        assertFalse(PackageUtils.validateName("com._example"));
        assertFalse(PackageUtils.validateName("com.example bad"));
    }
}
