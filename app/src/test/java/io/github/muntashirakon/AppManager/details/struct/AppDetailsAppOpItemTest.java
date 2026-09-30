// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PermissionInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.permission.Permission;

/**
 * Upstream 6495496ce: the linked permission follows the mode being set, not the op's current one.
 */
@RunWith(RobolectricTestRunner.class)
public class AppDetailsAppOpItemTest {
    // Every value AppOpsManagerCompat.Mode allows
    private static final int[] MODES = {-1, 0, 1, 2, 3, 4, 5};
    private static final int OP_CAMERA = 26;

    @Before
    public void setUp() {
        // PermUtils.isModifiable() needs these, or the permission is never touched at all
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
                ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS,
                ManifestCompat.permission.REVOKE_RUNTIME_PERMISSIONS);
    }

    @Test
    public void theRequestedModeDecidesTheLinkedPermissionWhateverTheCurrentMode() throws Exception {
        for (int current : MODES) {
            for (int requested : MODES) {
                Recorder item = new Recorder(current);
                assertTrue(item.hasModifiablePermission);

                item.setAppOp(packageInfo(), null, requested);

                boolean grant = requested == AppOpsManager.MODE_ALLOWED
                        || requested == AppOpsManager.MODE_FOREGROUND;
                String step = "current " + current + ", requested " + requested;
                assertEquals(step, (Boolean) grant, item.granted);
                assertEquals(step, requested, item.appliedMode);
            }
        }
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.N)
    public void foregroundRevokesBeforeAndroid10() {
        assertTrue(AppDetailsAppOpItem.grantsLinkedPermission(AppOpsManager.MODE_ALLOWED));
        // MODE_FOREGROUND only exists from Android 10
        assertFalse(AppDetailsAppOpItem.grantsLinkedPermission(AppOpsManager.MODE_FOREGROUND));
    }

    @Test
    public void anOpWithoutAPermissionOnlySetsTheMode() throws Exception {
        Recorder item = new Recorder();
        assertFalse(item.hasModifiablePermission);

        item.setAppOp(packageInfo(), null, AppOpsManager.MODE_ALLOWED);

        assertNull(item.granted);
        assertEquals(AppOpsManager.MODE_ALLOWED, item.appliedMode);
    }

    @NonNull
    private static PackageInfo packageInfo() {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = "com.example.camera";
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = packageInfo.packageName;
        packageInfo.applicationInfo.uid = 10123;
        packageInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM;
        return packageInfo;
    }

    @NonNull
    private static PermissionInfo cameraPermission() {
        PermissionInfo info = new PermissionInfo();
        info.name = "android.permission.CAMERA";
        info.protectionLevel = PermissionInfo.PROTECTION_DANGEROUS;
        return info;
    }

    /**
     * Stands in for the op's current mode and records what setAppOp asks of the system.
     */
    private static final class Recorder extends AppDetailsAppOpItem {
        private final int mCurrentMode;
        @Nullable
        Boolean granted;
        int appliedMode = Integer.MIN_VALUE;

        Recorder(int currentMode) {
            super(OP_CAMERA, cameraPermission(), false, 0, true);
            mCurrentMode = currentMode;
        }

        /**
         * An ignored op with no linked permission.
         */
        Recorder() {
            super(OP_CAMERA);
            mCurrentMode = AppOpsManager.MODE_IGNORED;
        }

        @Override
        public int getMode() {
            return mCurrentMode;
        }

        @Override
        void setLinkedPermission(@NonNull PackageInfo packageInfo, @NonNull Permission permission,
                                 @NonNull AppOpsManagerCompat appOpsManager, boolean grant) {
            granted = grant;
        }

        @Override
        void setMode(@NonNull PackageInfo packageInfo, @NonNull AppOpsManagerCompat appOpsManager, int mode) {
            appliedMode = mode;
        }

        @Override
        public void invalidate(@NonNull AppOpsManagerCompat appOpsManager, @NonNull PackageInfo packageInfo) {
        }
    }
}
