// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AppDetailsDiagnosticsContractTest {
    @Test
    public void appDetailsViewModelFailuresUseAppLogger() throws IOException {
        String source = read(findRepoRoot().resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/details/AppDetailsViewModel.java"));

        assertFalse("App Details background loaders should log through the app logger, not stderr",
                source.contains("printStackTrace()"));
        assertTrue("App Details background loaders should retain diagnostic logging",
                source.contains("Log.e(TAG, e);") || source.contains("Log.e(TAG, th);"));
    }

    @Test
    public void permissionSettingsFailuresUseLocalizedRecoveryCopy() throws IOException {
        Path repoRoot = findRepoRoot();
        String source = read(repoRoot.resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/details/AppDetailsPermissionsFragment.java"));
        String strings = read(repoRoot.resolve("app/src/main/res/values/strings.xml"));

        assertFalse("Permission settings failures should not expose raw platform exception text",
                source.contains("getLocalizedMessage()"));
        assertTrue("Permission settings failures should use localized recovery copy",
                source.contains("R.string.permission_settings_unavailable"));
        assertTrue("Permission settings recovery copy should be defined",
                strings.contains("name=\"permission_settings_unavailable\""));
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main/res"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
