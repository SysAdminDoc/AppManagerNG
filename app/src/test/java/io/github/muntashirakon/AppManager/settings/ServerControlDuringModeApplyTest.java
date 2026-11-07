// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.crypto.auth.AuthManager;

/**
 * Issue 19: the settings screen saves its mode after the transition lock is released, so a broadcast
 * landing between its connect and its save would be overwritten. This drives the real apply state.
 */
@RunWith(RobolectricTestRunner.class)
public class ServerControlDuringModeApplyTest {
    private static final String KEY = "correct-horse-battery-st";

    private final Application mContext = ApplicationProvider.getApplicationContext();
    private String mSavedMode;
    private String mSavedKey;

    @Before
    public void setUp() {
        mSavedMode = Ops.getMode();
        mSavedKey = AuthManager.getKey();
        AuthManager.setKey(KEY);
        Prefs.AppActions.setServerControlBroadcastsEnabled(true);
    }

    @After
    public void tearDown() {
        Prefs.AppActions.setServerControlBroadcastsEnabled(false);
        AuthManager.setKey(mSavedKey);
        Ops.setMode(mSavedMode);
    }

    @Test
    public void aBroadcastDuringAnApplyIsAnsweredBusyAndTheNextOneRuns() throws Exception {
        Ops.setMode(Ops.MODE_ROOT);
        ModeOfOpsApplyState screen = new ModeOfOpsApplyState();
        assertTrue(screen.begin(Ops.MODE_ROOT, Ops.MODE_ADB_OVER_TCP));
        try {
            assertEquals(5, sendSetModeNoRoot());
            assertEquals(Ops.MODE_ROOT, Ops.getMode());
        } finally {
            screen.dismiss();
        }

        // Positive control: the same broadcast, with the screen done
        assertEquals(-1, sendSetModeNoRoot());
        assertEquals(Ops.MODE_NO_ROOT, Ops.getMode());
    }

    private int sendSetModeNoRoot() throws Exception {
        boolean[] done = new boolean[1];
        int[] code = new int[1];
        Intent intent = new Intent(BuildConfig.APPLICATION_ID + ".action.SERVER_SET_MODE")
                .setPackage(BuildConfig.APPLICATION_ID)
                .putExtra("auth", KEY)
                .putExtra("mode", Ops.MODE_NO_ROOT);
        mContext.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override
            public void onReceive(@NonNull Context context, @NonNull Intent received) {
                code[0] = getResultCode();
                done[0] = true;
            }
        }, null, 12345, null, null);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!done[0]) {
            if (System.nanoTime() > deadline) {
                fail("The broadcast never finished");
            }
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(5);
        }
        return code[0];
    }
}
