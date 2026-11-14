// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;

/**
 * Magisk 31.1 dropped the su prompt, so picking Root without granting it in Magisk first just failed
 * with "Unable to use the current mode of operation", and granting it afterwards did nothing until
 * the app restarted. The other shadows come from {@link OpsNoRootInitTest}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
        OpsRootRefusedTest.SuRunnerUtils.class,
        OpsNoRootInitTest.RecordingShell.class,
        OpsNoRootInitTest.RecordingShizukuBridge.class,
        OpsNoRootInitTest.RecordingLocalServer.class,
        OpsNoRootInitTest.RecordingLocalServices.class,
        OpsNoRootInitTest.RecordingUsers.class,
        OpsNoRootInitTest.RecordingAdbUtils.class,
})
public class OpsRootRefusedTest {
    static final List<String> sCalls = new ArrayList<>();
    /** What su says: null when it's there and refused, false when there's no su at all. */
    static Boolean sRootGrant;

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        sCalls.clear();
        sRootGrant = null;
    }

    @After
    public void tearDown() {
        sCalls.clear();
        OpsNoRootInitTest.sCalls.clear();
    }

    @Test
    public void refusedRootOnMagisk31_1SaysWhereToGrantIt() {
        installMagisk(31100);

        assertEquals(Ops.STATUS_FAILURE, Ops.init(mContext, true, Ops.MODE_ROOT));

        assertFalse(Ops.isDirectRoot());
        assertEquals(mContext.getString(R.string.root_refused_magisk_superuser), latestToast());
    }

    @Test
    public void noSuOnAPhoneWithMagisk31_1KeepsTheGenericToast() {
        // Magisk installed only to patch a boot image: nothing refused, there's just no root
        sRootGrant = false;
        installMagisk(31100);

        assertEquals(Ops.STATUS_FAILURE, Ops.init(mContext, true, Ops.MODE_ROOT));

        assertEquals(mContext.getString(R.string.failed_to_use_the_current_mode_of_operation), latestToast());
    }

    @Test
    public void refusedRootOnOlderMagiskKeepsTheGenericToast() {
        // Magisk 30.7 still asks, so a refusal there means the user said no
        installMagisk(30700);

        assertEquals(Ops.STATUS_FAILURE, Ops.init(mContext, true, Ops.MODE_ROOT));

        assertEquals(mContext.getString(R.string.failed_to_use_the_current_mode_of_operation), latestToast());
    }

    @Test
    public void pickingRootAsksSuAgainButAPlainStartDoesNot() {
        Ops.init(mContext, true, Ops.MODE_ROOT);
        assertEquals(sCalls.toString(), "RunnerUtils.retryRootGrant", sCalls.get(0));
        assertTrue(sCalls.toString(), sCalls.contains("RunnerUtils.isAppGrantedRoot"));

        // Starts and background workers init the saved mode without asking, since on Magisk 30.7
        // and older every new su request is a prompt
        sCalls.clear();
        String before = Ops.getMode();
        try {
            Ops.setMode(Ops.MODE_ROOT);
            Ops.init(mContext, false);
            assertTrue(sCalls.toString(), sCalls.contains("RunnerUtils.isAppGrantedRoot"));
            assertFalse(sCalls.toString(), sCalls.contains("RunnerUtils.retryRootGrant"));
        } finally {
            Ops.setMode(before);
        }
    }

    @Test
    public void pickingAutoAsksSuAgainToo() {
        // Auto tries root first, so a grant made in Magisk since the last refusal should count
        Ops.init(mContext, true, Ops.MODE_AUTO);

        assertEquals(sCalls.toString(), "RunnerUtils.retryRootGrant", sCalls.get(0));
    }

    @Test
    public void automationAsksSuAgainOnlyWhereNoPromptCanComeUp() {
        // Magisk 31.1+ never prompts, so an unattended switch to Root can ask su again
        installMagisk(31100);
        Ops.switchModeUnattended(mContext, Ops.MODE_ROOT);
        assertTrue(sCalls.toString(), sCalls.contains("RunnerUtils.retryRootGrant"));

        // On Magisk 30.7 every new su request is a prompt, and nobody may be watching
        sCalls.clear();
        installMagisk(30700);
        Ops.switchModeUnattended(mContext, Ops.MODE_ROOT);
        assertTrue(sCalls.toString(), sCalls.contains("RunnerUtils.isAppGrantedRoot"));
        assertFalse(sCalls.toString(), sCalls.contains("RunnerUtils.retryRootGrant"));
    }

    private String latestToast() {
        shadowOf(Looper.getMainLooper()).idle();
        return ShadowToast.getTextOfLatestToast();
    }

    private void installMagisk(long versionCode) {
        PackageInfo info = new PackageInfo();
        info.packageName = "com.topjohnwu.magisk";
        info.setLongVersionCode(versionCode);
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = info.packageName;
        shadowOf(mContext.getPackageManager()).installPackage(info);
    }

    @Implements(RunnerUtils.class)
    public static class SuRunnerUtils {
        @Implementation
        protected static Boolean isAppGrantedRoot() {
            sCalls.add("RunnerUtils.isAppGrantedRoot");
            return sRootGrant;
        }

        @Implementation
        protected static void retryRootGrant() {
            sCalls.add("RunnerUtils.retryRootGrant");
        }

        @Implementation
        protected static Long getMagiskSuVersionCode() {
            return null;
        }
    }
}
