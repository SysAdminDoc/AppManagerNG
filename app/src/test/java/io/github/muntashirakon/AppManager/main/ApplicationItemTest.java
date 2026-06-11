// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ApplicationItemTest {
    @Test
    public void generateOtherInfoSkipsPerUserRuntimeFailures() {
        ApplicationItem item = app(new int[]{0, 10, 11});

        item.generateOtherInfo(new ApplicationItem.OtherInfoResolver() {
            @Override
            public int checkPermission(@NonNull String permissionName, @NonNull String packageName, int userId) {
                if (userId == 10) {
                    throw new SecurityException("private profile");
                }
                return userId == 0 ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
            }

            @Override
            public boolean isAppInactive(@NonNull String packageName, int userId) {
                if (userId == 10) {
                    throw new SecurityException("private profile");
                }
                return userId == 11;
            }
        });

        assertTrue(item.canReadLogs);
        assertTrue(item.isAppInactive);
    }

    @Test
    public void generateOtherInfoDefaultsUnavailableWhenPerUserQueriesFail() {
        ApplicationItem item = app(new int[]{10});

        item.generateOtherInfo(new ApplicationItem.OtherInfoResolver() {
            @Override
            public int checkPermission(@NonNull String permissionName, @NonNull String packageName, int userId) {
                throw new IllegalStateException("profile unavailable");
            }

            @Override
            public boolean isAppInactive(@NonNull String packageName, int userId) {
                throw new IllegalStateException("profile unavailable");
            }
        });

        assertFalse(item.canReadLogs);
        assertFalse(item.isAppInactive);
    }

    @NonNull
    private static ApplicationItem app(@NonNull int[] userIds) {
        ApplicationItem item = new ApplicationItem();
        item.packageName = "com.example.privateprofile";
        item.userIds = userIds;
        item.uid = 12345;
        item.versionName = "1.0";
        return item;
    }
}
