// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * The log viewer is exported (it takes shared text and log links), and its old "record" shortcut
 * extra started a recording at once, so any app or a web link could start one without asking.
 */
@RunWith(RobolectricTestRunner.class)
public class LogViewerRecordShortcutTest {
    private final Application mContext = ApplicationProvider.getApplicationContext();
    private ActivityController<LogViewerActivity> mController;
    private boolean mOmitSensitiveInfo;

    @Before
    public void setUp() {
        Ops.setAuthenticated(mContext, true);
        mOmitSensitiveInfo = LogLine.omitSensitiveInfo;
    }

    @After
    public void tearDown() {
        if (mController != null) {
            // Not destroyed: onDestroy loads the app's native library, which a host JVM can't
            mController.pause().stop();
        }
        Ops.setAuthenticated(mContext, false);
        // The screen sets this process-wide from the settings
        LogLine.omitSensitiveInfo = mOmitSensitiveInfo;
    }

    @Test
    public void theRecordShortcutAsksFirst() {
        mController = Robolectric.buildActivity(LogViewerActivity.class,
                new Intent(mContext, LogViewerActivity.class).putExtra("shortcut_action", "record"));
        LogViewerActivity activity = mController.setup().get();

        Intent started = shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RecordLogDialogActivity.class.getName(), started.getComponent().getClassName());
        // The record screen asks for storage itself, and nothing else opens on the way
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertNull("a recording started without the user's OK", shadowOf(mContext).getNextStartedService());
        assertTrue(activity.isFinishing());
    }
}
