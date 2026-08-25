// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import io.github.muntashirakon.AppManager.utils.AppPref;

/**
 * Every settings screen installs {@link SettingsDataStore}, whose getters resolve keys through
 * {@link AppPref} and throw {@link IllegalArgumentException} for anything unregistered. A
 * persistent preference with an unregistered key therefore crashes its screen during inflation,
 * which is exactly how Settings &gt; Privacy crashed in v0.6.12 (issue #8). This test walks every
 * preference XML so no settings screen can reintroduce that crash.
 */
public class SettingsPreferenceKeyParityTest {
    /** Preference element types that read a persisted value while their screen inflates. */
    private static final Set<String> VALUE_BACKED_TYPES = new HashSet<>(Arrays.asList(
            "SwitchPreferenceCompat",
            "SwitchPreference",
            "CheckBoxPreference",
            "ListPreference",
            "DropDownPreference",
            "EditTextPreference",
            "SeekBarPreference",
            "io.github.muntashirakon.preference.TopSwitchPreference"));

    /**
     * The profile configuration screen persists through its own profile-scoped
     * {@code ConfDataStore}, not {@link SettingsDataStore}, so its keys are intentionally not in
     * {@link AppPref}. {@link #profileConfigScreenKeepsItsOwnDataStore()} pins that assumption.
     */
    private static final String PROFILE_CONFIG_XML = "preferences_profile_config.xml";

    @Test
    public void everyPersistentPreferenceUsesARegisteredKey() throws Exception {
        Path xmlDir = findAppProjectDir().resolve("src/main/res/xml");
        List<String> offenders = new ArrayList<>();
        List<Path> preferenceFiles = listPreferenceFiles(xmlDir);
        assertFalse("Expected preference XML files under " + xmlDir, preferenceFiles.isEmpty());
        preferenceFiles.removeIf(path -> PROFILE_CONFIG_XML.equals(path.getFileName().toString()));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        int checked = 0;
        for (Path file : preferenceFiles) {
            Document document = factory.newDocumentBuilder().parse(file.toFile());
            for (String type : VALUE_BACKED_TYPES) {
                NodeList elements = document.getElementsByTagName(type);
                for (int i = 0; i < elements.getLength(); i++) {
                    Element element = (Element) elements.item(i);
                    if ("false".equals(attribute(element, "persistent"))) {
                        continue;
                    }
                    String key = attribute(element, "key");
                    String where = file.getFileName() + ": <" + type + ">";
                    if (key == null || key.isEmpty()) {
                        offenders.add(where + " is persistent but has no key");
                        continue;
                    }
                    checked++;
                    if (AppPref.PrefKey.indexOf(key) == -1) {
                        offenders.add(where + " persists unregistered key \"" + key + "\"");
                    }
                }
            }
        }

        Collections.sort(offenders);
        assertTrue("Persistent preferences must use keys registered in AppPref, or set"
                + " app:persistent=\"false\" and manage state in the fragment:\n"
                + String.join("\n", offenders), offenders.isEmpty());
        assertTrue("Expected to check dozens of persistent preferences, found " + checked,
                checked >= 30);
    }

    @Test
    public void profileConfigScreenKeepsItsOwnDataStore() throws IOException {
        Path fragment = findAppProjectDir().resolve(
                "src/main/java/io/github/muntashirakon/AppManager/profiles/ConfPreferences.java");
        String contents = new String(Files.readAllBytes(fragment),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("ConfPreferences must keep binding preferences_profile_config.xml to its"
                        + " profile-scoped ConfDataStore; if this changes, remove the exclusion in"
                        + " everyPersistentPreferenceUsesARegisteredKey",
                contents.contains("R.xml.preferences_profile_config")
                        && contents.contains("setPreferenceDataStore(new ConfDataStore())"));
    }

    private static String attribute(Element element, String name) {
        if (element.hasAttribute("app:" + name)) {
            return element.getAttribute("app:" + name);
        }
        if (element.hasAttribute("android:" + name)) {
            return element.getAttribute("android:" + name);
        }
        return null;
    }

    private static List<Path> listPreferenceFiles(Path xmlDir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(xmlDir, "preferences_*.xml")) {
            for (Path path : stream) {
                files.add(path);
            }
        }
        Collections.sort(files);
        return files;
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
}
