// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.util.AndroidException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;

import io.github.muntashirakon.AppManager.dhizuku.DhizukuCapabilities.Operation;

@RunWith(RobolectricTestRunner.class)
public class DhizukuOpsTest {
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
    public void canRunFollowsTheCachedStateAndTheUser() {
        assertTrue(DhizukuOps.canRun(Operation.HIDE_APPS, mUserId));
        assertFalse(DhizukuOps.canRun(Operation.HIDE_APPS, mUserId + 10));

        mFake.permitted = false;
        DhizukuSession.refresh(mContext);
        assertFalse(DhizukuOps.canRun(Operation.HIDE_APPS, mUserId));
    }

    @Test
    public void hideAndUnhideGoToTheDevicePolicy() throws RemoteException {
        DhizukuOps.setHidden(PACKAGE, mUserId, true);
        assertTrue(DhizukuOps.isHidden(PACKAGE, mUserId));
        DhizukuOps.setHidden(PACKAGE, mUserId, false);
        assertFalse(DhizukuOps.isHidden(PACKAGE, mUserId));
        assertEquals(Arrays.asList("setApplicationHidden " + PACKAGE + " true",
                "setApplicationHidden " + PACKAGE + " false"), policyCalls());
    }

    @Test
    public void suspendGoesToTheDevicePolicy() throws RemoteException {
        DhizukuOps.setSuspended(PACKAGE, mUserId, true);
        assertTrue(DhizukuOps.isSuspended(PACKAGE, mUserId));
        assertEquals(Collections.singletonList("setPackagesSuspended [" + PACKAGE + "] true"), policyCalls());
    }

    @Test
    public void grantSetsGrantedThenReleasesThePolicyLock() throws RemoteException {
        DhizukuOps.setRuntimePermission(PACKAGE, CAMERA, mUserId, true);
        // GRANTED (1), then DEFAULT (0) so the user can still change it in Settings
        assertEquals(Arrays.asList("setPermissionGrantState " + PACKAGE + " " + CAMERA + " 1",
                "setPermissionGrantState " + PACKAGE + " " + CAMERA + " 0"), policyCalls());
        assertTrue(mFake.isGranted(PACKAGE, CAMERA));
    }

    @Test
    public void revokeSetsDeniedThenReleasesThePolicyLock() throws RemoteException {
        DhizukuOps.setRuntimePermission(PACKAGE, CAMERA, mUserId, true);
        mFake.calls.clear();
        DhizukuOps.setRuntimePermission(PACKAGE, CAMERA, mUserId, false);
        assertEquals(Arrays.asList("setPermissionGrantState " + PACKAGE + " " + CAMERA + " 2",
                "setPermissionGrantState " + PACKAGE + " " + CAMERA + " 0"), policyCalls());
        assertFalse(mFake.isGranted(PACKAGE, CAMERA));
    }

    @Test
    public void refusedGrantFailsWithoutUnlocking() {
        mFake.refuseGrantState = true;
        assertThrows(RemoteException.class, () -> DhizukuOps.setRuntimePermission(PACKAGE, CAMERA, mUserId, true));
        assertEquals(Collections.singletonList("setPermissionGrantState " + PACKAGE + " " + CAMERA + " 1"),
                policyCalls());
    }

    @Test
    public void grantThatDoesNotTakeEffectFails() {
        mFake.grantsTakeEffect = false;
        assertThrows(RemoteException.class, () -> DhizukuOps.setRuntimePermission(PACKAGE, CAMERA, mUserId, true));
    }

    @Test
    public void clearDataWaitsForAndroidsAnswer() throws AndroidException {
        DhizukuOps.clearAppData(PACKAGE, mUserId);
        assertEquals(Collections.singletonList("clearApplicationUserData " + PACKAGE), policyCalls());

        mFake.clearDataSucceeds = false;
        assertThrows(AndroidException.class, () -> DhizukuOps.clearAppData(PACKAGE, mUserId));
    }

    @Test
    public void nothingReachesThePolicyWhenNotPermitted() {
        mFake.permitted = false;
        assertThrows(RemoteException.class, () -> DhizukuOps.setHidden(PACKAGE, mUserId, true));
        assertThrows(RemoteException.class, () -> DhizukuOps.setRuntimePermission(PACKAGE, CAMERA, mUserId, true));
        assertThrows(AndroidException.class, () -> DhizukuOps.clearAppData(PACKAGE, mUserId));
        assertEquals(Collections.emptyList(), policyCalls());
        assertFalse(DhizukuSession.isPermittedCached());
    }

    @Test
    public void otherUsersAreRefused() {
        assertThrows(RemoteException.class, () -> DhizukuOps.setHidden(PACKAGE, mUserId + 10, true));
        assertEquals(Collections.emptyList(), policyCalls());
    }

    private java.util.List<String> policyCalls() {
        java.util.List<String> calls = new java.util.ArrayList<>(mFake.calls);
        calls.removeAll(Collections.singleton("connect"));
        return calls;
    }
}
