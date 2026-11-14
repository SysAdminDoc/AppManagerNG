// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.os.Looper;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;

@RunWith(RobolectricTestRunner.class)
public class BloatwareDetailsDialogTest {
    private static final String FIRST = "com.example.first";
    private static final String SECOND = "com.example.second";

    private ActivityController<AppCompatActivity> mController;

    @Before
    public void setUp() {
        // Set directly, so the test doesn't depend on reading the definition files.
        StaticDataset.setDebloatObjectsForTesting(Arrays.asList(debloatObject(FIRST), debloatObject(SECOND)));
        mController = Robolectric.buildActivity(AppCompatActivity.class);
        mController.get().setTheme(R.style.AppTheme_V2);
        mController.setup();
    }

    @After
    public void tearDown() {
        mController.pause().stop().destroy();
        StaticDataset.clearDebloatObjectsCache();
    }

    @Test
    public void aDialogForAnotherPackageNeverShowsTheLastPackagesDetails() throws InterruptedException {
        FragmentManager fragmentManager = mController.get().getSupportFragmentManager();
        BloatwareDetailsDialog first = open(fragmentManager, FIRST);
        assertEquals(FIRST, waitForPackage(first));
        first.dismissNow();

        BloatwareDetailsDialog second = open(fragmentManager, SECOND);

        // Before its own details load, the new dialog shows nothing of the last package.
        assertNotEquals(FIRST, packageShown(second));
        assertEquals(SECOND, waitForPackage(second));
    }

    @NonNull
    private static BloatwareDetailsDialog open(@NonNull FragmentManager fragmentManager, @NonNull String packageName) {
        BloatwareDetailsDialog dialog = BloatwareDetailsDialog.getInstance(packageName);
        dialog.showNow(fragmentManager, BloatwareDetailsDialog.TAG);
        return dialog;
    }

    @Nullable
    private static String packageShown(@NonNull BloatwareDetailsDialog dialogFragment) {
        Dialog dialog = dialogFragment.getDialog();
        assertNotNull(dialog);
        // The details are attached only once something has loaded. Until then a loader shows.
        TextView packageName = dialog.findViewById(R.id.package_name);
        if (packageName == null) {
            return null;
        }
        CharSequence text = packageName.getText();
        return text == null || text.length() == 0 ? null : text.toString();
    }

    @Nullable
    private static String waitForPackage(@NonNull BloatwareDetailsDialog dialog) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            String shown = packageShown(dialog);
            if (shown != null) {
                return shown;
            }
            Thread.sleep(20);
        }
        return null;
    }

    @NonNull
    private static DebloatObject debloatObject(@NonNull String packageName) {
        DebloatObject debloatObject = new DebloatObject();
        debloatObject.packageName = packageName;
        debloatObject.type = "oem";
        return debloatObject;
    }
}
