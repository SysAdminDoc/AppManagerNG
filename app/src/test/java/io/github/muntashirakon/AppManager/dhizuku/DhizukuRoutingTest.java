// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.UserHandleHidden;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.permission.PermUtils;
import io.github.muntashirakon.AppManager.permission.Permission;
import io.github.muntashirakon.AppManager.permission.ReadOnlyPermission;
import io.github.muntashirakon.AppManager.permission.RuntimePermission;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

/**
 * The existing operations pick Dhizuku when the current mode lacks the permission they need,
 * and leave it alone otherwise.
 */
@RunWith(RobolectricTestRunner.class)
public class DhizukuRoutingTest {
    private static final String PACKAGE = "com.example.target";
    private static final String CAMERA = Manifest.permission.CAMERA;

    private Context mContext;
    private FakeDhizuku mFake;
    private int mUserId;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mFake = FakeDhizuku.install();
        mUserId = UserHandleHidden.myUserId();
        DhizukuSession.refresh(mContext);
        mFake.calls.clear();
    }

    @After
    public void tearDown() {
        FakeDhizuku.uninstall();
    }

    @Test
    public void capabilityGatesOpenOnlyWhilePermitted() {
        // No root, ADB or Shizuku in this test, so only Dhizuku can open them
        assertTrue(SelfPermissions.canFreezeUnfreezePackages());
        assertTrue(SelfPermissions.canClearAppUserData());
        assertTrue(SelfPermissions.canModifyRuntimePermissions());
        // Other permission kinds stay with the shell modes
        assertFalse(SelfPermissions.canModifyPermissions());

        mFake.permitted = false;
        DhizukuSession.refresh(mContext);
        assertFalse(SelfPermissions.canFreezeUnfreezePackages());
        assertFalse(SelfPermissions.canClearAppUserData());
        assertFalse(SelfPermissions.canModifyRuntimePermissions());
    }

    @Test
    public void freezeMethodsMapToHideAndSuspend() throws Exception {
        FreezeUtils.freeze(PACKAGE, mUserId, FreezeUtils.FREEZE_HIDE);
        FreezeUtils.freeze(PACKAGE + ".two", mUserId, FreezeUtils.FREEZE_SUSPEND);
        // Dhizuku can't disable apps, so the disable method hides instead
        FreezeUtils.freeze(PACKAGE + ".three", mUserId, FreezeUtils.FREEZE_DISABLE);
        assertEquals(Arrays.asList(
                "setApplicationHidden " + PACKAGE + " true",
                "setPackagesSuspended [" + PACKAGE + ".two] true",
                "setApplicationHidden " + PACKAGE + ".three true"), policyCalls());
    }

    @Test
    public void unfreezeLiftsHideAndSuspend() throws Exception {
        mFake.hidden.add(PACKAGE);
        mFake.suspended.add(PACKAGE);
        FreezeUtils.unfreeze(PACKAGE, mUserId);
        assertTrue(mFake.hidden.isEmpty());
        assertTrue(mFake.suspended.isEmpty());
    }

    @Test
    public void freezeLeavesDhizukuAloneWhenNotPermitted() {
        mFake.permitted = false;
        DhizukuSession.refresh(mContext);
        mFake.calls.clear();
        try {
            FreezeUtils.freeze(PACKAGE, mUserId, FreezeUtils.FREEZE_HIDE);
        } catch (Throwable ignore) {
            // The old path has nothing to freeze with here
        }
        assertEquals(Collections.emptyList(), policyCalls());
    }

    @Test
    public void runtimePermissionCompatRoutesThroughThePolicy() throws Exception {
        PermissionCompat.grantPermission(PACKAGE, CAMERA, mUserId);
        assertTrue(mFake.isGranted(PACKAGE, CAMERA));
        PermissionCompat.revokePermission(PACKAGE, CAMERA, mUserId);
        assertFalse(mFake.isGranted(PACKAGE, CAMERA));
        assertEquals(Arrays.asList(
                "setPermissionGrantState " + PACKAGE + " " + CAMERA + " 1",
                "setPermissionGrantState " + PACKAGE + " " + CAMERA + " 0",
                "setPermissionGrantState " + PACKAGE + " " + CAMERA + " 2",
                "setPermissionGrantState " + PACKAGE + " " + CAMERA + " 0"), policyCalls());
    }

    @Test
    public void appDetailsTogglesOnlyRuntimePermissions() throws Exception {
        Permission runtime = new RuntimePermission(CAMERA, false, AppOpsManagerCompat.OP_NONE, true, 0);
        Permission readOnly = new ReadOnlyPermission(Manifest.permission.INTERNET, true,
                AppOpsManagerCompat.OP_NONE, true, 0);
        assertTrue(PermUtils.isModifiable(runtime));
        assertFalse(PermUtils.isModifiable(readOnly));

        PermUtils.grantPermission(packageInfo(Build.VERSION_CODES.TIRAMISU), runtime, null, true, true);
        assertTrue(runtime.isGranted());
        assertTrue(mFake.isGranted(PACKAGE, CAMERA));

        PermUtils.revokePermission(packageInfo(Build.VERSION_CODES.TIRAMISU), runtime, null, true);
        assertFalse(runtime.isGranted());
        assertFalse(mFake.isGranted(PACKAGE, CAMERA));
    }

    @Test
    public void appDetailsLeavesPermissionsAloneWhenNotPermitted() {
        mFake.permitted = false;
        DhizukuSession.refresh(mContext);
        Permission runtime = new RuntimePermission(CAMERA, false, AppOpsManagerCompat.OP_NONE, true, 0);
        assertFalse(PermUtils.isModifiable(runtime));
    }

    @Test
    public void clearDataRoutesThroughThePolicy() throws Exception {
        PackageManagerCompat.clearApplicationUserData(new UserPackagePair(PACKAGE, mUserId));
        assertEquals(Collections.singletonList("clearApplicationUserData " + PACKAGE), policyCalls());
    }

    private static PackageInfo packageInfo(int targetSdk) {
        PackageInfo info = new PackageInfo();
        info.packageName = PACKAGE;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = PACKAGE;
        info.applicationInfo.targetSdkVersion = targetSdk;
        info.applicationInfo.uid = 10123;
        return info;
    }

    private List<String> policyCalls() {
        List<String> calls = new ArrayList<>(mFake.calls);
        calls.removeAll(Collections.singleton("connect"));
        return calls;
    }
}
