// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ui;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MultiSelectionLayoutContractTest {
    private static final String[] LAYOUTS = {
            "activity_debloater.xml",
            "activity_finder.xml",
            "activity_main.xml",
            "activity_main_v2.xml",
            "fragment_fm.xml",
            "fragment_logcat.xml"
    };

    @Test
    public void selectionSurfacesAreAnchoredAboveBottomInsets() throws IOException {
        Path layoutDir = findAppProjectDir().resolve("src/main/res/layout");
        for (String layout : LAYOUTS) {
            String xml = read(layoutDir.resolve(layout));
            int viewStart = xml.indexOf("io.github.muntashirakon.widget.MultiSelectionView");
            int viewEnd = xml.indexOf("/>", viewStart);
            assertTrue("Missing selection surface in " + layout, viewStart >= 0 && viewEnd > viewStart);
            String view = xml.substring(viewStart, viewEnd);
            assertTrue("Selection surface must be bottom anchored in " + layout,
                    view.contains("android:layout_gravity=\"bottom|center_horizontal\""));
            assertTrue("Selection surface must consume navigation insets in " + layout,
                    view.contains("android:fitsSystemWindows=\"true\""));
        }
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
