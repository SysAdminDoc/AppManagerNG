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

public class AutoFreezeOnLockContractTest {
    @Test
    public void receiverUsesDurableCancellableWorkAndIsRegistered() throws IOException {
        Path appDir = findAppProjectDir();
        String receiver = read(appDir.resolve("src/main/java/io/github/muntashirakon/AppManager/apk/behavior/AutoFreezeOnLockReceiver.java"));
        String worker = read(appDir.resolve("src/main/java/io/github/muntashirakon/AppManager/apk/behavior/AutoFreezeOnLockWorker.java"));
        String application = read(appDir.resolve("src/main/java/io/github/muntashirakon/AppManager/AppManager.java"));

        assertTrue(receiver.contains("enqueueUniqueWork("));
        assertTrue(receiver.contains("ExistingWorkPolicy.REPLACE"));
        assertTrue(receiver.contains("cancelUniqueWork("));
        assertFalse(receiver.contains("postDelayed("));
        assertFalse(receiver.contains("new Handler("));
        assertTrue(worker.contains("Ops.init(context, false)"));
        assertTrue(worker.contains("powerManager.isInteractive()"));
        assertTrue(application.contains("Intent.ACTION_SCREEN_OFF"));
        assertTrue(application.contains("Intent.ACTION_SCREEN_ON"));
        assertTrue(application.contains("Intent.ACTION_USER_PRESENT"));
        assertTrue(application.contains("ContextCompat.RECEIVER_EXPORTED"));
    }

    @Test
    public void rulesSettingsExposeToggleAndBoundedDelay() throws IOException {
        Path appDir = findAppProjectDir();
        String preferences = read(appDir.resolve("src/main/res/xml/preferences_rules.xml"));
        String rules = read(appDir.resolve("src/main/java/io/github/muntashirakon/AppManager/settings/RulesPreferences.java"));

        assertTrue(preferences.contains("app:key=\"auto_freeze_on_lock\""));
        assertTrue(preferences.contains("app:key=\"auto_freeze_delay_seconds\""));
        assertTrue(preferences.contains("app:dependency=\"auto_freeze_on_lock\""));
        assertTrue(rules.contains("SelfPermissions.canFreezeUnfreezePackages()"));
        assertTrue(rules.contains("AutoFreezeOnLockReceiver.cancel(requireContext())"));
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
