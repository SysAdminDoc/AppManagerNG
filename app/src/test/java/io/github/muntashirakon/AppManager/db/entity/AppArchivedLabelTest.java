// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.entity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import io.github.muntashirakon.AppManager.apk.installer.ShadowArchivedPackages;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowArchivedPackages.class)
public class AppArchivedLabelTest {
    private static final String PACKAGE = "com.example.archived";

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        ShadowArchivedPackages.reset();
        ShadowArchivedPackages.keep(PACKAGE, "Example", null);
    }

    @Test
    public void anAppArchivedForThisUserTakesTheLabelTheLauncherKept() {
        App app = App.fromPackageInfo(mContext, packageInfo(UserHandleHidden.myUserId(), 1_700_000_000_000L));

        assertEquals("Example", app.packageLabel);
    }

    @Test
    public void anAppArchivedForAnotherUserKeepsItsOwnLabelSinceAndroidAnswersOnlyForThisOne() {
        App app = App.fromPackageInfo(mContext, packageInfo(UserHandleHidden.myUserId() + 10, 1_700_000_000_000L));

        assertEquals(PACKAGE, app.packageLabel);
        assertTrue(ShadowArchivedPackages.sAsked.isEmpty());
    }

    @Test
    public void anAppThatIsNotArchivedIsNeverAsked() {
        App app = App.fromPackageInfo(mContext, packageInfo(UserHandleHidden.myUserId(), 0));

        assertEquals(PACKAGE, app.packageLabel);
        assertTrue(ShadowArchivedPackages.sAsked.isEmpty());
    }

    @NonNull
    private static PackageInfo packageInfo(int userId, long archiveTimeMillis) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = PACKAGE;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = PACKAGE;
        packageInfo.applicationInfo.uid = UserHandleHidden.getUid(userId, 10_124);
        if (archiveTimeMillis > 0) {
            ReflectionHelpers.setField(packageInfo, "mArchiveTimeMillis", archiveTimeMillis);
        }
        return packageInfo;
    }
}
