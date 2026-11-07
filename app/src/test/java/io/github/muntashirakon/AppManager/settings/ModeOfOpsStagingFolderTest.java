// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.servermanager.AdbLaunchFiles;

/**
 * A shell running as system can't write to /data/local/tmp, so the ADB command under Mode of
 * operation can copy the server into a folder the user picks (issue #19).
 */
@RunWith(RobolectricTestRunner.class)
public class ModeOfOpsStagingFolderTest {
    private ActivityController<FragmentActivity> mController;

    @Before
    public void setUp() {
        Prefs.Misc.setAdbManualStagingParent("");
    }

    @After
    public void tearDown() {
        if (mController != null) {
            mController.pause().stop().destroy();
        }
        Prefs.Misc.setAdbManualStagingParent("");
    }

    @Test
    public void theCommandFollowsTheFolderTypedForIt() {
        FragmentActivity activity = open();
        TextInputEditText folder = activity.findViewById(R.id.staging_folder);
        TextView command = activity.findViewById(android.R.id.text1);
        waitFor(() -> command.getText().toString().startsWith("{ mkdir -p '" + AdbLaunchFiles.STAGING_DIR + "' "));

        folder.setText("/data/system/amng");

        String dir = "/data/system/amng/" + BuildConfig.APPLICATION_ID;
        waitFor(() -> command.getText().toString().startsWith("{ mkdir -p '" + dir + "' "));
        assertTrue(command.getText().toString().contains("CLASSPATH='" + dir + "/"));
        assertEquals("/data/system/amng", Prefs.Misc.getAdbManualStagingParent());
        assertNull(((TextInputLayout) activity.findViewById(R.id.staging_folder_layout)).getError());
    }

    @Test
    public void aFolderTheCommandCantUseIsFlaggedAndNeverSaved() {
        Prefs.Misc.setAdbManualStagingParent("/data/system/amng");
        FragmentActivity activity = open();
        TextInputEditText folder = activity.findViewById(R.id.staging_folder);
        TextInputLayout layout = activity.findViewById(R.id.staging_folder_layout);
        assertEquals("/data/system/amng", folder.getText().toString());

        folder.setText("/data/system/amng; reboot");

        assertEquals(activity.getString(R.string.mode_of_op_staging_folder_invalid), String.valueOf(layout.getError()));
        assertEquals("/data/system/amng", Prefs.Misc.getAdbManualStagingParent());

        // Clearing it goes back to /data/local/tmp
        folder.setText("");
        assertNull(layout.getError());
        assertEquals("", Prefs.Misc.getAdbManualStagingParent());
        TextView command = activity.findViewById(android.R.id.text1);
        waitFor(() -> command.getText().toString().startsWith("{ mkdir -p '" + AdbLaunchFiles.STAGING_DIR + "' "));
    }

    private FragmentActivity open() {
        mController = Robolectric.buildActivity(FragmentActivity.class);
        FragmentActivity activity = mController.get();
        activity.setTheme(R.style.AppTheme_V2);
        mController.setup();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, new ModeOfOpsPreference())
                .commitNow();
        assertNotNull(activity.findViewById(R.id.staging_folder));
        return activity;
    }

    // The command is built off the main thread
    private static void waitFor(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (true) {
            shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) {
                return;
            }
            assertTrue("Timed out", System.nanoTime() < deadline);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }
}
