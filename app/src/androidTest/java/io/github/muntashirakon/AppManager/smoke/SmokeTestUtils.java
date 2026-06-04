// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.smoke;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

final class SmokeTestUtils {
    static final String TARGET_PACKAGE = "io.github.sysadmindoc.AppManagerNG.debug";

    private static final long LAUNCH_TIMEOUT_MILLIS = 15_000L;
    private static final long POLL_INTERVAL_MILLIS = 250L;

    private SmokeTestUtils() {
    }

    @NonNull
    static UiDevice device() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    static void launchMain(@NonNull UiDevice device) {
        Context context = ApplicationProvider.getApplicationContext();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(TARGET_PACKAGE);
        if (launchIntent == null) {
            throw new AssertionError("Launch intent must resolve for AppManagerNG debug.");
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        device.pressHome();
        context.startActivity(launchIntent);
        waitForTargetWindow(device);
    }

    static void launchTargetActivity(@NonNull UiDevice device, @NonNull String className) {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent();
        intent.setClassName(TARGET_PACKAGE, className);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        device.pressHome();
        context.startActivity(intent);
        waitForTargetWindow(device);
    }

    @NonNull
    static UiObject2 waitForObject(@NonNull UiDevice device, @NonNull BySelector selector,
                                   @NonNull String description) {
        UiObject2 object = device.wait(Until.findObject(selector), LAUNCH_TIMEOUT_MILLIS);
        if (object == null) {
            throw new AssertionError("Timed out waiting for " + description + ".");
        }
        return object;
    }

    @Nullable
    static UiObject2 waitForFirstChild(@NonNull UiObject2 parent, @NonNull String description) {
        long deadline = System.currentTimeMillis() + LAUNCH_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (parent.getChildCount() > 0) {
                return parent.getChildren().get(0);
            }
            sleep(POLL_INTERVAL_MILLIS);
        }
        throw new AssertionError("Timed out waiting for " + description + ".");
    }

    static void waitForTargetWindow(@NonNull UiDevice device) {
        Boolean opened = device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)),
                LAUNCH_TIMEOUT_MILLIS);
        if (!Boolean.TRUE.equals(opened)) {
            throw new AssertionError("AppManagerNG did not open its main window.");
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for UI state.", e);
        }
    }
}
