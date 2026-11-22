// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class DhizukuInstallerTest {
    private static final int ME = 0;
    private static final int USER_ALL = -1;

    private Context mContext;
    private FakeDhizuku mFake;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mFake = FakeDhizuku.install();
        DhizukuSession.refresh(mContext);
    }

    @After
    public void tearDown() {
        FakeDhizuku.uninstall();
    }

    @Test
    public void dhizukuInstallsOnlyWhenTheModeCannotAndOnlyForThisUser() {
        assertTrue(DhizukuInstaller.shouldUse(false, true, ME, false, ME, 2));
        // A mode that installs by itself keeps doing so
        assertFalse(DhizukuInstaller.shouldUse(true, true, ME, false, ME, 1));
        assertFalse(DhizukuInstaller.shouldUse(false, false, ME, false, ME, 1));
        // The owner acts on its own user only
        assertFalse(DhizukuInstaller.shouldUse(false, true, 10, false, ME, 2));
        // "All users" is fine on a device with one user and refused with more
        assertTrue(DhizukuInstaller.shouldUse(false, true, ME, true, ME, 1));
        assertFalse(DhizukuInstaller.shouldUse(false, true, ME, true, ME, 2));
        assertFalse(DhizukuInstaller.shouldUse(false, true, USER_ALL, false, ME, 1));
    }

    @Test
    public void sessionBinderIsWrappedSoItBelongsToTheOwner() throws RemoteException {
        IBinder session = new Binder();
        IBinder wrapped = DhizukuInstaller.wrapSession(mContext, session);
        assertNotSame(session, wrapped);
        assertSame(session, mFake.wrapped.get(wrapped));
        assertEquals(FakeDhizuku.OWNER.getPackageName(), DhizukuInstaller.getOwnerPackageName(mContext));
    }

    @Test
    public void nothingIsWrappedWhenNotPermitted() {
        mFake.permitted = false;
        assertThrows(RemoteException.class, () -> DhizukuInstaller.wrapSession(mContext, new Binder()));
        assertThrows(RemoteException.class, () -> DhizukuInstaller.getOwnerPackageName(mContext));
        assertTrue(mFake.wrapped.isEmpty());
    }
}
