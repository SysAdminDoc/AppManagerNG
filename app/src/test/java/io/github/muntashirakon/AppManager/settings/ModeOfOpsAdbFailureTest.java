// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

import io.github.muntashirakon.AppManager.servermanager.AdbFailure;

/**
 * Why ADB mode didn't connect shows under the mode (issue #20), only while an ADB mode is chosen
 * and not connected, and every connect path keeps it until one works.
 */
@RunWith(RobolectricTestRunner.class)
public class ModeOfOpsAdbFailureTest {
    private static final String OPS = "app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java";

    @Test
    public void theReasonShowsOnlyForAnAdbModeThatIsNotConnected() {
        Context context = ApplicationProvider.getApplicationContext();
        AdbFailure timeout = AdbFailure.classify(new TimeoutException(), false);

        CharSequence shown = ModeOfOpsPreference.adbFailureText(context, Ops.MODE_ADB_WIFI, false, timeout);
        assertEquals(timeout.explain(context).toString(), String.valueOf(shown));
        assertTrue(String.valueOf(shown), String.valueOf(shown).startsWith("AppManagerNG's privileged server didn't start in time. "));
        assertEquals(timeout.explain(context).toString(), String.valueOf(
                ModeOfOpsPreference.adbFailureText(context, Ops.MODE_ADB_OVER_TCP, false, timeout)));

        assertNull(ModeOfOpsPreference.adbFailureText(context, Ops.MODE_ADB_WIFI, true, timeout));
        assertNull(ModeOfOpsPreference.adbFailureText(context, Ops.MODE_ROOT, false, timeout));
        assertNull(ModeOfOpsPreference.adbFailureText(context, Ops.MODE_ADB_WIFI, false, null));
    }

    @Test
    public void everyAdbConnectPathKeepsTheReasonUntilOneWorks() throws IOException {
        String ops = read(OPS);
        for (String signature : new String[]{
                "private static int autoConnectWirelessDebuggingLocked(@NonNull Context context)",
                "private static int connectAdbLocked(int port,"}) {
            String body = body(ops, signature);
            int connected = body.indexOf("connectAdbFull(");
            assertTrue(signature, connected != -1 && body.indexOf("AdbFailure.clear();", connected) > connected);
            assertTrue(signature, body.indexOf("recordAdbFailure(", body.indexOf("catch (")) != -1);
        }
        String init = body(ops, "private static int initLocked(@NonNull Context context, boolean force, @NonNull @Mode String mode,");
        String tcp = init.substring(init.indexOf("case MODE_ADB_OVER_TCP:"));
        assertTrue(tcp, tcp.indexOf("AdbFailure.clear();") > tcp.indexOf("connectAdbFull("));
        String fallback = init.substring(init.indexOf("catch (Throwable e)"));
        assertTrue(fallback, fallback.contains("recordAdbFailure(context, e, MODE_ADB_WIFI.equals(mode))"));
        assertTrue(fallback, fallback.indexOf("adbFailure.explain(context)") > fallback.indexOf("if (status == STATUS_FAILURE)"));
        assertTrue(init, init.contains("throw new AdbFailure.NoWifiException();"));
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
