// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A screen that asked for storage and was made again before the answer came back waits for that
 * answer. After Android closes the app, the screen it brings back gets the answer as it starts,
 * which can be before the app is unlocked and the screen starts waiting.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class StoragePermissionTest {
    public static class Host extends FragmentActivity {
        final StoragePermission permission = StoragePermission.init(this);
    }

    private final Application mContext = ApplicationProvider.getApplicationContext();
    private ActivityController<Host> mController;

    @After
    public void tearDown() {
        if (mController != null) {
            mController.pause().stop().destroy();
        }
    }

    @Test
    public void aRefusalThatCameBackBeforeTheWaitIsStillHeard() {
        ShadowActivity.PermissionsRequest asked = askThenRecreate();
        Host again = mController.get();
        again.onRequestPermissionsResult(asked.requestCode, asked.requestedPermissions,
                new int[]{PackageManager.PERMISSION_DENIED});

        List<Boolean> answers = new ArrayList<>();
        again.permission.awaitResult(answers::add);

        assertEquals(Collections.singletonList(false), answers);
    }

    @Test
    public void aGrantThatCameBackBeforeTheWaitIsStillHeard() {
        ShadowActivity.PermissionsRequest asked = askThenRecreate();
        Host again = mController.get();
        shadowOf(mContext).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        again.onRequestPermissionsResult(asked.requestCode, asked.requestedPermissions,
                new int[]{PackageManager.PERMISSION_GRANTED});

        List<Boolean> answers = new ArrayList<>();
        again.permission.awaitResult(answers::add);

        assertEquals(Collections.singletonList(true), answers);
    }

    @Test
    public void anAnswerAfterTheWaitStartedIsHeardOnce() {
        ShadowActivity.PermissionsRequest asked = askThenRecreate();
        Host again = mController.get();
        List<Boolean> answers = new ArrayList<>();
        again.permission.awaitResult(answers::add);
        assertTrue("answered before Android did", answers.isEmpty());

        again.onRequestPermissionsResult(asked.requestCode, asked.requestedPermissions,
                new int[]{PackageManager.PERMISSION_DENIED});

        assertEquals(Collections.singletonList(false), answers);
    }

    @NonNull
    private ShadowActivity.PermissionsRequest askThenRecreate() {
        mController = Robolectric.buildActivity(Host.class).setup();
        Host first = mController.get();
        first.permission.request(granted -> {
            throw new AssertionError("the answer went to the screen that was closed");
        });
        ShadowActivity.PermissionsRequest asked = shadowOf(first).getLastRequestedPermission();
        assertNotNull(asked);
        mController.recreate();
        return asked;
    }
}
