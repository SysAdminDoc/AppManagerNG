// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SearchControlAccessibilityContractTest {
    @Test
    public void helpSearchNavigationButtonsHaveAccessibleLabels() throws IOException {
        String layout = read(findAppProjectDir().resolve("src/main/res/layout/activity_help.xml"));

        assertControlContentDescription(layout, "previous_button", "@string/find_previous_match");
        assertControlContentDescription(layout, "next_button", "@string/find_next_match");
    }

    @Test
    public void codeEditorSearchActionsHaveAccurateAccessibleLabels() throws IOException {
        String layout = read(findAppProjectDir().resolve("src/main/res/layout/fragment_code_editor.xml"));

        assertControlContentDescription(layout, "replace_all_button", "@string/action_replace_all");
        assertControlContentDescription(layout, "replace_button", "@string/replace");
        assertControlContentDescription(layout, "previous_button", "@string/find_previous_match");
        assertControlContentDescription(layout, "next_button", "@string/find_next_match");
        assertControlContentDescription(layout, "lock", "@string/editor_lock");
    }

    private static void assertControlContentDescription(String layout,
                                                        String viewId,
                                                        String expectedDescription) {
        String marker = "android:id=\"@+id/" + viewId + "\"";
        int start = layout.indexOf(marker);
        assertTrue("Missing view id " + viewId, start >= 0);
        int end = layout.indexOf("/>", start);
        assertTrue("Missing self-closing view block for " + viewId, end > start);
        String block = layout.substring(start, end);
        assertTrue(viewId + " should use " + expectedDescription,
                block.contains("android:contentDescription=\"" + expectedDescription + "\""));
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
