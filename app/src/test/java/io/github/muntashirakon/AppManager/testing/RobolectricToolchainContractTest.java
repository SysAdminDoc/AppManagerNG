// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.testing;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RobolectricToolchainContractTest {
    @Test
    public void sdk36TestsRequireJdk21() throws IOException {
        Path root = findRepoRoot();
        String properties = read(root.resolve("app/src/test/resources/robolectric.properties"));
        String build = read(root.resolve("app/build.gradle"));
        String building = read(root.resolve("BUILDING.rst"));

        assertTrue(properties.contains("sdk=36"));
        assertTrue(build.contains("maxHeapSize = \"1024m\""));
        assertTrue(build.contains("JavaVersion.VERSION_21"));
        assertTrue(build.contains("requires JDK 21 or newer"));
        assertTrue(building.contains("JDK 21+"));
        assertTrue(building.contains("Robolectric runs against Android SDK 36"));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main/java"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
