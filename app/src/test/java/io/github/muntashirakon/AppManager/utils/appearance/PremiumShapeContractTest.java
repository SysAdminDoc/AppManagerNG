// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PremiumShapeContractTest {
    @Test
    public void v2ControlResourcesDoNotUseDeprecatedPillToken() throws IOException {
        Path appDir = findAppProjectDir();
        String resources = read(appDir.resolve("src/main/res/values/dimens-v2.xml"))
                + read(appDir.resolve("src/main/res/values/themes-v2.xml"))
                + read(appDir.resolve("src/main/res/values/styles.xml"))
                + read(appDir.resolve("src/main/res/values/strings.xml"));

        assertTrue(resources.contains("premium_radius_control"));
        assertFalse(resources.contains("premium_radius_pill"));
        assertFalse(resources.contains("ShapeAppearance.AppTheme.V2.Pill"));
        assertFalse(resources.contains("pill-shaped"));
    }

    private static Path findAppProjectDir() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("src/main/res"))) {
                return cursor;
            }
            Path appDir = cursor.resolve("app");
            if (Files.isDirectory(appDir.resolve("src/main/res"))) {
                return appDir;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate app project directory");
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
