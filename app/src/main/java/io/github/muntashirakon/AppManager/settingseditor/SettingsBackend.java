// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.List;

/** Reads and writes the settings tables, through a privileged shell or through the app's own access. */
@WorkerThread
public interface SettingsBackend {
    /** Root, ADB or Shizuku, which can change any key of any table. */
    boolean isPrivileged();

    /** Whether this table takes changes at all. A key the system protects can still refuse one. */
    boolean canWrite(@NonNull SettingsTable table);

    @NonNull
    List<SettingEntry> list(@NonNull SettingsTable table) throws IOException;

    /** The current value, or {@code null} if the key isn't set. */
    @Nullable
    String get(@NonNull SettingsTable table, @NonNull String key) throws IOException;

    void put(@NonNull SettingsTable table, @NonNull String key, @NonNull String value) throws IOException;

    void delete(@NonNull SettingsTable table, @NonNull String key) throws IOException;
}
