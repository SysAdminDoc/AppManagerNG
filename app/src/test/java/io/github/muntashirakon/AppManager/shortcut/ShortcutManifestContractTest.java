// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

public class ShortcutManifestContractTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String SOURCE_PACKAGE = "io.github.muntashirakon.AppManager";

    @Test
    public void staticShortcutTargetsResolveToExportedManifestComponents() throws Exception {
        Map<String, ManifestComponent> components = readManifestComponents();
        Document shortcuts = parse(findAppProjectDir().resolve("src/main/res/xml/shortcuts.xml"));
        NodeList intents = shortcuts.getElementsByTagName("intent");

        for (int i = 0; i < intents.getLength(); i++) {
            Element intent = (Element) intents.item(i);
            String targetClass = intent.getAttributeNS(ANDROID_NS, "targetClass");
            if (targetClass == null || targetClass.isEmpty()) {
                continue;
            }
            ManifestComponent component = components.get(normalizeComponentName(targetClass));
            assertNotNull("Missing manifest component for static shortcut target " + targetClass, component);
            assertTrue("Static shortcut target must be exported: " + targetClass, component.exported);

            String action = intent.getAttributeNS(ANDROID_NS, "action");
            if (ShortcutDispatchActivity.class.getName().equals(normalizeComponentName(targetClass))) {
                assertTrue("ShortcutDispatchActivity manifest filter must declare action " + action,
                        component.actions.contains(action));
            }
        }
    }

    @Test
    public void activityAliasesTargetRealActivities() throws Exception {
        Map<String, ManifestComponent> components = readManifestComponents();
        for (ManifestComponent component : components.values()) {
            if (component.targetActivity == null) {
                continue;
            }
            assertTrue("Missing target activity for alias " + component.name + ": " + component.targetActivity,
                    components.containsKey(component.targetActivity));
        }
    }

    @Test
    public void debloaterAliasIsExportedWithoutLauncherCategory() throws Exception {
        Map<String, ManifestComponent> components = readManifestComponents();
        ManifestComponent alias = components.get(SOURCE_PACKAGE + ".debloat.DebloaterActivityAlias");

        assertNotNull(alias);
        assertTrue(alias.exported);
        assertEquals(SOURCE_PACKAGE + ".debloat.DebloaterActivity", alias.targetActivity);
        assertFalse(alias.hasLauncherCategory);
    }

    private static Map<String, ManifestComponent> readManifestComponents() throws Exception {
        Document manifest = parse(findAppProjectDir().resolve("src/main/AndroidManifest.xml"));
        Map<String, ManifestComponent> components = new HashMap<>();
        addManifestComponents(manifest, components, "activity");
        addManifestComponents(manifest, components, "activity-alias");
        return components;
    }

    private static void addManifestComponents(@SuppressWarnings("SameParameterValue") Document manifest,
                                              Map<String, ManifestComponent> components,
                                              String tagName) {
        NodeList nodes = manifest.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = normalizeComponentName(element.getAttributeNS(ANDROID_NS, "name"));
            ManifestComponent component = new ManifestComponent(name);
            component.exported = Boolean.parseBoolean(element.getAttributeNS(ANDROID_NS, "exported"));
            String targetActivity = element.getAttributeNS(ANDROID_NS, "targetActivity");
            if (targetActivity != null && !targetActivity.isEmpty()) {
                component.targetActivity = normalizeComponentName(targetActivity);
            }
            NodeList categories = element.getElementsByTagName("category");
            for (int categoryIndex = 0; categoryIndex < categories.getLength(); categoryIndex++) {
                Element category = (Element) categories.item(categoryIndex);
                if ("android.intent.category.LAUNCHER".equals(category.getAttributeNS(ANDROID_NS, "name"))) {
                    component.hasLauncherCategory = true;
                }
            }
            NodeList actions = element.getElementsByTagName("action");
            for (int actionIndex = 0; actionIndex < actions.getLength(); actionIndex++) {
                Element action = (Element) actions.item(actionIndex);
                component.actions.add(action.getAttributeNS(ANDROID_NS, "name"));
            }
            components.put(name, component);
        }
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

    private static final class ManifestComponent {
        final String name;
        final Set<String> actions = new HashSet<>();
        boolean exported;
        boolean hasLauncherCategory;
        String targetActivity;

        private ManifestComponent(String name) {
            this.name = name;
        }
    }
}
