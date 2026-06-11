// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;

/**
 * Immutable row in the local app-change audit feed.
 */
public final class AppChangeFeedEntry {
    @NonNull
    public final String kind;
    @NonNull
    public final String packageName;
    public final long timestampMillis;
    @NonNull
    public final String title;
    @NonNull
    public final String body;

    public AppChangeFeedEntry(@NonNull String kind, @NonNull String packageName, long timestampMillis,
                              @NonNull String title, @NonNull String body) {
        this.kind = kind;
        this.packageName = packageName;
        this.timestampMillis = timestampMillis;
        this.title = title;
        this.body = body;
    }

    @NonNull
    public static AppChangeFeedEntry now(@NonNull String kind, @NonNull String packageName,
                                         @NonNull String title, @NonNull String body) {
        return new AppChangeFeedEntry(kind, packageName, System.currentTimeMillis(), title, body);
    }
}
