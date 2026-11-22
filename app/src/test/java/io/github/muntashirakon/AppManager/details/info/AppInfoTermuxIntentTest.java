// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.terminal.TermuxResultReceiver;
import io.github.muntashirakon.AppManager.utils.Utils;

/**
 * App Info's Open in Termux and Run in Termux go through Termux's {@code login}, which ends with
 * {@code exec "$SHELL" -l "$@"}. Its arguments land on the shell's command line, so a first
 * argument that isn't an option is read as a script file name: {@code login su - 10123} makes bash
 * look for a file called {@code su}. The command has to arrive as one {@code -c} argument.
 * <p>
 * Termux also turns a command down without a word on screen while allow-external-apps is off, so
 * each command asks it to report back.
 */
@RunWith(RobolectricTestRunner.class)
public class AppInfoTermuxIntentTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private static void assertRunsThroughLoginShell(@NonNull Intent intent, @NonNull String command) {
        assertEquals("com.termux.RUN_COMMAND", intent.getAction());
        assertEquals("com.termux", intent.getComponent().getPackageName());
        assertEquals(Utils.TERMUX_LOGIN_PATH, intent.getStringExtra("com.termux.RUN_COMMAND_PATH"));
        assertArrayEquals(new String[]{"-c", command},
                intent.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS"));
        PendingIntent result = intent.getParcelableExtra("com.termux.RUN_COMMAND_PENDING_INTENT");
        assertNotNull("Termux has no way to say it refused the command", result);
        assertEquals(TermuxResultReceiver.class.getName(),
                shadowOf(result).getSavedIntent().getComponent().getClassName());
    }

    @Test
    public void openInTermuxStartsAShellAsTheAppsUid() {
        assertRunsThroughLoginShell(AppInfoFragment.buildOpenInTermuxIntent(mContext, 10123), "su - 10123");
    }

    @Test
    public void runInTermuxHandsRunAsItsPackageInOneCommand() {
        // su -c takes one command string; "su -c run-as pkg" would read pkg as the user to switch to
        assertRunsThroughLoginShell(AppInfoFragment.buildRunInTermuxIntent(mContext, "com.example.debuggable"),
                "su -c 'run-as com.example.debuggable'");
    }
}
