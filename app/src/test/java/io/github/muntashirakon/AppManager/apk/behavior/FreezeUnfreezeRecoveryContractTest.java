// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FreezeUnfreezeRecoveryContractTest {
    @Test
    public void freezeUnfreezeFailuresUseLocalizedRecoveryAndComplete() throws IOException {
        Path repoRoot = findRepoRoot();
        String freezeUnfreeze = read(repoRoot.resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/apk/behavior/FreezeUnfreeze.java"));
        String activity = read(repoRoot.resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/apk/behavior/FreezeUnfreezeActivity.java"));
        String service = read(repoRoot.resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/apk/behavior/FreezeUnfreezeService.java"));
        String strings = read(repoRoot.resolve("app/src/main/res/values/strings.xml"));

        assertFalse("Freeze/unfreeze launch failures should not expose raw platform exception text",
                freezeUnfreeze.contains("getLocalizedMessage()"));
        assertFalse("Freeze/unfreeze activity failures should log through the app logger, not stderr",
                activity.contains("printStackTrace()"));
        assertFalse("Freeze/unfreeze service failures should log through the app logger, not stderr",
                service.contains("printStackTrace()"));
        assertTrue("Freeze/unfreeze failures should use localized recovery copy",
                freezeUnfreeze.contains("R.string.freeze_unfreeze_failed")
                        && activity.contains("R.string.freeze_unfreeze_failed")
                        && strings.contains("name=\"freeze_unfreeze_failed\""));
        assertTrue("Freeze/unfreeze background failures should not leave the transparent activity open",
                activity.contains("mFailureLiveData.postValue(R.string.freeze_unfreeze_failed);")
                        && activity.contains("mIsFrozenLiveData.postValue(null);"));
        assertTrue("Freeze/unfreeze failures should keep scoped diagnostics in logs",
                freezeUnfreeze.contains("Log.e(TAG, \"Could not launch app during freeze/unfreeze shortcut for %s.\"")
                        && activity.contains("Log.e(TAG, \"Could not change frozen state for %s.\"")
                        && service.contains("Log.e(TAG, \"Could not freeze %s after lock.\""));
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
