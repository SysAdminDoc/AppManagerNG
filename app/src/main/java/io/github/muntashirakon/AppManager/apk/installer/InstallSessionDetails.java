// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.io.Paths;

/**
 * What the checksum prompt shows about a session besides the checksum: the app's name, the file
 * it came from, and the base APK to compare with the installed copy.
 *
 * <p>Kept in this process and looked up by session id, never carried in the prompt's extras:
 * {@link PackageInstallerActivity} is exported, so a path read from its intent could have come
 * from another app.
 */
public final class InstallSessionDetails {
    private static final String TAG = InstallSessionDetails.class.getSimpleName();
    private static final Map<Integer, InstallSessionDetails> sSessions = new ConcurrentHashMap<>();

    @Nullable
    public final CharSequence appLabel;
    @Nullable
    public final String apkName;
    /** The base APK as written into the session. It exists until the session ends. */
    @Nullable
    public final File baseApk;

    public InstallSessionDetails(@Nullable CharSequence appLabel, @Nullable String apkName, @Nullable File baseApk) {
        this.appLabel = appLabel;
        this.apkName = apkName;
        this.baseApk = baseApk;
    }

    static void register(int sessionId, @NonNull InstallSessionDetails details) {
        sSessions.put(sessionId, details);
    }

    static void unregister(int sessionId) {
        sSessions.remove(sessionId);
    }

    @Nullable
    static InstallSessionDetails get(int sessionId) {
        return sSessions.get(sessionId);
    }

    /**
     * The name of the file an install was opened from, or {@code null} when it didn't come from a
     * file (reinstalling an installed app).
     */
    @WorkerThread
    @Nullable
    static String apkNameOf(@Nullable ApkSource source) {
        Uri uri = source != null ? source.getUri() : null;
        if (uri == null) {
            return null;
        }
        try {
            // A document's display name, which the last path segment of a content URI often isn't
            String name = Paths.get(uri).getName();
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
        } catch (IllegalArgumentException | SecurityException e) {
            Log.w(TAG, "No display name for %s", e, uri);
        }
        return uri.getLastPathSegment();
    }
}
