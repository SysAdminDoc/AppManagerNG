// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.lifecycle;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LifecycleCleanupContractTest {
    @Test
    public void viewModelExecutorsAreShutdownWhenCleared() throws IOException {
        assertContains("app/src/main/java/io/github/muntashirakon/AppManager/settings/MainPreferencesViewModel.java",
                "protected void onCleared()",
                "mExecutor.shutdownNow();");
        assertContains("app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoViewModel.java",
                "protected void onCleared()",
                "mExecutor.shutdownNow();");
    }

    @Test
    public void appDetailsCleanupRunsBeforeExecutorShutdown() throws IOException {
        String source = readRepoFile("app/src/main/java/io/github/muntashirakon/AppManager/details/AppDetailsViewModel.java");
        int cleanup = source.indexOf("mBlocker.setReadOnly();");
        int shutdown = source.indexOf("mExecutor.shutdownNow();");

        assertTrue("AppDetailsViewModel should clean mutable blocker state on clear", cleanup >= 0);
        assertTrue("AppDetailsViewModel should shut down its executor", shutdown >= 0);
        assertTrue("Mutable blocker cleanup must run before executor shutdown", cleanup < shutdown);
    }

    @Test
    public void openPgpKeySelectionUnbindsAndStopsExecutor() throws IOException {
        assertContains("app/src/main/java/io/github/muntashirakon/AppManager/settings/crypto/OpenPgpKeySelectionDialogFragment.java",
                "public void onDestroy()",
                "mServiceConnection.unbindFromService();",
                "mExecutor.shutdownNow();");
    }

    private static void assertContains(String relativePath, String... snippets) throws IOException {
        String source = readRepoFile(relativePath);
        for (String snippet : snippets) {
            assertTrue(relativePath + " should contain " + snippet, source.contains(snippet));
        }
    }

    private static String readRepoFile(String relativePath) throws IOException {
        return new String(Files.readAllBytes(findRepoRoot().resolve(relativePath)), StandardCharsets.UTF_8);
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
