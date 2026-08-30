// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

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
    private static final String EXTERNAL_PACKAGE =
            "io.github.sysadmindoc.appmanagerng.signaltestapp";
    private static final String EXTERNAL_COMPONENT = EXTERNAL_PACKAGE
            + "/.SignalSenderActivity";

    @Test
    public void separatelyPackagedAppCannotInvokePrivatePackageSignal() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        int targetUid = context.getApplicationInfo().uid;
        int externalUid = context.getPackageManager()
                .getApplicationInfo(EXTERNAL_PACKAGE, 0).uid;
        assertNotEquals(targetUid, externalUid);
        assertEquals(PackageManager.PERMISSION_DENIED, context.getPackageManager().checkPermission(
                PackageChangeReceiver.INTERNAL_BROADCAST_PERMISSION, EXTERNAL_PACKAGE));
        CountDownLatch externalDelivery = new CountDownLatch(1);
        CountDownLatch localDelivery = new CountDownLatch(1);
        AtomicInteger externalDeliveries = new AtomicInteger();
        AtomicInteger localDeliveries = new AtomicInteger();
        PackageChangeReceiver receiver = new PackageChangeReceiver(context) {
            @Override
            protected void onPackageChanged(Intent intent, @Nullable Integer uid,
                                            @Nullable String[] packages) {
                if (packages == null || packages.length == 0) return;
                if ("com.example.external".equals(packages[0])) {
                    externalDeliveries.incrementAndGet();
                    externalDelivery.countDown();
                } else if ("com.example.local".equals(packages[0])) {
                    localDeliveries.incrementAndGet();
                    localDelivery.countDown();
                }
            }
        };
        try {
            String command = "am start -W --user current"
                    + " -n " + EXTERNAL_COMPONENT
                    + " --es target_package " + context.getPackageName()
                    + " --es target_action " + PackageChangeReceiver.ACTION_PACKAGE_ALTERED
                    + " --es changed_package com.example.external";
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    .executeShellCommand(command);

            assertFalse(externalDelivery.await(1500L, TimeUnit.MILLISECONDS));
            assertEquals(0, externalDeliveries.get());

            context.sendBroadcast(new Intent(PackageChangeReceiver.ACTION_PACKAGE_ALTERED)
                    .setPackage(context.getPackageName())
                    .putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST,
                            new String[]{"com.example.local"}));
            assertTrue(localDelivery.await(5L, TimeUnit.SECONDS));
            assertEquals(1, localDeliveries.get());
        } finally {
            receiver.close();
        }
    }
}
