// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.dhizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class DhizukuSessionTest {
    private Context mContext;
    private FakeDhizuku mFake;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mFake = FakeDhizuku.install();
    }

    @After
    public void tearDown() {
        FakeDhizuku.uninstall();
    }

    @Test
    public void consentComesFromTheServerNotThePlatformPermission() {
        // The probe's platform permission check stays false, as on a real device
        mFake.permitted = false;
        assertEquals(DhizukuState.NOT_PERMITTED, DhizukuSession.refresh(mContext));
        assertFalse(DhizukuSession.isPermittedCached());

        mFake.permitted = true;
        assertEquals(DhizukuState.PERMITTED, DhizukuSession.refresh(mContext));
        assertTrue(DhizukuSession.isPermittedCached());
        assertEquals(DhizukuState.PERMITTED, DhizukuSession.getCachedState());
    }

    @Test
    public void noOwnerMeansNoConnectionAttempt() {
        mFake.owner = false;
        assertEquals(DhizukuState.NO_OWNER, DhizukuSession.refresh(mContext));
        assertFalse(mFake.calls.contains("connect"));
    }

    @Test
    public void lostServerDropsTheCacheAndHandsOutNoPolicy() {
        assertEquals(DhizukuState.PERMITTED, DhizukuSession.refresh(mContext));
        assertNotNull(DhizukuSession.policy(mContext));

        mFake.serverAnswers = false;
        assertNull(DhizukuSession.policy(mContext));
        assertEquals(DhizukuState.NOT_CONNECTED, DhizukuSession.getCachedState());
        assertFalse(DhizukuSession.isPermittedCached());
    }

    @Test
    public void revokedConsentHandsOutNoPolicy() {
        assertEquals(DhizukuState.PERMITTED, DhizukuSession.refresh(mContext));
        mFake.permitted = false;
        assertNull(DhizukuSession.policy(mContext));
        assertNull(DhizukuSession.permittedClient(mContext));
        assertEquals(DhizukuState.NOT_PERMITTED, DhizukuSession.getCachedState());
    }

    @Test
    public void requestAsksDhizukuAndReportsTheNewState() {
        mFake.permitted = false;
        DhizukuSession.refresh(mContext);

        assertEquals(DhizukuState.PERMITTED, DhizukuSession.requestPermissionAndWait(mContext, 1000));
        assertTrue(mFake.calls.contains("requestPermission"));
        assertTrue(DhizukuSession.isPermittedCached());
    }

    @Test
    public void deniedRequestLeavesAppNotPermitted() {
        mFake.permitted = false;
        mFake.grantOnRequest = false;
        assertEquals(DhizukuState.NOT_PERMITTED, DhizukuSession.requestPermissionAndWait(mContext, 1000));
        assertFalse(DhizukuSession.isPermittedCached());
    }

    @Test
    public void requestIsNotSentWhenDhizukuIsNotTheOwner() {
        mFake.owner = false;
        assertEquals(DhizukuState.NO_OWNER, DhizukuSession.requestPermissionAndWait(mContext, 1000));
        assertFalse(mFake.calls.contains("requestPermission"));
    }

    @Test
    public void androidFiveNeverLoadsTheClient() {
        mFake.sdk = 22;
        assertEquals(DhizukuState.UNSUPPORTED_ANDROID, DhizukuSession.refresh(mContext));
        assertFalse(mFake.calls.contains("connect"));
    }
}
