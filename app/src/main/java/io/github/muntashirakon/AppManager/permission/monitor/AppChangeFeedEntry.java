// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;

/**
 * Immutable row in the local app-change audit feed.
 */
public final class AppChangeFeedEntry {
    public static final long UNKNOWN_VERSION_CODE = -1L;

    @NonNull
    public final String kind;
    @NonNull
    public final String packageName;
    public final long timestampMillis;
    @NonNull
    public final String title;
    @NonNull
    public final String body;
    public final long beforeVersionCode;
    public final long afterVersionCode;

    public AppChangeFeedEntry(@NonNull String kind, @NonNull String packageName, long timestampMillis,
                              @NonNull String title, @NonNull String body) {
        this(kind, packageName, timestampMillis, title, body,
                UNKNOWN_VERSION_CODE, UNKNOWN_VERSION_CODE);
    }

    public AppChangeFeedEntry(@NonNull String kind, @NonNull String packageName, long timestampMillis,
                              @NonNull String title, @NonNull String body,
                              long beforeVersionCode, long afterVersionCode) {
        this.kind = kind;
        this.packageName = packageName;
        this.timestampMillis = timestampMillis;
        this.title = title;
        this.body = body;
        this.beforeVersionCode = beforeVersionCode;
        this.afterVersionCode = afterVersionCode;
    }

    @NonNull
    public static AppChangeFeedEntry now(@NonNull String kind, @NonNull String packageName,
                                         @NonNull String title, @NonNull String body) {
        return new AppChangeFeedEntry(kind, packageName, System.currentTimeMillis(), title, body);
    }

    public boolean hasVersionContext() {
        return beforeVersionCode >= 0 || afterVersionCode >= 0;
    }
}
