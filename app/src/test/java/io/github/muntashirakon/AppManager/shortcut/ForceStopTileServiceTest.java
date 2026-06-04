// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ForceStopTileServiceTest {
    @Test
    public void supportsRequestAddTileOnlyOnTiramisuPlus() {
        assertFalse(ForceStopTileService.supportsRequestAddTile(Build.VERSION_CODES.S_V2));
        assertTrue(ForceStopTileService.supportsRequestAddTile(Build.VERSION_CODES.TIRAMISU));
        assertTrue(ForceStopTileService.supportsRequestAddTile(Build.VERSION_CODES.UPSIDE_DOWN_CAKE));
    }

    @Test
    public void tileComponentTargetsForceStopTileService() {
        Context context = ApplicationProvider.getApplicationContext();

        assertEquals(ForceStopTileService.class.getName(),
                ForceStopTileService.getTileComponentName(context).getClassName());
    }
}
