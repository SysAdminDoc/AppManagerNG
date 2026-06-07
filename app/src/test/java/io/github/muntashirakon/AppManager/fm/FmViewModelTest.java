// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class FmViewModelTest {
    @Test
    public void getDisplayPathNameFormatsLoadErrorNames() {
        Uri uri = Uri.parse("file:///sdcard/%09=payload%0Afolder");

        assertEquals("' =payload folder", FmViewModel.getDisplayPathName(Paths.get(uri)));
    }
}
