// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Privacy settings wires {@link SettingsDataStore}, which rejects any preference
 * {@code app:key} that is not registered in {@link io.github.muntashirakon.AppManager.utils.AppPref}.
 * A mismatch crashes the screen on open with {@code IllegalArgumentException: Invalid key}.
 */
public class PrivacyPreferencesKeyParityTest {
    private static final String APP_NS = "http://schemas.android.com/apk/res-auto";
    private static final Pattern PREF_ENUM = Pattern.compile(
            "PREF_[A-Z0-9_]+_(BOOL|INT|LONG|FLOAT|STR)");

    @Test
    public void privacySwitchKeysAreRegisteredInAppPref() throws Exception {
        Set<String> appPrefKeys = loadAppPrefKeys();
        Document privacy = parse(findProjectRoot().resolve("app/src/main/res/xml/preferences_privacy.xml"));
        NodeList switches = privacy.getElementsByTagName("SwitchPreferenceCompat");
        for (int i = 0; i < switches.getLength(); ++i) {
            Element pref = (Element) switches.item(i);
            String key = pref.getAttributeNS(APP_NS, "key");
            if (key.isEmpty()) {
                continue;
            }
            boolean persistent = !"false".equals(pref.getAttributeNS(APP_NS, "persistent"));
            if (!persistent) {
                // e.g. toggle_internet is bound manually and does not use SettingsDataStore.
                continue;
            }
            assertTrue("Privacy switch key '" + key + "' must exist in AppPref or opening"
                            + " Settings > Privacy crashes with IllegalArgumentException",
                    appPrefKeys.contains(key));
        }
    }

    private static Set<String> loadAppPrefKeys() throws IOException {
        Path appPref = findProjectRoot().resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/utils/AppPref.java");
        String source = new String(Files.readAllBytes(appPref), StandardCharsets.UTF_8);
        Matcher matcher = PREF_ENUM.matcher(source);
        Set<String> keys = new HashSet<>();
        while (matcher.find()) {
            String enumName = matcher.group();
            int typeSep = enumName.lastIndexOf('_');
            keys.add(enumName.substring("PREF_".length(), typeSep).toLowerCase(Locale.ROOT));
        }
        return keys;
    }

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static Path findProjectRoot() throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cursor != null; i++) {
            if (Files.exists(cursor.resolve("settings.gradle"))
                    && Files.exists(cursor.resolve("app/src/main/AndroidManifest.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        fail("Could not locate AppManagerNG project root");
        return cursor;
    }
}
