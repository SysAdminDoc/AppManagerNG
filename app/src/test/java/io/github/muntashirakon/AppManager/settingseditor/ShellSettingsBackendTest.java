// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Issue 48: the privileged route runs the settings command and reads its answers. */
public class ShellSettingsBackendTest {
    private final List<String> mCommands = new ArrayList<>();

    @Test
    public void listsAndReadsThroughTheSettingsCommand() throws IOException {
        ShellSettingsBackend backend = backend(new ShellSettingsBackend.Output(0,
                Arrays.asList("b=2", "a=null"), Collections.emptyList()), null);

        assertEquals(Arrays.asList(new SettingEntry("a", null), new SettingEntry("b", "2")),
                backend.list(SettingsTable.GLOBAL));
        assertEquals("settings --user 10 list global", mCommands.get(0));
    }

    @Test
    public void getReadsNullAsNotSet() throws IOException {
        assertNull(backend(new ShellSettingsBackend.Output(0, Collections.singletonList("null"),
                Collections.emptyList()), null).get(SettingsTable.SECURE, "missing"));
        assertEquals("a\nb", backend(new ShellSettingsBackend.Output(0, Arrays.asList("a", "b"),
                Collections.emptyList()), null).get(SettingsTable.SECURE, "multi"));
    }

    @Test
    public void aFailedWriteSaysWhy() {
        ShellSettingsBackend backend = backend(new ShellSettingsBackend.Output(255, Collections.emptyList(),
                Arrays.asList("java.lang.SecurityException: Permission denial", "\tat x.y.z")), null);

        IOException error = assertThrows(IOException.class,
                () -> backend.put(SettingsTable.SECURE, "adb_enabled", "0"));
        assertEquals("java.lang.SecurityException: Permission denial", error.getMessage());
        assertEquals("settings --user 10 put secure 'adb_enabled' '0'", mCommands.get(0));
        assertThrows(IOException.class, () -> backend.delete(SettingsTable.SECURE, "adb_enabled"));
    }

    @Test
    public void androidFiveListsThroughTheProviderInstead() throws IOException {
        List<SettingEntry> fromProvider = Collections.singletonList(new SettingEntry("wifi_on", "1"));
        ShellSettingsBackend backend = backend(new ShellSettingsBackend.Output(1, Collections.emptyList(),
                Collections.singletonList("Invalid command: list")), new ListOnly(fromProvider));

        assertEquals(fromProvider, backend.list(SettingsTable.GLOBAL));
    }

    @NonNull
    private ShellSettingsBackend backend(@NonNull ShellSettingsBackend.Output output,
                                         @Nullable SettingsBackend fallback) {
        return new ShellSettingsBackend(command -> {
            mCommands.add(command);
            return output;
        }, 10, fallback);
    }

    private static final class ListOnly implements SettingsBackend {
        @NonNull
        private final List<SettingEntry> mEntries;

        ListOnly(@NonNull List<SettingEntry> entries) {
            mEntries = entries;
        }

        @Override
        public boolean isPrivileged() {
            return false;
        }

        @Override
        public boolean canWrite(@NonNull SettingsTable table) {
            return false;
        }

        @NonNull
        @Override
        public List<SettingEntry> list(@NonNull SettingsTable table) {
            return mEntries;
        }

        @Nullable
        @Override
        public String get(@NonNull SettingsTable table, @NonNull String key) {
            return null;
        }

        @Override
        public void put(@NonNull SettingsTable table, @NonNull String key, @NonNull String value) {
        }

        @Override
        public void delete(@NonNull SettingsTable table, @NonNull String key) {
        }
    }
}
