// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fm.FmUtils;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.self.SelfUriManager;

/**
 * Finds the one APK an intent hands to App Details. Opening an APK puts it in the intent's data, while
 * sharing one with {@link Intent#ACTION_SEND} puts it in {@link Intent#EXTRA_STREAM} and the clip data,
 * so App Details used to ignore shared APKs (upstream App Manager #2047).
 * <p>
 * Explicit data wins, then the clip data, then {@link Intent#EXTRA_STREAM}. The clip data outranks the
 * extra because it holds the URIs the sender granted access to; the platform copies
 * {@link Intent#EXTRA_STREAM} into it only when the sender set no clip data of its own. Whether the chosen
 * file is readable and really an APK is decided when it is loaded, off the main thread.
 */
final class ApkIntentSource {
    private static final ApkIntentSource NONE = new ApkIntentSource(null, 0);

    @Nullable
    final Uri uri;
    /** Why the intent's payload can't be opened, or 0 when it can or when there is none. */
    @StringRes
    final int error;

    private ApkIntentSource(@Nullable Uri uri, @StringRes int error) {
        this.uri = uri;
        this.error = error;
    }

    /**
     * An intent that carries no URI at all resolves to neither a URI nor an error, so the caller's
     * other sources still apply. A share that carries no file is an error.
     */
    @NonNull
    static ApkIntentSource resolve(@NonNull Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            return new ApkIntentSource(null, R.string.apk_intent_multiple);
        }
        Uri data = intent.getData();
        if (data != null) {
            return accept(data);
        }
        ClipData clipData = intent.getClipData();
        if (clipData != null) {
            Uri clipUri = null;
            for (int i = 0; i < clipData.getItemCount(); ++i) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri == null || uri.equals(clipUri)) {
                    continue;
                }
                if (clipUri != null) {
                    return new ApkIntentSource(null, R.string.apk_intent_multiple);
                }
                clipUri = uri;
            }
            if (clipUri != null) {
                return accept(clipUri);
            }
        }
        Uri stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class);
        if (stream != null) {
            return accept(stream);
        }
        return Intent.ACTION_SEND.equals(action) ? new ApkIntentSource(null, R.string.apk_intent_unusable) : NONE;
    }

    @NonNull
    private static ApkIntentSource accept(@NonNull Uri uri) {
        String rawScheme = uri.getScheme();
        if (SelfUriManager.APP_MANAGER_SCHEME.equals(rawScheme) || SelfUriManager.AM_SCHEME.equals(rawScheme)) {
            // A deep link the parser rejected, such as one without a package. It always ended with
            // "Could not fetch package info", and still does.
            return new ApkIntentSource(null, R.string.failed_to_fetch_package_info);
        }
        Uri sanitized = uri.isOpaque() ? null : FmUtils.sanitizeContentInput(uri);
        String scheme = sanitized != null ? sanitized.getScheme() : null;
        if (ContentResolver.SCHEME_CONTENT.equals(scheme) || ContentResolver.SCHEME_FILE.equals(scheme)) {
            return new ApkIntentSource(sanitized, 0);
        }
        return new ApkIntentSource(null, R.string.apk_intent_unusable);
    }
}
