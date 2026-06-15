// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew;

import static org.junit.Assert.assertEquals;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.utils.LangUtils;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.N)
public class ApkWhatsNewFinderTest {
    @Test
    public void findChangesDoesNotMutateInputs() {
        Set<String> newInfo = setOf("common", "added");
        Set<String> oldInfo = setOf("common", "removed");

        ApkWhatsNewFinder.findChanges(newInfo, oldInfo);

        assertEquals(setOf("common", "added"), newInfo);
        assertEquals(setOf("common", "removed"), oldInfo);
    }

    @Test
    public void findChangesReportsAddedAndRemovedValues() {
        List<ApkWhatsNewFinder.Change> changes = ApkWhatsNewFinder.findChanges(
                setOf("common", "added"),
                setOf("common", "removed"));

        assertEquals(2, changes.size());
        assertChange(changes, ApkWhatsNewFinder.CHANGE_ADD, "added");
        assertChange(changes, ApkWhatsNewFinder.CHANGE_REMOVED, "removed");
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.N)
    public void buildSdkInfoIncludesTargetAndMinSdk() {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.targetSdkVersion = 35;
        applicationInfo.minSdkVersion = 23;

        assertEquals("Target" + LangUtils.getSeparatorString() + "35, Min"
                        + LangUtils.getSeparatorString() + "23",
                ApkWhatsNewFinder.buildSdkInfo("Target", "Min", true, applicationInfo));
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.M)
    public void buildSdkInfoUsesUnknownForMinSdkBeforeNougat() {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.targetSdkVersion = 35;

        assertEquals("Target" + LangUtils.getSeparatorString() + "35, Min"
                        + LangUtils.getSeparatorString() + "?",
                ApkWhatsNewFinder.buildSdkInfo("Target", "Min", true, applicationInfo));
    }

    @Test
    public void buildSdkInfoUsesUnknownForMissingApplicationInfo() {
        assertEquals("Target" + LangUtils.getSeparatorString() + "?, Min"
                        + LangUtils.getSeparatorString() + "?",
                ApkWhatsNewFinder.buildSdkInfo("Target", "Min", true, null));
    }

    private static void assertChange(List<ApkWhatsNewFinder.Change> changes, int type, String value) {
        for (ApkWhatsNewFinder.Change change : changes) {
            if (change.changeType == type && change.value.equals(value)) {
                return;
            }
        }
        throw new AssertionError("Missing change " + type + " " + value + " in " + changes);
    }

    private static Set<String> setOf(String... values) {
        Set<String> set = new HashSet<>();
        for (String value : values) {
            set.add(value);
        }
        return set;
    }
}
