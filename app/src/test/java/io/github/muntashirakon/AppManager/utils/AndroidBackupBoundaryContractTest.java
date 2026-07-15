// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AndroidBackupBoundaryContractTest {
    private static final String[] DEVICE_LOCAL_SHARED_PREFS = {
            "device_local_secrets.xml",
            "keystore.xml",
            "io.github.sysadmindoc.AppManagerNG.batch_ops_journal.xml",
            "installer_state.xml",
            "server_secrets.xml"
    };

    @Test
    public void cloudTransferAndLegacyRulesExcludeDeviceLocalState() throws IOException {
        Path appDir = findAppProjectDir();
        String modernRules = read(appDir.resolve("src/main/res/xml/backup_rules.xml"));
        String legacyRules = read(appDir.resolve("src/main/res/xml/full_backup_rules.xml"));
        for (String fileName : DEVICE_LOCAL_SHARED_PREFS) {
            String exclusion = "<exclude domain=\"sharedpref\" path=\"" + fileName + "\" />";
            assertEquals(fileName, 2, occurrences(modernRules, exclusion));
            assertEquals(fileName, 1, occurrences(legacyRules, exclusion));
        }
        // The BKS file is intentionally portable: its Android-keystore-wrapped password is not.
        // A transferred keystore therefore requires the recovery password instead of silently
        // carrying device-bound key material.
        assertFalse(modernRules.contains("path=\"am_keystore.bks\""));
        assertFalse(legacyRules.contains("path=\"am_keystore.bks\""));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            ++count;
            index += needle.length();
        }
        return count;
    }

    private static Path findAppProjectDir() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("src/main/res"))) return cursor;
            Path appDir = cursor.resolve("app");
            if (Files.isDirectory(appDir.resolve("src/main/res"))) return appDir;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate app project directory");
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
