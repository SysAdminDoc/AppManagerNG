// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Pins the ported Wireless-Debugging instruction improvements (upstream 133b5acb7f): the
 * connect/pair dialogs use dedicated titles/messages and the developer-options intents
 * deep-link straight to the Wireless-Debugging toggle. Source-scan so it stays valid without
 * a device.
 */
public class WirelessDebuggingInstructionsContractTest {
    @Test
    public void opsDialogsUseDedicatedCopyAndDeepLink() throws IOException {
        String ops = read("app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java");
        assertTrue("connect dialog must use the dedicated wireless-debugging title",
                ops.contains("R.string.manual_wireless_debugging_title"));
        assertTrue("connect dialog must use the dedicated instructions",
                ops.contains("R.string.manual_wireless_debugging_instructions"));
        assertTrue("pair dialog must use the dedicated pairing title",
                ops.contains("R.string.adb_pairing_title"));
        assertTrue("developer-options intent must deep-link to the wireless-debugging toggle",
                ops.contains("\":settings:fragment_args_key\", \"toggle_adb_wireless\""));
    }

    @Test
    public void newStringsAreDefined() throws IOException {
        String strings = read("app/src/main/res/values/strings.xml");
        assertTrue(strings.contains("name=\"adb_pairing_title\""));
        assertTrue(strings.contains("name=\"manual_wireless_debugging_title\""));
        assertTrue(strings.contains("name=\"manual_wireless_debugging_instructions\""));
    }

    private static String read(String relativePath) throws IOException {
        Path root = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && root != null; i++) {
            if (Files.exists(root.resolve("settings.gradle"))) {
                break;
            }
            root = root.getParent();
        }
        return new String(Files.readAllBytes(root.resolve(relativePath)), StandardCharsets.UTF_8);
    }
}
