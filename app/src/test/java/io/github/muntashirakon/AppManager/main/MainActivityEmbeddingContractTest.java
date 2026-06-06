// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivityEmbeddingContractTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String WINDOW_NS = "http://schemas.android.com/apk/res-auto";
    private static final String SOURCE_PACKAGE = "io.github.muntashirakon.AppManager";
    private static final String MAIN_ACTIVITY = SOURCE_PACKAGE + ".main.MainActivity";
    private static final String APP_DETAILS_ACTIVITY = SOURCE_PACKAGE + ".details.AppDetailsActivity";
    private static final String PLACEHOLDER_ACTIVITY = SOURCE_PACKAGE + ".main.MainSplitPlaceholderActivity";

    @Test
    public void mainDetailsSplitPairTargetsCurrentActivities() throws Exception {
        Document splits = parse(findAppProjectDir().resolve("src/main/res/xml/main_activity_splits.xml"));
        Element splitPairRule = getOnlyElement(splits, "SplitPairRule");
        Element splitPairFilter = getOnlyElement(splitPairRule, "SplitPairFilter");

        assertEquals("false", splitPairRule.getAttributeNS(WINDOW_NS, "clearTop"));
        assertEquals("never", splitPairRule.getAttributeNS(WINDOW_NS, "finishPrimaryWithSecondary"));
        assertEquals("always", splitPairRule.getAttributeNS(WINDOW_NS, "finishSecondaryWithPrimary"));
        assertEquals("locale", splitPairRule.getAttributeNS(WINDOW_NS, "splitLayoutDirection"));
        assertEquals("alwaysAllow", splitPairRule.getAttributeNS(WINDOW_NS, "splitMaxAspectRatioInPortrait"));
        assertEquals("900", splitPairRule.getAttributeNS(WINDOW_NS, "splitMinWidthDp"));
        assertEquals("0.42", splitPairRule.getAttributeNS(WINDOW_NS, "splitRatio"));
        assertTrue(Integer.parseInt(splitPairRule.getAttributeNS(WINDOW_NS, "splitMinWidthDp"))
                >= WindowWidthSizeClass.EXPANDED_MIN_DP);

        assertEquals(MAIN_ACTIVITY, splitPairFilter.getAttributeNS(WINDOW_NS, "primaryActivityName"));
        assertEquals(APP_DETAILS_ACTIVITY, splitPairFilter.getAttributeNS(WINDOW_NS, "secondaryActivityName"));
    }

    @Test
    public void mainPlaceholderTargetsMainActivityOnly() throws Exception {
        Document splits = parse(findAppProjectDir().resolve("src/main/res/xml/main_activity_splits.xml"));
        Element placeholderRule = getOnlyElement(splits, "SplitPlaceholderRule");
        Element activityFilter = getOnlyElement(placeholderRule, "ActivityFilter");

        assertEquals(PLACEHOLDER_ACTIVITY, placeholderRule.getAttributeNS(WINDOW_NS, "placeholderActivityName"));
        assertEquals("locale", placeholderRule.getAttributeNS(WINDOW_NS, "splitLayoutDirection"));
        assertEquals("alwaysAllow", placeholderRule.getAttributeNS(WINDOW_NS, "splitMaxAspectRatioInPortrait"));
        assertEquals("900", placeholderRule.getAttributeNS(WINDOW_NS, "splitMinWidthDp"));
        assertEquals("0.42", placeholderRule.getAttributeNS(WINDOW_NS, "splitRatio"));
        assertEquals("false", placeholderRule.getAttributeNS(WINDOW_NS, "stickyPlaceholder"));
        assertEquals(MAIN_ACTIVITY, activityFilter.getAttributeNS(WINDOW_NS, "activityName"));
    }

    @Test
    public void embeddedActivitiesHaveExpectedManifestExposure() throws Exception {
        Document manifest = parse(findAppProjectDir().resolve("src/main/AndroidManifest.xml"));

        Element main = findManifestActivity(manifest, MAIN_ACTIVITY);
        Element appDetails = findManifestActivity(manifest, APP_DETAILS_ACTIVITY);
        Element placeholder = findManifestActivity(manifest, PLACEHOLDER_ACTIVITY);

        assertEquals("true", main.getAttributeNS(ANDROID_NS, "exported"));
        assertEquals("false", appDetails.getAttributeNS(ANDROID_NS, "exported"));
        assertEquals("false", placeholder.getAttributeNS(ANDROID_NS, "exported"));
    }

    private static Element findManifestActivity(Document manifest, String activityName) {
        NodeList activities = manifest.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (activityName.equals(normalizeComponentName(activity.getAttributeNS(ANDROID_NS, "name")))) {
                return activity;
            }
        }
        throw new AssertionError("Missing manifest activity " + activityName);
    }

    private static Element getOnlyElement(Document document, String tagName) {
        return getOnlyElement(document.getDocumentElement(), tagName);
    }

    private static Element getOnlyElement(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        assertEquals("Expected exactly one " + tagName, 1, nodes.getLength());
        Element element = (Element) nodes.item(0);
        assertNotNull(element);
        return element;
    }

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static String normalizeComponentName(String name) {
        if (name.startsWith(".")) {
            return SOURCE_PACKAGE + name;
        }
        if (name.contains(".")) {
            return name;
        }
        return SOURCE_PACKAGE + "." + name;
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
