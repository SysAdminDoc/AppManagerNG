// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** The three tables of Android's settings provider, by the names the {@code settings} command uses. */
public enum SettingsTable {
    SYSTEM("system"),
    SECURE("secure"),
    GLOBAL("global");

    /** The name in {@code settings list <table>} and in the audit trail. */
    @NonNull
    public final String shellName;

    SettingsTable(@NonNull String shellName) {
        this.shellName = shellName;
    }

    @NonNull
    public Uri getContentUri() {
        switch (this) {
            case SYSTEM:
                return Settings.System.CONTENT_URI;
            case SECURE:
                return Settings.Secure.CONTENT_URI;
            case GLOBAL:
            default:
                return Settings.Global.CONTENT_URI;
        }
    }

    @Nullable
    public static SettingsTable fromShellName(@Nullable String name) {
        for (SettingsTable table : values()) {
            if (table.shellName.equals(name)) {
                return table;
            }
        }
        return null;
    }
}
