// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Keeps destructive and trust-sensitive fallbacks observable. A failure may still have a
 * conservative fallback, but it must identify the operation that was skipped.
 */
public class DestructiveTrustFailureLoggingContractTest {
    @Test
    public void permissionDenialsRemainConservativeAndLogged() throws IOException {
        String source = readSource("self/SelfPermissions.java");
        assertLogged(source, "Could not grant self permission %s.",
                "Could not grant the usage stats permission.",
                "Could not allow the usage stats app-op.",
                "Could not check permission %s for uid %d.");
    }

    @Test
    public void missingServicesKeepTheirFallbacksObservable() throws IOException {
        assertLogged(readSource("batchops/BatchOpsService.java"),
                "Could not register the privileged binder death listener.",
                "Could not unregister the privileged binder death listener.");
        assertLogged(readSource("backup/RestoreOp.java"),
                "Could not query installed package %s; treating the restore as not installed.",
                "Could not prepare the privileged package staging directory; trying the backup directory.");
        assertLogged(readSource("settings/Ops.java"),
                "Could not open the developer settings.",
                "Could not open App Manager settings for %s.");
    }

    @Test
    public void malformedResultsKeepTheirFallbacksObservable() throws IOException {
        assertLogged(readSource("apk/installer/PackageInstallerCompat.java"),
                "Could not determine whether %s is an updated system app.",
                "Could not find package %s for user %d while selecting an uninstall target.");
        assertLogged(readSource("backup/BackupOp.java"),
                "Could not read app-op state for %s; continuing without app-op entries.");
        assertLogged(readSource("crypto/ks/KeyStoreManager.java"),
                "Could not migrate keystore alias %s.");
        assertLogged(readSource("rules/compontents/ComponentsBlocker.java"),
                "Could not verify component state for %s/%s.");
    }

    @Test
    public void targetedProductionFilesHaveNoEmptyFailureCatches() throws IOException {
        String[][] catches = {
                {"apk/installer/PackageInstallerCompat.java", "catch (Throwable ignore)"},
                {"batchops/BatchOpsService.java", "catch (Throwable ignore)"},
                {"backup/RestoreOp.java", "catch (Exception ignore)"},
                {"backup/BackupOp.java", "catch (Exception ignore)"},
                {"crypto/ks/KeyStoreManager.java", "catch (Exception ignore)"},
                {"rules/compontents/ComponentsBlocker.java", "catch (Throwable ignore)"},
                {"settings/Ops.java", "catch (Exception ignore)"},
                {"self/SelfPermissions.java", "catch (Exception ignore)"},
                {"self/SelfPermissions.java", "catch (RemoteException ignore)"}
        };
        for (String[] target : catches) {
            String source = readSource(target[0]);
            assertFalse(target[0] + " still contains " + target[1], source.contains(target[1]));
        }
    }

    private static void assertLogged(String source, String... messages) {
        for (String message : messages) {
            int messageIndex = source.indexOf(message);
            assertTrue("Missing operation-specific log: " + message, messageIndex >= 0);
            int blockStart = Math.max(0, messageIndex - 160);
            assertTrue("Operation message is not attached to a structured log: " + message,
                    source.substring(blockStart, messageIndex).contains("Log."));
        }
    }

    private static String readSource(String relativePath) throws IOException {
        Path root = findRepoRoot();
        Path source = root.resolve("app/src/main/java/io/github/muntashirakon/AppManager").resolve(relativePath);
        return new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main"))
                    && Files.isDirectory(cursor.resolve("libcore"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
