// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Every image control says what it is or is marked decorative. Lint's ContentDescription check
 * only looks at plain ImageView and ImageButton tags, so AppCompat and Material image views slip
 * past it, and this test covers them.
 */
public class ImageLabelContractTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String TOOLS = "http://schemas.android.com/tools";
    private static final String[] IMAGE_TAGS = {
            "ImageView", "ImageButton", "AppCompatImageView", "AppCompatImageButton", "ShapeableImageView",
    };

    @Test
    public void everyImageControlHasALabelOrIsDecorative() throws Exception {
        Path res = appDir().resolve("src/main/res");
        List<Path> layouts = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(res)) {
            for (Path dir : (Iterable<Path>) dirs::iterator) {
                if (!dir.getFileName().toString().startsWith("layout")) continue;
                try (Stream<Path> files = Files.list(dir)) {
                    files.filter(p -> p.toString().endsWith(".xml")).forEach(layouts::add);
                }
            }
        }
        assertTrue(layouts.size() > 100);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        List<String> unlabeled = new ArrayList<>();
        int images = 0;
        for (Path layout : layouts) {
            NodeList all = factory.newDocumentBuilder().parse(layout.toFile()).getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); ++i) {
                Element element = (Element) all.item(i);
                if (!isImageControl(element.getTagName())) continue;
                ++images;
                if (element.hasAttributeNS(ANDROID, "contentDescription")
                        || element.hasAttributeNS(ANDROID, "importantForAccessibility")) {
                    continue;
                }
                // A suppression hides the control from TalkBack users just the same
                String where = res.relativize(layout).toString().replace(File.separatorChar, '/')
                        + " " + element.getAttributeNS(ANDROID, "id");
                unlabeled.add(element.getAttributeNS(TOOLS, "ignore").contains("ContentDescription")
                        ? where + " (tools:ignore)" : where);
            }
        }
        assertTrue(images > 50);
        assertEquals("Add android:contentDescription, or android:importantForAccessibility=\"no\" when "
                + "the image is decorative", new ArrayList<String>(), unlabeled);
    }

    private static boolean isImageControl(String tagName) {
        String simpleName = tagName.substring(tagName.lastIndexOf('.') + 1);
        for (String tag : IMAGE_TAGS) {
            if (tag.equals(simpleName)) return true;
        }
        return false;
    }

    private static Path appDir() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("src/main/res"))) {
            if (Files.isDirectory(cursor.resolve("app/src/main/res"))) {
                return cursor.resolve("app");
            }
            cursor = cursor.getParent();
        }
        return cursor;
    }
}
