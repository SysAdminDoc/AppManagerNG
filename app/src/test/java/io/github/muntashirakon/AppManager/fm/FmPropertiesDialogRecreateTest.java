// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import io.github.muntashirakon.AppManager.fm.dialogs.FilePropertiesDialogFragment;
import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * A Properties sheet closed by tapping outside came back by itself on every rotation, one more
 * copy each time, and only killing the app cleared it (#34).
 */
@RunWith(RobolectricTestRunner.class)
public class FmPropertiesDialogRecreateTest {
    private Application mApp;
    private boolean mAuthenticatedBefore;
    private File mDir;
    private File mFile;
    private ActivityController<FmActivity> mController;

    @Before
    public void setUp() throws IOException {
        mApp = ApplicationProvider.getApplicationContext();
        mAuthenticatedBefore = Ops.isAuthenticated();
        Ops.setAuthenticated(mApp, true);
        // Environment.isExternalStorageManager() reads the first external dir, and Robolectric
        // lists none unless one is added
        ShadowEnvironment.addExternalDir("fm-properties-storage");
        mDir = Files.createTempDirectory("fm-properties").toFile();
        mFile = new File(mDir, "notes.txt");
        Files.write(mFile.toPath(), "hello".getBytes(StandardCharsets.UTF_8));
        Intent intent = new Intent(mApp, FmActivity.class).setData(Uri.fromFile(mDir));
        mController = Robolectric.buildActivity(FmActivity.class, intent);
    }

    @After
    public void tearDown() {
        mController.pause().stop().destroy();
        Ops.setAuthenticated(mApp, mAuthenticatedBefore);
        mFile.delete();
        mDir.delete();
    }

    @Test
    public void aDismissedPropertiesSheetStaysClosedAcrossRotations() {
        openFileManager();
        showProperties();
        assertEquals(1, countPropertiesSheets());

        DialogFragment sheet = (DialogFragment) fragmentManager().findFragmentByTag(FilePropertiesDialogFragment.TAG);
        assertNotNull(sheet);
        sheet.dismiss();
        idle();
        assertEquals(0, countPropertiesSheets());

        mController.recreate();
        idle();
        assertEquals("a dismissed sheet came back after a rotation", 0, countPropertiesSheets());

        mController.recreate();
        idle();
        assertEquals("a dismissed sheet came back after a second rotation", 0, countPropertiesSheets());
    }

    @Test
    public void anOpenPropertiesSheetSurvivesRotationWithoutStacking() {
        openFileManager();
        showProperties();

        mController.recreate();
        idle();
        mController.recreate();
        idle();

        assertEquals(1, countPropertiesSheets());
    }

    @Test
    public void aDismissedSheetStaysClosedAfterChangingFolderAndRotating() {
        openFileManager();
        showProperties();
        ((DialogFragment) fragmentManager().findFragmentByTag(FilePropertiesDialogFragment.TAG)).dismiss();
        idle();

        viewModel().loadFiles(Uri.fromFile(mDir.getParentFile()));
        idle();
        mController.recreate();
        idle();

        assertEquals(0, countPropertiesSheets());
    }

    private void openFileManager() {
        mController.setup();
        idle();
        // Robolectric never grants all-files access, so answer the settings screen the activity
        // opens; the file manager loads either way
        ShadowActivity shadowActivity = shadowOf(mController.get());
        ShadowActivity.IntentForResult request = shadowActivity.getNextStartedActivityForResult();
        if (request != null) {
            shadowActivity.receiveResult(request.intent, Activity.RESULT_CANCELED, null);
            idle();
        }
        assertNotNull("the file list never loaded", fmFragment());
    }

    private void showProperties() {
        viewModel().getDisplayPropertiesLiveData().setValue(Uri.fromFile(mFile));
        idle();
    }

    private FmViewModel viewModel() {
        return new ViewModelProvider(fmFragment()).get(FmViewModel.class);
    }

    private Fragment fmFragment() {
        return fragmentManager().findFragmentByTag(FmFragment.TAG);
    }

    private FragmentManager fragmentManager() {
        return mController.get().getSupportFragmentManager();
    }

    private int countPropertiesSheets() {
        int count = 0;
        for (Fragment fragment : fragmentManager().getFragments()) {
            if (fragment instanceof FilePropertiesDialogFragment) {
                ++count;
            }
        }
        return count;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
