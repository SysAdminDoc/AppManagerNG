// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowToast;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class AppInfoFragmentCacheClearTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void storageIsReadAgainOnlyAfterAConfirmedClear() {
        int[] reloads = new int[1];

        AppInfoFragment.finishCacheClear(true, () -> ++reloads[0]);

        assertEquals(1, reloads[0]);
        assertEquals(mContext.getString(R.string.done), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void aFailedClearKeepsTheLastTotalsShown() {
        AppInfoFragment.finishCacheClear(false, () -> fail("storage was read again after a failed clear"));

        assertEquals(mContext.getString(R.string.failed), ShadowToast.getTextOfLatestToast());
    }
}
