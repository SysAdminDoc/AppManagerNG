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
 * A selectable text view ignores maxLines when it lays out, so a capped one wraps anyway and shows
 * half of the line that didn't fit. A capped view that isn't selectable cuts its text off without a
 * hint unless it also sets an ellipsis.
 */
public class ClippedTextContractTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test
    public void noTextViewCapsItsLinesWithoutEndingTheTextCleanly() throws Exception {
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
        List<String> clipped = new ArrayList<>();
        for (Path layout : layouts) {
            NodeList all = factory.newDocumentBuilder().parse(layout.toFile()).getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); ++i) {
                Element element = (Element) all.item(i);
                String tag = element.getTagName();
                if (!isCapped(element) || isEditable(tag)
                        // Scrolls inside its own box, so the rest of the text stays reachable
                        || "vertical".equals(element.getAttributeNS(ANDROID, "scrollbars"))) {
                    continue;
                }
                String where = layout.getFileName() + " " + element.getAttributeNS(ANDROID, "id");
                if ("true".equals(element.getAttributeNS(ANDROID, "textIsSelectable"))) {
                    clipped.add(where + " is selectable and capped");
                } else if (isText(tag) && element.getAttributeNS(ANDROID, "ellipsize").isEmpty()) {
                    clipped.add(where + " is capped with no ellipsis");
                }
            }
        }
        assertEquals(new ArrayList<String>(), clipped);
    }

    private static boolean isCapped(Element element) {
        return !element.getAttributeNS(ANDROID, "maxLines").isEmpty()
                || !element.getAttributeNS(ANDROID, "lines").isEmpty()
                || "true".equals(element.getAttributeNS(ANDROID, "singleLine"));
    }

    private static boolean isEditable(String tag) {
        // TextInputTextView is a read-only field, so it counts as display text
        return !tag.endsWith("TextInputTextView") && (tag.contains("EditText") || tag.contains("TextInput")
                || tag.contains("Search") || tag.contains("AutoComplete"));
    }

    private static boolean isText(String tag) {
        // A read-only field is still an EditText, so it scrolls inside its box when it isn't selectable
        return tag.endsWith("TextView") && !tag.endsWith("TextInputTextView")
                || tag.endsWith("Button") && !tag.contains("Image");
    }

    private static Path appDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        return new File(cwd.toFile(), "src/main/res").isDirectory() ? cwd : cwd.resolve("app");
    }
}
