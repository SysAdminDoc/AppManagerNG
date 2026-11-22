// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;

import android.content.Intent;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.utils.Utils;

/**
 * App Info's Open in Termux and Run in Termux go through Termux's {@code login}, which ends with
 * {@code exec "$SHELL" -l "$@"}. Its arguments land on the shell's command line, so a first
 * argument that isn't an option is read as a script file name: {@code login su - 10123} makes bash
 * look for a file called {@code su}. The command has to arrive as one {@code -c} argument.
 * <p>
 * Both are sent through {@code TermuxBridge.send}, which asks Termux to report a refusal and brings
 * Termux forward (see {@code TermuxBridgeTest}).
 */
@RunWith(RobolectricTestRunner.class)
public class AppInfoTermuxIntentTest {
    private static void assertRunsThroughLoginShell(@NonNull Intent intent, @NonNull String command) {
        assertEquals("com.termux.RUN_COMMAND", intent.getAction());
        assertEquals("com.termux", intent.getComponent().getPackageName());
        assertEquals(Utils.TERMUX_LOGIN_PATH, intent.getStringExtra("com.termux.RUN_COMMAND_PATH"));
        // The command is the first line; the session then carries on in the user's shell (see TermuxBridgeTest)
        String[] args = intent.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS");
        assertEquals(2, args.length);
        assertEquals("-c", args[0]);
        assertEquals(command, args[1].split("\n")[0]);
    }

    @Test
    public void openInTermuxStartsAShellAsTheAppsUid() {
        assertRunsThroughLoginShell(AppInfoFragment.buildOpenInTermuxIntent(10123), "su - 10123");
    }

    @Test
    public void runInTermuxHandsRunAsItsPackageInOneCommand() {
        // su -c takes one command string; "su -c run-as pkg" would read pkg as the user to switch to
        assertRunsThroughLoginShell(AppInfoFragment.buildRunInTermuxIntent("com.example.debuggable"),
                "su -c 'run-as com.example.debuggable'");
    }
}
