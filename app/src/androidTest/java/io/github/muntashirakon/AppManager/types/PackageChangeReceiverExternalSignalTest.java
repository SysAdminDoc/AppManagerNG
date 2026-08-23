// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class PackageChangeReceiverExternalSignalTest {
    @Test
    public void shellUidCannotInvokePrivatePackageSignal() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        CountDownLatch localDelivery = new CountDownLatch(1);
        AtomicInteger deliveries = new AtomicInteger();
        PackageChangeReceiver receiver = new PackageChangeReceiver(context) {
            @Override
            protected void onPackageChanged(Intent intent, @Nullable Integer uid,
                                            @Nullable String[] packages) {
                deliveries.incrementAndGet();
                localDelivery.countDown();
            }
        };
        try {
            String command = "am broadcast --user current"
                    + " -a " + PackageChangeReceiver.ACTION_PACKAGE_ALTERED
                    + " -p " + context.getPackageName()
                    + " --esa " + Intent.EXTRA_CHANGED_PACKAGE_LIST + " com.example.external";
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .executeShellCommand(command);

            assertFalse(localDelivery.await(1500L, TimeUnit.MILLISECONDS));
            assertEquals(0, deliveries.get());

            context.sendBroadcast(new Intent(PackageChangeReceiver.ACTION_PACKAGE_ALTERED)
                    .setPackage(context.getPackageName())
                    .putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST,
                            new String[]{"com.example.local"}));
            assertTrue(localDelivery.await(5L, TimeUnit.SECONDS));
            assertEquals(1, deliveries.get());
        } finally {
            receiver.close();
        }
    }
}
