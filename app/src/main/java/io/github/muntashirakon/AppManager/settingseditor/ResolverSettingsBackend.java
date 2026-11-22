// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The settings provider as the app itself sees it, without root, ADB or Shizuku. Reading works for what Android lets
 * apps read. System takes the keys apps may change once "Modify system settings" is allowed, and Secure and Global
 * only if WRITE_SECURE_SETTINGS was granted to the app over ADB.
 */
public class ResolverSettingsBackend implements SettingsBackend {
    @NonNull
    private final Context mContext;
    @NonNull
    private final ContentResolver mResolver;

    public ResolverSettingsBackend(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mResolver = mContext.getContentResolver();
    }

    @Override
    public boolean isPrivileged() {
        return false;
    }

    @Override
    public boolean canWrite(@NonNull SettingsTable table) {
        if (table == SettingsTable.SYSTEM) {
            // Below Android 6 the permission is granted with the install.
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(mContext);
        }
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    @Override
    public List<SettingEntry> list(@NonNull SettingsTable table) throws IOException {
        String[] projection = {Settings.NameValueTable.NAME, Settings.NameValueTable.VALUE};
        try (Cursor cursor = mResolver.query(table.getContentUri(), projection, null, null, null)) {
            if (cursor == null) {
                throw new IOException("The settings provider returned nothing for " + table.shellName);
            }
            List<SettingEntry> entries = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                String key = cursor.getString(0);
                if (key != null) {
                    entries.add(new SettingEntry(key, cursor.getString(1)));
                }
            }
            Collections.sort(entries, (a, b) -> {
                int order = a.key.compareToIgnoreCase(b.key);
                return order != 0 ? order : a.key.compareTo(b.key);
            });
            return entries;
        } catch (SecurityException | IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Nullable
    @Override
    public String get(@NonNull SettingsTable table, @NonNull String key) throws IOException {
        try {
            switch (table) {
                case SYSTEM:
                    return Settings.System.getString(mResolver, key);
                case SECURE:
                    return Settings.Secure.getString(mResolver, key);
                case GLOBAL:
                default:
                    return Settings.Global.getString(mResolver, key);
            }
        } catch (SecurityException | IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public void put(@NonNull SettingsTable table, @NonNull String key, @NonNull String value) throws IOException {
        boolean saved;
        try {
            switch (table) {
                case SYSTEM:
                    saved = Settings.System.putString(mResolver, key, value);
                    break;
                case SECURE:
                    saved = Settings.Secure.putString(mResolver, key, value);
                    break;
                case GLOBAL:
                default:
                    saved = Settings.Global.putString(mResolver, key, value);
                    break;
            }
        } catch (SecurityException | IllegalArgumentException e) {
            // Android refuses keys apps may not change ("You cannot change private secure settings").
            throw new IOException(e.getMessage(), e);
        }
        if (!saved) {
            throw new IOException("The settings provider didn't save " + key);
        }
    }

    @Override
    public void delete(@NonNull SettingsTable table, @NonNull String key) throws IOException {
        try {
            mResolver.delete(Uri.withAppendedPath(table.getContentUri(), key), null, null);
        } catch (SecurityException | IllegalArgumentException | UnsupportedOperationException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
