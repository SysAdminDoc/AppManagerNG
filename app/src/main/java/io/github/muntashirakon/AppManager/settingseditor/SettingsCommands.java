// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * The {@code settings} shell commands the editor runs. Keys are limited to the characters setting names use, and
 * keys and values are single-quoted, so nothing typed into the editor is read by the shell as a command.
 */
public final class SettingsCommands {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.:-]+");

    private SettingsCommands() {
    }

    public static boolean isValidKey(@Nullable String key) {
        return key != null && KEY.matcher(key).matches();
    }

    @NonNull
    public static String list(@NonNull SettingsTable table, int userId) {
        return prefix(userId) + "list " + table.shellName;
    }

    @NonNull
    public static String get(@NonNull SettingsTable table, @NonNull String key, int userId) {
        return prefix(userId) + "get " + table.shellName + " " + quote(requireKey(key));
    }

    @NonNull
    public static String put(@NonNull SettingsTable table, @NonNull String key, @NonNull String value, int userId) {
        return prefix(userId) + "put " + table.shellName + " " + quote(requireKey(key)) + " " + quote(value);
    }

    @NonNull
    public static String delete(@NonNull SettingsTable table, @NonNull String key, int userId) {
        return prefix(userId) + "delete " + table.shellName + " " + quote(requireKey(key));
    }

    /**
     * POSIX single quotes: everything inside is literal, line breaks included, and a quote in the text becomes
     * {@code '\''}. The runner's own escaping drops line breaks, which a value may hold.
     */
    @NonNull
    static String quote(@NonNull String text) {
        return "'" + text.replace("'", "'\\''") + "'";
    }

    @NonNull
    private static String prefix(int userId) {
        return "settings --user " + userId + " ";
    }

    @NonNull
    private static String requireKey(@NonNull String key) {
        if (!isValidKey(key)) {
            throw new IllegalArgumentException("Not a setting name: " + key);
        }
        return key;
    }
}
