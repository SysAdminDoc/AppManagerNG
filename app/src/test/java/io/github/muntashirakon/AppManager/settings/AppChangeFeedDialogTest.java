// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.os.Looper;

import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;

/**
 * The feed loads on a background thread with the application context, and the dialog was built from
 * that same context. An application context has no Material theme, so the first TextInputLayout threw
 * "The style on this component requires your app theme to be Theme.MaterialComponents".
 */
@RunWith(RobolectricTestRunner.class)
public class AppChangeFeedDialogTest {
    private ActivityController<SettingsActivity> mController;

    @Before
    public void setUp() {
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), true);
        mController = Robolectric.buildActivity(SettingsActivity.class);
        ShadowDialog.reset();
    }

    @After
    public void tearDown() {
        mController.pause().stop().destroy();
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), false);
    }

    @Test
    public void tappingAppChangeFeedOpensItsDialog() throws InterruptedException {
        SettingsActivity activity = mController.setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        PrivacyPreferences fragment = new PrivacyPreferences();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
        Preference feed = fragment.findPreference("app_change_feed");
        assertNotNull(feed);
        ShadowDialog.reset();

        feed.performClick();

        Dialog dialog = waitForDialog();
        assertNotNull("The feed dialog never opened", dialog);
        assertTrue(dialog.isShowing());
        TextInputLayout filter = (TextInputLayout) findFirstInput(dialog.getWindow().getDecorView());
        assertNotNull(filter);
    }

    private static android.view.View findFirstInput(android.view.View view) {
        if (view instanceof TextInputLayout) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); ++i) {
                android.view.View found = findFirstInput(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Dialog waitForDialog() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Dialog dialog = ShadowDialog.getLatestDialog();
            if (dialog != null) return dialog;
            Thread.sleep(20);
        }
        return null;
    }
}
