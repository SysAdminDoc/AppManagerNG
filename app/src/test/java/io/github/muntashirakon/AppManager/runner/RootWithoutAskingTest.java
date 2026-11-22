// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.fragment.app.FragmentActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.misc.DeviceInfo2;
import io.github.muntashirakon.AppManager.misc.DeviceInfo2TextTest;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.FileUtils;

/**
 * With No root chosen, opening App Info (for its Open in Termux item), the device info dialog and
 * the shared storage fallback each opened a root shell to check for root, which is a superuser
 * prompt on a rooted phone (upstream App Manager #2048). The shadow records that root shell probe.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = RootWithoutAskingTest.RecordingRunnerUtils.class)
public class RootWithoutAskingTest {
    static final List<String> sProbes = new ArrayList<>();
    static boolean sSuOnPath;

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private int mWorkingUid;

    @Before
    public void setUp() {
        sProbes.clear();
        mWorkingUid = Ops.getWorkingUid();
    }

    @After
    public void tearDown() throws ReflectiveOperationException {
        sProbes.clear();
        sSuOnPath = false;
        setDirectRoot(false);
        Ops.setWorkingUid(mWorkingUid);
    }

    @Test
    public void noRootModeAnswersFromThePathWithoutAsking() {
        sSuOnPath = true;
        assertTrue(RunnerUtils.hasRootWithoutAsking());
        sSuOnPath = false;
        assertFalse(RunnerUtils.hasRootWithoutAsking());

        assertEquals(Collections.emptyList(), sProbes);
    }

    @Test
    public void rootModeStillCountsAsRoot() throws ReflectiveOperationException {
        // A root manager that keeps su off the PATH still gives a root shell once root is the mode
        setDirectRoot(true);

        assertTrue(RunnerUtils.hasRootWithoutAsking());
        assertEquals(Collections.emptyList(), sProbes);
    }

    @Test
    public void sharedStorageFallbackDoesNotAskForRootOutsideRootMode() throws Exception {
        sSuOnPath = true;
        Ops.setWorkingUid(android.os.Process.myUid());
        // A folder Android can't make: its parent is a file
        File notAFolder = mTemp.newFile("external");
        File[] extDirs = {new File(notAFolder, "cache")};

        assertThrows(FileNotFoundException.class, () -> FileUtils.getBestExternalPathWithRoot(extDirs));

        assertEquals(Collections.emptyList(), sProbes);
    }

    @Test
    public void deviceInfoDoesNotAskForRootOutsideRootMode() {
        sSuOnPath = true;
        FragmentActivity activity = Robolectric.buildActivity(DeviceInfo2TextTest.DisplayActivity.class).setup().get();
        DeviceInfo2 info = new DeviceInfo2(activity);

        try {
            info.loadInfo();
        } catch (UnsatisfiedLinkError e) {
            // Later on it reads the CPU through the app's native library, which a host JVM can't
            // load. The root check is the first thing it does.
        }

        assertTrue(info.hasRoot);
        assertEquals(Collections.emptyList(), sProbes);
    }

    private static void setDirectRoot(boolean directRoot) throws ReflectiveOperationException {
        Field field = Ops.class.getDeclaredField("sDirectRoot");
        field.setAccessible(true);
        field.setBoolean(null, directRoot);
    }

    @Implements(RunnerUtils.class)
    public static class RecordingRunnerUtils {
        /** The real one opens a root shell: a superuser prompt on a rooted phone. */
        @Implementation
        protected static Boolean isAppGrantedRoot() {
            sProbes.add("isAppGrantedRoot");
            return sSuOnPath ? null : false;
        }

        @Implementation
        protected static boolean isSuOnPath() {
            return sSuOnPath;
        }
    }
}
