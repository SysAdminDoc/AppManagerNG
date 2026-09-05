// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainActivityFinderMenuContractTest {
    @Test
    public void finderRemainsAvailableFromStableMainMenu() throws IOException {
        Path appDir = findAppProjectDir();
        String menu = read(appDir.resolve("src/main/res/menu/activity_main_actions.xml"));
        String activity = read(appDir.resolve(
                "src/main/java/io/github/muntashirakon/AppManager/main/MainActivity.java"));

        assertTrue("The main menu must declare Finder",
                menu.contains("android:id=\"@+id/action_finder\""));
        assertTrue("The main menu must open FinderActivity",
                activity.contains("new Intent(this, FinderActivity.class)"));
        assertFalse("Finder must not be restricted to debug builds",
                activity.contains("finderMenu.setVisible(BuildConfig.DEBUG)"));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findAppProjectDir() throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cursor != null; i++) {
            if (Files.exists(cursor.resolve("src/main/AndroidManifest.xml"))) {
                return cursor;
            }
            Path appDir = cursor.resolve("app");
            if (Files.exists(appDir.resolve("src/main/AndroidManifest.xml"))) {
                return appDir;
            }
            cursor = cursor.getParent();
        }
        throw new IOException("Could not locate app/src/main/AndroidManifest.xml");
    }
}
