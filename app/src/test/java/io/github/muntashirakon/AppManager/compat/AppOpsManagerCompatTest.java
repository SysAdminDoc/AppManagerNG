// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class AppOpsManagerCompatTest {
    @Test
    public void audioVolumeOpsAreNamedAndSupported() {
        int[] ops = AppOpsManagerCompat.getAudioVolumeOps();
        assertTrue(ops.length >= 7);

        Set<Integer> uniqueOps = new HashSet<>();
        for (int op : ops) {
            assertTrue(op > AppOpsManagerCompat.OP_NONE);
            assertTrue(op < AppOpsManagerCompat._NUM_OP);
            assertTrue("Duplicate audio volume op " + op, uniqueOps.add(op));
            assertTrue(AppOpsManagerCompat.isAudioVolumeOp(op));
            assertNotEquals(String.valueOf(op), AppOpsManagerCompat.getKnownOpName(op));
        }

        assertTrue(uniqueOps.contains(AppOpsManagerCompat.OP_AUDIO_MEDIA_VOLUME));
        assertEquals("AUDIO_MEDIA_VOLUME",
                AppOpsManagerCompat.getKnownOpName(AppOpsManagerCompat.OP_AUDIO_MEDIA_VOLUME));
    }

    @Test
    public void setModeUsesUidModeForAospAudioVolumeOps() {
        assertTrue(AppOpsManagerCompat.usesUidModeForSetMode(
                AppOpsManagerCompat.OP_AUDIO_MEDIA_VOLUME, Build.VERSION_CODES.M, false));
        assertFalse(AppOpsManagerCompat.usesUidModeForSetMode(
                AppOpsManagerCompat.OP_AUDIO_MEDIA_VOLUME, Build.VERSION_CODES.LOLLIPOP_MR1, false));
        assertFalse(AppOpsManagerCompat.usesUidModeForSetMode(
                AppOpsManagerCompat.OP_AUDIO_MEDIA_VOLUME, Build.VERSION_CODES.M, true));
    }

    @Test
    public void setModeSchedulesOsRevertWatchForAppOps() throws IOException {
        String source = readRepoFile("app/src/main/java/io/github/muntashirakon/AppManager/compat/"
                + "AppOpsManagerCompat.java");

        assertTrue(source.contains("OsRevertMonitor.watchAppOp(ContextUtils.getContext(), "
                + "packageName, uid, op, mode);"));
    }

    private static String readRepoFile(String relativePath) throws IOException {
        Path root = repoRoot();
        return new String(Files.readAllBytes(root.resolve(relativePath)), StandardCharsets.UTF_8);
    }

    private static Path repoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null && !Files.exists(cursor.resolve("settings.gradle"))) {
            cursor = cursor.getParent();
        }
        if (cursor == null) {
            throw new IllegalStateException("Could not find repository root.");
        }
        return cursor;
    }
}
