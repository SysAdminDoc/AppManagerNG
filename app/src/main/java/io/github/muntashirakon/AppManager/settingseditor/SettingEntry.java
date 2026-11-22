// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/** One row of a settings table. A {@code null} value is a key the provider holds with no value. */
public final class SettingEntry {
    @NonNull
    public final String key;
    @Nullable
    public final String value;

    public SettingEntry(@NonNull String key, @Nullable String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SettingEntry)) return false;
        SettingEntry that = (SettingEntry) o;
        return key.equals(that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @NonNull
    @Override
    public String toString() {
        return key + "=" + value;
    }
}
