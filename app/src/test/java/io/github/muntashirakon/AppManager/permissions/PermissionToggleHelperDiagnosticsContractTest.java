// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PermissionToggleHelperDiagnosticsContractTest {
    @Test
    public void permissionToggleHelperFailuresUseAppLogger() throws IOException {
        String source = read(findRepoRoot().resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/permissions/PermissionToggleHelper.java"));

        assertFalse("Permission helper failures should log through the app logger, not stderr",
                source.contains("printStackTrace()"));
        assertMethodContains(source, "public static State load", "Could not load permission %s for %s/%d.");
        assertMethodContains(source, "public static Boolean toggle", "Could not toggle permission %s for %s/%d.");
        assertMethodContains(source, "public static boolean revoke", "Could not revoke permission %s for %s/%d.");
        assertMethodContains(source, "public static boolean grant", "Could not grant permission %s for %s/%d.");
        assertMethodContains(source, "public static PermissionReferenceRule loadReference",
                "Could not load permission reference %s for %s/%d.");
        assertMethodContains(source, "public static boolean pinReference",
                "Could not pin permission reference %s for %s/%d.");
        assertMethodContains(source, "private static void persistRule",
                "Could not persist permission rule %s for %s/%d.");
    }

    private static void assertMethodContains(String source, String methodSignature, String expectedText) {
        int start = source.indexOf(methodSignature);
        assertTrue("Missing method " + methodSignature, start >= 0);
        int nextMethod = source.indexOf("\n    @", start + methodSignature.length());
        int end = nextMethod > start ? nextMethod : source.length();
        String method = source.substring(start, end);
        assertTrue(methodSignature + " should log " + expectedText, method.contains(expectedText));
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
