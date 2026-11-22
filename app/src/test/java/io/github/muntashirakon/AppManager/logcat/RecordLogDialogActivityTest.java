// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * The screen the widget and the log viewer's old record shortcut open. A recording is written to
 * shared storage, and unlike the log viewer's Record, this screen never asked for it, so the user
 * could say OK to a recording that couldn't be written.
 */
@RunWith(RobolectricTestRunner.class)
public class RecordLogDialogActivityTest {
    private final Application mContext = ApplicationProvider.getApplicationContext();
    private ActivityController<RecordLogDialogActivity> mController;

    @Before
    public void setUp() {
        Ops.setAuthenticated(mContext, true);
    }

    @After
    public void tearDown() {
        if (mController != null) {
            mController.pause().stop().destroy();
        }
        Ops.setAuthenticated(mContext, false);
    }

    @Test
    public void withoutAllFilesAccessItOpensThatSettingBeforeTheDialog() {
        // Android 11 and later. Coming back is answered by Android itself, which a test can't stand in for.
        RecordLogDialogActivity activity = start();

        ShadowActivity.IntentForResult asked = shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(asked);
        assertEquals(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, asked.intent.getAction());
        assertNull(dialog(activity));
    }

    @Test
    @Config(sdk = 29)
    public void aRefusedStorageRequestClosesTheScreenWithoutTheDialog() {
        RecordLogDialogActivity activity = start();

        ShadowActivity.PermissionsRequest asked = shadowOf(activity).getLastRequestedPermission();
        assertNotNull(asked);
        assertArrayEquals(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, asked.requestedPermissions);
        assertNull(dialog(activity));

        activity.onRequestPermissionsResult(asked.requestCode, asked.requestedPermissions,
                new int[]{PackageManager.PERMISSION_DENIED});
        idle();

        assertNull(dialog(activity));
        assertTrue(activity.isFinishing());
    }

    @Test
    @Config(sdk = 29)
    public void withStorageAccessTheDialogShowsAndClosingItClosesTheScreen() {
        shadowOf(mContext).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        RecordLogDialogActivity activity = start();

        RecordLogDialogFragment dialog = dialog(activity);
        assertNotNull(dialog);
        assertFalse(activity.isFinishing());

        dialog.dismiss();
        idle();
        assertTrue(activity.isFinishing());
    }

    @Test
    @Config(sdk = 29)
    public void afterARotationTheSameDialogStillClosesTheScreen() {
        shadowOf(mContext).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        start();

        RecordLogDialogActivity activity = mController.recreate().get();
        idle();

        assertEquals("a second dialog over the first", 1, activity.getSupportFragmentManager().getFragments().stream()
                .filter(f -> f instanceof RecordLogDialogFragment).count());
        RecordLogDialogFragment dialog = dialog(activity);
        assertNotNull(dialog);
        dialog.dismiss();
        idle();
        assertTrue(activity.isFinishing());
    }

    @NonNull
    private RecordLogDialogActivity start() {
        mController = Robolectric.buildActivity(RecordLogDialogActivity.class,
                new Intent(mContext, RecordLogDialogActivity.class));
        RecordLogDialogActivity activity = mController.setup().get();
        idle();
        return activity;
    }

    @Nullable
    private static RecordLogDialogFragment dialog(@NonNull RecordLogDialogActivity activity) {
        return (RecordLogDialogFragment) activity.getSupportFragmentManager()
                .findFragmentByTag(RecordLogDialogFragment.TAG);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
