// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * In no-root mode the platform installer reports back through a broadcast, and the receiver waiting
 * for it is registered at runtime. Android only hands a broadcast to a runtime receiver when the
 * broadcast names no component, so a callback naming the receiver class reached nobody and every
 * no-root install and uninstall waited on a prompt that never came (issue #21). Robolectric delivers
 * such broadcasts anyway, which is why this checks the intent itself.
 */
@RunWith(RobolectricTestRunner.class)
public class PackageInstallerCallbackIntentTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void theCallbackCanReachTheReceiverRegisteredAtRuntime() {
        Intent callback = PackageInstallerCompat.newInstallerCallbackIntent(mContext);

        assertNull(callback.getComponent());
        assertEquals(mContext.getPackageName(), callback.getPackage());
        assertTrue(new IntentFilter(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER)
                .match(mContext.getContentResolver(), callback, false, "test") >= 0);
    }

    @Test
    public void whatThePlatformFillsInCannotSendTheCallbackElsewhere() {
        Intent callback = PackageInstallerCompat.newInstallerCallbackIntent(mContext);
        Intent filled = new Intent("com.example.OTHER")
                .setComponent(new ComponentName("com.example", "com.example.Receiver"))
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION);
        filled.setSelector(new Intent("com.example.SELECTOR"));

        // The PendingIntent is created without fill-in flags, so this is all a sender can change
        callback.fillIn(filled, 0);

        assertEquals(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER, callback.getAction());
        assertEquals(mContext.getPackageName(), callback.getPackage());
        assertNull(callback.getComponent());
        assertNull(callback.getSelector());
        assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION,
                callback.getIntExtra(PackageInstaller.EXTRA_STATUS, -1));
    }
}
