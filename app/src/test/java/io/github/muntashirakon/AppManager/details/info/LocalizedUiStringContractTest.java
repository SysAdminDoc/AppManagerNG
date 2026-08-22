// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LocalizedUiStringContractTest {
    @Test
    public void targetedUiMessagesAndDefaultsUseResources() throws IOException {
        String appInfo = readSource("app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java");
        String newFolder = readSource("app/src/main/java/io/github/muntashirakon/AppManager/fm/dialogs/NewFolderDialogFragment.java");
        String newLink = readSource("app/src/main/java/io/github/muntashirakon/AppManager/fm/dialogs/NewSymbolicLinkDialogFragment.java");
        String strings = readSource("app/src/main/res/values/strings.xml");

        assertFalse(appInfo.contains("Error: "));
        assertFalse(appInfo.contains("No DEVICE_POWER permission."));
        assertFalse(appInfo.contains("No sensor permission."));
        assertFalse(appInfo.contains("No MANAGE_NETWORK_POLICY permission."));
        assertFalse(newFolder.contains("setText(\"New folder\")"));
        assertFalse(newLink.contains("setText(\"New link\")"));
        assertTrue(strings.contains("name=\"error_with_details\""));
        assertTrue(strings.contains("name=\"no_device_power_permission\""));
        assertTrue(strings.contains("name=\"no_sensor_permission\""));
        assertTrue(strings.contains("name=\"no_manage_network_policy_permission\""));
        assertTrue(strings.contains("name=\"new_folder_name\""));
        assertTrue(strings.contains("name=\"new_link_name\""));
    }

    private static String readSource(String relativePath) throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main"))
                    && Files.isDirectory(cursor.resolve("libcore"))) {
                return new String(Files.readAllBytes(cursor.resolve(relativePath)), StandardCharsets.UTF_8);
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
