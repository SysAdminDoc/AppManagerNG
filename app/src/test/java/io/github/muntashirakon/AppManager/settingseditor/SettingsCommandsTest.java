// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Issue 48: the shell commands behind the settings editor, and that typed text can't become a command. */
public class SettingsCommandsTest {
    private static final String[] HOSTILE_VALUES = {
            "it's", "$(touch pwned)", "`touch pwned`", "a; touch pwned", "a && touch pwned", "a | touch pwned",
            "line one\nline two", "\"double\" and \\backslash", "$HOME ${PATH} *", "'", "", "-1", "  spaced  ",
    };

    @Test
    public void commandsNameTheUserTableAndKey() {
        assertEquals("settings --user 0 list secure", SettingsCommands.list(SettingsTable.SECURE, 0));
        assertEquals("settings --user 10 get global 'adb_enabled'",
                SettingsCommands.get(SettingsTable.GLOBAL, "adb_enabled", 10));
        assertEquals("settings --user 0 put system 'screen_brightness' '128'",
                SettingsCommands.put(SettingsTable.SYSTEM, "screen_brightness", "128", 0));
        assertEquals("settings --user 0 delete secure 'my.key:1-2'",
                SettingsCommands.delete(SettingsTable.SECURE, "my.key:1-2", 0));
    }

    @Test
    public void singleQuotesInAValueAreClosedAndReopened() {
        assertEquals("'it'\\''s'", SettingsCommands.quote("it's"));
        assertEquals("''", SettingsCommands.quote(""));
    }

    @Test
    public void keysAreLimitedToSettingNameCharacters() {
        assertTrue(SettingsCommands.isValidKey("Settings.Secure_key-1:2"));
        for (String key : new String[]{"", " ", "a b", "a;b", "a'b", "a$b", "a/b", "a\nb", "ключ", null}) {
            assertFalse(String.valueOf(key), SettingsCommands.isValidKey(key));
        }
        assertThrows(IllegalArgumentException.class,
                () -> SettingsCommands.put(SettingsTable.SECURE, "a; reboot", "1", 0));
        assertThrows(IllegalArgumentException.class,
                () -> SettingsCommands.delete(SettingsTable.GLOBAL, "$(reboot)", 0));
    }

    @Test
    public void aRealShellHandsEveryValueOverUnchanged() throws Exception {
        assumeTrue("needs a POSIX sh", shAvailable());
        for (String value : HOSTILE_VALUES) {
            String command = SettingsCommands.put(SettingsTable.SECURE, "a_key", value, 0);
            // A stand-in for the settings command that prints each argument it got, bracketed.
            String output = sh("settings() { for arg in \"$@\"; do printf '[%s]' \"$arg\"; done; }; " + command
                    + "; printf '|'; [ -e pwned ] && printf 'RAN' || printf 'safe'");

            assertEquals(value, "[--user][0][put][secure][a_key][" + value + "]|safe", output);
        }
    }

    private static boolean shAvailable() {
        try {
            Process p = new ProcessBuilder("sh", "-c", "true").redirectErrorStream(true).start();
            return p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    // The script goes in on stdin: Windows argument passing drops the quotes inside a -c argument.
    private static String sh(String script) throws Exception {
        Process p = new ProcessBuilder("sh", "-s").redirectErrorStream(true)
                .directory(java.nio.file.Files.createTempDirectory("settings-quote").toFile()).start();
        try (OutputStream in = p.getOutputStream()) {
            in.write((script + "\n").getBytes(StandardCharsets.UTF_8));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
