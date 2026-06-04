// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sysadmindoc.appmanagerng.benchmark;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.benchmark.macro.MacrobenchmarkScope;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

final class BenchmarkJourneys {
    private static final long WINDOW_WAIT_MILLIS = 15_000L;
    private static final long IDLE_WAIT_MILLIS = 2_000L;
    private static final String SETTINGS_ACTIVITY =
            "io.github.muntashirakon.AppManager.settings.SettingsActivity";

    private BenchmarkJourneys() {
    }

    static void launchMainList(@NonNull MacrobenchmarkScope scope) {
        scope.pressHome();
        scope.startActivityAndWait();
        waitForTargetWindow(scope.getDevice());
        waitForObject(scope.getDevice(), By.res(BenchmarkConfig.TARGET_PACKAGE, "item_list"),
                "main app list");
    }

    static void scrollMainList(@NonNull UiDevice device) {
        UiObject2 list = waitForObject(device, By.res(BenchmarkConfig.TARGET_PACKAGE, "item_list"),
                "main app list");
        // Exercise both directions so frame timing captures bind and reuse work
        // without leaving later iterations at the end of the list.
        list.scroll(Direction.DOWN, 0.85f);
        device.waitForIdle(IDLE_WAIT_MILLIS);
        list.scroll(Direction.UP, 0.85f);
        device.waitForIdle(IDLE_WAIT_MILLIS);
    }

    static Intent backupSettingsIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClassName(BenchmarkConfig.TARGET_PACKAGE, SETTINGS_ACTIVITY);
        intent.setData(Uri.parse("app-manager://settings/backup_restore_prefs"));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    static void waitForBackupSettings(@NonNull UiDevice device) {
        waitForTargetWindow(device);
        waitForObject(device, By.res(BenchmarkConfig.TARGET_PACKAGE, "main_layout"),
                "settings fragment container");
        waitForObject(device, By.res(BenchmarkConfig.TARGET_PACKAGE, "recycler_view"),
                "backup preferences list");
    }

    static void waitForTargetWindow(@NonNull UiDevice device) {
        Boolean opened = device.wait(
                Until.hasObject(By.pkg(BenchmarkConfig.TARGET_PACKAGE).depth(0)),
                WINDOW_WAIT_MILLIS);
        if (!Boolean.TRUE.equals(opened)) {
            throw new AssertionError("AppManagerNG did not open its target window.");
        }
    }

    @NonNull
    private static UiObject2 waitForObject(@NonNull UiDevice device, @NonNull BySelector selector,
                                           @NonNull String description) {
        UiObject2 object = device.wait(Until.findObject(selector), WINDOW_WAIT_MILLIS);
        if (object == null) {
            throw new AssertionError("Timed out waiting for " + description + ".");
        }
        return object;
    }
}
