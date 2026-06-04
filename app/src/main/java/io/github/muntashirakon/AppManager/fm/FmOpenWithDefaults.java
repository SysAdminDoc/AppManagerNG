// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Locale;

import io.github.muntashirakon.io.Path;

public final class FmOpenWithDefaults {
    @VisibleForTesting
    static final String PREF_NAME = "fm_open_with_defaults";
    private static final String KEY_FILE_PREFIX = "file:";
    private static final String KEY_EXTENSION_PREFIX = "extension:";

    private FmOpenWithDefaults() {
    }

    @NonNull
    public static Intent buildViewIntent(@NonNull Path path, @Nullable String customType) {
        int flags = Intent.FLAG_ACTIVITY_NEW_TASK;
        if (path.canRead()) {
            flags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
        }
        if (path.canWrite()) {
            flags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(FmProvider.getContentUri(path), customType != null ? customType : path.getType());
        intent.setFlags(flags);
        return intent;
    }

    @Nullable
    public static Intent getDefaultIntent(@NonNull Context context, @NonNull Path path) {
        ResolvedDefault resolvedDefault = getResolvedDefault(context, path.getUri(), path.getExtension());
        if (resolvedDefault == null) {
            return null;
        }
        Intent intent = buildViewIntent(path, null);
        resolvedDefault.handler.applyTo(intent);
        if (intent.resolveActivityInfo(context.getPackageManager(), 0) == null) {
            getPreferences(context).edit().remove(resolvedDefault.key).apply();
            return null;
        }
        return intent;
    }

    public static void setDefault(@NonNull Context context, @NonNull Path path,
                                  @NonNull OpenWithHandler handler, boolean fileOnly) {
        setDefault(context, path.getUri(), path.getExtension(), handler, fileOnly);
    }

    @VisibleForTesting
    static void setDefault(@NonNull Context context, @NonNull Uri uri, @Nullable String extension,
                           @NonNull OpenWithHandler handler, boolean fileOnly) {
        String key = fileOnly ? fileKey(uri) : extensionKey(extension);
        if (key == null) {
            key = fileKey(uri);
        }
        getPreferences(context).edit().putString(key, handler.flatten()).apply();
    }

    public static boolean hasDefault(@NonNull Context context, @NonNull Path path) {
        return getResolvedDefault(context, path.getUri(), path.getExtension()) != null;
    }

    public static void clearDefaults(@NonNull Context context, @NonNull Path path) {
        clearDefaults(context, path.getUri(), path.getExtension());
    }

    @VisibleForTesting
    static void clearDefaults(@NonNull Context context, @NonNull Uri uri, @Nullable String extension) {
        SharedPreferences.Editor editor = getPreferences(context).edit().remove(fileKey(uri));
        String extensionKey = extensionKey(extension);
        if (extensionKey != null) {
            editor.remove(extensionKey);
        }
        editor.apply();
    }

    @Nullable
    @VisibleForTesting
    static OpenWithHandler getDefault(@NonNull Context context, @NonNull Uri uri, @Nullable String extension) {
        ResolvedDefault resolvedDefault = getResolvedDefault(context, uri, extension);
        return resolvedDefault != null ? resolvedDefault.handler : null;
    }

    @Nullable
    private static ResolvedDefault getResolvedDefault(@NonNull Context context,
                                                      @NonNull Uri uri,
                                                      @Nullable String extension) {
        SharedPreferences preferences = getPreferences(context);
        String fileKey = fileKey(uri);
        OpenWithHandler fileHandler = OpenWithHandler.unflatten(preferences.getString(fileKey, null));
        if (fileHandler != null) {
            return new ResolvedDefault(fileKey, fileHandler);
        }
        String extensionKey = extensionKey(extension);
        if (extensionKey == null) {
            return null;
        }
        OpenWithHandler extensionHandler = OpenWithHandler.unflatten(preferences.getString(extensionKey, null));
        return extensionHandler != null ? new ResolvedDefault(extensionKey, extensionHandler) : null;
    }

    @NonNull
    private static SharedPreferences getPreferences(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    private static String fileKey(@NonNull Uri uri) {
        return KEY_FILE_PREFIX + uri.toString();
    }

    @Nullable
    @VisibleForTesting
    static String extensionKey(@Nullable String extension) {
        String normalized = normalizeExtension(extension);
        return normalized != null ? KEY_EXTENSION_PREFIX + normalized : null;
    }

    @Nullable
    private static String normalizeExtension(@Nullable String extension) {
        if (extension == null) {
            return null;
        }
        String normalized = extension.trim();
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        if (TextUtils.isEmpty(normalized)) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public static final class OpenWithHandler {
        @NonNull
        public final String packageName;
        @NonNull
        public final String activityName;

        public OpenWithHandler(@NonNull String packageName, @NonNull String activityName) {
            this.packageName = packageName;
            this.activityName = activityName;
        }

        public void applyTo(@NonNull Intent intent) {
            intent.setClassName(packageName, activityName);
        }

        @NonNull
        private String flatten() {
            return new ComponentName(packageName, activityName).flattenToString();
        }

        @Nullable
        private static OpenWithHandler unflatten(@Nullable String flattened) {
            if (flattened == null) {
                return null;
            }
            ComponentName componentName = ComponentName.unflattenFromString(flattened);
            if (componentName == null) {
                return null;
            }
            return new OpenWithHandler(componentName.getPackageName(), componentName.getClassName());
        }
    }

    private static final class ResolvedDefault {
        @NonNull
        final String key;
        @NonNull
        final OpenWithHandler handler;

        private ResolvedDefault(@NonNull String key, @NonNull OpenWithHandler handler) {
            this.key = key;
            this.handler = handler;
        }
    }
}
