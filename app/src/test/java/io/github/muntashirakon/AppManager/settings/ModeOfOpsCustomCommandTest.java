// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The custom commands under Mode of operation carry the server's token.
 */
@RunWith(RobolectricTestRunner.class)
public class ModeOfOpsCustomCommandTest {
    @Test
    public void aCopiedCommandIsMarkedSensitive() {
        Context context = ApplicationProvider.getApplicationContext();
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        clipboard.clearPrimaryClip();

        ModeOfOpsPreference.copyCommand(context, "");
        assertNull(clipboard.getPrimaryClip());

        ModeOfOpsPreference.copyCommand(context, "sh run_server.sh 62001 0123456789abcdef");
        ClipData clip = clipboard.getPrimaryClip();
        assertNotNull(clip);
        assertEquals("sh run_server.sh 62001 0123456789abcdef", clip.getItemAt(0).getText().toString());
        PersistableBundle extras = clip.getDescription().getExtras();
        assertNotNull(extras);
        assertTrue(extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE));
    }

    @Test
    public void theRootCommandDoesNotDependOnTheAdbOne() throws IOException {
        String model = body(read("app/src/main/java/io/github/muntashirakon/AppManager/settings/MainPreferencesViewModel.java"),
                "public void loadCustomCommands()");
        // The ADB command failed on its own and took the root one with it
        int adbPosted = model.indexOf("mCustomCommand0.postValue(adbCommand);");
        int rootTry = model.indexOf("try {", adbPosted);
        assertTrue(model, adbPosted != -1 && rootTry > adbPosted);
        assertTrue(model, model.indexOf("ServerConfig.getServerRunnerCommand(0)") > rootTry);
        assertFalse(model, model.contains("mCustomCommand1.postValue(null)"));
    }

    private static String body(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(signature, start != -1);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); ++i) {
            char c = source.charAt(i);
            if (c == '{') {
                ++depth;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open, i + 1);
            }
        }
        throw new AssertionError(signature);
    }

    private static String read(String path) throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null && !Files.isDirectory(cursor.resolve("app/src/main/java"))) {
            cursor = cursor.getParent();
        }
        return new String(Files.readAllBytes(cursor.resolve(path)), StandardCharsets.UTF_8);
    }
}
