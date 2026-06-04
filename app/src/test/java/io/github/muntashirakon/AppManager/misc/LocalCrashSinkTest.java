// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@RunWith(RobolectricTestRunner.class)
public class LocalCrashSinkTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        deleteRecursively(LocalCrashSink.getCrashDir(mContext));
    }

    @Test
    public void writeCrashFile_writesScrubbedJson() throws Exception {
        Throwable throwable = new IllegalStateException(
                "com.example.secret /sdcard/private person@example.com uid=10345");

        File file = LocalCrashSink.writeCrashFile(mContext, "main", throwable,
                "report com.example.secret /storage/emulated/0/private.txt person@example.com uid=10345",
                new Date(0));

        assertNotNull(file);
        assertTrue(file.getName().endsWith(".json"));
        JSONObject json = new JSONObject(readText(file));
        assertTrue(json.getString("captured_at_utc").equals("1970-01-01T00:00:00Z"));
        assertTrue(json.getString("throwable_class").equals(IllegalStateException.class.getName()));

        String serialized = json.toString();
        assertTrue(serialized.contains("<package>"));
        assertTrue(serialized.contains("<path>"));
        assertTrue(serialized.contains("<email>"));
        assertTrue(serialized.contains("uid=<redacted>"));
        assertFalse(serialized.contains("com.example.secret"));
        assertFalse(serialized.contains("/sdcard/private"));
        assertFalse(serialized.contains("/storage/emulated/0/private.txt"));
        assertFalse(serialized.contains("person@example.com"));
        assertFalse(serialized.contains("uid=10345"));
    }

    @Test
    public void buildSupportSummary_listsRecentJsonCrashes() {
        Throwable throwable = new IllegalStateException(
                "com.example.secret /sdcard/private person@example.com uid=10345");
        LocalCrashSink.writeCrashFile(mContext, "main", throwable,
                "report com.example.secret /storage/emulated/0/private.txt person@example.com uid=10345",
                new Date(0));

        String summary = LocalCrashSink.buildSupportSummary(mContext);

        assertTrue(summary.contains("1970-01-01T00:00:00Z"));
        assertTrue(summary.contains(IllegalStateException.class.getName()));
        assertTrue(summary.contains("<package>"));
        assertFalse(summary.contains("com.example.secret"));
        assertFalse(summary.contains("/sdcard/private"));
        assertFalse(summary.contains("person@example.com"));
        assertFalse(summary.contains("uid=10345"));
    }

    private static String readText(File file) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
