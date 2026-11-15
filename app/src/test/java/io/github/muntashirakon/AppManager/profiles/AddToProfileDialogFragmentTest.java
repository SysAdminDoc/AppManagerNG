// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.os.Looper;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.io.OutputStream;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.io.Path;

@RunWith(RobolectricTestRunner.class)
public class AddToProfileDialogFragmentTest {
    private ActivityController<AppCompatActivity> mController;

    @Before
    public void setUp() {
        mController = Robolectric.buildActivity(AppCompatActivity.class);
        mController.get().setTheme(R.style.AppTheme_V2);
        mController.setup();
    }

    @After
    public void tearDown() {
        mController.pause().stop().destroy();
    }

    @Test
    public void savedProfilesFillTheListAfterTheyLoadOffTheMainThread() throws Exception {
        AppsProfile profile = (AppsProfile) BaseProfile.newProfile("Work apps", BaseProfile.PROFILE_TYPE_APPS, null);
        Path profilePath = ProfileManager.requireProfilePathById(profile.profileId);
        try (OutputStream os = profilePath.openOutputStream()) {
            profile.write(os);
        }

        AddToProfileDialogFragment fragment = AddToProfileDialogFragment.getInstance(new String[]{"com.example.app"});
        fragment.showNow(mController.get().getSupportFragmentManager(), AddToProfileDialogFragment.TAG);
        Dialog dialog = fragment.getDialog();
        assertNotNull(dialog);
        RecyclerView list = dialog.findViewById(android.R.id.list);
        assertNotNull(list);

        RecyclerView.Adapter<?> adapter = waitForItems(list);
        assertEquals(1, adapter.getItemCount());
        shadowOf(Looper.getMainLooper()).idle();
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
        assertNotNull(holder);
        CharSequence row = ((TextView) holder.itemView.findViewById(android.R.id.text1)).getText();
        assertTrue(row.toString(), row.toString().startsWith("Work apps"));
    }

    @NonNull
    private static RecyclerView.Adapter<?> waitForItems(@NonNull RecyclerView list) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            RecyclerView.Adapter<?> adapter = list.getAdapter();
            if (adapter != null && adapter.getItemCount() > 0) {
                return adapter;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("The profiles never loaded into the list");
    }
}
