// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RoutinePackageChangeReceiverTest {
    @Test
    public void resolveTriggerTypeMapsPackageBroadcasts() {
        assertEquals(Integer.valueOf(ProfileTrigger.TYPE_ON_APP_INSTALL),
                RoutinePackageChangeReceiver.resolveTriggerType(packageIntent(Intent.ACTION_PACKAGE_ADDED, false)));
        assertEquals(Integer.valueOf(ProfileTrigger.TYPE_ON_APP_UPDATE),
                RoutinePackageChangeReceiver.resolveTriggerType(packageIntent(Intent.ACTION_PACKAGE_ADDED, true)));
        assertEquals(Integer.valueOf(ProfileTrigger.TYPE_ON_APP_UPDATE),
                RoutinePackageChangeReceiver.resolveTriggerType(packageIntent(Intent.ACTION_PACKAGE_REPLACED, false)));
        assertEquals(Integer.valueOf(ProfileTrigger.TYPE_ON_APP_UNINSTALL),
                RoutinePackageChangeReceiver.resolveTriggerType(packageIntent(Intent.ACTION_PACKAGE_REMOVED, false)));
        assertNull(RoutinePackageChangeReceiver.resolveTriggerType(
                packageIntent(Intent.ACTION_PACKAGE_REMOVED, true)));
    }

    @Test
    public void extractPackageNameReadsPackageUri() {
        Intent intent = packageIntent(Intent.ACTION_PACKAGE_ADDED, false);
        assertEquals("com.example.app", RoutinePackageChangeReceiver.extractPackageName(intent));
    }

    private static Intent packageIntent(String action, boolean replacing) {
        Intent intent = new Intent(action, Uri.parse("package:com.example.app"));
        intent.putExtra(Intent.EXTRA_REPLACING, replacing);
        return intent;
    }
}
