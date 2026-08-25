// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ui;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class MultiSelectionLayoutContractTest {
    @Test
    public void selectionSurfacesAreAnchoredAboveBottomInsets() throws IOException {
        // Every layout that hosts a MultiSelectionView is checked, not a hardcoded list. The
        // Running Apps screen was missed by the original per-screen fix (issue #9) precisely
        // because a new selection surface can appear in any layout.
        Path layoutDir = findAppProjectDir().resolve("src/main/res/layout");
        List<Path> layouts = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(layoutDir, "*.xml")) {
            for (Path path : stream) {
                if (read(path).contains("io.github.muntashirakon.widget.MultiSelectionView")) {
                    layouts.add(path);
                }
            }
        }
        assertTrue("Expected layouts hosting MultiSelectionView", layouts.size() >= 7);
        for (Path layoutPath : layouts) {
            String layout = layoutPath.getFileName().toString();
            String xml = read(layoutPath);
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
