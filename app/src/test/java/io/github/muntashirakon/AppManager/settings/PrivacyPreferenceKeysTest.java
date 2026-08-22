// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.utils.AppPref;

public class PrivacyPreferenceKeysTest {
    private static final Pattern SWITCH_PATTERN = Pattern.compile(
            "<SwitchPreferenceCompat\\b(.*?)/>", Pattern.DOTALL);
    private static final Pattern KEY_PATTERN = Pattern.compile("app:key=\\\"([^\\\"]+)\\\"");
    private static final String[] MONITOR_KEYS = {
            "enable_permission_change_monitor",
            "enable_signing_cert_change_monitor",
            "enable_app_change_auditor",
            "enable_app_update_change_report"
    };

    @Test
    public void monitorSwitchesUseRegisteredPersistenceKeys() throws IOException {
        Path appDir = findAppProjectDir();
        String preferences = read(appDir.resolve("src/main/res/xml/preferences_privacy.xml"));
        String fragment = read(appDir.resolve(
                "src/main/java/io/github/muntashirakon/AppManager/settings/PrivacyPreferences.java"));

        for (String key : MONITOR_KEYS) {
            assertNotEquals("Preference key must be registered in AppPref: " + key,
                    -1, AppPref.PrefKey.indexOf(key));
            assertTrue(preferences.contains("app:key=\"" + key + "\""));
            assertTrue(fragment.contains("requirePreference(\"" + key + "\")"));
        }
        assertFalse(preferences.contains("app:key=\"permission_change_monitor\""));
        assertFalse(preferences.contains("app:key=\"signing_cert_change_monitor\""));
        assertFalse(preferences.contains("app:key=\"app_change_auditor\""));
        assertFalse(preferences.contains("app:key=\"app_update_change_report\""));
    }

    @Test
    public void everyPersistentPrivacySwitchUsesARegisteredKey() throws IOException {
        String preferences = read(findAppProjectDir().resolve("src/main/res/xml/preferences_privacy.xml"));
        Matcher switches = SWITCH_PATTERN.matcher(preferences);
        int persistentSwitches = 0;
        while (switches.find()) {
            String attributes = switches.group(1);
            if (attributes.contains("app:persistent=\"false\"")) {
                continue;
            }
            Matcher key = KEY_PATTERN.matcher(attributes);
            assertTrue("Persistent privacy switch is missing a key", key.find());
            assertNotEquals("Persistent privacy switch key must be registered in AppPref: " + key.group(1),
                    -1, AppPref.PrefKey.indexOf(key.group(1)));
            persistentSwitches++;
        }
        assertTrue("Expected the Privacy screen to contain persistent switches", persistentSwitches >= 8);
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
