// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.ResolveInfo;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.HashSet;
import java.util.Set;

/**
 * Issue 40: App Info says how an app fits a TV, and the main list can show only the apps a TV launcher lists.
 */
@RunWith(RobolectricTestRunner.class)
public class TvSupportTest {
    @Test
    public void aLeanbackLauncherActivityMakesATvApp() {
        assertEquals(TvSupport.MADE_FOR_TV, TvSupport.classify(true, null));
    }

    @Test
    public void theLeanbackFeatureMakesATvAppRequiredOrNot() {
        assertEquals(TvSupport.MADE_FOR_TV, TvSupport.classify(false,
                new FeatureInfo[]{feature("android.software.leanback", true)}));
        assertEquals(TvSupport.MADE_FOR_TV, TvSupport.classify(false,
                new FeatureInfo[]{feature("android.software.leanback", false)}));
    }

    @Test
    public void anOptionalTouchscreenWorksWithoutTouch() {
        assertEquals(TvSupport.WORKS_WITHOUT_TOUCH, TvSupport.classify(false,
                new FeatureInfo[]{feature("android.hardware.camera", true),
                        feature("android.hardware.touchscreen", false)}));
    }

    @Test
    public void anythingElseIsTouchOnly() {
        assertEquals(TvSupport.TOUCH_ONLY, TvSupport.classify(false, null));
        assertEquals(TvSupport.TOUCH_ONLY, TvSupport.classify(false,
                new FeatureInfo[]{feature("android.hardware.touchscreen", true)}));
    }

    @Test
    public void theTvFilterListsThePackagesATvLauncherShows() {
        Context context = RuntimeEnvironment.getApplication();
        int[] users = new int[]{UserHandleHidden.myUserId()};
        // The app under test declares TV launcher activities itself, so start from what is already listed
        Set<String> before = TvSupport.leanbackPackages(context, users);
        assertFalse(before.contains("com.example.tv"));
        assertFalse(before.contains("com.example.phone"));
        Intent tvIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Intent phoneIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        shadowOf(context.getPackageManager()).addResolveInfoForIntent(tvIntent, launcher("com.example.tv"));
        shadowOf(context.getPackageManager()).addResolveInfoForIntent(phoneIntent, launcher("com.example.phone"));

        Set<String> expected = new HashSet<>(before);
        expected.add("com.example.tv");
        assertEquals(expected, TvSupport.leanbackPackages(context, users));
    }

    @NonNull
    private static ResolveInfo launcher(@NonNull String packageName) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = packageName;
        info.activityInfo.name = packageName + ".Main";
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.applicationInfo.packageName = packageName;
        return info;
    }

    @NonNull
    private static FeatureInfo feature(@NonNull String name, boolean required) {
        FeatureInfo info = new FeatureInfo();
        info.name = name;
        info.flags = required ? FeatureInfo.FLAG_REQUIRED : 0;
        return info;
    }
}
