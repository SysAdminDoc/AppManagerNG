// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.muntashirakon.AppManager.logcat.helper.WidgetHelper;
import io.github.muntashirakon.AppManager.oneclickops.ClearCacheAppWidget;
import io.github.muntashirakon.AppManager.usage.DataUsageAppWidget;
import io.github.muntashirakon.AppManager.usage.ScreenTimeAppWidget;

@RunWith(RobolectricTestRunner.class)
public class WidgetThemeRefreshTest {
    @Test
    public void getNightModeMask_ignoresUnrelatedConfigurationBits() {
        Configuration configuration = new Configuration();
        configuration.uiMode = Configuration.UI_MODE_TYPE_DESK | Configuration.UI_MODE_NIGHT_YES;

        assertEquals(Configuration.UI_MODE_NIGHT_YES, AppManager.getNightModeMask(configuration));

        configuration.uiMode = Configuration.UI_MODE_TYPE_CAR | Configuration.UI_MODE_NIGHT_NO;
        assertEquals(Configuration.UI_MODE_NIGHT_NO, AppManager.getNightModeMask(configuration));
    }

    @Test
    public void refreshAllHelpers_handleNoInstalledWidgets() {
        Context context = RuntimeEnvironment.getApplication();

        ScreenTimeAppWidget.updateWidgets(context);
        DataUsageAppWidget.updateWidgets(context);
        ClearCacheAppWidget.updateWidgets(context);
        WidgetHelper.updateWidgets(context, false);
    }

    @Test
    public void applicationRefreshesEveryWidgetOffMainThreadOnNightModeChange() throws IOException {
        String source = readRepoFile("app/src/main/java/io/github/muntashirakon/AppManager/AppManager.java");

        assertTrue(source.contains("public void onConfigurationChanged(@NonNull Configuration newConfig)"));
        assertTrue(source.contains("if (nightModeMask == mNightModeMask)"));
        assertTrue(source.contains("ThreadUtils.postOnBackgroundThread(() -> refreshWidgetsForTheme(this))"));
        assertTrue(source.contains("ScreenTimeAppWidget.updateWidgets(context);"));
        assertTrue(source.contains("DataUsageAppWidget.updateWidgets(context);"));
        assertTrue(source.contains("ClearCacheAppWidget.updateWidgets(context);"));
        assertTrue(source.contains("WidgetHelper.updateWidgets(context);"));
        assertFalse(source.contains("Intent.ACTION_CONFIGURATION_CHANGED"));
    }

    private static String readRepoFile(String relativePath) throws IOException {
        return new String(Files.readAllBytes(findRepoRoot().resolve(relativePath)), StandardCharsets.UTF_8);
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main/java"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
